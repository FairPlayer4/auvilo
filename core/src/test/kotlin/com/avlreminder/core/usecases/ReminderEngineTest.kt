package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeDevice
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.TransitionResult
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        val id = create(reminder(Schedule.Daily(LocalTime.of(22, 55))))
        val o = open(id)
        clock.now = at(26, 22, 55)
        engine.onAlarm(o.id)
        assertEquals(at(26, 23), alarms.set[o.id]!!.first)
    }

    @Test
    fun `no reaction until the end of the day counts as missed`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        clock.now = at(26, 23)
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
        engine.act(o.id, Action.Complete(false))
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
        val result = engine.act(o.id, Action.Complete(false))
        assertTrue(result is TransitionResult.Ok)
        assertFalse(alarms.set.containsKey(o.id))
        val next = open(id)
        assertEquals(at(27, 18), next.dueAt)
        assertEquals(at(27, 18), alarms.set[next.id]!!.first)
    }

    @Test
    fun `a photo is stored as proof`() = runTest {
        val id = create(reminder().copy(proof = ProofSpec.PHOTO))
        val o = open(id)
        engine.act(o.id, Action.Complete(false, Evidence(photoPath = "proofs/1.jpg")))
        assertEquals("proofs/1.jpg", repo.proofs.single().filePath)
        assertEquals(Resolution.GOAL, repo.occurrences[o.id]!!.resolution)
    }

    @Test
    fun `rejected actions change nothing`() = runTest {
        val id = create()
        val o = open(id)
        val result = engine.act(o.id, Action.Complete(minimumOnly = true))
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
        engine.act(open(id).id, Action.Complete(minimumOnly = true))
        clock.now = at(27, 18, 5)
        assertTrue(engine.act(open(id).id, Action.Complete(minimumOnly = true)) is TransitionResult.Rejected)
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
        clock.now = at(26, 23, 30)
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
        engine.act(open(id).id, Action.Complete(false))
        assertNull(repo.openOccurrence(id))
        assertTrue(engine.sync().isEmpty())
        assertTrue(alarms.set.isEmpty())
    }

    @Test
    fun `a finished one-off reminder moved to a later time today is planned again`() = runTest {
        val id = create(reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 9, 0))))
        engine.act(open(id).id, Action.Complete(false))
        create(repo.reminders[id]!!.copy(title = "Neuer Titel"))
        assertNull(repo.openOccurrence(id))
        engine.setActive(id, false)

        // Moving it also switches it on again.
        create(repo.reminders[id]!!.copy(schedule = Schedule.Once(LocalDateTime.of(2026, 10, 26, 15, 0))))
        assertTrue(repo.reminders[id]!!.active)
        val o = open(id)
        assertEquals(at(26, 15), o.dueAt)
        assertEquals(at(26, 15), alarms.set[o.id]!!.first)
    }

    @Test
    fun `a skipped one-off reminder moved to a passed time today rings right away`() = runTest {
        val id = create(reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 9, 0))))
        engine.act(open(id).id, Action.Skip("keine Zeit"))
        clock.now = at(26, 12)
        val result = engine.save(repo.reminders[id]!!.copy(schedule = Schedule.Once(LocalDateTime.of(2026, 10, 26, 10, 0))))
        val o = open(id)
        assertEquals(at(26, 10), o.dueAt)
        assertEquals(OccurrenceState.DUE, o.state)
        assertEquals(o.id, (result as SaveResult.Saved).ring!!.occurrence.id)
    }

    @Test
    fun `a one-off reminder cannot be moved to an earlier day`() = runTest {
        val id = create(reminder(Schedule.Once(LocalDateTime.of(2026, 10, 26, 9, 0))))
        engine.act(open(id).id, Action.Complete(false))
        clock.now = at(27, 8)
        val result = engine.save(repo.reminders[id]!!.copy(schedule = Schedule.Once(LocalDateTime.of(2026, 10, 26, 20, 0))))
        assertEquals(SaveResult.Invalid(listOf("Zeitpunkt liegt in der Vergangenheit")), result)
        // Unchanged, the passed time does not block other edits.
        assertTrue(engine.save(repo.reminders[id]!!.copy(title = "Neuer Titel")) is SaveResult.Saved)
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
        engine.act(open(id).id, Action.Complete(false))
        assertEquals(at(13, 21, month = 11), open(id).dueAt)
    }

    // --- V1.1a ------------------------------------------------------------------------------

    private val device = FakeDevice()
    private val dndEngine = ReminderEngine(repo, alarms, clock, device)

    @Test
    fun `do not disturb keeps the alarm silent unless the reminder ignores it`() = runTest {
        val id = (dndEngine.save(reminder()) as SaveResult.Saved).id
        val special = (dndEngine.save(reminder().copy(ignoreDnd = true)) as SaveResult.Saved).id
        device.dnd = true
        clock.now = at(26, 18)
        assertEquals(AlarmLevel.SILENT, dndEngine.onAlarm(open(id).id)!!.level)
        assertEquals(AlarmLevel.FULLSCREEN, dndEngine.onAlarm(open(special).id)!!.level)
        // Rounds go on silently; the first one after the end rings normally.
        assertEquals(at(26, 18, 10), alarms.set[open(id).id]!!.first)
        device.dnd = false
        clock.now = at(26, 18, 10)
        assertEquals(AlarmLevel.FULLSCREEN, dndEngine.onAlarm(open(id).id)!!.level)
    }

    @Test
    fun `the proof window pauses the alarm`() = runTest {
        val id = create(reminder().copy(proof = ProofSpec.PHOTO))
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        clock.now = at(26, 18, 2)
        assertTrue(engine.startProof(o.id) is TransitionResult.Ok)
        assertEquals(at(26, 18, 12), alarms.set[o.id]!!.first)
        // A sync (e.g. reboot) keeps the pause.
        engine.sync()
        assertEquals(at(26, 18, 12), alarms.set[o.id]!!.first)
        // An early alarm does not ring.
        clock.now = at(26, 18, 5)
        assertNull(engine.onAlarm(o.id))
        // When the window is over, the escalation goes on.
        clock.now = at(26, 18, 12)
        val ring = engine.onAlarm(o.id)!!
        assertEquals(OccurrenceState.ESCALATING, ring.occurrence.state)
        assertNull(ring.occurrence.pausedUntil)
        assertEquals(at(26, 18, 22), alarms.set[o.id]!!.first)
    }

    @Test
    fun `leaving the proof rings again after a minute`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = at(26, 18)
        engine.onAlarm(o.id)
        engine.startProof(o.id)
        clock.now = at(26, 18, 3)
        assertTrue(engine.abortProof(o.id) is TransitionResult.Ok)
        assertEquals(at(26, 18, 4), alarms.set[o.id]!!.first)
        assertEquals(1, events(EventType.PROOF_STARTED).size)
        assertEquals(1, events(EventType.PROOF_ABORTED).size)
    }

    @Test
    fun `the proof window ends at the day end`() = runTest {
        val id = create(reminder(Schedule.Daily(LocalTime.of(22, 55))))
        val o = open(id)
        clock.now = at(26, 22, 55)
        engine.onAlarm(o.id)
        engine.startProof(o.id)
        assertEquals(at(26, 23), repo.occurrences[o.id]!!.pausedUntil)
        assertTrue(engine.startProof(999) is TransitionResult.Rejected)
        repo.reminders.remove(id)
        assertTrue(engine.startProof(o.id) is TransitionResult.Rejected)
    }

    @Test
    fun `snoozing past the day end is rejected`() = runTest {
        val id = create(reminder(Schedule.Daily(LocalTime.of(22, 0))).copy(snoozeMinutes = listOf(30, 60)))
        val o = open(id)
        clock.now = at(26, 22, 10)
        engine.onAlarm(o.id)
        assertEquals(listOf(30), engine.snoozeOptions(repo.reminders[id]!!, repo.occurrences[o.id]!!))
        assertTrue(engine.act(o.id, Action.Snooze(Duration.ofMinutes(60))) is TransitionResult.Rejected)
        assertTrue(engine.act(o.id, Action.Snooze(Duration.ofMinutes(30))) is TransitionResult.Ok)
    }

    @Test
    fun `text and audio proofs are stored`() = runTest {
        val spec = ProofSpec(ProofKind.EVIDENCE, photo = true, textMinChars = 5, audioMinSeconds = 3)
        val id = create(reminder().copy(proof = spec))
        val o = open(id)
        engine.act(o.id, Action.Complete(false, Evidence("p.jpg", " Filter neu ", "a.m4a", 4)))
        assertEquals(listOf(ProofType.PHOTO, ProofType.TEXT, ProofType.AUDIO), repo.proofs.map { it.type })
        assertEquals("Filter neu", repo.proofs[1].text)
        assertEquals(Resolution.GOAL, repo.occurrences[o.id]!!.resolution)
        // The same text next time does not count as proof.
        val next = open(id)
        engine.act(next.id, Action.Complete(false, Evidence("p2.jpg", "Filter neu", "b.m4a", 4)))
        assertEquals(Resolution.NO_PROOF, repo.occurrences[next.id]!!.resolution)
    }

    @Test
    fun `overcome reminders need the startklar answers`() = runTest {
        val r = reminder(character = Character.OVERCOME)
        assertEquals(SaveResult.Invalid(listOf("Startklar-Fragen beantworten")), engine.save(r))
        val answers = (1..4).map { PrepAnswer(reminderId = 0, question = it, text = "Antwort $it", at = at(1, 0)) }
        val id = (engine.save(r, answers) as SaveResult.Saved).id
        assertEquals(4, repo.prep.size)
        assertTrue(repo.prep.all { it.reminderId == id && it.at == at(26, 8) })
        // Editing without answers keeps the old ones; only changes are added.
        assertTrue(engine.save(repo.reminders[id]!!.copy(title = "Neu")) is SaveResult.Saved)
        engine.save(repo.reminders[id]!!, listOf(answers[0], answers[3].copy(text = "Matte ausrollen")))
        assertEquals(5, repo.prep.size)
        assertEquals("Matte ausrollen", engine.prep(id)[4]!!.text)
        engine.delete(id)
        assertTrue(repo.prep.isEmpty())
    }

    @Test
    fun `an older overcome reminder is asked on its next edit`() = runTest {
        val id = repo.saveReminder(reminder(character = Character.OVERCOME))
        assertTrue(engine.save(repo.reminders[id]!!.copy(title = "Neu")) is SaveResult.Invalid)
    }

    @Test
    fun `completing ahead of time plans the next regular date`() = runTest {
        val weekdays = Schedule.Weekdays(LocalTime.of(18, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val id = create(reminder(weekdays))
        val first = open(id)
        clock.now = at(26, 12)
        engine.act(first.id, Action.Complete(false))
        val done = repo.occurrences[first.id]!!
        assertTrue(done.resolvedAt!! < done.dueAt)
        assertEquals(at(29, 18), open(id).dueAt)
        assertFalse(alarms.set.containsKey(first.id))
    }
}
