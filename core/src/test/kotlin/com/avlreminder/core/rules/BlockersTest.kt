package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BlockersTest {
    private fun ready(text: String?, unknown: Boolean = false) =
        PrepAnswer(reminderId = 1, question = Prep.READY, text = text, unknown = unknown, at = at(26, 8))

    private fun occurrence(resolution: Resolution? = null, state: OccurrenceState = OccurrenceState.CLOSED, postponed: Int = 0, note: String? = null) =
        Occurrence(reminderId = 1, dueAt = at(26, 18), state = state, resolution = resolution, postponeCount = postponed, note = note)

    @Test
    fun `what is missing for a start is a blocker`() {
        assertEquals("Matte fehlt", Blockers.fromReady(ready(" Matte fehlt ")))
        assertNull(Blockers.fromReady(ready(Prep.READY_YES)))
        assertNull(Blockers.fromReady(ready(null, unknown = true)))
        assertNull(Blockers.fromReady(ready("  ")))
        assertNull(Blockers.fromReady(PrepAnswer(reminderId = 1, question = Prep.GOAL, text = "x", at = at(26, 8))))
    }

    @Test
    fun `tasks are suggested for the evening before, else the next full hour`() {
        assertEquals(LocalDateTime.of(2026, 10, 27, 19, 0), Blockers.suggestTaskTime(at(28, 18), at(26, 8), ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), Blockers.suggestTaskTime(at(26, 18), at(26, 8, 20), ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), Blockers.suggestTaskTime(null, at(26, 8, 20), ZONE))
    }

    @Test
    fun `a blocker solved after the last occurrence counts`() {
        val solved = Blocker(reminderId = 1, text = "x", createdAt = at(26, 8), solvedAt = at(26, 12))
        assertTrue(Blockers.solvedSince(listOf(solved), at(26, 10)))
        assertFalse(Blockers.solvedSince(listOf(solved), at(26, 13)))
        assertFalse(Blockers.solvedSince(listOf(solved.copy(solvedAt = null)), at(26, 10)))
    }

    @Test
    fun `postponed, skipped and missed occurrences in a row are stuck, done ends the streak`() {
        val history = listOf(
            occurrence(state = OccurrenceState.DUE) to 3,
            occurrence(Resolution.MISSED) to 0,
            occurrence(Resolution.SKIPPED, note = "keine Lust") to 0,
            occurrence(Resolution.SKIPPED, note = SkipReasons.PAUSED) to 0,
            occurrence(Resolution.DROPPED) to 0,
            occurrence(Resolution.GOAL, state = OccurrenceState.DONE) to 0,
            occurrence(Resolution.MISSED) to 0,
        )
        assertEquals(3, Blockers.stuckStreak(history))
        assertEquals(1, Blockers.stuckStreak(listOf(occurrence(state = OccurrenceState.PLANNED, postponed = 1) to 0)))
        assertEquals(0, Blockers.stuckStreak(listOf(occurrence(state = OccurrenceState.DUE) to 2)))
        assertEquals(0, Blockers.stuckStreak(emptyList()))
    }

    @Test
    fun `the notice comes the evening before or some minutes before`() {
        val daily = reminder()
        val o = Occurrence(reminderId = 1, dueAt = at(27, 18))
        assertNull(PreNotices.at(daily, o, ZONE))
        assertEquals(at(26, 19), PreNotices.at(daily.copy(preNotice = PreNotice.Evening(LocalTime.of(19, 0))), o, ZONE))
        assertEquals(at(27, 17, 30), PreNotices.at(daily.copy(preNotice = PreNotice.Before(30)), o, ZONE))
        assertNull(PreNotices.at(daily.copy(preNotice = PreNotice.Before(30)), o.copy(state = OccurrenceState.DUE), ZONE))
        val arrival = daily.copy(schedule = Schedule.OnArrival, preNotice = PreNotice.Before(30))
        assertNull(PreNotices.at(arrival, o, ZONE))
    }

    @Test
    fun `the notice names the first step and the open blockers`() {
        val blockers = listOf(
            Blocker(reminderId = 1, text = "Matte kaufen", createdAt = at(26, 8)),
            Blocker(reminderId = 1, text = "erledigt", createdAt = at(26, 8), solvedAt = at(26, 9)),
        )
        val r = reminder()
        val (title, lines) = PreNotices.text(r, Occurrence(reminderId = 1, dueAt = at(27, 18)), "Schuhe an", blockers, at(26, 19), ZONE)
        assertEquals("Morgen 18:00: Katzenbrunnen", title)
        assertEquals(listOf("Erster Schritt: Schuhe an", "Noch offen: Matte kaufen"), lines)
        assertEquals("Heute 18:00: Katzenbrunnen", PreNotices.text(r, Occurrence(reminderId = 1, dueAt = at(27, 18)), null, emptyList(), at(27, 9), ZONE).first)
        assertEquals("2026-10-29 18:00: Katzenbrunnen", PreNotices.text(r, Occurrence(reminderId = 1, dueAt = at(29, 18)), " ", emptyList(), at(27, 9), ZONE).first)
    }

    @Test
    fun `validation knows deadlines and notices`() {
        val d = Schedule.Deadline(LocalDateTime.of(2026, 10, 30, 12, 0), setOf(java.time.DayOfWeek.MONDAY), LocalTime.of(17, 0), LocalTime.of(9, 0), 2)
        val problems = Validation.problems(reminder(d).copy(arrival = com.avlreminder.core.model.Arrival(1), preNotice = PreNotice.Before(2)))
        assertEquals(listOf("Fenster: Ende nach dem Beginn", "Abstand 5 bis 480 min", "Frist ohne Ankunft", "Vorab 5 bis 1440 min vorher"), problems)
        assertTrue("Vorab-Reminder braucht eine Uhrzeit" in Validation.problems(reminder(Schedule.OnArrival).copy(preNotice = PreNotice.Before(30))))
        assertThrows<IllegalArgumentException> { AlertSettings(askBlockerAfter = 0) }
    }
}
