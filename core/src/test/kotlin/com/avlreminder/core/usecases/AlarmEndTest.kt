package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.TransitionResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The end of an alarm is an effect of the engine like its ring (Review-Lauf 5, E17): after the
 * command is stored, for every way an occurrence stops ringing – also when the system closes it
 * (Frage 3). The callers in the app do not decide it any more.
 */
class AlarmEndTest {
    private class TransactionRepo(val inner: FakeRepository = FakeRepository()) : ReminderRepository by inner {
        var inTransaction = false
        override suspend fun <T> transaction(block: suspend () -> T): T {
            inTransaction = true
            try {
                return inner.transaction(block)
            } finally {
                inTransaction = false
            }
        }
    }

    private val clock = MutableClock(at(26, 8))
    private val repo = TransactionRepo()
    private var settings = AlertSettings()
    private val ended = mutableListOf<Long>()
    private val rung = mutableListOf<Long>()
    private var changed = 0
    private val engine = ReminderEngine(
        repo, FakeAlarms(), clock, settings = { settings },
        ringer = object : RingPort {
            override fun ring(ring: Ring) {
                rung += ring.occurrence.id
            }

            override fun end(occurrenceId: Long) {
                assertFalse(repo.inTransaction, "an alarm ends only once the command is stored")
                ended += occurrenceId
            }
        },
        changed = { changed++ },
    )

    /** A daily reminder at 18:00 that rings now. */
    private suspend fun ringing(r: com.avlreminder.core.model.Reminder = reminder()): Long {
        val id = (engine.save(r) as SaveResult.Saved).id
        val o = repo.openOccurrence(id)!!
        clock.now = o.currentAt
        engine.onAlarm(o.id)!!
        ended.clear()
        return o.id
    }

    @Test
    fun `an action ends the ringing`() = runTest {
        val o = ringing()
        engine.act(o, Action.Snooze(Duration.ofMinutes(10)))
        assertEquals(listOf(o), ended)
    }

    @Test
    fun `leaving the proof screen does not end it, the escalation goes on`() = runTest {
        val o = ringing()
        engine.startProof(o)
        ended.clear()
        engine.abortProof(o)
        assertEquals(emptyList<Long>(), ended)
    }

    @Test
    fun `missed at the end of the day it ends too (Frage 3)`() = runTest {
        val o = ringing()
        clock.now = at(27, 8)
        engine.sync()
        assertEquals(Resolution.MISSED, repo.occurrence(o)!!.resolution)
        assertEquals(listOf(o), ended)
    }

    @Test
    fun `skipped because of the emergency pause it ends too (Frage 3)`() = runTest {
        val o = ringing()
        settings = AlertSettings(alarmsPausedUntil = AlertSettings.PAUSED_FOR_GOOD, alarmsPausedFrom = at(26, 9))
        clock.now = at(27, 8)
        engine.sync()
        assertEquals(SkipReasons.EMERGENCY, repo.occurrence(o)!!.note)
        assertEquals(listOf(o), ended)
    }

    @Test
    fun `quiet until tomorrow or the next window it ends too, though still open (Review-Lauf 6)`() = runTest {
        // A checklist with progress today: the rest is carried over to tomorrow.
        val items = listOf(ChecklistItem(title = "Brunnen 1"), ChecklistItem(title = "Brunnen 2"))
        val o = ringing(reminder().copy(checklist = items))
        val first = repo.reminder(repo.occurrence(o)!!.reminderId)!!.checklist.first().id
        engine.act(o, Action.CheckItem(first))
        ended.clear()
        clock.now = at(26, 23)
        engine.sync()
        assertEquals(OccurrenceState.PLANNED, repo.occurrence(o)!!.state)
        assertEquals(listOf(o), ended)

        // A deadline whose window of today ended: quiet until tomorrow's window.
        ended.clear()
        clock.now = at(26, 9)
        val deadline = Schedule.Deadline(
            due = LocalDateTime.of(2026, 10, 30, 12, 0), days = DayOfWeek.entries.take(5).toSet(),
            from = LocalTime.of(9, 0), until = LocalTime.of(17, 0),
        )
        val d = ringing(reminder(deadline).copy(title = "Steuer"))
        clock.now = at(26, 18)
        engine.sync()
        assertTrue(repo.occurrence(d)!!.isOpen)
        assertTrue(d in ended)
    }

    @Test
    fun `deleting or pausing ends it`() = runTest {
        val o = ringing()
        val id = repo.occurrence(o)!!.reminderId
        assertTrue(engine.setActive(id, false))
        assertEquals(listOf(o), ended)
        ended.clear()
        val other = ringing(reminder().copy(title = "Lesen"))
        engine.delete(repo.occurrence(other)!!.reminderId)
        assertEquals(listOf(other), ended)
    }

    @Test
    fun `a button on a closed occurrence takes what is left away, a rejected one on an open one not`() = runTest {
        val o = ringing()
        engine.act(o, Action.Complete(minimumOnly = false))
        ended.clear()
        assertTrue(engine.act(o, Action.Snooze(Duration.ofMinutes(10))) is TransitionResult.Rejected)
        assertEquals(listOf(o), ended)
        ended.clear()
        assertTrue(engine.act(999, Action.Complete(minimumOnly = false)) is TransitionResult.Rejected)
        assertEquals(listOf(999L), ended)
        ended.clear()
        // Open, but the minimum is not offered: it keeps ringing.
        val open = ringing(reminder().copy(title = "Lesen"))
        assertTrue(engine.act(open, Action.Complete(minimumOnly = true)) is TransitionResult.Rejected)
        assertEquals(emptyList<Long>(), ended)
    }

    @Test
    fun `a snooze button on yesterday's missed occurrence takes it away (Review-Lauf 6)`() = runTest {
        val o = ringing()
        clock.now = at(27, 8)
        engine.sync()
        assertEquals(Resolution.MISSED, repo.occurrence(o)!!.resolution)
        ended.clear()
        assertTrue(engine.act(o, Action.Snooze(Duration.ofMinutes(10))) is TransitionResult.Rejected)
        assertEquals(listOf(o), ended)
    }

    @Test
    fun `a session at its maximum ends`() = runTest {
        val o = ringing(reminder().copy(session = SessionSpec(checkMinutes = 15, maxMinutes = 30, finalMinutes = 5)))
        engine.start(o)
        ended.clear()
        repeat(20) {
            clock.now = clock.now.plus(Duration.ofMinutes(5))
            engine.onAlarm(o)
        }
        // No answer after the last reminder: missed (D5).
        assertEquals(Resolution.MISSED, repo.occurrence(o)!!.resolution)
        assertTrue(o in ended)
    }

    @Test
    fun `what rings again in the same command is not ended`() = runTest {
        val o = ringing()
        rung.clear()
        // The next round of the same occurrence.
        clock.now = clock.now.plus(Duration.ofMinutes(30))
        engine.sync()
        engine.onAlarm(o)
        assertFalse(o in ended)
    }

    @Test
    fun `the app hears of each command, not of reading`() = runTest {
        val o = ringing()
        changed = 0
        engine.alarmView(o)
        engine.snoozeHints()
        assertEquals(0, changed)
        engine.act(o, Action.Snooze(Duration.ofMinutes(10)))
        assertEquals(1, changed)
    }
}
