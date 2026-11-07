package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.Stats
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

/** "Zurücknehmen" after a misclick (Anf. 7.31). */
class ReopenEngineTest {
    private val clock = MutableClock(at(26, 7))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val engine = ReminderEngine(repo, alarms, clock)

    private val threeTimes = Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(19, 0)))

    private suspend fun create(schedule: Schedule = Schedule.Daily(LocalTime.of(18, 0)), edit: (com.avlreminder.core.model.Reminder) -> com.avlreminder.core.model.Reminder = { it }): Long =
        (engine.save(edit(reminder(schedule, created = at(26, 7)))) as SaveResult.Saved).id

    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    private fun assertRejected(result: TransitionResult) = assertInstanceOf(TransitionResult.Rejected::class.java, result)

    @Test
    fun `a done occurrence is open again, rings in 10 minutes and the next one goes`() = runTest {
        val id = create()
        val first = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(first.id)
        clock.now = at(26, 18, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        val next = open(id)
        assertEquals(at(27, 18), next.dueAt)

        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(first.id))
        val reopened = repo.occurrence(first.id)!!
        assertEquals(OccurrenceState.PLANNED, reopened.state)
        assertNull(reopened.resolution)
        assertNull(reopened.resolvedAt)
        assertEquals(at(26, 18, 15), reopened.currentAt)
        assertEquals(0, reopened.postponeCount)
        assertEquals(first.id, open(id).id)
        assertNull(repo.occurrence(next.id))
        assertNull(alarms.set[next.id])
        assertEquals(at(26, 18, 15), alarms.set[first.id]!!.first)
        val reopenedEvent = repo.events(first.id).last()
        assertEquals(EventType.REOPENED, reopenedEvent.type)
        assertEquals("GOAL", reopenedEvent.detail)
        // It counts nowhere until done again.
        assertEquals(0, Stats.of(repo.reminder(id)!!, repo.recentResolved(id, 10), clock.now, ZONE).total.goal)

        clock.now = at(26, 18, 15)
        assertTrue(engine.onAlarm(first.id) != null)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        assertEquals(Resolution.GOAL, repo.occurrence(first.id)!!.resolution)
        assertEquals(at(27, 18), open(id).dueAt)
        assertEquals(1, Stats.of(repo.reminder(id)!!, repo.recentResolved(id, 10), clock.now, ZONE).total.goal)
    }

    @Test
    fun `only the last one, and not once the next one has rung`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        clock.now = at(26, 8, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        val second = open(id)
        clock.now = at(26, 13)
        engine.onAlarm(second.id)
        assertRejected(engine.reopen(first.id))
        engine.act(second.id, Action.Complete(minimumOnly = false))
        assertRejected(engine.reopen(first.id))
        assertRejected(engine.reopen(open(id).id))
        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(second.id))
    }

    @Test
    fun `several times a day only until the next time`() = runTest {
        val id = create(threeTimes)
        val first = open(id)
        clock.now = at(26, 8, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        // 12:55 + 10 min would pass 13:00, where it counts as missed (VS1).
        clock.now = at(26, 12, 55)
        assertRejected(engine.reopen(first.id))
        clock.now = at(26, 12, 0)
        val next = open(id)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(first.id))
        assertNull(repo.occurrence(next.id))
        // Not answered: missed at 13:00, and 13:00 rings as usual.
        clock.now = at(26, 12, 10)
        engine.onAlarm(first.id)
        clock.now = at(26, 13)
        engine.onAlarm(first.id)
        assertEquals(Resolution.MISSED, repo.occurrence(first.id)!!.resolution)
        assertEquals(at(26, 13), open(id).dueAt)
    }

    @Test
    fun `too late at the end of the day`() = runTest {
        val id = create(Schedule.Daily(LocalTime.of(22, 0)))
        val first = open(id)
        clock.now = at(26, 23, 55)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        assertRejected(engine.reopen(first.id))
        assertEquals(OccurrenceState.DONE, repo.occurrence(first.id)!!.state)
    }

    @Test
    fun `skipped by hand can be taken back, missed and skipped by the system not`() = runTest {
        val id = create()
        val first = open(id)
        clock.now = at(26, 18)
        engine.act(first.id, Action.Skip("keine Zeit"))
        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(first.id))
        assertEquals(OccurrenceState.PLANNED, repo.occurrence(first.id)!!.state)
        assertNull(repo.occurrence(first.id)!!.note)
        assertEquals("SKIPPED", repo.events(first.id).last().detail)

        // Not answered until the end of the day: missed, stays missed.
        clock.now = at(27, 7)
        engine.sync()
        assertEquals(Resolution.MISSED, repo.occurrence(first.id)!!.resolution)
        assertRejected(engine.reopen(first.id))

        // Skipped by the system (here: paused) stays.
        val second = open(id)
        clock.now = at(27, 18, 30)
        engine.act(second.id, Action.Skip(SkipReasons.PAUSED))
        assertRejected(engine.reopen(second.id))
        // A paused reminder cannot be taken back.
        val third = open(id)
        clock.now = at(28, 18, 5)
        engine.act(third.id, Action.Complete(minimumOnly = false))
        engine.setActive(id, false)
        assertRejected(engine.reopen(third.id))
    }

    @Test
    fun `a checklist loses only the tick that completed it`() = runTest {
        val id = create { it.copy(checklist = listOf("a", "b", "c").map { t -> ChecklistItem(title = t) }) }
        val (a, b, c) = repo.reminder(id)!!.checklist
        val first = open(id)
        clock.now = at(26, 17)
        engine.act(first.id, Action.CheckItem(a.id))
        clock.now = at(26, 18)
        engine.act(first.id, Action.CheckItem(b.id))
        clock.now = at(26, 18, 5)
        engine.act(first.id, Action.CheckItem(c.id))
        assertEquals(Resolution.GOAL, repo.occurrence(first.id)!!.resolution)

        engine.reopen(first.id)
        assertEquals(listOf(a.id, b.id), engine.itemsDone(first.id).map { it.itemId })
        // The series counts from the first item, as before (C5b).
        assertEquals(at(26, 17), repo.occurrence(first.id)!!.firstProgressAt)
        assertEquals(c, engine.alarmView(first.id)!!.nextItem)
    }

    @Test
    fun `all at once goes back as a whole, earlier ticks stay`() = runTest {
        val id = create { it.copy(checklist = listOf("a", "b", "c").map { t -> ChecklistItem(title = t) }) }
        val (a) = repo.reminder(id)!!.checklist
        val first = open(id)
        clock.now = at(26, 17)
        engine.act(first.id, Action.CheckItem(a.id))
        clock.now = at(26, 18, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        engine.reopen(first.id)
        assertEquals(listOf(a.id), engine.itemsDone(first.id).map { it.itemId })
    }

    @Test
    fun `the proof given counts again at the next completion`() = runTest {
        val id = create { it.copy(proof = ProofSpec(ProofKind.EVIDENCE, textMinChars = 5)) }
        val first = open(id)
        clock.now = at(26, 18, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false, evidence = Evidence(text = "Brunnen gereinigt")))
        assertEquals(Resolution.GOAL, repo.occurrence(first.id)!!.resolution)
        engine.reopen(first.id)
        assertTrue(engine.alarmView(first.id)!!.proofCarried)
        // Without a new proof, and not "same text as last time".
        engine.act(first.id, Action.Complete(minimumOnly = false))
        assertEquals(Resolution.GOAL, repo.occurrence(first.id)!!.resolution)
        assertEquals(1, repo.proofs.count { it.occurrenceId == first.id })
        // The next occurrence asks for its own proof again.
        val next = open(id)
        assertFalse(engine.alarmView(next.id)!!.proofCarried)
    }

    @Test
    fun `done without proof carries nothing`() = runTest {
        val id = create { it.copy(proof = ProofSpec(ProofKind.EVIDENCE, textMinChars = 5)) }
        val first = open(id)
        clock.now = at(26, 18, 5)
        engine.act(first.id, Action.Complete(minimumOnly = false))
        assertEquals(Resolution.NO_PROOF, repo.occurrence(first.id)!!.resolution)
        engine.reopen(first.id)
        assertFalse(engine.alarmView(first.id)!!.proofCarried)
    }

    @Test
    fun `a preparation task taken back opens its blocker again`() = runTest {
        val main = create()
        val blocker = engine.addBlocker(main, "Filter kaufen")
        val task = (engine.planTask(blocker, LocalDateTime.of(2026, 10, 26, 12, 0)) as SaveResult.Saved).id
        clock.now = at(26, 12)
        val o = open(task)
        engine.onAlarm(o.id)
        engine.act(o.id, Action.Complete(minimumOnly = false))
        assertFalse(repo.blocker(blocker)!!.isOpen)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.reopen(o.id))
        assertTrue(repo.blocker(blocker)!!.isOpen)
        assertEquals(task, repo.blocker(blocker)!!.taskReminderId)
    }

    @Test
    fun `an open occurrence or an unknown one cannot be taken back`() = runTest {
        val id = create()
        assertRejected(engine.reopen(open(id).id))
        assertRejected(engine.reopen(999))
    }
}
