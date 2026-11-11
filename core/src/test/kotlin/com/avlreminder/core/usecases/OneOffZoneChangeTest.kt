package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.SkipReasons
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Einmalige Reminder und Fristen nach einem Wechsel der Zeitzone (Reise, Sicherung auf einem
 * Gerät in einer anderen Zone wiederhergestellt): Sie kommen nur wieder, wenn ihr Zeitpunkt
 * wirklich verschoben wurde. Gefunden 2026-11-09: Sicherung vom S24 (MEZ) im Emulator (GMT)
 * legte für jeden erledigten einmaligen Reminder einen „verpasst“-Termin an.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OneOffZoneChangeTest {

    /** Ein Gerät, dessen Zeitzone wechseln kann; die Daten bleiben. */
    class Welt {
        val repo = FakeRepository()
        val alarms = FakeAlarms()
        var now: Instant = at(26, 8)
        var zone: ZoneId = ZONE
        val engine get() = ReminderEngine(repo, alarms, MutableClock(now, zone))
        var id = 0L

        suspend fun anlegen(schedule: Schedule) {
            id = (engine.save(reminder(schedule)) as SaveResult.Saved).id
        }
        suspend fun handeln(action: Action) {
            engine.act(repo.openOccurrence(id)!!.id, action)
        }
        suspend fun speichern(schedule: Schedule? = null, titel: String? = null) {
            val r = repo.reminders[id]!!
            val result = engine.save(r.copy(schedule = schedule ?: r.schedule, title = titel ?: r.title))
            check(result is SaveResult.Saved) { result.toString() }
        }
        fun reisen(nach: String) {
            zone = ZoneId.of(nach)
        }
        val termine: List<Occurrence> get() = repo.occurrences.values.sortedBy { it.id }
    }

    /** Montag 26.10. 9:00 (MEZ), einmalig. */
    private val einmalig = Schedule.Once(LocalDateTime.of(2026, 10, 26, 9, 0))

    /** Fällig Freitag 30.10. 12:00, Fenster Mo–Fr 9–17. */
    private val frist = Schedule.Deadline(
        due = LocalDateTime.of(2026, 10, 30, 12, 0), days = DayOfWeek.entries.take(5).toSet(),
        from = LocalTime.of(9, 0), until = LocalTime.of(17, 0),
    )

    /**
     * Ergebnis eines Falls: der offene Termin (dueAt) oder null, und wie viele Termine es gibt.
     * Ein zusätzlicher Termin ohne offenen wäre der „verpasst“-Termin aus dem Fehler.
     */
    data class Erwartet(val offen: Instant?, val termine: Int)

    @ParameterizedTest(name = "{0}")
    @MethodSource("faelle")
    fun `einmalig und Frist kommen nur nach echtem Verschieben wieder`(ablauf: suspend Welt.() -> Unit, erwartet: Erwartet) = runTest {
        val welt = Welt()
        welt.ablauf()
        // Der Start der App nach dem Wechsel.
        welt.engine.sync()
        val offen = welt.repo.openOccurrence(welt.id)
        assertEquals(erwartet, Erwartet(offen?.dueAt, welt.termine.size), welt.termine.toString())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verschobenWaehrendOffen")
    fun `ein offener einmaliger Termin wird beim Verschieben ersetzt oder verlegt`(ablauf: suspend Welt.() -> Unit, uebersprungen: Int) = runTest {
        val welt = Welt()
        welt.ablauf()
        val skipped = welt.termine.count { it.resolution == Resolution.SKIPPED && it.note == SkipReasons.SCHEDULE_CHANGED }
        assertEquals(uebersprungen, skipped, welt.termine.toString())
        assertEquals(at(26, 16), welt.repo.openOccurrence(welt.id)!!.dueAt)
    }

    @Suppress("unused") // used by @MethodSource
    private fun faelle() = listOf(
        // --- Zeitzone gewechselt: kein neuer Termin ---------------------------------------
        fall("Einmalig erledigt, Zeitzone London: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            now = at(27, 8); reisen("Europe/London")
        },
        fall("Einmalig erledigt, Zeitzone Tokio: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            now = at(27, 8); reisen("Asia/Tokyo")
        },
        fall("Einmalig übersprungen, Zeitzone London: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(einmalig); handeln(Action.Skip("keine Zeit"))
            now = at(27, 8); reisen("Europe/London")
        },
        fall("Einmalig verpasst, Zeitzone London: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(einmalig)
            now = at(27, 8); engine.sync()
            reisen("Europe/London")
        },
        fall("Einmalig erledigt, in London nur Titel geändert: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            now = at(27, 8); reisen("Europe/London"); speichern(titel = "Neuer Titel")
        },
        fall("Einmalig erledigt, in London mit gleicher Uhrzeit gespeichert: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            reisen("Europe/London"); speichern(einmalig)
        },
        fall("Frist erledigt, Zeitzone London: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(frist); handeln(Action.Complete(false))
            now = at(31, 8); reisen("Europe/London")
        },
        fall("Frist erledigt, in London nur Titel geändert: klingelt nicht erneut", Erwartet(null, 1)) {
            anlegen(frist); handeln(Action.Complete(false))
            reisen("Europe/London"); speichern(titel = "Neuer Titel")
        },
        // --- Zeitpunkt wirklich verschoben: ein neuer Termin ----------------------------
        fall("Einmalig erledigt, auf 15 Uhr verschoben: klingelt erneut", Erwartet(at(26, 15), 2)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0)))
        },
        fall("Einmalig erledigt, in London auf 15 Uhr verschoben: klingelt um 15 Uhr Londoner Zeit", Erwartet(at(26, 16), 2)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            reisen("Europe/London"); speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0)))
        },
        fall("Einmalig verschoben, danach nur Titel geändert: Termin bleibt", Erwartet(at(26, 15), 2)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0)))
            speichern(titel = "Neuer Titel")
        },
        fall("Einmalig verschoben, danach Zeitzone London: Termin bleibt einer", Erwartet(at(26, 15), 2)) {
            anlegen(einmalig); handeln(Action.Complete(false))
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0)))
            reisen("Europe/London")
        },
        fall("Frist erledigt, auf Montag 2.11. verschoben: klingelt im nächsten Fenster erneut", Erwartet(at(26, 9), 2)) {
            anlegen(frist); handeln(Action.Complete(false))
            speichern(frist.copy(due = LocalDateTime.of(2026, 11, 2, 12, 0)))
        },
    )

    @Suppress("unused") // used by @MethodSource
    private fun verschobenWaehrendOffen() = listOf(
        // Nie geklingelt: der Termin wandert mit, nichts wird übersprungen.
        named<suspend Welt.() -> Unit>("Noch nicht geklingelt, verschoben: Termin wandert, nichts übersprungen") {
            anlegen(Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0)))
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 16, 0)))
        }.let { arguments(it, 0) },
        named<suspend Welt.() -> Unit>("Erledigt, zweimal verschoben: ein Termin, nichts übersprungen") {
            anlegen(einmalig); handeln(Action.Complete(false))
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0)))
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 16, 0)))
        }.let { arguments(it, 0) },
        // Hat geklingelt: er endet als „Zeitplan geändert“, ein neuer kommt zur neuen Zeit.
        named<suspend Welt.() -> Unit>("Klingelt gerade, verschoben: alter Termin übersprungen, neuer geplant") {
            anlegen(einmalig)
            now = at(26, 9, 5); engine.sync()
            speichern(Schedule.Once(LocalDateTime.of(2026, 10, 26, 16, 0)))
        }.let { arguments(it, 1) },
    )

    private fun fall(name: String, erwartet: Erwartet, ablauf: suspend Welt.() -> Unit) =
        arguments(named(name, ablauf), erwartet)
}
