package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.LocationPort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlertSettings
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Commands as a whole: one transaction each, what is due rings afterwards through the port. */
class EngineCommandTest {
    /** Notes whether a transaction is running. */
    private class TransactionRepo(val inner: FakeRepository = FakeRepository()) : ReminderRepository by inner {
        var inTransaction = false
        var transactions = 0
        override suspend fun <T> transaction(block: suspend () -> T): T {
            check(!inTransaction) { "nested transaction" }
            inTransaction = true
            transactions++
            try {
                return inner.transaction(block)
            } finally {
                inTransaction = false
            }
        }
    }

    private val clock = MutableClock(at(26, 8))
    private val repo = TransactionRepo()
    private val rung = mutableListOf<Ring>()
    private var settings = AlertSettings()
    private var location = LocationPort { null }
    private val alarms = FakeAlarms()
    private var checks = 0
    private val engine = ReminderEngine(
        repo,
        object : AlarmPort {
            override fun set(occurrenceId: Long, at: Instant, kind: AlarmKind) {
                assertFalse(repo.inTransaction, "alarms change only once the command is stored")
                alarms.set(occurrenceId, at, kind)
            }
            override fun cancel(occurrenceId: Long) {
                assertFalse(repo.inTransaction, "alarms change only once the command is stored")
                alarms.cancel(occurrenceId)
            }
        },
        clock, settings = { settings },
        location = {
            assertFalse(repo.inTransaction, "no position check inside the transaction (E6)")
            checks++
            location.isAt(it)
        },
        ringer = { ring ->
            assertFalse(repo.inTransaction, "rings only once the command is stored")
            rung += ring
        },
    )

    private suspend fun create(r: Reminder = reminder()): Long = (engine.save(r) as SaveResult.Saved).id
    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    @Test
    fun `what is due rings through the port after the transaction`() = runTest {
        val id = create()
        val o = open(id)
        clock.now = o.currentAt
        val ring = engine.onAlarm(o.id)!!
        assertEquals(listOf(ring), rung)
    }

    @Test
    fun `a command inside another one joins its transaction`() = runTest {
        val id = create()
        val blocker = engine.addBlocker(id, "Schuhe fehlen")
        repo.transactions = 0
        // Planning a task saves a reminder inside the command.
        assertTrue(engine.planTask(blocker, at(26, 20).atZone(clock.zone).toLocalDateTime()) is SaveResult.Saved)
        assertEquals(1, repo.transactions)
    }

    @Test
    fun `a failing command rings nothing, also not later`() = runTest {
        val first = engine.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5))
        val second = engine.savePlace(Place(name = "Büro", lat = 48.2, lon = 11.6))
        create(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(first)))
        create(reminder(Schedule.OnArrival).copy(title = "Brief", arrival = Arrival(second)))
        // At home, but the second position check fails.
        location = LocationPort { place -> if (place.id == first) true else error("kein GPS") }
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { engine.checkPlaces() } }
        assertTrue(rung.isEmpty())
        location = LocationPort { false }
        engine.sync()
        assertTrue(rung.isEmpty())
    }

    @Test
    fun `the position is found outside the transaction, once per place, and the command runs again with it`() = runTest {
        val home = engine.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5))
        create(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(home)))
        create(reminder(Schedule.OnArrival).copy(title = "Brief", arrival = Arrival(home)))
        location = LocationPort { true }
        checks = 0
        repo.transactions = 0
        val rings = engine.checkPlaces()
        assertEquals(listOf("Paket", "Brief"), rings.map { it.reminder.title })
        assertEquals(listOf("Paket", "Brief"), rung.map { it.reminder.title })
        assertEquals(1, checks)
        // The first try stopped at the missing position and left nothing behind.
        assertEquals(2, repo.transactions)
        assertEquals(1, repo.inner.events.count { it.type == com.avlreminder.core.model.EventType.DUE && it.occurrenceId == open(rings[0].reminder.id).id })
    }

    @Test
    fun `switched on again by hand a reached weekly goal stays quiet`() = runTest {
        val id = create(
            reminder(Schedule.Daily(LocalTime.of(18, 0))).copy(minimum = "kurz", weeklyGoal = WeeklyGoal(target = 1, max = 7, minimum = 1)),
        )
        // Monday done: the goal of the week is reached.
        clock.now = at(26, 18)
        engine.onAlarm(open(id).id)
        engine.act(open(id).id, Action.Complete(minimumOnly = false))
        // Tuesday rings while all alarms are paused.
        settings = AlertSettings(alarmsPausedUntil = AlertSettings.PAUSED_FOR_GOOD)
        clock.now = at(27, 18)
        engine.onAlarm(open(id).id)
        settings = AlertSettings()
        rung.clear()
        clock.now = at(27, 18, 30)
        assertTrue(engine.resume().isEmpty())
        assertTrue(rung.isEmpty())
    }
}
