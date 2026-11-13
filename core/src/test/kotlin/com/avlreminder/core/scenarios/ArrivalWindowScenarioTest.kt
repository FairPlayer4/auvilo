package com.avlreminder.core.scenarios

import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ankunft im Zeitraum mit WLAN (Anf. 7.29, design.md 6): ganze Tage mit [Simulation].
 * Training um 19:30 mit Vorlauf 90 min, also Zeitraum 18:00–19:30, Puffer 5 min, Prüfung alle 20 min.
 * 2026-10-26 ist ein Montag.
 */
class ArrivalWindowScenarioTest {
    private val sim = Simulation(at(26, 6))
    private val workdays = DayOfWeek.entries.take(5).toSet()

    private suspend fun home(): Long =
        sim.place(Place(name = "Zuhause", lat = 48.1, lon = 11.5, wifis = listOf(PlaceWifi(HOME_24), PlaceWifi(HOME_5))))

    private suspend fun training(place: Long, time: LocalTime = LocalTime.of(19, 30), arrival: Arrival = Arrival(place)): Long =
        sim.create(reminder(Schedule.Weekdays(time, workdays)).copy(title = "Training", arrival = arrival))

    private fun monday() = sim.repo.occurrences.values.single { it.dueAt == at(26, 19, 30) }

    // KRITISCH: Der Fall, der 7.29 ausgelöst hat (2026-11-04), nachgestellt.
    @Test
    fun `morgens angekommen klingelt nichts, verschoben und unklar wartet es, abends nach dem Puffer`() = runTest {
        val id = training(home())
        sim.wifi(HOME_24, at(26, 7, 12))
        assertEquals(emptyList<String>(), sim.rings)
        assertTrue(sim.diag.any { "too early" in it }, sim.diag.toString())
        sim.wifi(null, at(26, 7, 45))
        // Abends weder WLAN noch Ortsangabe: unklar, also klingelt es zur spätesten Uhrzeit.
        sim.runUntil(at(26, 19, 31))
        sim.act(id, at(26, 19, 32)) { Action.Postpone(at(26, 20, 30)) }
        sim.runUntil(at(26, 20, 31))
        assertTrue(sim.open(id).awaitsArrival)
        sim.wifi(HOME_5, at(26, 20, 34))
        sim.runUntil(at(26, 20, 45))
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN", "Mo 20:39 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `im Zeitraum angekommen klingelt es nach dem Puffer, nicht sofort`() = runTest {
        training(home())
        sim.wifi(HOME_24, at(26, 18, 40))
        assertEquals(emptyList<String>(), sim.rings)
        sim.runUntil(at(26, 18, 46))
        assertEquals(listOf("Mo 18:45 Training FULLSCREEN"), sim.rings)
    }

    // KRITISCH: F2 – wer zu Beginn schon da ist (Homeoffice), wird zur spätesten Uhrzeit erinnert.
    @Test
    fun `schon zu Beginn des Zeitraums da klingelt zur spaetesten Uhrzeit`() = runTest {
        training(home())
        sim.wifi(HOME_24, at(26, 8))
        sim.runUntil(at(26, 19, 31))
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN"), sim.rings)
        // Nach der ersten Prüfung steht fest, dass man da ist: keine weiteren Prüfungen.
        assertEquals(listOf(at(26, 18)), sim.placeChecks)
    }

    // KRITISCH: F2 im Doze (Review-Lauf 4): die Prüfung zu Beginn ist exakt, die übrigen nicht.
    @Test
    fun `im Doze kommt die erste Pruefung puenktlich und entscheidet F2`() = runTest {
        training(home())
        sim.doze = Duration.ofMinutes(15)
        sim.wifi(HOME_24, at(26, 8))
        sim.runUntil(at(26, 17, 59))
        assertTrue(sim.placeAlarms.exact.isNotEmpty(), "die Prüfung zu Beginn ist exakt")
        sim.runUntil(at(26, 19, 31))
        assertEquals(listOf(at(26, 18)), sim.placeChecks)
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `kommt die erste Pruefung trotzdem spaet, gilt F2 weiter`() = runTest {
        training(home())
        sim.exactLate = Duration.ofMinutes(12)
        sim.wifi(HOME_24, at(26, 8))
        sim.runUntil(at(26, 19, 31))
        assertEquals(listOf(at(26, 18, 12)), sim.placeChecks)
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `eine spaete Pruefung nach der ersten ist eine Ankunft, nicht F2`() = runTest {
        training(home())
        sim.doze = Duration.ofMinutes(15)
        sim.wifi(HOME_24, at(26, 18, 10), reported = false)
        sim.runUntil(at(26, 18, 41))
        // 18:00 exakt und weg, 18:20 im Doze erst 18:35: da, Alarm nach dem Puffer.
        assertEquals(listOf(at(26, 18), at(26, 18, 35)), sim.placeChecks)
        assertEquals(listOf("Mo 18:40 Training FULLSCREEN"), sim.rings)
    }

    // Zwei Reminder an einem Ort (Review-Lauf 5, Frage 4): eine Prüfung pro Ort. Die für das Training
    // um 18:20 geplante kommt im Doze erst 18:35, im Zeitraum von „Lesen“ (ab 18:30): für „Lesen“
    // ist sie die Prüfung zu Beginn, also F2; für das Training eine Ankunft.
    @Test
    fun `zwei Reminder an einem Ort, die Pruefung vor dem zweiten Zeitraum kommt darin`() = runTest {
        val place = home()
        training(place)
        sim.create(reminder(Schedule.Weekdays(LocalTime.of(20, 0), workdays)).copy(title = "Lesen", arrival = Arrival(place)))
        sim.doze = Duration.ofMinutes(15)
        sim.wifi(HOME_24, at(26, 18, 10), reported = false)
        sim.runUntil(at(26, 20, 1))
        assertEquals(listOf(at(26, 18), at(26, 18, 35)), sim.placeChecks)
        // Nobody reacts: the training escalates; what counts is the first ring of each.
        assertEquals("Mo 18:40 Training FULLSCREEN", sim.rings.first())
        assertEquals("Mo 20:00 Lesen FULLSCREEN", sim.rings.first { "Lesen" in it })
    }

    // Review-Lauf 5, Frage 5: Wer nach der Ankunft die Ankunftsregel ändert, bekommt sie neu entschieden;
    // ein neuer Titel ändert nichts.
    @Test
    fun `eine geaenderte Ankunftsregel nach der Ankunft gilt neu, ein neuer Titel nicht`() = runTest {
        val place = home()
        val id = training(place)
        sim.wifi(HOME_24, at(26, 18, 40))
        assertEquals(at(26, 18, 45), sim.open(id).currentAt)

        sim.runUntil(at(26, 18, 41))
        val r = sim.repo.reminder(id)!!
        sim.engine.save(r.copy(title = "Training lang"))
        assertEquals(at(26, 18, 40), sim.open(id).arrivedAt)
        assertEquals(at(26, 18, 45), sim.open(id).currentAt)

        sim.engine.save(r.copy(arrival = Arrival(place, bufferMinutes = 15)))
        assertEquals(null, sim.open(id).arrivedAt)
        assertEquals(at(26, 19, 30), sim.open(id).currentAt)
        // Still at home: the next check (19:00) finds it, the new buffer counts.
        sim.runUntil(at(26, 19, 31))
        assertTrue(at(26, 19) in sim.placeChecks, sim.placeChecks.toString())
        assertEquals("Mo 19:15 Training FULLSCREEN", sim.rings.first())
    }

    // Review-Lauf 6, Frage 1: Verschoben und schon angekommen, dann ein anderer Ort: Seine Zeit ist
    // weg, er wartet wieder auf die Ankunft, jetzt am neuen Ort.
    @Test
    fun `verschoben angekommen und dann ein anderer Ort wartet auf die Ankunft dort`() = runTest {
        val id = training(home())
        val studio = sim.place(Place(name = "Studio", lat = 48.2, lon = 11.6, wifis = listOf(PlaceWifi(OTHER))))
        sim.runUntil(at(26, 19, 31))
        sim.act(id, at(26, 19, 32)) { Action.Postpone(at(26, 20, 30)) }
        sim.runUntil(at(26, 20, 31))
        sim.wifi(HOME_5, at(26, 20, 34))
        assertEquals(at(26, 20, 39), sim.open(id).currentAt)

        sim.runUntil(at(26, 20, 35))
        sim.engine.save(sim.repo.reminder(id)!!.copy(arrival = Arrival(studio)))
        assertTrue(sim.open(id).awaitsArrival)
        assertEquals(null, sim.open(id).arrivedAt)
        sim.runUntil(at(26, 20, 50))
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN"), sim.rings)
        sim.wifi(OTHER, at(26, 20, 50))
        sim.runUntil(at(26, 21))
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN", "Mo 20:55 Training FULLSCREEN"), sim.rings)
    }

    // KRITISCH: F1 – eine frische WLAN-Suche ohne das WLAN von Zuhause heißt klar weg: entfällt.
    @Test
    fun `zur spaetesten Uhrzeit klar weg entfaellt ohne Ton`() = runTest {
        val id = training(home())
        sim.location.here = null
        sim.scan(setOf(OTHER), at(26, 19, 25))
        sim.runUntil(at(26, 19, 35))
        assertEquals(emptyList<String>(), sim.rings)
        assertEquals(Resolution.DROPPED, monday().resolution)
        assertEquals(at(27, 19, 30), sim.open(id).dueAt)
    }

    @Test
    fun `eine alte WLAN-Suche sagt nicht klar weg, unklar klingelt es`() = runTest {
        training(home())
        sim.location.here = null
        sim.scan(setOf(OTHER), at(26, 19, 15))
        sim.runUntil(at(26, 19, 31))
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `WLAN des Orts in Reichweite ohne Verbindung ist unklar, dann zaehlt der Rueckfall`() = runTest {
        training(home())
        sim.scan(setOf(HOME_5), at(26, 19, 25))
        // Der Rückfall (grobe Ortsabfrage) kommt erst zur spätesten Uhrzeit und sagt weg.
        sim.location.here = false
        sim.runUntil(at(26, 19, 35))
        assertEquals(emptyList<String>(), sim.rings)
        assertEquals(Resolution.DROPPED, monday().resolution)
    }

    @Test
    fun `eine Verbindung, die Android nicht meldet, findet die naechste Pruefung`() = runTest {
        training(home())
        sim.wifi(HOME_24, at(26, 18, 50), reported = false)
        sim.runUntil(at(26, 19, 6))
        assertEquals(listOf(at(26, 18), at(26, 18, 20), at(26, 18, 40), at(26, 19)), sim.placeChecks)
        assertEquals(listOf("Mo 19:05 Training FULLSCREEN"), sim.rings)
    }

    // KRITISCH: OZ4 – der Puffer gilt auch über die späteste Uhrzeit hinaus, und kurz weg bricht ihn nicht ab.
    @Test
    fun `kurz vor der spaetesten Uhrzeit angekommen und wieder weg klingelt nach dem Puffer`() = runTest {
        training(home())
        sim.wifi(HOME_24, at(26, 19, 28))
        sim.wifi(null, at(26, 19, 29))
        sim.scan(setOf(OTHER), at(26, 19, 29))
        sim.runUntil(at(26, 19, 40))
        assertEquals(listOf("Mo 19:33 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `die erste Ankunft im Zeitraum zaehlt, eine zweite verschiebt nichts`() = runTest {
        training(home())
        sim.wifi(HOME_24, at(26, 18, 10))
        sim.wifi(null, at(26, 18, 12))
        sim.wifi(HOME_5, at(26, 18, 14))
        sim.runUntil(at(26, 18, 16))
        assertEquals(listOf("Mo 18:15 Training FULLSCREEN"), sim.rings)
    }

    // KRITISCH: Mehrere Reminder am selben Ort teilen sich eine Prüfung, der kürzeste Abstand gilt.
    @Test
    fun `zwei Reminder am selben Ort pruefen gemeinsam im kuerzeren Abstand`() = runTest {
        val place = home()
        training(place, arrival = Arrival(place, checkMinutes = 30))
        sim.create(
            reminder(Schedule.Weekdays(LocalTime.of(19, 0), workdays))
                .copy(title = "Wäsche", arrival = Arrival(place, leadMinutes = 60, checkMinutes = 15)),
        )
        sim.wifi(HOME_24, at(26, 18, 35), reported = false)
        sim.runUntil(at(26, 18, 55))
        assertEquals(listOf(at(26, 18), at(26, 18, 15), at(26, 18, 30), at(26, 18, 45)), sim.placeChecks)
        assertEquals(listOf("Mo 18:50 Training FULLSCREEN", "Mo 18:50 Wäsche FULLSCREEN"), sim.rings.sorted())
    }

    @Test
    fun `nach dem Verschieben pruefen die Pruefungen bis zur Ankunft weiter`() = runTest {
        val id = training(home())
        sim.runUntil(at(26, 19, 31))
        sim.act(id, at(26, 19, 32)) { Action.Postpone(at(26, 20)) }
        sim.wifi(HOME_24, at(26, 20, 30), reported = false)
        sim.runUntil(at(26, 20, 46))
        // Ab 20:00 alle 20 min: 20:20 noch weg, 20:40 da, Alarm nach dem Puffer.
        assertEquals(listOf(at(26, 18), at(26, 18, 20), at(26, 18, 40), at(26, 19), at(26, 19, 20), at(26, 20, 20), at(26, 20, 40)), sim.placeChecks)
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN", "Mo 20:45 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `trotzdem klingeln klingelt zur spaetesten Uhrzeit, eine fruehere Ankunft im Zeitraum vorher`() = runTest {
        val place = home()
        training(place, arrival = Arrival(place, WhenAway.RING))
        sim.scan(setOf(OTHER), at(26, 19, 25))
        sim.runUntil(at(26, 19, 31))
        assertEquals(listOf("Mo 19:30 Training FULLSCREEN"), sim.rings)
        sim.runUntil(at(27, 6))
        sim.wifi(HOME_24, at(27, 18, 20))
        sim.runUntil(at(27, 18, 26))
        assertEquals("Di 18:25 Training FULLSCREEN", sim.rings.last())
    }

    @Test
    fun `wenn ich zuhause bin klingelt bei der WLAN-Verbindung sofort, ohne Pruefungen`() = runTest {
        val place = home()
        sim.create(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(place)))
        sim.runUntil(at(26, 12))
        sim.wifi(HOME_5, at(26, 12, 3))
        assertEquals(listOf("Mo 12:03 Paket FULLSCREEN"), sim.rings)
        assertEquals(emptyList<Any>(), sim.placeChecks)
    }

    @Test
    fun `Vorlauf 0 bleibt wie vor 7_29, die Ankunft am Tag klingelt sofort`() = runTest {
        val place = home()
        training(place, arrival = Arrival(place, leadMinutes = 0))
        sim.wifi(HOME_24, at(26, 7, 12))
        assertEquals(listOf("Mo 07:12 Training FULLSCREEN"), sim.rings)
        assertEquals(emptyList<Any>(), sim.placeChecks)
    }

    @Test
    fun `ein fremdes WLAN ist keine Ankunft`() = runTest {
        training(home())
        sim.wifi(OTHER, at(26, 18, 40))
        sim.runUntil(at(26, 18, 50))
        assertEquals(emptyList<String>(), sim.rings)
    }

    // design.md 6: Bearbeiten leert arrivedAt – die Ankunft gehört zum alten Zeitraum, nicht zum neuen.
    @Test
    fun `nach der Ankunft auf spaeter verlegt zaehlt die Ankunft nicht mehr`() = runTest {
        val id = training(home())
        sim.wifi(HOME_24, at(26, 18, 40))
        sim.runUntil(at(26, 18, 42))
        val edited = checkNotNull(sim.repo.reminder(id)).copy(schedule = Schedule.Weekdays(LocalTime.of(21, 0), workdays))
        sim.engine.save(edited)
        assertEquals(null, sim.open(id).arrivedAt)
        // Noch zuhause: die erste Prüfung des neuen Zeitraums (19:30) findet einen da (F2), es klingelt um 21:00.
        sim.runUntil(at(26, 21, 1))
        assertEquals(listOf("Mo 21:00 Training FULLSCREEN"), sim.rings)
    }

    private companion object {
        const val HOME_24 = "aa:bb:cc:00:00:01"
        const val HOME_5 = "AA:BB:CC:00:00:02"
        const val OTHER = "11:22:33:44:55:66"
    }
}
