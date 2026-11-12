package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeGeofences
import com.avlreminder.core.FakeLocation
import com.avlreminder.core.FakePreNotices
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.TransitionResult
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Postponing and skipping in advance, "heute später" (Anf. 7.32). */
class AdvanceEngineTest {
    private val clock = MutableClock(at(26, 9))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val notices = FakePreNotices()
    private val location = FakeLocation()
    private val engine = ReminderEngine(repo, alarms, clock, geofences = FakeGeofences(), location = location, preNotices = notices)

    private suspend fun create(edit: (com.avlreminder.core.model.Reminder) -> com.avlreminder.core.model.Reminder = { it }): Long =
        (engine.save(edit(reminder(Schedule.Daily(LocalTime.of(18, 0)), created = at(26, 7)))) as SaveResult.Saved).id

    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    @Test
    fun `postponing in advance moves the alarm and counts against the limit`() = runTest {
        val id = create { it.copy(postponeLimit = 1) }
        val o = open(id)
        val view = engine.alarmView(o.id, early = true)!!
        assertTrue(view.canPostpone)
        assertEquals(at(26, 23), view.missedAt)

        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o.id, Action.Postpone(at(26, 19))))
        val moved = repo.occurrence(o.id)!!
        assertEquals(OccurrenceState.PLANNED, moved.state)
        assertEquals(at(26, 19), moved.currentAt)
        assertEquals(1, moved.postponeCount)
        assertFalse(moved.hasFired)
        assertEquals(at(26, 19), alarms.set[o.id]!!.first)
        assertEquals(EventType.POSTPONED, repo.events(o.id).last().type)
        // The limit is used up: no second move, in advance or later.
        assertFalse(engine.alarmView(o.id, early = true)!!.canPostpone)
        assertInstanceOf(TransitionResult.Rejected::class.java, engine.act(o.id, Action.Postpone(at(26, 20))))

        clock.now = at(26, 19)
        assertTrue(engine.onAlarm(o.id) != null)
        assertEquals(OccurrenceState.DUE, repo.occurrence(o.id)!!.state)
    }

    @Test
    fun `the notice before moves along`() = runTest {
        val id = create { it.copy(preNotice = PreNotice.Before(15)) }
        val o = open(id)
        assertEquals(at(26, 17, 45), notices.set[o.id])
        engine.act(o.id, Action.Postpone(at(26, 19)))
        assertEquals(at(26, 18, 45), notices.set[o.id])
        val digest = engine.preNotice(o.id)!!
        assertTrue(digest.title.startsWith("Heute 19:00"), digest.title)
    }

    @Test
    fun `an arrival reminder moved in advance keeps its new time and waits when away`() = runTest {
        val place = engine.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5))
        val id = create { it.copy(title = "Training", arrival = Arrival(place, WhenAway.DROP)) }
        val o = open(id)
        engine.act(o.id, Action.Postpone(at(26, 19)))
        // Home earlier than planned: no ring, it keeps 19:00.
        clock.now = at(26, 18, 30)
        assertTrue(engine.onArrival(place).isEmpty())
        assertEquals(OccurrenceState.PLANNED, repo.occurrence(o.id)!!.state)
        // Away at 19:00: waits for the arrival instead of being dropped.
        location.here = false
        clock.now = at(26, 19)
        assertNull(engine.onAlarm(o.id))
        val waiting = repo.occurrence(o.id)!!
        assertTrue(waiting.awaitsArrival)
        assertTrue(waiting.isOpen)
        // Back home: rings after the buffer (7.29 OZ4).
        clock.now = at(26, 19, 40)
        assertTrue(engine.onArrival(place).isEmpty())
        assertEquals(at(26, 19, 45), alarms.set[o.id]!!.first)
        clock.now = at(26, 19, 45)
        assertEquals(o.id, engine.onAlarm(o.id)!!.occurrence.id)
    }

    @Test
    fun `skipped or done in advance and taken back, it comes at its planned time`() = runTest {
        val id = create()
        val o = open(id)
        engine.act(o.id, Action.Skip("Termin beim Arzt"))
        assertEquals(Resolution.SKIPPED, repo.occurrence(o.id)!!.resolution)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(o.id))
        assertEquals(at(26, 18), repo.occurrence(o.id)!!.currentAt)
        assertEquals(at(26, 18), alarms.set[o.id]!!.first)

        engine.act(o.id, Action.Complete(minimumOnly = false))
        engine.reopen(o.id)
        assertEquals(at(26, 18), repo.occurrence(o.id)!!.currentAt)
    }

    @Test
    fun `the snoozing hint counts postponing too`() = runTest {
        val id = create()
        for (day in 26..28) {
            val o = open(id)
            clock.now = at(day, 9)
            engine.act(o.id, Action.Postpone(at(day, 19)))
            engine.act(o.id, Action.Postpone(at(day, 20)))
            clock.now = at(day, 20)
            engine.onAlarm(o.id)
            engine.act(o.id, Action.Complete(minimumOnly = false))
        }
        clock.now = at(28, 21)
        assertEquals(id, engine.snoozeHints().single().reminder.id)
    }
}
