package com.avlreminder.core.scenarios

import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Whole days as Kevin lives them, played through the engine with [Simulation]. Each test tells a
 * day; the simulation checks after every step that nothing breaks the general rules.
 * 2026-10-26 is a Monday.
 */
class DayScenarioTest {
    private val sim = Simulation(at(26, 6))
    private val workdays = DayOfWeek.entries.take(5).toSet()

    private fun ringsOf(title: String) = sim.rings.filter { " $title " in it }

    // --- Training: evenings at home, only when at home -------------------------------------

    private suspend fun training(): Pair<Long, Long> {
        val home = sim.place(Place(name = "Zuhause", lat = 48.1, lon = 11.5))
        val id = sim.create(
            reminder(Schedule.Weekdays(LocalTime.of(19, 0), workdays)).copy(title = "Training", arrival = Arrival(home, WhenAway.DROP)),
        )
        return home to id
    }

    @Test
    fun `training rings when coming home in the evening and is quiet once done`() = runTest {
        val (home, id) = training()
        sim.leave(at(26, 7, 45))
        sim.arrive(home, at(26, 18, 30))
        sim.act(id, at(26, 18, 45)) { Action.Complete(minimumOnly = false) }
        sim.runUntil(at(27, 6))

        assertEquals(listOf("Mo 18:30 Training FULLSCREEN", "Mo 18:40 Training FULLSCREEN"), ringsOf("Training"))
        assertEquals(at(27, 19), sim.open(id).dueAt)
    }

    @Test
    fun `training is dropped without a sound when still away at its time`() = runTest {
        val (_, id) = training()
        sim.leave(at(26, 7, 45))
        sim.runUntil(at(27, 6))

        assertEquals(emptyList<String>(), sim.rings)
        val monday = sim.repo.occurrences.values.single { it.dueAt == at(26, 19) }
        assertEquals(Resolution.DROPPED, monday.resolution)
        assertEquals(at(27, 19), sim.open(id).dueAt)
    }

    @Test
    fun `training at home at its time rings at its time`() = runTest {
        val (_, _) = training()
        sim.location.here = true
        sim.runUntil(at(26, 19, 5))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `training rings when the position is unknown, so nothing is lost`() = runTest {
        training()
        sim.location.here = null
        sim.runUntil(at(26, 19, 5))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN"), sim.rings)
    }

    /**
     * Today's rule (Anf. 5.1, T2b): an arrival before the time makes it due at once, there is no
     * earliest time. A geofence that reports home in the morning thus rings the evening training
     * in the morning. Kevin's case of 2026-11-03 may be this; open until his scenario is known.
     */
    @Test
    fun `an arrival in the morning rings the evening training at once (open point)`() = runTest {
        val (home, _) = training()
        sim.arrive(home, at(26, 7, 10))
        assertEquals(listOf("Mo 07:10 Training FULLSCREEN"), sim.rings)
    }

    @Test
    fun `a second arrival the same day does not ring again once done`() = runTest {
        val (home, id) = training()
        sim.leave(at(26, 7, 45))
        sim.arrive(home, at(26, 17))
        sim.act(id, at(26, 17, 5)) { Action.Complete(minimumOnly = false) }
        sim.leave(at(26, 17, 30))
        sim.arrive(home, at(26, 18, 30))
        sim.runUntil(at(26, 23, 30))
        assertEquals(listOf("Mo 17:00 Training FULLSCREEN"), ringsOf("Training"))
    }

    // --- Postponed with an arrival: the decision is made (7.29 OZ5, Review B2) ---------------

    /** Rang at 19:00 with the position unknown, then postponed to 20:30. */
    private suspend fun postponedTraining(): Pair<Long, Long> {
        val (home, id) = training()
        sim.location.here = null
        sim.runUntil(at(26, 19, 1))
        sim.act(id, at(26, 19, 2)) { Action.Postpone(at(26, 20, 30)) }
        return home to id
    }

    @Test
    fun `postponed and away at the new time it waits and rings on the arrival`() = runTest {
        val (home, id) = postponedTraining()
        sim.leave(at(26, 20))
        sim.runUntil(at(26, 20, 35))
        assertTrue(sim.open(id).awaitsArrival)
        sim.arrive(home, at(26, 21, 10))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN", "Mo 21:10 Training FULLSCREEN"), ringsOf("Training"))
    }

    @Test
    fun `postponed and never back home it is missed at the end of the day, not dropped`() = runTest {
        val (_, id) = postponedTraining()
        sim.leave(at(26, 20))
        sim.runUntil(at(27, 6))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN"), ringsOf("Training"))
        val monday = sim.repo.occurrences.values.single { it.dueAt == at(26, 19) }
        assertEquals(Resolution.MISSED, monday.resolution)
        assertEquals(at(27, 19), sim.open(id).dueAt)
    }

    @Test
    fun `postponed with the position unknown at the new time it waits as well`() = runTest {
        val (_, id) = postponedTraining()
        sim.runUntil(at(26, 20, 35))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN"), ringsOf("Training"))
        assertTrue(sim.open(id).awaitsArrival)
    }

    @Test
    fun `postponed and at home at the new time it rings then`() = runTest {
        postponedTraining()
        sim.location.here = true
        sim.runUntil(at(26, 20, 31))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN", "Mo 20:30 Training FULLSCREEN"), ringsOf("Training"))
    }

    @Test
    fun `waiting for the arrival survives a restart`() = runTest {
        val (home, id) = postponedTraining()
        sim.leave(at(26, 20))
        sim.runUntil(at(26, 20, 35))
        sim.phoneOff(until = at(26, 21))
        assertTrue(sim.open(id).awaitsArrival)
        sim.arrive(home, at(26, 21, 10))
        assertEquals(listOf("Mo 19:00 Training FULLSCREEN", "Mo 21:10 Training FULLSCREEN"), ringsOf("Training"))
    }

    // --- An ordinary daily reminder -----------------------------------------------------------

    @Test
    fun `an ignored day rings every ten minutes until the end of the day and then counts as missed`() = runTest {
        val id = sim.create(reminder().copy(title = "Brunnen"))
        sim.runUntil(at(27, 6))

        val rings = ringsOf("Brunnen")
        assertEquals("Mo 18:00 Brunnen FULLSCREEN", rings.first())
        assertEquals("Mo 22:50 Brunnen FULLSCREEN", rings.last())
        assertEquals(30, rings.size)
        assertEquals(Resolution.MISSED, sim.repo.occurrences.values.single { it.dueAt == at(26, 18) }.resolution)
        assertEquals(at(27, 18), sim.open(id).dueAt)
    }

    @Test
    fun `snoozing moves the next ring and doing it ends the day's alarms`() = runTest {
        val id = sim.create(reminder().copy(title = "Brunnen"))
        sim.act(id, at(26, 18, 2)) { Action.Snooze(Duration.ofMinutes(30)) }
        sim.act(id, at(26, 18, 40)) { Action.Complete(minimumOnly = false) }
        sim.runUntil(at(27, 6))
        assertEquals(listOf("Mo 18:00 Brunnen FULLSCREEN", "Mo 18:32 Brunnen FULLSCREEN"), ringsOf("Brunnen"))
    }

    @Test
    fun `editing a snoozed reminder keeps today's occurrence (Review B1)`() = runTest {
        val id = sim.create(reminder(Schedule.Daily(LocalTime.of(8, 0))).copy(title = "Tabletten"))
        sim.act(id, at(26, 8, 15)) { Action.Snooze(Duration.ofMinutes(10)) }
        sim.runUntil(at(26, 8, 18))
        sim.engine.save(sim.repo.reminder(id)!!.copy(title = "Tabletten morgens"))
        assertEquals(at(26, 8), sim.open(id).dueAt)
        sim.runUntil(at(26, 8, 26))
        assertEquals("Mo 08:25 Tabletten morgens FULLSCREEN", sim.rings.last())
    }

    @Test
    fun `with the phone off over the evening the missed day is noticed on start and today is planned`() = runTest {
        val id = sim.create(reminder().copy(title = "Brunnen"))
        sim.phoneOff(until = at(27, 7))

        assertEquals(emptyList<String>(), sim.rings)
        assertEquals(Resolution.MISSED, sim.repo.occurrences.values.single { it.dueAt == at(26, 18) }.resolution)
        assertEquals(at(27, 18), sim.open(id).dueAt)
    }

    @Test
    fun `several times a day ring each time, the next time takes over`() = runTest {
        sim.create(reminder(Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(20, 0)))).copy(title = "Tabletten"))
        sim.runUntil(at(26, 8, 5))
        val id = sim.repo.reminders.keys.single()
        sim.act(id, at(26, 8, 5)) { Action.Complete(minimumOnly = false) }
        sim.runUntil(at(26, 13, 5))
        sim.act(id, at(26, 13, 5)) { Action.Complete(minimumOnly = false) }
        sim.runUntil(at(26, 20, 5))

        assertEquals(listOf("Mo 08:00", "Mo 13:00", "Mo 20:00"), ringsOf("Tabletten").map { it.take(8) })
    }

    // --- Checklists ---------------------------------------------------------------------------

    private suspend fun cleaning(): Pair<Long, List<Long>> {
        val id = sim.create(
            reminder(Schedule.Weekdays(LocalTime.of(10, 0), setOf(DayOfWeek.SATURDAY))).copy(
                title = "Wochenputz",
                checklist = listOf(ChecklistItem(title = "Bad"), ChecklistItem(title = "Küche"), ChecklistItem(title = "Böden")),
            ),
        )
        return id to sim.repo.reminders[id]!!.checklist.map { it.id }
    }

    @Test
    fun `items checked during the week are remembered when the checklist rings on its day`() = runTest {
        val (id, items) = cleaning()
        sim.act(id, at(28, 19)) { Action.CheckItem(items[0]) }
        sim.runUntil(at(31, 10, 5))

        val o = sim.open(id)
        assertEquals(OccurrenceState.DUE, o.state)
        assertEquals(listOf(items[0]), sim.repo.done.filter { it.occurrenceId == o.id }.map { it.itemId })
        // A checked item does not end the rounds: the next one starts ten minutes after it.
        sim.act(id, at(31, 10, 25)) { Action.CheckItem(items[1]) }
        sim.act(id, at(31, 10, 50)) { Action.CheckItem(items[2]) }
        sim.runUntil(at(31, 23, 30))

        val done = sim.repo.occurrences.values.single { it.dueAt == at(31, 10) }
        assertEquals(Resolution.GOAL, done.resolution)
        assertEquals(listOf("Sa 10:00", "Sa 10:10", "Sa 10:20", "Sa 10:35", "Sa 10:45"), ringsOf("Wochenputz").map { it.take(8) })
    }

    @Test
    fun `all items checked before the day completes it early, the next one is a week later`() = runTest {
        val (id, items) = cleaning()
        items.forEach { item -> sim.act(id, at(29, 18)) { Action.CheckItem(item) } }
        sim.runUntil(at(31, 23, 30))

        assertEquals(emptyList<String>(), sim.rings)
        assertEquals(at(7, 10, month = 11), sim.open(id).dueAt)
    }

    /**
     * Today's rule (C3): only an item done on the day it is due carries the rest to the next day.
     * Items checked on an earlier day ("Punkte abhaken", V1.3b0.1) do not, and at the end of the
     * day the occurrence is missed – the next one starts without them. Possibly Kevin's
     * "nicht richtig als gelöst gespeichert"; open until his scenario is known.
     */
    @Test
    fun `items checked before the day are lost when nothing more is done on the day (open point)`() = runTest {
        val (id, items) = cleaning()
        sim.act(id, at(28, 19)) { Action.CheckItem(items[0]) }
        sim.runUntil(at(1, 6, month = 11))

        val saturday = sim.repo.occurrences.values.single { it.dueAt == at(31, 10) }
        assertEquals(Resolution.MISSED, saturday.resolution)
        val next = sim.open(id)
        assertEquals(at(7, 10, month = 11), next.dueAt)
        assertTrue(sim.repo.done.none { it.occurrenceId == next.id })
    }

    @Test
    fun `an item done on the day carries the rest to the next day`() = runTest {
        val (id, items) = cleaning()
        sim.act(id, at(31, 10, 5)) { Action.CheckItem(items[0]) }
        sim.runUntil(at(1, 9, month = 11))

        val o = sim.open(id)
        assertEquals(at(31, 10), o.dueAt)
        assertEquals(at(1, 10, month = 11), o.currentAt)
        sim.runUntil(at(1, 10, 5, month = 11))
        assertEquals("So 10:00", ringsOf("Wochenputz").last().take(8))
    }
}
