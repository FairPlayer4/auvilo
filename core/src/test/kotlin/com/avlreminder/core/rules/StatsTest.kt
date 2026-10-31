package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatsTest {
    private val daily = reminder().copy(id = 1)
    private var nextId = 1L

    private fun day(date: LocalDate, hour: Int = 18): Instant = LocalDateTime.of(date, LocalTime.of(hour, 0)).atZone(ZONE).toInstant()

    private fun resolved(due: Instant, resolution: Resolution, at: Instant = due.plusSeconds(300), reminderId: Long = 1) = Occurrence(
        id = nextId++, reminderId = reminderId, dueAt = due,
        state = if (resolution in setOf(Resolution.GOAL, Resolution.MINIMUM, Resolution.NO_PROOF)) OccurrenceState.DONE else OccurrenceState.CLOSED,
        resolution = resolution, resolvedAt = at,
    )

    /** One occurrence per day from Monday 2026-10-05 on. */
    private fun days(vararg r: Resolution) = r.mapIndexed { i, res -> resolved(day(LocalDate.of(2026, 10, 5).plusDays(i.toLong())), res) }

    @Test
    fun `daily series count days, keep on skipped and allow one miss a week`() {
        val g = Resolution.GOAL
        val list = days(g, g, Resolution.SKIPPED, Resolution.MISSED, Resolution.MINIMUM, g, g, /* next week */ g, Resolution.MISSED, Resolution.MISSED, g)
        val streak = Stats.streak(daily, list, at(26, 8), ZONE)!!
        assertEquals(StreakUnit.DAYS, streak.unit)
        // 5 in the first week (one joker), Monday 6, joker, reset, 1.
        assertEquals(1, streak.current)
        assertEquals(6, streak.best)
    }

    @Test
    fun `counts per period and done before the reminder`() {
        val early = resolved(at(26, 18), Resolution.GOAL, at = at(26, 12))
        val list = listOf(
            early,
            resolved(at(27, 18), Resolution.MINIMUM),
            resolved(at(28, 18), Resolution.MISSED),
            resolved(day(LocalDate.of(2026, 10, 2)), Resolution.NO_PROOF),
            resolved(day(LocalDate.of(2026, 3, 2)), Resolution.SKIPPED),
            resolved(day(LocalDate.of(2025, 3, 2)), Resolution.DROPPED),
            resolved(at(28, 18), Resolution.GOAL, reminderId = 2),
        )
        val stats = Stats.of(daily, list, at(29, 8), ZONE)
        assertEquals(Counts(goal = 1, minimum = 1, missed = 1, early = 1), stats.week)
        assertEquals(Counts(goal = 1, minimum = 1, missed = 1, noProof = 1, early = 1), stats.month)
        assertEquals(1, stats.year.skipped)
        assertEquals(1, stats.total.dropped)
        assertEquals(2, stats.total.done)
    }

    @Test
    fun `weekly goals count weeks with the minimum, the running week only once reached`() {
        val training = daily.copy(
            schedule = Schedule.Weekdays(LocalTime.of(19, 0), DayOfWeek.entries.take(5).toSet()),
            weeklyGoal = WeeklyGoal(3, 4, 1),
        )
        val list = listOf(
            resolved(day(LocalDate.of(2026, 9, 28)), Resolution.GOAL),
            // Week of 5.10.: only missed → joker of October.
            resolved(day(LocalDate.of(2026, 10, 5)), Resolution.MISSED),
            // Week of 12.10.: only dropped → neutral.
            resolved(day(LocalDate.of(2026, 10, 12)), Resolution.DROPPED),
            resolved(day(LocalDate.of(2026, 10, 19)), Resolution.MINIMUM),
            // Running week, not yet reached.
            resolved(day(LocalDate.of(2026, 10, 26)), Resolution.MISSED),
        )
        val streak = Stats.streak(training, list, at(27, 20), ZONE)!!
        assertEquals(Streak(StreakUnit.WEEKS, 2, 2), streak)
        assertEquals(WeekStatus(training.weeklyGoal!!, 0, 0, 4), WeeklyGoals.status(training, list, LocalDate.of(2026, 10, 27), ZONE))
        assertEquals(1, WeeklyGoals.status(training, list, LocalDate.of(2026, 10, 22), ZONE)!!.minimums)
        assertNull(WeeklyGoals.status(daily, list, LocalDate.of(2026, 10, 22), ZONE))
        val reached = list + resolved(at(27, 19), Resolution.GOAL)
        assertEquals(3, Stats.streak(training, reached, at(27, 20), ZONE)!!.current)
    }

    @Test
    fun `interval series count completions at most a day late`() {
        val water = daily.copy(schedule = Schedule.AfterDone(7, LocalTime.of(9, 0)))
        val list = listOf(
            resolved(day(LocalDate.of(2026, 9, 1)), Resolution.GOAL),
            resolved(day(LocalDate.of(2026, 9, 8)), Resolution.GOAL, at = day(LocalDate.of(2026, 9, 9))),
            // Three days late: the September joker.
            resolved(day(LocalDate.of(2026, 9, 16)), Resolution.GOAL, at = day(LocalDate.of(2026, 9, 19))),
            resolved(day(LocalDate.of(2026, 9, 26)), Resolution.MISSED),
        )
        assertEquals(Streak(StreakUnit.TIMES, 0, 2), Stats.streak(water, list, at(1, 8), ZONE))
    }

    @Test
    fun `one-off reminders have no series`() {
        assertNull(Stats.streak(daily.copy(schedule = Schedule.OnArrival), emptyList(), at(1, 8), ZONE))
    }

    @Test
    fun `week status, offers left and how strict it rings`() {
        val workdays = Schedule.Weekdays(LocalTime.of(19, 0), DayOfWeek.entries.take(5).toSet())
        val thursday = LocalDate.of(2026, 10, 29)
        assertEquals(2, WeeklyGoals.offersLeft(workdays, thursday))
        assertEquals(4, WeeklyGoals.offersLeft(Schedule.Daily(LocalTime.NOON), thursday))
        assertEquals(0, WeeklyGoals.offersLeft(workdays, thursday.plusDays(2)))
        assertNull(WeeklyGoals.offerDays(Schedule.OnArrival))
        assertFalse(WeeklyGoals.isOfferDay(Schedule.OnArrival, thursday))
        assertTrue(WeeklyGoals.isRescueDay(workdays, thursday.plusDays(2)))
        assertFalse(WeeklyGoals.isRescueDay(Schedule.Daily(LocalTime.NOON), thursday.plusDays(2)))

        val goal = WeeklyGoal(3, 4, 1)
        val tight = WeekStatus(goal, done = 1, minimums = 0, offersLeft = 2)
        assertTrue(tight.tight)
        assertEquals(Strictness.STRICT, WeeklyGoals.strictness(Strictness.STRICT, tight, rescue = false))
        assertEquals(Strictness.NORMAL, WeeklyGoals.strictness(Strictness.RELAXED, tight, rescue = false))
        assertEquals(Strictness.RELAXED, WeeklyGoals.strictness(Strictness.RELAXED, tight.copy(offersLeft = 3), rescue = false))
        assertNull(WeeklyGoals.strictness(Strictness.STRICT, tight.copy(done = 3), rescue = false))
        assertEquals(Strictness.STRICT, WeeklyGoals.strictness(Strictness.RELAXED, tight.copy(done = 0), rescue = true))
        // No rescue left after Sunday 10:00.
        assertNull(WeeklyGoals.nextRescue(workdays, at(1, 11, month = 11), ZONE, null))
    }

    @Test
    fun `a weekly goal needs a fitting schedule and order`() {
        val workdays = Schedule.Weekdays(LocalTime.of(19, 0), DayOfWeek.entries.take(5).toSet())
        fun problems(schedule: Schedule, goal: WeeklyGoal) = Validation.problems(daily.copy(schedule = schedule, weeklyGoal = goal))
        assertEquals(listOf("Wochenziel nur bei täglich oder an Wochentagen"), problems(Schedule.AfterDone(2, LocalTime.NOON), WeeklyGoal(1)))
        assertEquals(listOf("Wochenziel: mindestens ≤ Ziel ≤ höchstens"), problems(workdays, WeeklyGoal(3, 2, 1)))
        assertEquals(listOf("Wochenziel: mindestens ≤ Ziel ≤ höchstens"), problems(workdays, WeeklyGoal(3, 4, 0)))
        assertEquals(listOf("Wochenziel: höchstens 5× bei diesen Tagen"), problems(workdays, WeeklyGoal(3, 6, 1)))
        assertTrue(problems(workdays, WeeklyGoal(3, 4, 1)).isEmpty())
    }
}
