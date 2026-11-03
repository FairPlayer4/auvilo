package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeadlinesTest {
    private val workdays = DayOfWeek.entries.take(5).toSet()

    /** Friday 2026-10-30 12:00, windows Mo–Fr 9–17. */
    private val deadline = Schedule.Deadline(
        due = LocalDateTime.of(2026, 10, 30, 12, 0), days = workdays,
        from = LocalTime.of(9, 0), until = LocalTime.of(17, 0),
    )

    @Test
    fun `a deadline needs days and a positive interval`() {
        assertThrows<IllegalArgumentException> { deadline.copy(days = emptySet()) }
        assertThrows<IllegalArgumentException> { deadline.copy(intervalMinutes = 0) }
    }

    @Test
    fun `windows lie on the chosen days between from and until`() {
        assertTrue(Deadlines.inWindow(deadline, at(26, 9), ZONE))
        assertFalse(Deadlines.inWindow(deadline, at(26, 17), ZONE))
        assertFalse(Deadlines.inWindow(deadline, at(31, 10), ZONE))
        assertEquals(at(27, 9), Deadlines.nextWindowStart(deadline, at(26, 9), ZONE))
        assertEquals(at(2, 9, month = 11), Deadlines.nextWindowStart(deadline, at(30, 17), ZONE))
        assertEquals(at(26, 17), Deadlines.windowEnd(deadline, at(26, 10), ZONE))
        assertEquals(at(27, 17), Deadlines.windowEnd(deadline, at(26, 18), ZONE))
    }

    @Test
    fun `the first ring is the next window, inside one an interval from now`() {
        assertEquals(at(26, 9), Deadlines.firstDue(deadline, at(26, 8), ZONE))
        assertEquals(at(26, 11), Deadlines.firstDue(deadline, at(26, 10), ZONE))
        assertEquals(at(27, 9), Deadlines.firstDue(deadline, at(26, 16, 30), ZONE))
    }

    @Test
    fun `a window must be left before the deadline`() {
        assertTrue(Deadlines.hasWindowBefore(deadline, at(30, 10), ZONE))
        assertFalse(Deadlines.hasWindowBefore(deadline, at(30, 11, 30), ZONE))
        assertFalse(Deadlines.hasWindowBefore(deadline, at(31, 10), ZONE))
    }

    @Test
    fun `the last window day is the last day with a window starting before the deadline`() {
        assertEquals(LocalDate.of(2026, 10, 30), Deadlines.lastWindowDay(deadline, ZONE))
        val early = deadline.copy(due = LocalDateTime.of(2026, 10, 30, 8, 0))
        assertEquals(LocalDate.of(2026, 10, 29), Deadlines.lastWindowDay(early, ZONE))
        val sunday = deadline.copy(due = LocalDateTime.of(2026, 11, 1, 12, 0))
        assertEquals(LocalDate.of(2026, 10, 30), Deadlines.lastWindowDay(sunday, ZONE))
        val saturdays = deadline.copy(days = setOf(DayOfWeek.SATURDAY), due = LocalDateTime.of(2026, 10, 30, 8, 0))
        assertEquals(LocalDate.of(2026, 10, 24), Deadlines.lastWindowDay(saturdays, ZONE))
    }

    @Test
    fun `an occurrence after the deadline is overdue`() {
        assertFalse(Deadlines.isOverdue(deadline, Occurrence(reminderId = 1, dueAt = at(30, 9)), ZONE))
        assertTrue(Deadlines.isOverdue(deadline, Occurrence(reminderId = 1, dueAt = at(2, 9, month = 11)), ZONE))
    }

    @Test
    fun `tomorrow is the next window on a later day, before the deadline unless overdue`() {
        assertEquals(at(27, 9), Deadlines.tomorrow(deadline, at(26, 10), ZONE, overdue = false))
        assertEquals(at(30, 9), Deadlines.tomorrow(deadline, at(29, 10), ZONE, overdue = false))
        assertNull(Deadlines.tomorrow(deadline, at(30, 10), ZONE, overdue = false))
        assertEquals(at(2, 9, month = 11), Deadlines.tomorrow(deadline, at(30, 10), ZONE, overdue = true))
    }

    @Test
    fun `the schedule rings once, a missed deadline again in the next window`() {
        assertEquals(at(26, 9), ScheduleRules.nextDue(deadline, at(26, 8), ZONE))
        assertNull(ScheduleRules.nextDue(deadline, at(26, 12), ZONE, previousDueAt = at(26, 9)))
        assertEquals(
            at(2, 9, month = 11),
            ScheduleRules.nextDue(deadline, at(30, 12), ZONE, previousDueAt = at(26, 9), previousWasDone = false, previousMissed = true),
        )
    }
}
