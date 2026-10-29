package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleRulesTest {
    private val six = LocalTime.of(18, 0)

    private fun next(schedule: Schedule, now: java.time.Instant, prev: java.time.Instant? = null, done: java.time.Instant? = null, wasDone: Boolean = true) =
        ScheduleRules.nextDue(schedule, now, ZONE, prev, done, wasDone)

    @Test
    fun `once is due at its time and only once`() {
        val once = Schedule.Once(LocalDateTime.of(2026, 10, 28, 9, 0))
        assertEquals(at(28, 9), next(once, at(26, 8)))
        assertNull(next(once, at(28, 10), prev = at(28, 9)))
        // Moved to another time after it happened: due again, also when that time has passed.
        assertEquals(at(28, 9), next(once, at(28, 10), prev = at(27, 9)))
    }

    @Test
    fun `daily is due today if the time is still ahead`() {
        assertEquals(at(26, 18), next(Schedule.Daily(six), at(26, 8)))
    }

    @Test
    fun `daily is due tomorrow once the time has passed`() {
        assertEquals(at(27, 18), next(Schedule.Daily(six), at(26, 19)))
    }

    @Test
    fun `late completion does not create a backlog`() {
        // Due Monday 18:00, done only Wednesday 10:00: next is Wednesday 18:00, not Tuesday.
        assertEquals(at(28, 18), next(Schedule.Daily(six), at(28, 10), prev = at(26, 18)))
    }

    @Test
    fun `next occurrence lies after a previous one that is still in the future`() {
        // Previous was postponed into the future and resolved early: next after the previous due time.
        assertEquals(at(28, 18), next(Schedule.Daily(six), at(26, 8), prev = at(27, 18)))
    }

    @Test
    fun `weekdays skip days not selected`() {
        val mwf = Schedule.Weekdays(six, setOf(MONDAY, WEDNESDAY, FRIDAY))
        assertEquals(at(28, 18), next(mwf, at(26, 19)))
        assertEquals(at(2, 18, month = 11), next(mwf, at(30, 19)))
    }

    @Test
    fun `weekdays need at least one day`() {
        assertThrows<IllegalArgumentException> { Schedule.Weekdays(six, emptySet()) }
    }

    @Test
    fun `after done counts from the last completion`() {
        val every14 = Schedule.AfterDone(14, LocalTime.of(21, 0))
        assertEquals(at(9, 21, month = 11), next(every14, at(26, 22), prev = at(26, 21), done = at(26, 21, 30)))
    }

    @Test
    fun `after done without start day starts one interval after creation`() {
        val every14 = Schedule.AfterDone(14, LocalTime.of(21, 0))
        assertEquals(at(9, 21, month = 11), next(every14, at(26, 8)))
    }

    @Test
    fun `after done is first due on the chosen start day`() {
        val today = Schedule.AfterDone(14, LocalTime.of(21, 0), firstOn = LocalDate.of(2026, 10, 26))
        assertEquals(at(26, 21), next(today, at(26, 8)))
        val later = Schedule.AfterDone(14, LocalTime.of(21, 0), firstOn = LocalDate.of(2026, 10, 30))
        assertEquals(at(30, 21), next(later, at(26, 8)))
    }

    @Test
    fun `after done start day whose time has passed is due at the next possible time`() {
        val today = Schedule.AfterDone(14, LocalTime.of(7, 0), firstOn = LocalDate.of(2026, 10, 26))
        assertEquals(at(27, 7), next(today, at(26, 8)))
    }

    @Test
    fun `after done ignores the start day once it was done`() {
        val every14 = Schedule.AfterDone(14, LocalTime.of(21, 0), firstOn = LocalDate.of(2026, 10, 26))
        assertEquals(at(9, 21, month = 11), next(every14, at(26, 22), prev = at(26, 21), done = at(26, 21, 30)))
    }

    @Test
    fun `after done reminds the next day when it was skipped or missed`() {
        val every14 = Schedule.AfterDone(14, LocalTime.of(21, 0))
        assertEquals(at(27, 21), next(every14, at(26, 21, 30), prev = at(26, 21), done = at(12, 21), wasDone = false))
    }

    @Test
    fun `after done whose interval already passed is due at the next possible time`() {
        val every2 = Schedule.AfterDone(2, LocalTime.of(9, 0))
        assertEquals(at(27, 9), next(every2, at(26, 10), prev = at(20, 9), done = at(20, 9)))
    }

    @Test
    fun `after done needs a positive interval`() {
        assertThrows<IllegalArgumentException> { Schedule.AfterDone(0, six) }
    }
}
