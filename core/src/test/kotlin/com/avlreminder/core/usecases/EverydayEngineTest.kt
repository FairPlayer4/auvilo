package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.DayTimes
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.rules.Snoozing
import com.avlreminder.core.rules.TransitionResult
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** V1.3a: several times a day (VS1), weekend times (VS2), snoozing hint (NT2), pausing all alarms (PS7). */
class EverydayEngineTest {
    private val clock = MutableClock(at(26, 7))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private var settings = AlertSettings()
    private val engine = ReminderEngine(repo, alarms, clock, settings = { settings })

    private val threeTimes = Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(19, 0)))

    private suspend fun create(schedule: Schedule, strictness: Strictness = Strictness.NORMAL): Long =
        (engine.save(reminder(schedule, created = at(26, 7)).copy(strictness = strictness)) as SaveResult.Saved).id

    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    // --- Several times a day ---------------------------------------------------------------

    @Test
    fun `several times a day ring one after the other`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        assertEquals(at(26, 8), first.dueAt)
        clock.now = at(26, 8, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        assertEquals(at(26, 13), open(id).dueAt)
    }

    @Test
    fun `an unanswered time is missed when the next one starts, which then rings`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        clock.now = at(26, 8)
        engine.onAlarm(first.id)
        // The round stops at the next time, not 10 minutes after it.
        clock.now = at(26, 12, 55)
        engine.onAlarm(first.id)
        assertEquals(at(26, 13), alarms.set[first.id]!!.first)
        clock.now = at(26, 13)
        assertNull(engine.onAlarm(first.id))
        assertEquals(Resolution.MISSED, repo.occurrence(first.id)!!.resolution)
        val second = open(id)
        assertEquals(at(26, 13), second.dueAt)
        assertEquals(AlarmLevel.FULLSCREEN, engine.onAlarm(second.id)!!.level)
    }

    @Test
    fun `an alarm a bit late still rings the time that just started`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        clock.now = at(26, 8)
        engine.onAlarm(first.id)
        clock.now = at(26, 13, 1)
        engine.onAlarm(first.id)
        assertEquals(at(26, 13), open(id).dueAt)
    }

    @Test
    fun `after phone off the time running now rings at once`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        clock.now = at(26, 8)
        engine.onAlarm(first.id)
        // Off from 12:50 to 13:30.
        clock.now = at(26, 13, 30)
        engine.sync()
        assertEquals(Resolution.MISSED, repo.occurrence(first.id)!!.resolution)
        val second = open(id)
        assertEquals(at(26, 13), second.dueAt)
        assertEquals(at(26, 13), alarms.set[second.id]!!.first)
    }

    @Test
    fun `snoozing and postponing stay before the next time`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        clock.now = at(26, 12, 55)
        engine.onAlarm(first.id)
        assertEquals(emptyList<Int>(), engine.snoozeOptions(repo.reminder(id)!!, open(id)))
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(first.id, Action.Snooze(Duration.ofMinutes(10))))
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(first.id, Action.Postpone(at(26, 14))))
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(first.id, Action.Postpone(at(26, 12, 58))))
    }

    private suspend fun checklist(vararg titles: String): Long =
        (engine.save(reminder(threeTimes, created = at(26, 7)).copy(checklist = titles.map { ChecklistItem(title = it) })) as SaveResult.Saved).id

    @Test
    fun `a checklist several times a day does one item per time and keeps the rest`() = runTest {
        val id = checklist("a", "b", "c")
        val (a, b, c) = repo.reminder(id)!!.checklist
        val first = open(id)
        clock.now = at(26, 8)
        assertEquals(a, engine.onAlarm(first.id)!!.nextItem)
        // One item is this time's part: done, the rest comes at 13:00, no rest for tomorrow.
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(first.id, Action.CheckItem(a.id)))
        assertEquals(Resolution.GOAL, repo.occurrence(first.id)!!.resolution)
        val second = open(id)
        assertEquals(at(26, 13), second.dueAt)
        clock.now = at(26, 13)
        assertEquals(b, engine.onAlarm(second.id)!!.nextItem)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(second.id, Action.CheckItem(a.id)))
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(second.id, Action.DeferRest(at(27, 8))))
        // A time without an item is missed, the list keeps its state.
        clock.now = at(26, 19)
        engine.onAlarm(second.id)
        assertEquals(Resolution.MISSED, repo.occurrence(second.id)!!.resolution)
        val third = open(id)
        assertEquals(b, engine.onAlarm(third.id)!!.nextItem)
        assertEquals(TransitionResult.Ok::class, engine.act(third.id, Action.CheckItem(b.id))::class)
        // The last item completes the list; the next time starts it anew.
        val fourth = open(id)
        assertEquals(at(27, 8), fourth.dueAt)
        clock.now = at(27, 8)
        assertEquals(c, engine.onAlarm(fourth.id)!!.nextItem)
        engine.act(fourth.id, Action.CheckItem(c.id))
        clock.now = at(27, 13)
        assertEquals(a, engine.onAlarm(open(id).id)!!.nextItem)
    }

    @Test
    fun `all at once completes the list of several times a day`() = runTest {
        val id = checklist("a", "b")
        val (a) = repo.reminder(id)!!.checklist
        clock.now = at(26, 8)
        engine.act(open(id).id, Action.CheckItem(a.id))
        clock.now = at(26, 13)
        val second = open(id)
        engine.onAlarm(second.id)
        engine.act(second.id, Action.Complete(minimumOnly = false))
        assertEquals(Resolution.GOAL, repo.occurrence(second.id)!!.resolution)
        clock.now = at(26, 19)
        assertEquals(a, engine.onAlarm(open(id).id)!!.nextItem)
    }

    @Test
    fun `several times a day the weekly minimum limit counts days`() = runTest {
        val id = (engine.save(reminder(threeTimes, created = at(26, 7)).copy(minimum = "kurz", minimumLimitPerWeek = 1)) as SaveResult.Saved).id
        clock.now = at(26, 8)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(open(id).id, Action.Complete(minimumOnly = true)))
        // The same day once more: still the one day.
        clock.now = at(26, 13)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(open(id).id, Action.Complete(minimumOnly = true)))
        clock.now = at(27, 8)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(open(id).id, Action.Complete(minimumOnly = true)))
    }

    @Test
    fun `one time a day still counts as missed at the end of the day`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(8, 0)))
        val o = open(id)
        clock.now = at(26, 8)
        engine.onAlarm(o.id)
        assertEquals(at(26, 8, 10), alarms.set[o.id]!!.first)
        assertEquals(listOf(10), engine.snoozeOptions(repo.reminder(id)!!, open(id)))
    }

    // --- Weekend times ---------------------------------------------------------------------

    @Test
    fun `on the weekend its own end of the day and loud time apply`() = runTest {
        settings = AlertSettings(weekend = DayTimes(LocalTime.of(20, 0), LocalTime.of(22, 0)))
        clock.now = at(31, 7) // Saturday
        val id = create(Schedule.Daily(LocalTime.of(19, 0)), Strictness.STRICT)
        val o = open(id)
        clock.now = at(31, 19)
        assertEquals(AlarmLevel.FULLSCREEN, engine.onAlarm(o.id)!!.level)
        clock.now = at(31, 20)
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, engine.onAlarm(o.id)!!.level)
        clock.now = at(31, 22)
        assertNull(engine.onAlarm(o.id))
        assertEquals(Resolution.MISSED, repo.occurrence(o.id)!!.resolution)
    }

    @Test
    fun `on weekdays the weekend times do not apply`() = runTest {
        settings = AlertSettings(weekend = DayTimes(LocalTime.of(20, 0), LocalTime.of(22, 0)))
        val id = create(Schedule.Daily(LocalTime.of(19, 0)), Strictness.STRICT)
        val o = open(id)
        clock.now = at(26, 19)
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, engine.onAlarm(o.id)!!.level)
        clock.now = at(26, 22)
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, engine.onAlarm(o.id)!!.level)
    }

    // --- Snoozing again and again ----------------------------------------------------------

    /** Three days: due at 18:00, snoozed twice each, done at 19:20. */
    private suspend fun snoozedThreeTimes(id: Long) {
        for (day in 26..28) {
            val o = open(id)
            clock.now = at(day, 18)
            engine.onAlarm(o.id)
            engine.act(o.id, Action.Snooze(Duration.ofMinutes(10)))
            clock.now = at(day, 18, 10)
            engine.onAlarm(o.id)
            engine.act(o.id, Action.Snooze(Duration.ofMinutes(10)))
            clock.now = at(day, 19, 20)
            engine.act(o.id, Action.Complete(minimumOnly = false))
        }
    }

    @Test
    fun `snoozing again and again suggests the time it was really done`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        assertTrue(engine.snoozeHints().isEmpty())
        snoozedThreeTimes(id)
        val hint = engine.snoozeHints().single()
        assertEquals(LocalTime.of(19, 15), hint.time)
        assertEquals(Strictness.RELAXED, hint.relax)

        engine.answerSnoozeHint(id, SnoozeHintChoice.TIME)
        assertEquals(Schedule.Daily(LocalTime.of(19, 15)), repo.reminder(id)!!.schedule)
        assertEquals(at(29, 19, 15), open(id).dueAt)
        assertTrue(engine.snoozeHints().isEmpty())
    }

    @Test
    fun `lowering the strictness and no thanks both start the count anew`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        snoozedThreeTimes(id)
        engine.answerSnoozeHint(id, SnoozeHintChoice.RELAX)
        assertEquals(Strictness.RELAXED, repo.reminder(id)!!.strictness)
        assertTrue(engine.snoozeHints().isEmpty())

        val other = create(Schedule.Daily(LocalTime.of(18, 0)))
        clock.now = at(26, 7)
        snoozedThreeTimes(other)
        engine.answerSnoozeHint(other, SnoozeHintChoice.DISMISS)
        assertEquals(Schedule.Daily(LocalTime.of(18, 0)), repo.reminder(other)!!.schedule)
        assertEquals(at(28, 19, 20).plus(Duration.ofDays(Snoozing.QUIET_DAYS)), repo.reminder(other)!!.hintQuietUntil)
    }

    // --- Pausing all alarms ----------------------------------------------------------------

    @Test
    fun `paused alarms ring silently and come back when the pause ends`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        val o = open(id)
        settings = AlertSettings(alarmsPausedUntil = at(26, 20))
        clock.now = at(26, 18)
        assertEquals(AlarmLevel.SILENT, engine.onAlarm(o.id)!!.level)
        assertEquals(at(26, 20), alarms.set[o.id]!!.first)
        clock.now = at(26, 20)
        assertEquals(AlarmLevel.FULLSCREEN, engine.onAlarm(o.id)!!.level)
    }

    @Test
    fun `left open while paused is skipped, not missed`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        val o = open(id)
        settings = AlertSettings(alarmsPausedUntil = AlertSettings.PAUSED_FOR_GOOD)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        assertEquals(at(26, 23), alarms.set[o.id]!!.first)
        clock.now = at(26, 23)
        assertNull(engine.onAlarm(o.id))
        val closed = repo.occurrence(o.id)!!
        assertEquals(Resolution.SKIPPED, closed.resolution)
        assertEquals(SkipReasons.EMERGENCY, closed.note)
        assertNotEquals(o.id, open(id).id)
    }

    @Test
    fun `a pause that held at the end of the day skips, also when that is found later`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        settings = AlertSettings(alarmsPausedUntil = at(27, 6), alarmsPausedFrom = at(26, 17))
        // Phone off over night: the end of the day is handled after the pause.
        clock.now = at(27, 7)
        engine.sync()
        val closed = repo.occurrence(o.id)!!
        assertEquals(Resolution.SKIPPED, closed.resolution)
        assertEquals(SkipReasons.EMERGENCY, closed.note)
    }

    @Test
    fun `a pause switched on after it rang does not skip`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        // Ignored all evening, paused shortly before the end of the day.
        settings = AlertSettings(alarmsPausedUntil = at(26, 23, 58), alarmsPausedFrom = at(26, 22, 58))
        clock.now = at(26, 23)
        engine.onAlarm(o.id)
        assertEquals(Resolution.MISSED, repo.occurrence(o.id)!!.resolution)
    }

    @Test
    fun `a pause over before the end of the day does not skip`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        settings = AlertSettings(alarmsPausedUntil = at(26, 19))
        clock.now = at(26, 23)
        engine.onAlarm(o.id)
        assertEquals(Resolution.MISSED, repo.occurrence(o.id)!!.resolution)
    }

    @Test
    fun `an answer to a card that is gone changes nothing`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(18, 0)))
        assertInstanceOf(SaveResult.Invalid::class.java, engine.answerSnoozeHint(id, SnoozeHintChoice.TIME))
        assertInstanceOf(SaveResult.Invalid::class.java, engine.answerSnoozeHint(id, SnoozeHintChoice.RELAX))
        assertNull(repo.reminder(id)!!.hintQuietUntil)
    }

    @Test
    fun `several times a day do not go with a weekly goal`() = runTest {
        val result = engine.save(reminder(threeTimes, created = at(26, 7)).copy(weeklyGoal = WeeklyGoal(3)))
        assertInstanceOf(SaveResult.Invalid::class.java, result)
    }
}
