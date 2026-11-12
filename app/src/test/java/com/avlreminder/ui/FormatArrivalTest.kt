package com.avlreminder.ui

import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** What *Heute* and the list say about an arrival (design.md 6). */
class FormatArrivalTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val home = Place(1, "Zuhause", 0.0, 0.0)
    private val work = Place(2, "Arbeit", 0.0, 0.0)
    private val places = listOf(home, work)
    private val at1930: Instant = ZonedDateTime.of(2026, 11, 11, 19, 30, 0, 0, zone).toInstant()
    private val at1835: Instant = ZonedDateTime.of(2026, 11, 11, 18, 35, 0, 0, zone).toInstant()

    private fun training(arrival: Arrival?, schedule: Schedule = Schedule.Daily(listOf(LocalTime.of(19, 30)))) =
        Reminder(id = 1, title = "Training", schedule = schedule, arrival = arrival, createdAt = at1930)

    private val planned = Occurrence(reminderId = 1, dueAt = at1930)

    @Test
    fun heuteSagtAbWannDieAnkunftZaehltUndWannSonst() {
        val cases = listOf(
            Triple("Zeitraum 90 min zuhause", training(Arrival(1, WhenAway.DROP)) to planned, "ab 18:00 bei Ankunft zuhause, sonst 19:30"),
            Triple("Zeitraum 30 min am zweiten Ort", training(Arrival(2, WhenAway.RING, leadMinutes = 30)) to planned, "ab 19:00 bei Ankunft am Ort „Arbeit“, sonst 19:30"),
            Triple("angekommen", training(Arrival(1)) to planned.copy(currentAt = at1835, arrivedAt = at1835.minusSeconds(300)), "angekommen – klingelt 18:35"),
            Triple("Vorlauf 0 wie bisher", training(Arrival(1, leadMinutes = 0)) to planned, null),
            Triple("verschoben, wartet auf Ankunft", training(Arrival(1)) to planned.copy(awaitsArrival = true), null),
            Triple("ohne Ankunft", training(null) to planned, null),
            Triple("wenn ich ankomme", training(Arrival(1, WhenAway.RING), Schedule.OnArrival) to planned, null),
        )
        for ((name, input, expected) in cases) {
            assertEquals(name, expected, Format.arrival(input.first, input.second, zone, places))
        }
    }

    @Test
    fun derPlanNenntVorlaufOrtUndWasSonstPassiert() {
        val cases = listOf(
            Triple("Zeitraum, entfällt", Arrival(1, WhenAway.DROP), "täglich 19:30 · ab 90 min vorher bei Ankunft zuhause, sonst entfällt"),
            Triple("ohne Zeitraum, klingeln", Arrival(1, WhenAway.RING, leadMinutes = 0), "täglich 19:30 · bei Ankunft zuhause, sonst klingeln"),
            Triple("zweiter Ort", Arrival(2, WhenAway.RING, leadMinutes = 60), "täglich 19:30 · ab 60 min vorher bei Ankunft am Ort „Arbeit“, sonst klingeln"),
            // Unknown places (e.g. not loaded yet) read as "zuhause" instead of failing.
            Triple("Ort unbekannt", Arrival(9, WhenAway.DROP), "täglich 19:30 · ab 90 min vorher bei Ankunft zuhause, sonst entfällt"),
        )
        for ((name, arrival, expected) in cases) assertEquals(name, expected, Format.plan(training(arrival), places))
        assertEquals("wenn ich am Ort „Arbeit“ bin", Format.plan(training(Arrival(2, WhenAway.RING), Schedule.OnArrival), places))
    }
}
