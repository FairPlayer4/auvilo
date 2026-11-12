package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeDevice
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.rules.Stats
import com.avlreminder.core.rules.TransitionResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Sitzung mit Timer im Ablauf der Engine (Anf. 7.11 D1–D7, 7.20 V2, design.md E.3). */
class SessionEngineTest {
    private val clock = MutableClock(at(26, 8))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val device = FakeDevice()
    private var settings = AlertSettings()
    private val engine = ReminderEngine(repo, alarms, clock, device = device, settings = { settings })

    /** Training at 18:00, every 10 min asked, at most 90 min, 5 min to react, goal 45 min. */
    private suspend fun training(edit: (Reminder) -> Reminder = { it }): Long =
        (engine.save(edit(reminder(Schedule.Daily(LocalTime.of(18, 0)), created = at(26, 8)).copy(session = SessionSpec(goalMinutes = 45)))) as SaveResult.Saved).id

    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    /** Rings at 18:00 and is started at [startAt]. */
    private suspend fun started(id: Long, startAt: Instant = at(26, 18, 2)): Long {
        val o = open(id)
        clock.now = at(26, 18)
        assertNotNull(engine.onAlarm(o.id))
        clock.now = startAt
        assertInstanceOf(TransitionResult.Ok::class.java, engine.start(o.id))
        return o.id
    }

    @Test
    fun `Ablauf – Start beendet den Alarm, Nachfragen kommen, Fertig haelt die Dauer fest`() = runTest {
        val id = training()
        val o = started(id)
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        // The check of a relaxed session is an exact alarm, no alarm clock.
        assertEquals(at(26, 18, 12) to AlarmKind.EXACT, alarms.set[o])

        clock.now = at(26, 18, 12)
        val check = engine.onAlarm(o)!!
        assertEquals(AlarmLevel.BANNER, check.level)
        val session = check.view.session!!
        assertFalse(session.final)
        assertEquals(SessionAnswer.STILL_ON, session.suggestion)
        assertEquals(at(26, 19, 32), session.maxAt)
        // Unanswered it asks again after the interval.
        assertEquals(at(26, 18, 22), alarms.set[o]!!.first)

        clock.now = at(26, 18, 13)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, Action.StillOn))
        assertEquals(at(26, 18, 23), alarms.set[o]!!.first)

        // D4: the goal duration reached suggests "Fertig".
        clock.now = at(26, 18, 47)
        assertEquals(SessionAnswer.DONE, engine.alarmView(o)!!.session!!.suggestion)

        clock.now = at(26, 18, 50)
        engine.act(o, Action.Complete(minimumOnly = false))
        val done = repo.occurrence(o)!!
        assertEquals(Resolution.GOAL, done.resolution)
        assertEquals(Duration.ofMinutes(48), done.sessionDuration)
        assertEquals(Duration.ofMinutes(48), engine.sessionAverage(id))
        assertEquals(at(27, 18), open(id).dueAt)
        assertEquals(listOf(EventType.STARTED, EventType.CHECK_ASKED, EventType.CHECK_ANSWERED, EventType.COMPLETED),
            repo.events(o).map { it.type }.filter { it in SESSION_EVENTS + EventType.COMPLETED })
    }

    @Test
    fun `vorab starten (V2) – der Alarm zur Uhrzeit entfaellt`() = runTest {
        val id = training()
        val o = open(id).id
        clock.now = at(26, 17)
        assertTrue(engine.alarmView(o, early = true)!!.canStart)
        engine.start(o)
        assertEquals(at(26, 17, 10), alarms.set[o]!!.first)
        clock.now = at(26, 17, 40)
        engine.act(o, Action.Complete(minimumOnly = false))
        assertEquals(at(27, 18), open(id).dueAt)
        assertTrue(repo.events(o).none { it.type == EventType.DUE })
    }

    @Test
    fun `ohne Sitzung am Reminder wird kein Start angeboten`() = runTest {
        val id = training { it.copy(session = null) }
        val o = open(id).id
        clock.now = at(26, 18)
        assertFalse(engine.onAlarm(o)!!.view.canStart)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.start(o))
    }

    @Test
    fun `maximale Dauer – letzter Reminder so streng wie moeglich, ohne Antwort verpasst`() = runTest {
        val id = training()
        val o = started(id, at(26, 18))
        clock.now = at(26, 19, 30)
        val last = engine.onAlarm(o)!!
        assertTrue(last.view.session!!.final)
        // From 18:00 on it may be loud.
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, last.level)
        assertEquals(at(26, 19, 35) to AlarmKind.ALARM_CLOCK, alarms.set[o])
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(o, Action.StillOn))

        clock.now = at(26, 19, 35)
        assertNull(engine.onAlarm(o))
        assertEquals(Resolution.MISSED, repo.occurrence(o)!!.resolution)
        assertEquals(at(27, 18), open(id).dueAt)
    }

    @Test
    fun `maximale Dauer mit erledigtem Punkt – Minimum (D5)`() = runTest {
        val id = training { it.copy(checklist = listOf(ChecklistItem(title = "Aufwärmen"), ChecklistItem(title = "Laufen"))) }
        val o = started(id, at(26, 18))
        clock.now = at(26, 18, 20)
        engine.act(o, Action.CheckItem(repo.reminder(id)!!.checklist.first().id))
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        clock.now = at(26, 19, 30)
        engine.onAlarm(o)
        clock.now = at(26, 19, 35)
        engine.onAlarm(o)
        assertEquals(Resolution.MINIMUM, repo.occurrence(o)!!.resolution)
    }

    @Test
    fun `Notaus – Nachfragen still, am Maximum ohne letzten Alarm abgebrochen`() = runTest {
        val id = training()
        val o = started(id, at(26, 18))
        settings = AlertSettings(alarmsPausedUntil = at(27, 8), alarmsPausedFrom = at(26, 18, 5))
        clock.now = at(26, 18, 10)
        assertEquals(AlarmLevel.SILENT, engine.onAlarm(o)!!.level)
        clock.now = at(26, 19, 30)
        assertNull(engine.onAlarm(o))
        assertEquals(Resolution.ABORTED, repo.occurrence(o)!!.resolution)
        assertTrue(repo.events(o).none { it.type == EventType.FINAL_CALL })
    }

    @Test
    fun `Nicht stoeren – die Reaktionszeit des letzten Reminders beginnt erst ohne DND`() = runTest {
        val id = training()
        val o = started(id, at(26, 18))
        device.dnd = true
        clock.now = at(26, 19, 30)
        assertEquals(AlarmLevel.SILENT, engine.onAlarm(o)!!.level)
        clock.now = at(26, 19, 35)
        assertNotNull(engine.onAlarm(o))
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        device.dnd = false
        clock.now = at(26, 19, 40)
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, engine.onAlarm(o)!!.level)
        assertEquals(at(26, 19, 45), alarms.set[o]!!.first)
        clock.now = at(26, 19, 45)
        engine.onAlarm(o)
        assertEquals(Resolution.MISSED, repo.occurrence(o)!!.resolution)
    }

    @Test
    fun `Tagesgrenze – die Sitzung laeuft weiter und zaehlt fuer ihren Starttag`() = runTest {
        val id = training()
        val o = started(id, at(26, 22, 50))
        clock.now = at(26, 23, 5)
        // Found after the end of the day (phone off): the missed check rings, nothing is missed.
        val rings = engine.sync()
        assertEquals(listOf(o), rings.map { it.occurrence.id })
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        clock.now = at(27, 0, 30)
        engine.act(o, Action.Complete(minimumOnly = false))
        val history = Overview.history(listOf(repo.reminder(id)!!), repo.recentResolved(id, 10), ZONE)
        assertEquals(LocalDate.of(2026, 10, 26), history.single().date)
        assertEquals(at(27, 18), open(id).dueAt)
    }

    // KRITISCH: Wochengrenze (Review-Lauf 4, Frage 4): Limit, Wochenziel und Statistik zählen
    // eine Sitzung in der Woche ihres Starts, wie der Verlauf den Tag (D7).
    @Test
    fun `Wochengrenze – die Sitzung zaehlt in der Woche ihres Starts`() = runTest {
        // Sa 24.10. und So 25.10. gehören zur Woche ab Mo 19.10., Mo 26.10. beginnt die nächste.
        clock.now = at(24, 8)
        val id = (engine.save(
            reminder(Schedule.Daily(LocalTime.of(23, 0)), created = at(24, 8)).copy(
                session = SessionSpec(minimumMinutes = 20), minimumLimitPerWeek = 1,
                weeklyGoal = WeeklyGoal(target = 2, max = 7),
            ),
        ) as SaveResult.Saved).id
        suspend fun ringAndStart(at: Instant): Long {
            val o = open(id)
            clock.now = o.dueAt
            assertNotNull(engine.onAlarm(o.id))
            clock.now = at
            assertInstanceOf(TransitionResult.Ok::class.java, engine.start(o.id))
            return o.id
        }
        // Samstag: Teilschritt, das Limit der Woche ist aufgebraucht.
        val saturday = ringAndStart(at(24, 23, 5))
        clock.now = at(24, 23, 40)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(saturday, Action.Complete(minimumOnly = true)))
        // Sonntag 23:20 gestartet, Montag 0:10 beantwortet: das Limit der alten Woche gilt.
        val sunday = ringAndStart(at(25, 23, 20))
        clock.now = at(26, 0, 10)
        assertFalse(engine.alarmView(sunday)!!.minimumAllowed)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(sunday, Action.Complete(minimumOnly = true)))
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(sunday, Action.Complete(minimumOnly = false)))
        val reminder = repo.reminder(id)!!
        assertEquals(2, engine.weekStatus(reminder, LocalDate.of(2026, 10, 25))!!.done)
        assertEquals(0, engine.weekStatus(reminder, LocalDate.of(2026, 10, 26))!!.done)
        val stats = Stats.of(reminder, repo.recentResolved(id, 10), clock.now, ZONE)
        assertEquals(0, stats.week.goal + stats.week.minimum)
    }

    // Zurücknehmen nach einem Verklicken (Review-Lauf 4, Frage 2).
    @Test
    fun `Abbruch zuruecknehmen – die Sitzung laeuft mit dem alten Start weiter`() = runTest {
        val id = training()
        val o = started(id, at(26, 18, 2))
        clock.now = at(26, 18, 30)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, Action.Abort))
        assertEquals(Resolution.ABORTED, repo.occurrence(o)!!.resolution)
        assertEquals(at(27, 18), open(id).dueAt)

        clock.now = at(26, 18, 30).plusSeconds(40)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(o))
        val back = repo.occurrence(o)!!
        assertEquals(OccurrenceState.RUNNING, back.state)
        assertEquals(at(26, 18, 2), back.startedAt)
        assertNull(back.resolution)
        // The next check after the interval; the next occurrence goes until this one ends.
        assertEquals(at(26, 18, 40).plusSeconds(40), alarms.set[o]!!.first)
        assertEquals(o, open(id).id)
        assertEquals(Resolution.ABORTED.name, repo.events(o).last().detail)
    }

    @Test
    fun `Abbruch zuruecknehmen – nach dem Maximum oder unter Notaus nicht mehr`() = runTest {
        val id = training()
        val o = started(id, at(26, 18))
        clock.now = at(26, 19, 29)
        engine.act(o, Action.Abort)
        clock.now = at(26, 19, 30)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.reopen(o))

        // Notaus: am Maximum vom System abgebrochen, das Maximum ist da schon erreicht.
        val next = open(id).id
        clock.now = at(27, 18)
        assertNotNull(engine.onAlarm(next))
        assertInstanceOf(TransitionResult.Ok::class.java, engine.start(next))
        settings = AlertSettings(alarmsPausedUntil = at(28, 8), alarmsPausedFrom = at(27, 18, 5))
        clock.now = at(27, 19, 30)
        assertNull(engine.onAlarm(next))
        assertEquals(Resolution.ABORTED, repo.occurrence(next)!!.resolution)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.reopen(next))
    }

    @Test
    fun `Neustart nach dem Maximum – sync bringt sofort den letzten Reminder`() = runTest {
        val id = training()
        val o = started(id, at(26, 18))
        clock.now = at(26, 20)
        val ring = engine.sync().single()
        assertTrue(ring.view.session!!.final)
        assertEquals(at(26, 20, 5), alarms.set[o]!!.first)
    }

    @Test
    fun `mehrmals taeglich – ein Termin waehrend der Sitzung zaehlt nicht extra`() = runTest {
        clock.now = at(26, 7)
        val times = Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(19, 0)))
        val id = (engine.save(reminder(times, created = at(26, 7)).copy(session = SessionSpec(maxMinutes = 360))) as SaveResult.Saved).id
        val o = open(id).id
        clock.now = at(26, 8)
        engine.onAlarm(o)
        engine.start(o)
        clock.now = at(26, 13, 1)
        engine.sync()
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        clock.now = at(26, 13, 30)
        engine.act(o, Action.Abort)
        assertEquals(Resolution.ABORTED, repo.occurrence(o)!!.resolution)
        assertEquals(at(26, 19), open(id).dueAt)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("aenderungen")
    fun `waehrend der Sitzung – nur Texte aenderbar (D7)`(name: String, change: (Reminder) -> Reminder, allowed: Boolean) = runTest {
        val id = training()
        started(id)
        val result = engine.save(change(repo.reminder(id)!!))
        assertEquals(allowed, result is SaveResult.Saved, "$name: $result")
    }

    @Test
    fun `waehrend der Sitzung – Pausieren und Archivieren abgelehnt, nach dem Ende erlaubt`() = runTest {
        val id = training()
        val o = started(id)
        assertFalse(engine.setActive(id, false))
        assertFalse(engine.setArchived(id, true))
        assertTrue(repo.reminder(id)!!.active)
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        engine.act(o, Action.Abort)
        assertTrue(engine.setArchived(id, true))
        assertFalse(repo.reminder(id)!!.active)
    }

    @Test
    fun `Nachweis-Bildschirm in der Sitzung – die Nachfrage wartet, ueber die Tagesgrenze hinaus`() = runTest {
        val id = training()
        val o = started(id, at(26, 22, 55))
        clock.now = at(26, 22, 58)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.startProof(o))
        // The proof window (10 min) is not cut at the end of the day.
        assertEquals(at(26, 23, 8), alarms.set[o]!!.first)
        clock.now = at(26, 23, 5)
        assertNull(engine.onAlarm(o))
    }

    @Test
    fun `Uebersicht – eine laufende Sitzung steht unter laeuft, nicht unter faellig`() = runTest {
        val id = training()
        val o = started(id)
        val today = Overview.today(repo.reminders(), repo.openOccurrences(), clock.now, ZONE)
        assertEquals(listOf(o), today.running.map { it.occurrence.id })
        assertTrue(today.dueNow.isEmpty())
        assertEquals(o, Overview.next(today)?.occurrence?.id)
    }

    companion object {
        private val SESSION_EVENTS = setOf(EventType.STARTED, EventType.CHECK_ASKED, EventType.CHECK_ANSWERED, EventType.FINAL_CALL, EventType.ABORTED)

        @JvmStatic
        fun aenderungen() = listOf(
            arguments("Titel", { r: Reminder -> r.copy(title = "Laufen") }, true),
            arguments("Warum, Ziel und Minimum", { r: Reminder -> r.copy(why = "fit", goal = "5 km", minimum = "1 km") }, true),
            arguments("Uhrzeit", { r: Reminder -> r.copy(schedule = Schedule.Daily(LocalTime.of(19, 0))) }, false),
            arguments("Strenge", { r: Reminder -> r.copy(strictness = Strictness.RELAXED) }, false),
            arguments("Sitzung aus", { r: Reminder -> r.copy(session = null) }, false),
            arguments("Nachfrage-Intervall", { r: Reminder -> r.copy(session = r.session!!.copy(checkMinutes = 20)) }, false),
            arguments("pausiert über das Formular", { r: Reminder -> r.copy(active = false) }, false),
        )
    }
}
