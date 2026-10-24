package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.TransitionResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderEngineTest {
    private val clock = MutableClock(at(26, 8))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val engine = ReminderEngine(repo, alarms, clock)

    private suspend fun create(r: com.avlreminder.core.model.Reminder = reminder()): Long =
        (engine.save(r) as SaveResult.Saved).id

    private suspend fun open(reminderId: Long) = repo.openOccurrence(reminderId)!!
    private fun events(type: EventType) = repo.events.filter { it.type == type }

    @Test
    fun `saving a reminder plans its first occurrence and alarm`() = runTest {
        val id = create()
        val o = open(id)
        assertEquals(at(26, 18), o.dueAt)
        assertEquals(at(26, 18) to AlarmKind.ALARM_CLOCK, alarms.set[o.id])
        assertEquals(1, events(EventType.CREATED).size)
    }

    @Test
    fun `relaxed reminders use a plain exact alarm`() = runTest {
        val id = create(reminder().copy(strictness = Strictness.RELAXED))
        assertEquals(AlarmKind.EXACT, alarms.set[open(id).id]!!.second)
    }

    @Test
    fun `invalid reminders are not saved`() = runTest {
        val result = engine.save(reminder().copy(title = ""))
        assertEquals(SaveResult.Invalid(listOf("Titel fehlt")), result)
        assertTrue(repo.reminders.isEmpty())
    }

    @Test
    fun `a one-off reminder in the past is rejected`() = runTest {
        val result = engine.save(reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 7, 0))))
        assertTrue(result is SaveResult.Invalid)
    }

    @Test
    fun `changing the schedule moves an occurrence that has not started`() = runTest {
        val id = create()
        val before = open(id)
        create(repo.reminders[id]!!.copy(schedule = Schedule.Daily(LocalTime.of(19, 0))))
        val after = open(id)
        assertEquals(before.id, after.id)
        assertEquals(at(26, 19), after.dueAt)
        assertEquals(at(26, 19), alarms.set[after.id]!!.first)
    }

    @Test
    fun `saving unchanged keeps the occurrence`() = runTest {
        val id = create()
        val before = open(id)
        create(repo.reminders[id]!!.copy(title = "Neu"))
        assertEquals(before, open(id))
    }

    @Test
    fun `alarm fires the occurrence and plans the next round`() = runTest {
        val id = create(reminder().copy(strictness = Strictness.STRICT))
        val o = open(id)
        clock.now = at(26, 18)
        val ring = engine.onAlarm(o.id)!!
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, ring.level)
        assertEquals(OccurrenceState.DUE, ring.occurrence.state)
        assertEquals(at(26, 18, 10), alarms.set[o.id]!!.first)
    }

    @Test
    fun `a second alarm escalates`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        clock.now = at(26, 18, 10)
        val ring = engine.onAlarm(o.id)!!
        assertEquals(OccurrenceState.ESCALATING, ring.occurrence.state)
        assertEquals(AlarmLevel.FULLSCREEN, ring.level)
    }

    @Test
    fun `the last round before the deadline waits for the deadline`() = runTest {
        val id = create(reminder(Schedule.Daily(LocalTime.of(20, 55))))
        val o = open(id)
        clock.now = at(26, 20, 55)
        engine.onAlarm(o.id)
        assertEquals(at(26, 21), alarms.set[o.id]!!.first)
    }

    @Test
    fun `no reaction until the window ends counts as missed`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        clock.now = at(26, 21)
        assertNull(engine.onAlarm(o.id))
        assertEquals(Resolution.MISSED, repo.occurrences[o.id]!!.resolution)
        assertEquals(at(27, 18), open(id).dueAt)
    }

    @Test
    fun `relaxed reminders show a banner and repeat every ten minutes`() = runTest {
        val id = create(reminder().copy(strictness = Strictness.RELAXED))
        val o = open(id)
        clock.now = at(26, 18)
        assertEquals(AlarmLevel.BANNER, engine.onAlarm(o.id)!!.level)
        assertEquals(at(26, 18, 10), alarms.set[o.id]!!.first)
    }

    @Test
    fun `stale or early alarms show nothing`() = runTest {
        val id = create()
        val o = open(id)
        assertNull(engine.onAlarm(999))
        clock.now = at(26, 17)
        assertNull(engine.onAlarm(o.id))
        assertEquals(at(26, 18), alarms.set[o.id]!!.first)
        engine.setActive(id, false)
        clock.now = at(26, 18)
        assertNull(engine.onAlarm(o.id))
    }

    @Test
    fun `alarms for resolved occurrences show nothing`() = runTest {
        val id = create()
        val o = open(id)
        engine.act(o.id, Action.Complete(false, true))
        assertNull(engine.onAlarm(o.id))
    }

    @Test
    fun `alarm of a deleted reminder shows nothing`() = runTest {
        val id = create()
        val o = open(id)
        repo.reminders.remove(id)
        assertNull(engine.onAlarm(o.id))
    }

    @Test
    fun `completing plans the next occurrence and cancels the alarm`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18, 30)
        val result = engine.act(o.id, Action.Complete(false, true))
        assertTrue(result is TransitionResult.Ok)
        assertFalse(alarms.set.containsKey(o.id))
        val next = open(id)
        assertEquals(at(27, 18), next.dueAt)
        assertEquals(at(27, 18), alarms.set[next.id]!!.first)
    }

    @Test
    fun `a photo is stored as proof`() = runTest {
        val id = create(reminder().copy(proof = ProofType.PHOTO))
        val o = open(id)
        engine.act(o.id, Action.Complete(false, proofProvided = true), photoPath = "proofs/1.jpg")
        assertEquals("proofs/1.jpg", repo.proofs.single().filePath)
        assertEquals(Resolution.GOAL, repo.occurrences[o.id]!!.resolution)
    }

    @Test
    fun `rejected actions change nothing`() = runTest {
        val id = create()
        val o = open(id)
        val result = engine.act(o.id, Action.Complete(minimumOnly = true, proofProvided = true))
        assertTrue(result is TransitionResult.Rejected)
        assertTrue(repo.occurrences[o.id]!!.isOpen)
        assertTrue(engine.act(999, Action.Miss) is TransitionResult.Rejected)
    }

    @Test
    fun `action on an orphaned occurrence is rejected`() = runTest {
        val id = create()
        val o = open(id)
        repo.reminders.remove(id)
        assertTrue(engine.act(o.id, Action.Miss) is TransitionResult.Rejected)
    }

    @Test
    fun `weekly minimum limit is counted from the history`() = runTest {
        val id = create(reminder().copy(minimum = "kurz", minimumLimitPerWeek = 1))
        clock.now = at(26, 18, 5)
        engine.act(open(id).id, Action.Complete(minimumOnly = true, proofProvided = true))
        clock.now = at(27, 18, 5)
        assertTrue(engine.act(open(id).id, Action.Complete(minimumOnly = true, proofProvided = true)) is TransitionResult.Rejected)
        assertEquals(1, engine.limits(repo.reminders[id]!!).minimumsThisWeek)
        // Next week the minimum is available again.
        clock.now = at(2, 18, 5, month = 11)
        assertEquals(0, engine.limits(repo.reminders[id]!!).minimumsThisWeek)
    }

    @Test
    fun `snooze rings again later`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        engine.act(o.id, Action.Snooze(Duration.ofMinutes(10)))
        assertEquals(at(26, 18, 10), alarms.set[o.id]!!.first)
        assertEquals(OccurrenceState.PLANNED, repo.occurrences[o.id]!!.state)
    }

    @Test
    fun `a missed alarm is detected and rings at once`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 19) // the alarm at 18:00 never came
        val rings = engine.sync()
        assertEquals(listOf(o.id), rings.map { it.occurrence.id })
        assertEquals(1, events(EventType.MISSED_DETECTED).size)
        assertEquals(OccurrenceState.DUE, repo.occurrences[o.id]!!.state)
    }

    @Test
    fun `a missed alarm detected after the deadline counts as missed`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(27, 8)
        assertTrue(engine.sync().isEmpty())
        assertEquals(Resolution.MISSED, repo.occurrences[o.id]!!.resolution)
        assertEquals(1, events(EventType.MISSED_DETECTED).size)
        assertEquals(at(27, 18), open(id).dueAt)
        assertEquals(at(27, 18), alarms.set[open(id).id]!!.first)
    }

    @Test
    fun `sync closes fired occurrences past the deadline`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        clock.now = at(26, 22)
        assertTrue(engine.sync().isEmpty())
        assertEquals(Resolution.MISSED, repo.occurrences[o.id]!!.resolution)
        assertEquals(at(27, 18), open(id).dueAt)
    }

    @Test
    fun `sync rebuilds alarms from the database`() = runTest {
        val id = create()
        alarms.set.clear() // e.g. after a reboot
        assertTrue(engine.sync().isEmpty())
        assertEquals(at(26, 18), alarms.set[open(id).id]!!.first)
    }

    @Test
    fun `sync within the grace period does not report a miss`() = runTest {
        val id = create()
        clock.now = at(26, 18, 1)
        assertTrue(engine.sync().isEmpty())
        assertTrue(events(EventType.MISSED_DETECTED).isEmpty())
        assertNotNull(alarms.set[open(id).id])
    }

    @Test
    fun `a finished one-off reminder plans nothing more`() = runTest {
        val id = create(reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 9, 0))))
        engine.act(open(id).id, Action.Complete(false, true))
        assertNull(repo.openOccurrence(id))
        assertTrue(engine.sync().isEmpty())
        assertTrue(alarms.set.isEmpty())
    }

    @Test
    fun `pausing cancels the alarm and resuming plans anew without a miss`() = runTest {
        val id = create()
        val o = open(id)
        engine.setActive(id, false)
        assertFalse(alarms.set.containsKey(o.id))
        assertTrue(engine.sync().isEmpty())
        clock.now = at(28, 12)
        engine.setActive(id, true)
        assertEquals(at(28, 18), open(id).currentAt)
        assertTrue(events(EventType.MISSED_DETECTED).isEmpty())
        assertTrue(engine.sync().isEmpty())
    }

    @Test
    fun `resuming a one-off whose time passed skips it`() = runTest {
        val id = create(reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 9, 0))))
        engine.setActive(id, false)
        clock.now = at(26, 10)
        engine.setActive(id, true)
        assertEquals(Resolution.SKIPPED, repo.occurrences.values.single().resolution)
        engine.setActive(999, true) // unknown id is ignored
    }

    @Test
    fun `resuming before the time keeps the occurrence`() = runTest {
        val id = create()
        val o = open(id)
        engine.setActive(id, false)
        engine.setActive(id, true)
        assertEquals(o, open(id))
        assertEquals(at(26, 18), alarms.set[o.id]!!.first)
    }

    @Test
    fun `deleting removes data and alarm`() = runTest {
        val id = create()
        val o = open(id)
        engine.delete(id)
        assertTrue(repo.reminders.isEmpty())
        assertFalse(alarms.set.containsKey(o.id))
    }

    @Test
    fun `after done reminders count from the actual completion`() = runTest {
        val id = create(reminder(Schedule.AfterDone(14, LocalTime.of(21, 0))))
        assertEquals(at(9, 21, month = 11), open(id).dueAt)
        clock.now = at(30, 12) // done early
        engine.act(open(id).id, Action.Complete(false, true))
        assertEquals(at(13, 21, month = 11), open(id).dueAt)
    }
}
