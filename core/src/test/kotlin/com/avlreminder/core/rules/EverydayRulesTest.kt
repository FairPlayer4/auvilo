package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.severalADay
import com.avlreminder.core.reminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EverydayRulesTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    // --- Several times a day ---------------------------------------------------------------

    @Test
    fun `times must ascend without duplicates`() {
        assertThrows<IllegalArgumentException> { Schedule.Daily(listOf(t(13), t(8))) }
        assertThrows<IllegalArgumentException> { Schedule.Daily(listOf(t(8), t(8))) }
        assertThrows<IllegalArgumentException> { Schedule.Weekdays(emptyList(), setOf(DayOfWeek.MONDAY)) }
        assertEquals(t(8), Schedule.Daily(listOf(t(8), t(13))).time)
        assertTrue(Schedule.Daily(listOf(t(8), t(13))).severalADay)
        assertFalse(Schedule.Daily(t(8)).severalADay)
        assertFalse(Schedule.OnArrival.severalADay)
    }

    @Test
    fun `a time in the gap of the clock change does not hide a later one`() {
        // 29.03.2026: 02:30 does not exist and becomes 03:30, after the 03:00 time.
        val s = Schedule.Daily(listOf(t(2, 30), t(3)))
        assertEquals(at(29, 3, month = 3), ScheduleRules.nextDue(s, at(29, 0, month = 3), ZONE))
    }

    @Test
    fun `after phone off the time that is running now still comes`() {
        val s = Schedule.Daily(listOf(t(8), t(13), t(19)))
        fun next(now: java.time.Instant, done: Boolean = false) =
            ScheduleRules.nextDue(s, now, ZONE, previousDueAt = at(26, 8), previousWasDone = done, previousMissed = !done)
        // Off from 12:50 to 13:30: 08:00 is missed only now, 13:00 still rings.
        assertEquals(at(26, 13), next(at(26, 13, 30)))
        // Off until after 19:00: the time running now, not the one before.
        assertEquals(at(26, 19), next(at(26, 19, 30)))
        // Off until the next day: today's times are over.
        assertEquals(at(27, 8), next(at(27, 7)))
        // Done: no time that has already started.
        assertEquals(at(26, 19), next(at(26, 13, 30), done = true))
    }

    @Test
    fun `weekdays with several times skip the other days`() {
        val s = Schedule.Weekdays(listOf(t(8), t(19)), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(at(26, 8), ScheduleRules.nextDue(s, at(26, 7), ZONE))
        assertEquals(at(26, 19), ScheduleRules.nextDue(s, at(26, 9), ZONE, previousDueAt = at(26, 8)))
        assertEquals(at(28, 8), ScheduleRules.nextDue(s, at(26, 20), ZONE, previousDueAt = at(26, 19)))
        assertEquals(at(26, 19), ScheduleRules.nextTime(s, at(26, 8), ZONE))
        assertEquals(at(28, 8), ScheduleRules.nextTime(s, at(26, 19), ZONE))
        assertNull(ScheduleRules.nextTime(Schedule.Daily(t(8)), at(26, 8), ZONE))
        assertNull(ScheduleRules.nextTime(Schedule.OnArrival, at(26, 8), ZONE))
    }

    @Test
    fun `done early, the next time is the one after the planned`() {
        val s = Schedule.Daily(listOf(t(8), t(13)))
        assertEquals(at(26, 13), ScheduleRules.nextDue(s, at(26, 7), ZONE, previousDueAt = at(26, 8)))
    }

    @Test
    fun `several times do not go with a weekly goal, an arrival or more than six times`() {
        val several = Schedule.Daily(listOf(t(8), t(13)))
        assertTrue("Wochenziel nur mit einer Uhrzeit am Tag" in Validation.problems(reminder(several).copy(weeklyGoal = com.avlreminder.core.model.WeeklyGoal(3))))
        assertTrue("Ankunft nur mit einer Uhrzeit am Tag" in Validation.problems(reminder(several).copy(arrival = Arrival(1))))
        val seven = Schedule.Daily((8..14).map { t(it) })
        assertTrue("Höchstens 6 Uhrzeiten am Tag" in Validation.problems(reminder(seven)))
        assertTrue(Validation.problems(reminder(several)).isEmpty())
    }

    @Test
    fun `a day counts in the series when one of its times was done`() {
        val r = reminder(Schedule.Daily(listOf(t(8), t(13), t(19))))
        fun o(day: Int, hour: Int, res: Resolution) = Occurrence(
            id = (day * 100 + hour).toLong(), reminderId = r.id, dueAt = at(day, hour),
            state = if (res == Resolution.GOAL) OccurrenceState.DONE else OccurrenceState.CLOSED, resolution = res, resolvedAt = at(day, hour, 30),
        )
        val list = listOf(
            o(26, 8, Resolution.MISSED), o(26, 13, Resolution.GOAL), o(26, 19, Resolution.MISSED),
            o(27, 8, Resolution.GOAL),
            o(28, 8, Resolution.SKIPPED), o(28, 13, Resolution.SKIPPED),
            o(29, 8, Resolution.GOAL),
        )
        assertEquals(Streak(StreakUnit.DAYS, 3, 3), Stats.streak(r, list, at(29, 20), ZONE))
    }

    // --- Weekend times ---------------------------------------------------------------------

    @Test
    fun `weekend times apply on Saturday and Sunday only`() {
        val s = AlertSettings(weekend = DayTimes(t(20), t(22)))
        assertEquals(t(22), s.on(LocalDate.of(2026, 10, 31)).dayEnd)
        assertEquals(t(20), s.on(LocalDate.of(2026, 11, 1)).loudFrom)
        assertEquals(t(23), s.on(LocalDate.of(2026, 10, 30)).dayEnd)
        assertEquals(t(23), AlertSettings().on(LocalDate.of(2026, 10, 31)).dayEnd)
        assertThrows<IllegalArgumentException> { DayTimes(t(22), t(20)) }
    }

    @Test
    fun `the end of the day comes from the day it falls on`() {
        val s = AlertSettings(weekend = DayTimes(t(20), t(22)))
        // Friday 22:30 → Friday 23:00; Saturday 22:30 → Sunday 22:00; Sunday 23:30 → Monday 23:00.
        assertEquals(at(30, 23), Escalation.missedAt(at(30, 22, 30), ZONE, s))
        assertEquals(at(1, 22, month = 11), Escalation.missedAt(at(31, 22, 30), ZONE, s))
        assertEquals(at(2, 23, month = 11), Escalation.missedAt(at(1, 23, 30, month = 11), ZONE, s))
    }

    @Test
    fun `pausing all alarms ends at its time`() {
        val s = AlertSettings(alarmsPausedUntil = at(26, 20))
        assertTrue(s.alarmsPaused(at(26, 19)))
        assertFalse(s.alarmsPaused(at(26, 20)))
        assertFalse(AlertSettings().alarmsPaused(at(26, 19)))
        assertTrue(AlertSettings(alarmsPausedUntil = AlertSettings.PAUSED_FOR_GOOD).alarmsPaused(at(26, 19)))
        assertTrue(SkipReasons.EMERGENCY in SkipReasons.SYSTEM)
    }

    // --- Snoozing again and again ----------------------------------------------------------

    private fun done(day: Int, hour: Int, minute: Int) = Occurrence(
        reminderId = 1, dueAt = at(day, 18), state = OccurrenceState.DONE, resolution = Resolution.GOAL,
        resolvedAt = at(day, hour, minute),
    )

    @Test
    fun `often means three in a row twice each, or one four times`() {
        val o = done(26, 19, 0)
        assertTrue(Snoozing.often(listOf(o to 2, o to 2, o to 3)))
        assertFalse(Snoozing.often(listOf(o to 2, o to 1, o to 3)))
        assertTrue(Snoozing.often(listOf(o to 0, o to 4, o to 0)))
        assertFalse(Snoozing.often(listOf(o to 5, o to 5)))
    }

    @Test
    fun `the suggested time is the median done time on a quarter hour`() {
        assertEquals(t(19, 15), Snoozing.doneAt(listOf(done(26, 19, 10), done(27, 19, 20), done(28, 21, 0)), ZONE))
        assertEquals(t(23, 45), Snoozing.doneAt(listOf(done(26, 23, 58)), ZONE))
        val missed = done(26, 19, 0).copy(state = OccurrenceState.CLOSED, resolution = Resolution.MISSED)
        assertNull(Snoozing.doneAt(listOf(missed), ZONE))
    }

    @Test
    fun `no hint without a time to move or a strictness to lower`() {
        val recent = listOf(done(28, 18, 5) to 2, done(27, 18, 5) to 2, done(26, 18, 5) to 2)
        val relaxed = reminder(Schedule.Daily(t(18))).copy(strictness = Strictness.RELAXED)
        // Done right at the planned time: nothing to move, nothing to lower.
        assertNull(Snoozing.hint(relaxed, recent, null, at(29, 8), ZONE))
        val strict = relaxed.copy(strictness = Strictness.STRICT)
        assertEquals(Strictness.NORMAL, Snoozing.hint(strict, recent, null, at(29, 8), ZONE)!!.relax)
        // Quiet until later, or only older occurrences: no hint.
        assertNull(Snoozing.hint(strict, recent, at(30, 8), at(29, 8), ZONE))
        assertNull(Snoozing.hint(strict, recent, at(27, 0), at(29, 8), ZONE))
        // Several a day, a one-off, paused or a preparation task: no hint.
        assertNull(Snoozing.hint(strict.copy(schedule = Schedule.Daily(listOf(t(8), t(18)))), recent, null, at(29, 8), ZONE))
        assertNull(Snoozing.hint(strict.copy(schedule = Schedule.OnArrival), recent, null, at(29, 8), ZONE))
        assertNull(Snoozing.hint(strict.copy(active = false), recent, null, at(29, 8), ZONE))
        assertNull(Snoozing.hint(strict.copy(prepFor = 2), recent, null, at(29, 8), ZONE))
        val afterDone = strict.copy(schedule = Schedule.AfterDone(14, t(17)))
        assertEquals(t(18), Snoozing.hint(afterDone, recent, null, at(29, 8), ZONE)!!.time)
    }
}
