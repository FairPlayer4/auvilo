package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakePreNotices
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.TransitionResult
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeadlineAndBlockerEngineTest {
    private val clock = MutableClock(at(26, 8))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val notices = FakePreNotices()
    private var settings = AlertSettings()
    private var commands = 0
    private val engine = ReminderEngine(repo, alarms, clock, settings = { settings }, preNotices = notices, changed = { commands++ })

    /** Friday 2026-10-30 12:00, windows Mo–Fr 9–17, every hour. */
    private val deadline = Schedule.Deadline(
        due = LocalDateTime.of(2026, 10, 30, 12, 0), days = DayOfWeek.entries.take(5).toSet(),
        from = LocalTime.of(9, 0), until = LocalTime.of(17, 0),
    )

    private suspend fun create(r: Reminder, prep: List<PrepAnswer> = emptyList()): Long =
        (engine.save(r, prep) as SaveResult.Saved).id

    private suspend fun open(reminderId: Long) = repo.openOccurrence(reminderId)!!

    private fun answer(q: Int, text: String) = PrepAnswer(reminderId = 0, question = q, text = text, at = at(26, 8))

    private fun allAnswers(ready: String = Prep.READY_YES) =
        listOf(answer(1, "fit"), answer(2, "müde"), answer(3, ready), answer(4, "Schuhe an"))

    // --- Deadlines -------------------------------------------------------------------------

    @Test
    fun `a deadline rings in its windows every interval and is quiet in between`() = runTest {
        val id = create(reminder(deadline).copy(strictness = Strictness.RELAXED))
        val o = open(id)
        assertEquals(at(26, 9), o.dueAt)
        clock.now = at(26, 9)
        val ring = engine.onAlarm(o.id)!!
        assertEquals(AlarmLevel.BANNER, ring.level)
        assertEquals(at(27, 9), ring.tomorrow)
        assertEquals(at(26, 10), alarms.set[o.id]!!.first)
        clock.now = at(26, 16)
        engine.onAlarm(o.id)
        assertEquals(at(26, 17), alarms.set[o.id]!!.first)
        clock.now = at(26, 17)
        assertNull(engine.onAlarm(o.id))
        val closed = open(id)
        assertEquals(o.id, closed.id)
        assertEquals(OccurrenceState.PLANNED, closed.state)
        assertEquals(at(27, 9), closed.currentAt)
        assertEquals(0, closed.postponeCount)
        assertEquals(1, repo.events.count { it.type == EventType.WINDOW_CLOSED })
    }

    @Test
    fun `the last window day rings stricter, the deadline passed it is overdue`() = runTest {
        val id = create(reminder(deadline).copy(strictness = Strictness.RELAXED))
        clock.now = at(30, 9, 30)
        engine.sync()
        val o = open(id)
        assertEquals(at(30, 10, 30), o.currentAt)
        clock.now = at(30, 10, 30)
        val ring = engine.onAlarm(o.id)!!
        assertEquals(AlarmLevel.FULLSCREEN, ring.level)
        assertNull(ring.tomorrow)
        // Snoozing past the deadline is not possible, postponing neither.
        assertTrue(engine.act(o.id, Action.Postpone(at(30, 13))) is TransitionResult.Rejected)
        clock.now = at(30, 12)
        assertNull(engine.onAlarm(o.id))
        assertEquals(Resolution.MISSED, repo.occurrences[o.id]!!.resolution)
        val overdue = open(id)
        assertEquals(at(2, 9, month = 11), overdue.dueAt)
        val today = Overview.today(repo.reminders(), repo.occurrences.values.toList(), clock.now, ZONE)
        assertEquals(listOf(overdue.id), today.overdue.map { it.occurrence.id })
        assertTrue(today.upcoming.isEmpty())
        assertEquals(listOf("überfällig: Katzenbrunnen"), Digests.morning(today, ZONE).lines)
        // Overdue rings stricter and goes on window by window until done or discarded.
        clock.now = at(2, 9, month = 11)
        val late = engine.onAlarm(overdue.id)!!
        assertEquals(AlarmLevel.FULLSCREEN, late.level)
        assertEquals(at(3, 9, month = 11), late.tomorrow)
        assertTrue(engine.act(overdue.id, Action.Postpone(at(3, 9, month = 11))) is TransitionResult.Ok)
        clock.now = at(3, 17, month = 11)
        engine.sync()
        assertEquals(at(4, 9, month = 11), open(id).currentAt)
        engine.act(overdue.id, Action.Skip(SkipReasons.DISCARDED))
        assertNull(repo.openOccurrence(id))
        assertTrue(ReminderList.finished(repo.reminders[id]!!, null))
    }

    @Test
    fun `a deadline needs a window before it, moving it starts anew`() = runTest {
        clock.now = at(30, 11, 30)
        assertEquals(SaveResult.Invalid(listOf("Kein Ausführungsfenster vor der Frist")), engine.save(reminder(deadline)))
        clock.now = at(26, 10)
        val id = create(reminder(deadline))
        assertEquals(at(26, 11), open(id).dueAt)
        // Renaming keeps the occurrence.
        create(repo.reminders[id]!!.copy(title = "Steuer"))
        assertEquals(at(26, 11), open(id).dueAt)
        val first = open(id).id
        create(repo.reminders[id]!!.copy(schedule = deadline.copy(from = LocalTime.of(14, 0))))
        assertEquals(SkipReasons.SCHEDULE_CHANGED, repo.occurrences[first]!!.note)
        assertEquals(at(26, 14), open(id).dueAt)
    }

    // --- Notice before ---------------------------------------------------------------------

    @Test
    fun `the notice before is planned with the occurrence and gone when done in advance`() = runTest {
        val tuesday = Schedule.Weekdays(LocalTime.of(18, 0), setOf(DayOfWeek.TUESDAY))
        val id = create(reminder(tuesday).copy(preNotice = PreNotice.Evening(LocalTime.of(19, 0))), listOf(answer(4, "Schuhe an")))
        val o = open(id)
        assertEquals(at(26, 19), notices.set[o.id])
        engine.addBlocker(id, "Matte kaufen")
        clock.now = at(26, 19)
        val notice = engine.preNotice(o.id)!!
        assertEquals("Morgen 18:00: Katzenbrunnen", notice.title)
        assertEquals(listOf("Erster Schritt: Schuhe an", "Noch offen: Matte kaufen"), notice.lines)
        engine.act(o.id, Action.Complete(minimumOnly = false))
        assertNull(notices.set[o.id])
        assertNull(engine.preNotice(o.id))
        assertNotNull(notices.set[open(id).id])
    }

    @Test
    fun `a notice already passed is not set`() = runTest {
        val id = create(reminder().copy(preNotice = PreNotice.Evening(LocalTime.of(19, 0))))
        assertTrue(notices.set.isEmpty())
        engine.setActive(id, false)
        assertNull(engine.preNotice(open(id).id))
        create(repo.reminders[id]!!.copy(active = true, preNotice = PreNotice.Before(30)))
        assertEquals(at(26, 17, 30), notices.set[open(id).id])
        engine.delete(id)
        assertTrue(notices.set.isEmpty())
    }

    // --- Blockers and tasks ----------------------------------------------------------------

    @Test
    fun `what is missing becomes a blocker, ready right away solves it`() = runTest {
        val id = create(reminder(character = Character.OVERCOME), allAnswers("Matte fehlt"))
        val blockers = engine.blockers(id)
        assertEquals(listOf("Matte fehlt"), blockers.map { it.text })
        assertEquals(BlockerOrigin.STARTKLAR, blockers.single().origin)
        assertEquals(blockers.single().id, engine.addBlocker(id, "matte fehlt "))
        create(repo.reminders[id]!!, listOf(answer(3, Prep.READY_YES)))
        assertFalse(engine.blockers(id).single().isOpen)
    }

    @Test
    fun `a task for a blocker solves it when done and is not in the list`() = runTest {
        val id = create(reminder())
        val blocker = engine.addBlocker(id, "Filter kaufen")
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), engine.suggestTaskTime(id))
        val task = (engine.planTask(blocker, LocalDateTime.of(2026, 10, 26, 12, 0)) as SaveResult.Saved).id
        val saved = repo.reminders[task]!!
        assertEquals(id, saved.prepFor)
        assertEquals(Strictness.RELAXED, saved.strictness)
        assertEquals(Character.DOCUMENT, saved.character)
        assertEquals(task, repo.blocker(blocker)!!.taskReminderId)
        assertEquals(listOf(id), ReminderList.group(repo.reminders(), emptyMap(), ReminderSort.NAME).active.map { it.id })
        clock.now = at(26, 12)
        val o = open(task)
        engine.onAlarm(o.id)
        engine.act(o.id, Action.Complete(minimumOnly = false))
        assertFalse(repo.blocker(blocker)!!.isOpen)
    }

    @Test
    fun `solving a blocker by hand removes its task, deleting a task leaves it open`() = runTest {
        val id = create(reminder())
        val first = engine.addBlocker(id, "Filter kaufen")
        val task = (engine.planTask(first, LocalDateTime.of(2026, 10, 26, 12, 0)) as SaveResult.Saved).id
        engine.solveBlocker(first)
        assertNull(repo.reminders[task])
        assertNull(repo.blocker(first)!!.taskReminderId)
        engine.solveBlocker(first)
        val second = engine.addBlocker(id, "Pumpe reinigen")
        val other = (engine.planTask(second, LocalDateTime.of(2026, 10, 26, 12, 0)) as SaveResult.Saved).id
        val replaced = (engine.planTask(second, LocalDateTime.of(2026, 10, 26, 13, 0)) as SaveResult.Saved).id
        assertNull(repo.reminders[other])
        engine.delete(replaced)
        assertTrue(repo.blocker(second)!!.isOpen)
        assertNull(repo.blocker(second)!!.taskReminderId)
        val third = (engine.planTask(second, LocalDateTime.of(2026, 10, 26, 13, 0)) as SaveResult.Saved).id
        engine.delete(id)
        assertNull(repo.reminders[third])
        assertTrue(engine.blockers(id).isEmpty())
        assertTrue(engine.planTask(second, LocalDateTime.of(2026, 10, 26, 13, 0)) is SaveResult.Invalid)
    }

    @Test
    fun `a solved blocker counts as the minimum once`() = runTest {
        val id = create(reminder().copy(solvedBlockerIsMinimum = true, minimum = "kurz", minimumLimitPerWeek = 2))
        val blocker = engine.addBlocker(id, "Filter kaufen")
        clock.now = at(26, 18)
        val o = open(id)
        assertFalse(engine.onAlarm(o.id)!!.blockerSolved)
        assertTrue(engine.act(o.id, Action.Complete(minimumOnly = false, blockerSolved = true)) is TransitionResult.Rejected)
        engine.solveBlocker(blocker)
        val ring = engine.onAlarm(o.id)!!
        assertTrue(ring.blockerSolved)
        engine.act(o.id, Action.Complete(minimumOnly = false, blockerSolved = true))
        val done = repo.occurrences[o.id]!!
        assertEquals(Resolution.MINIMUM, done.resolution)
        assertEquals(Lifecycle.BLOCKER_SOLVED, done.note)
        clock.now = at(27, 18)
        assertFalse(engine.onAlarm(open(id).id)!!.blockerSolved)
    }

    // --- Asking when it gets stuck -----------------------------------------------------------

    @Test
    fun `after two stuck occurrences the alarm asks what keeps one from it`() = runTest {
        val id = create(reminder(character = Character.OVERCOME), allAnswers())
        for (day in 26..27) {
            clock.now = at(day, 18)
            val o = open(id)
            assertFalse(engine.onAlarm(o.id)!!.askBlocker)
            engine.act(o.id, Action.Skip("keine Lust"))
        }
        clock.now = at(28, 18)
        val o = open(id)
        assertTrue(engine.onAlarm(o.id)!!.askBlocker)
        settings = AlertSettings(askBlockerAfter = 3)
        assertFalse(engine.onAlarm(o.id)!!.askBlocker)
        settings = AlertSettings()
        val blocker = engine.answerAsk(o.id, " Zu müde ")
        assertEquals(BlockerOrigin.ASKED, repo.blocker(blocker!!)!!.origin)
        assertFalse(engine.onAlarm(o.id)!!.askBlocker)
        engine.act(o.id, Action.Complete(minimumOnly = false))
        clock.now = at(29, 18)
        assertFalse(engine.onAlarm(open(id).id)!!.askBlocker)
        assertNull(engine.answerAsk(open(id).id, " "))
        assertNull(engine.answerAsk(9999, "x"))
    }

    /** The answer and its task are one command (Review-Lauf 5, E19): both or neither. */
    @Test
    fun `answering with a task plans it in the same command`() = runTest {
        val id = create(reminder(character = Character.OVERCOME), allAnswers())
        clock.now = at(26, 18)
        val o = open(id)
        val at = engine.suggestTaskTime(id)
        commands = 0
        val blocker = engine.answerAsk(o.id, "Zu müde", planTask = true)!!
        assertEquals(1, commands, "blocker and task are one command")
        val task = repo.reminders().single { it.prepFor == id }
        // Doing the task solves this blocker (S5).
        assertEquals(task.id, repo.blocker(blocker)!!.taskReminderId)
        assertEquals("Zu müde", task.title)
        assertEquals(Schedule.Once(at), task.schedule)
        assertEquals(BlockerOrigin.ASKED, repo.blocker(blocker)!!.origin)
        assertNull(engine.answerAsk(o.id, " ", planTask = true))
        assertEquals(1, repo.reminders().count { it.prepFor == id })
    }

    @Test
    fun `documenting reminders and tasks are never asked`() = runTest {
        val id = create(reminder())
        for (day in 26..28) {
            clock.now = at(day, 18)
            val o = open(id)
            assertFalse(engine.onAlarm(o.id)!!.askBlocker)
            engine.act(o.id, Action.Skip("keine Lust"))
        }
    }
}
