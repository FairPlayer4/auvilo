package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeGeofences
import com.avlreminder.core.FakeLocation
import com.avlreminder.core.FakePreNotices
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PostponePeriod
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.TransitionResult
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Das Verschiebe-Limit pro Woche oder Monat (Z2b, Wunsch 2026-11-13): Es zählt, wann verschoben
 * wurde, über alle Termine des Reminders; mit der neuen Woche bzw. dem neuen Monat geht es wieder.
 */
class PostponePeriodEngineTest {
    private val clock = MutableClock(at(26, 9))
    private val repo = FakeRepository()
    private val engine = ReminderEngine(repo, FakeAlarms(), clock, geofences = FakeGeofences(), location = FakeLocation(), preNotices = FakePreNotices())

    private suspend fun create(r: Reminder): Long = (engine.save(r) as SaveResult.Saved).id

    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    @Test
    fun `per week it counts the week and comes back on Monday`() = runTest {
        val id = create(
            reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 18, 0)), created = at(26, 7))
                .copy(postponeLimit = 2, postponePeriod = PostponePeriod.WEEK),
        )
        val o = open(id).id
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, Action.Postpone(at(31, 10))))
        assertEquals(1, engine.alarmView(o, early = true)!!.postponesLeft)

        clock.now = at(31, 10)
        assertTrue(engine.onAlarm(o) != null)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, Action.Postpone(at(2, 10, month = 11))))
        val usedUp = engine.alarmView(o, early = true)!!
        assertFalse(usedUp.canPostpone)
        assertEquals(0, usedUp.postponesLeft)
        assertEquals(at(2, 0, month = 11), usedUp.postponeAgainAt)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(o, Action.Postpone(at(1, 10, month = 11))))

        // A new week: twice again, though this occurrence was moved twice already.
        clock.now = at(2, 10, month = 11)
        assertTrue(engine.onAlarm(o) != null)
        val again = engine.alarmView(o)!!
        assertEquals(2, repo.occurrence(o)!!.postponeCount)
        assertTrue(again.canPostpone)
        assertEquals(2, again.postponesLeft)
        assertNull(again.postponeAgainAt)
    }

    // Never allowed is never allowed again: no "wieder ab Mo …" that Monday would not keep (Review 2026-11-18).
    @Test
    fun `with a limit of zero per week the alarm promises no date`() = runTest {
        val id = create(
            reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 18, 0)), created = at(26, 7))
                .copy(postponeLimit = 0, postponePeriod = PostponePeriod.WEEK),
        )
        val view = engine.alarmView(open(id).id, early = true)!!
        assertFalse(view.canPostpone)
        assertEquals(0, view.postponesLeft)
        assertNull(view.postponeAgainAt)
    }

    @Test
    fun `per month it counts over the occurrences until the first of the next month`() = runTest {
        clock.now = at(30, 9)
        val id = create(reminder(Schedule.Daily(LocalTime.of(18, 0)), created = at(30, 7)).copy(postponeLimit = 1, postponePeriod = PostponePeriod.MONTH))
        val first = open(id).id
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(first, Action.Postpone(at(30, 19))))
        assertEquals(at(1, 0, month = 11), engine.alarmView(first, early = true)!!.postponeAgainAt)
        engine.act(first, Action.Complete(minimumOnly = false))

        // The next occurrence: used up in October.
        val second = open(id)
        assertEquals(at(31, 18), second.dueAt)
        assertFalse(engine.alarmView(second.id, early = true)!!.canPostpone)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(second.id, Action.Postpone(at(31, 19))))
        clock.now = at(31, 9)
        engine.act(second.id, Action.Complete(minimumOnly = false))

        // November.
        clock.now = at(1, 9, month = 11)
        val third = open(id)
        assertEquals(at(1, 18, month = 11), third.dueAt)
        val view = engine.alarmView(third.id, early = true)!!
        assertTrue(view.canPostpone)
        assertEquals(1, view.postponesLeft)
    }

    @Test
    fun `the rest for tomorrow counts only when it counted before`() = runTest {
        val items = listOf(ChecklistItem(title = "Brunnen 1"), ChecklistItem(title = "Brunnen 2"))
        val id = create(
            reminder(Schedule.Daily(LocalTime.of(18, 0)), created = at(26, 7))
                .copy(checklist = items, postponeLimit = 1, postponePeriod = PostponePeriod.WEEK),
        )
        val o = open(id).id
        clock.now = at(26, 18)
        assertTrue(engine.onAlarm(o) != null)
        engine.act(o, Action.CheckItem(repo.reminder(id)!!.checklist.first().id))
        // An item done today: free.
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, Action.DeferRest(at(27, 18))))

        clock.now = at(27, 18)
        assertTrue(engine.onAlarm(o) != null)
        assertEquals(OccurrenceState.DUE, repo.occurrence(o)!!.state)
        assertTrue(engine.alarmView(o)!!.canPostpone)
        // Nothing done today: it counts, and the week is used up.
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, Action.DeferRest(at(28, 18))))
        clock.now = at(28, 18)
        assertTrue(engine.onAlarm(o) != null)
        val view = engine.alarmView(o)!!
        assertFalse(view.canPostpone)
        assertFalse(view.canDeferRest)
    }
}
