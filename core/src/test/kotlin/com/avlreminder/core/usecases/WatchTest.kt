package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeDevice
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.rules.TransitionResult
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Was die Uhr zeigt und was ihre Knöpfe tun (Anf. 7.14 G5–G9, design.md 17). */
class WatchTest {
    private val clock = MutableClock(at(26, 8))
    private val repo = FakeRepository()
    private val device = FakeDevice()
    private var settings = AlertSettings()
    private val engine = ReminderEngine(repo, FakeAlarms(), clock, device = device, settings = { settings })

    private suspend fun save(title: String, hour: Int, edit: (Reminder) -> Reminder = { it }): Long {
        val r = reminder(Schedule.Daily(LocalTime.of(hour, 0)), created = at(26, 8)).copy(title = title)
        return (engine.save(edit(r)) as SaveResult.Saved).id
    }

    private suspend fun training(title: String = "Training", hour: Int = 18, edit: (Reminder) -> Reminder = { it }) =
        save(title, hour) { edit(it.copy(session = SessionSpec(goalMinutes = 45, minimumMinutes = 10))) }

    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    private suspend fun state(): WatchState {
        val now = clock.now
        val today = Overview.today(repo.reminders(), repo.openOccurrences(), now, ZONE)
        val views = repo.openOccurrences().mapNotNull { engine.alarmView(it.id) }
        return Watch.state(today, views, settings.alarmsPausedUntil)
    }

    /** Rings at [hour]:00 and is started two minutes later. */
    private suspend fun started(id: Long, hour: Int = 18): Long {
        val o = open(id).id
        clock.now = at(26, hour)
        engine.onAlarm(o)
        clock.now = at(26, hour, 2)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.start(o))
        return o
    }

    @Test
    fun `laufende Sitzung – Antworten wie in der Benachrichtigung, Vorschlag zuerst`() = runTest {
        val o = started(training())
        clock.now = at(26, 18, 5)
        val s = state().sessions.single()
        assertEquals(o, s.occurrenceId)
        assertEquals("Training", s.title)
        assertEquals(at(26, 18, 2), s.startedAt)
        assertEquals(at(26, 19, 32), s.maxAt)
        assertEquals(45, s.goalMinutes)
        assertEquals(10, s.minimumMinutes)
        assertEquals(listOf(SessionAnswer.STILL_ON, SessionAnswer.DONE, SessionAnswer.MINIMUM), s.answers)
        assertFalse(s.final)
        assertFalse(s.proofOnPhone)

        // D4: with a goal the minimum duration suggests nothing yet, the goal reached "Fertig".
        clock.now = at(26, 18, 13)
        assertEquals(SessionAnswer.STILL_ON, state().sessions.single().answers.first())
        clock.now = at(26, 18, 48)
        assertEquals(SessionAnswer.DONE, state().sessions.single().answers.first())
    }

    @Test
    fun `beim letzten Reminder kein Noch dran`() = runTest {
        val o = started(training())
        clock.now = at(26, 19, 32)
        engine.onAlarm(o)
        val s = state().sessions.single()
        assertTrue(s.final)
        assertFalse(SessionAnswer.STILL_ON in s.answers)
    }

    @Test
    fun `mit Foto-Nachweis nur Noch dran, Fertig am Handy`() = runTest {
        started(training { it.copy(proof = ProofSpec(ProofKind.EVIDENCE, photo = true)) })
        val s = state().sessions.single()
        assertTrue(s.proofOnPhone)
        assertEquals(listOf(SessionAnswer.STILL_ON), s.answers)
    }

    @Test
    fun `ohne Minimum kein Teilschritt`() = runTest {
        started(save("Lernen", 18) { it.copy(session = SessionSpec()) })
        assertEquals(listOf(SessionAnswer.STILL_ON, SessionAnswer.DONE), state().sessions.single().answers)
    }

    @Test
    fun `startbar – klingelnd zuerst, dann spaeter heute, ohne Sitzung nicht`() = runTest {
        training("Training", 18)
        training("Dehnen", 20)
        save("Katzenbrunnen", 19)
        val ringing = open(training("Aufräumen", 9)).id
        clock.now = at(26, 9)
        engine.onAlarm(ringing)
        clock.now = at(26, 9, 1)
        assertEquals(
            listOf(WatchStart(ringing, "Aufräumen", at(26, 9), true), WatchStart(open(1).id, "Training", at(26, 18), false)),
            state().startable.take(2),
        )
        assertEquals(listOf("Aufräumen", "Training", "Dehnen"), state().startable.map { it.title })
        assertNull(state().next)
    }

    @Test
    fun `hoechstens drei zum Starten, eine laufende Sitzung ist keine davon`() = runTest {
        (10..14).forEach { training("T$it", it) }
        clock.now = at(26, 9)
        assertEquals(Watch.STARTABLE, state().startable.size)
        started(1, hour = 10)
        assertEquals(listOf("T11", "T12", "T13"), state().startable.map { it.title })
    }

    @Test
    fun `ohne Sitzung und Start nur der naechste Reminder`() = runTest {
        save("Katzenbrunnen", 19)
        clock.now = at(26, 9)
        val s = state()
        assertEquals(WatchNext("Katzenbrunnen", at(26, 19)), s.next)
        assertTrue(s.sessions.isEmpty() && s.startable.isEmpty())
    }

    @Test
    fun `Notaus wird mitgegeben`() = runTest {
        settings = AlertSettings(alarmsPausedUntil = at(26, 12))
        assertEquals(at(26, 12), state().pausedUntil)
    }

    @Test
    fun `Nachfrage – hoerbar, still bei Nicht stoeren und im Notaus`() = runTest {
        val o = started(training())
        clock.now = at(26, 18, 12)
        assertEquals(WatchCheck(o, "Training", audible = true, final = false), Watch.check(engine.onAlarm(o)!!))
        device.dnd = true
        clock.now = at(26, 18, 22)
        assertFalse(Watch.check(engine.onAlarm(o)!!)!!.audible)
        device.dnd = false
        settings = AlertSettings(alarmsPausedUntil = at(26, 23))
        clock.now = at(26, 18, 32)
        assertFalse(Watch.check(engine.onAlarm(o)!!)!!.audible)
    }

    @Test
    fun `ein Alarm ohne Sitzung ist keine Nachfrage`() = runTest {
        val o = open(save("Katzenbrunnen", 19)).id
        clock.now = at(26, 19)
        assertNull(Watch.check(engine.onAlarm(o)!!))
    }

    @ParameterizedTest
    @EnumSource(SessionAnswer::class)
    fun `jede Antwort hat ihren Knopf`(answer: SessionAnswer) {
        assertEquals(answer.name, WatchAction.of(answer).name)
    }

    @Test
    fun `die Knoepfe loesen die Aktionen der Engine aus`() {
        assertEquals(Action.Start, WatchAction.START.toAction())
        assertEquals(Action.StillOn, WatchAction.STILL_ON.toAction())
        assertEquals(Action.Complete(minimumOnly = false), WatchAction.DONE.toAction())
        assertEquals(Action.Complete(minimumOnly = true), WatchAction.MINIMUM.toAction())
        assertEquals(Action.Abort, WatchAction.ABORT.toAction())
        assertNull(WatchAction.REOPEN.toAction())
    }

    @Test
    fun `Fertig von der Uhr beendet die Sitzung wie aus der Benachrichtigung`() = runTest {
        val id = training()
        val o = started(id)
        clock.now = at(26, 18, 50)
        assertInstanceOf(TransitionResult.Ok::class.java, engine.act(o, WatchAction.DONE.toAction()!!))
        assertTrue(state().sessions.isEmpty())
    }
}
