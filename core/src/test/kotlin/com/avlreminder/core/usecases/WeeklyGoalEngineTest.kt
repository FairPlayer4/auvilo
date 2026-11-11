package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.WeeklyGoals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import com.avlreminder.core.rules.SkipReasons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Training Mo–Fr 19:00, goal 3–4× a week, minimum 1× (design 12.3, points 1–5). */
class WeeklyGoalEngineTest {
    private val clock = MutableClock(at(26, 8))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val engine = ReminderEngine(repo, alarms, clock)

    private val workdays = DayOfWeek.entries.take(5).toSet()
    private fun training(goal: WeeklyGoal = WeeklyGoal(target = 3, max = 4, minimum = 1)): Reminder =
        reminder(Schedule.Weekdays(LocalTime.of(19, 0), workdays)).copy(title = "Training", minimum = "10 min", weeklyGoal = goal)

    private suspend fun create(r: Reminder = training()): Long = (engine.save(r) as SaveResult.Saved).id
    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    /** Rings at the occurrence's time and returns the ring. */
    private suspend fun fire(id: Long): Ring? {
        val o = open(id)
        clock.now = o.currentAt
        return engine.onAlarm(o.id)
    }

    private suspend fun doTraining(id: Long, minimum: Boolean = false) {
        fire(id)
        engine.act(open(id).id, Action.Complete(minimumOnly = minimum))
    }

    @Test
    fun `from the target on offers ring silently and end neutral, from the maximum on not at all`() = runTest {
        val id = create()
        repeat(3) { doTraining(id) }
        // Thursday: target reached.
        val ring = fire(id)!!
        assertEquals(AlarmLevel.SILENT, ring.level)
        assertTrue(ring.week!!.reached)
        val thursday = open(id)
        // No further rounds, only the end of the day.
        assertEquals(at(29, 23), alarms.set[thursday.id]!!.first)
        clock.now = at(29, 23)
        engine.onAlarm(thursday.id)
        val ended = repo.occurrences.getValue(thursday.id)
        assertEquals(Resolution.SKIPPED, ended.resolution)
        assertEquals(SkipReasons.GOAL_REACHED, ended.note)

        // Friday the fourth: then nothing more this week.
        doTraining(id)
        assertEquals(at(2, 19, month = 11), open(id).dueAt)
    }

    @Test
    fun `a tight week rings one step stricter`() = runTest {
        clock.now = at(29, 8)
        val id = create()
        // Thursday at 0: two offers left for a target of 3.
        val ring = fire(id)!!
        assertTrue(ring.week!!.tight)
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, ring.level)
    }

    @Test
    fun `a week still at 0 gets a strict rescue on Saturday and Sunday`() = runTest {
        clock.now = at(30, 8)
        val id = create()
        fire(id)
        // Friday not done: missed at the end of the day.
        clock.now = at(30, 23)
        engine.onAlarm(open(id).id)
        assertEquals(at(31, 10), open(id).dueAt)
        val saturday = fire(id)!!
        assertEquals(AlarmLevel.FULLSCREEN, saturday.level)

        clock.now = at(31, 23)
        engine.onAlarm(open(id).id)
        assertEquals(at(1, 10, month = 11), open(id).dueAt)

        // Sunday the minimum: the week is saved, Monday as usual.
        doTraining(id, minimum = true)
        assertEquals(at(2, 19, month = 11), open(id).dueAt)
        assertEquals(1, engine.weekStatus(repo.reminders.getValue(id), at(1, 12, month = 11).atZone(clock.zone).toLocalDate())!!.done)
    }

    @Test
    fun `no rescue once the minimum is reached`() = runTest {
        clock.now = at(30, 8)
        val id = create()
        doTraining(id, minimum = true)
        assertEquals(at(2, 19, month = 11), open(id).dueAt)
    }

    // KRITISCH: Alarm (zählt in der Datenbank) und Bildschirme (zählen geladene Termine)
    // zeigen denselben Wochenstand, auch an der Wochengrenze: Montag 00:30 ist in UTC noch Sonntag.
    @Test
    fun `alarm and screens count the same week`() = runTest {
        val id = create(training(WeeklyGoal(target = 3, max = 7)).copy(schedule = Schedule.Daily(LocalTime.of(0, 30))))
        doTraining(id)
        doTraining(id, minimum = true)
        repeat(5) { doTraining(id) } // Tuesday to Monday 00:30
        val r = repo.reminders.getValue(id)
        val resolved = repo.occurrences.values.filter { it.resolvedAt != null }
        for (day in (26..31).map { LocalDate.of(2026, 10, it) } + LocalDate.of(2026, 11, 1) + LocalDate.of(2026, 11, 2)) {
            assertEquals(engine.weekStatus(r, day), WeeklyGoals.status(r, resolved, day, clock.zone), "$day")
        }
    }

    @Test
    fun `the overview lists active reminders with a weekly goal`() = runTest {
        val id = create()
        create(reminder())
        doTraining(id)
        val weeks = engine.weekGoals()
        assertEquals(listOf("Training"), weeks.map { it.first.title })
        assertEquals("1 von 3–4, Minimum erreicht", Digests.week(weeks.single().second))
    }
}
