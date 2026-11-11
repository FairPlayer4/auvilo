package com.avlreminder.core.usecases

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeGeofences
import com.avlreminder.core.FakeLocation
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.TransitionResult
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacesAndChecklistsEngineTest {
    private val clock = MutableClock(at(26, 8))
    private val repo = FakeRepository()
    private val alarms = FakeAlarms()
    private val geofences = FakeGeofences()
    private val location = FakeLocation()
    private val engine = ReminderEngine(repo, alarms, clock, geofences = geofences, location = location)

    private val home = Place(name = "Zuhause", lat = 48.1, lon = 11.5)
    private suspend fun homeId() = engine.savePlace(home)

    /** Training: Mo–Fr, at arrival; otherwise 19:00 if at home, else dropped. */
    private fun training(placeId: Long, whenAway: WhenAway = WhenAway.DROP) = reminder(
        Schedule.Weekdays(LocalTime.of(19, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
    ).copy(title = "Training", arrival = Arrival(placeId, whenAway))

    private suspend fun create(r: Reminder): Long = (engine.save(r) as SaveResult.Saved).id
    private suspend fun open(id: Long) = repo.openOccurrence(id)!!

    // --- Arrival ---------------------------------------------------------------------------

    @Test
    fun `an arrival reminder needs the place to exist`() = runTest {
        val result = engine.save(training(placeId = 42))
        assertEquals(SaveResult.Invalid(listOf("Ort zuerst in den Einstellungen festlegen")), result)
    }

    @Test
    fun `places of active arrival reminders are watched`() = runTest {
        val place = homeId()
        assertEquals(emptyList<Place>(), geofences.watched)
        val id = create(training(place))
        assertEquals(listOf(home.copy(id = place)), geofences.watched)
        engine.setActive(id, false)
        assertEquals(emptyList<Place>(), geofences.watched)
        engine.setActive(id, true)
        assertEquals(1, geofences.watched!!.size)
        engine.delete(id)
        assertEquals(emptyList<Place>(), geofences.watched)
        assertEquals(listOf(home.copy(id = place)), engine.places())
    }

    @Test
    fun `without a window, arriving rings today's occurrence before its time`() = runTest {
        // Lead 0: as before 7.29 (design.md Z.7 4.).
        val r = training(homeId())
        val id = create(r.copy(arrival = r.arrival!!.copy(leadMinutes = 0)))
        clock.now = at(26, 17, 30)
        val rings = engine.onArrival(repo.places.keys.single())
        val o = rings.single().occurrence
        assertEquals(OccurrenceState.DUE, o.state)
        assertEquals(at(26, 17, 30), o.currentAt)
        assertEquals(at(26, 19), o.dueAt)
        assertEquals(id, rings.single().reminder.id)
        // Next round in 10 min.
        assertEquals(at(26, 17, 40), alarms.set[o.id]!!.first)
    }

    @Test
    fun `arriving on another day, after the time or after snoozing does nothing`() = runTest {
        val place = homeId()
        val id = create(training(place))
        clock.now = at(25, 17) // Sunday: today's occurrence is Monday's
        assertTrue(engine.onArrival(place).isEmpty())

        clock.now = at(26, 19, 1)
        engine.onAlarm(open(id).id)
        engine.act(open(id).id, Action.Snooze(java.time.Duration.ofMinutes(30)))
        clock.now = at(26, 19, 10)
        assertTrue(engine.onArrival(place).isEmpty())
        assertTrue(engine.onArrival(place + 1).isEmpty())
    }

    @Test
    fun `away at the fallback time drops the day neutrally`() = runTest {
        val id = create(training(homeId()))
        location.here = false
        clock.now = at(26, 19)
        val first = open(id)
        assertNull(engine.onAlarm(first.id))
        val dropped = repo.occurrences[first.id]!!
        assertEquals(Resolution.DROPPED, dropped.resolution)
        assertEquals(at(27, 19), open(id).dueAt)
        assertEquals(at(27, 19), alarms.set[open(id).id]!!.first)
    }

    @Test
    fun `at home or unknown position at the fallback time rings`() = runTest {
        val id = create(training(homeId()))
        val other = create(training(repo.places.keys.single()).copy(title = "Dehnen"))
        location.here = true
        clock.now = at(26, 19)
        assertEquals(OccurrenceState.DUE, engine.onAlarm(open(id).id)!!.occurrence.state)
        location.here = null
        assertEquals(OccurrenceState.DUE, engine.onAlarm(open(other).id)!!.occurrence.state)
    }

    @Test
    fun `ring anyway does not ask for the position`() = runTest {
        val id = create(training(homeId(), WhenAway.RING))
        location.here = false
        clock.now = at(26, 19)
        assertEquals(OccurrenceState.DUE, engine.onAlarm(open(id).id)!!.occurrence.state)
        assertEquals(0, location.checks)
    }

    @Test
    fun `when I am home waits without an alarm and rings on arrival`() = runTest {
        val place = homeId()
        val id = create(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(place)))
        val o = open(id)
        assertEquals(OccurrenceState.PLANNED, o.state)
        assertNull(alarms.set[o.id])
        // Days later it is neither fired nor missed.
        clock.now = at(29, 12)
        assertTrue(engine.sync().isEmpty())
        assertEquals(OccurrenceState.PLANNED, open(id).state)

        val ring = engine.onArrival(place).single()
        assertEquals(at(29, 12), ring.occurrence.currentAt)
        engine.act(ring.occurrence.id, Action.Complete(false))
        assertNull(repo.openOccurrence(id))
    }

    @Test
    fun `saving a reminder for home while at home rings right away`() = runTest {
        val place = homeId()
        val id = create(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(place)))
        create(reminder(Schedule.OnArrival).copy(title = "Brief", arrival = Arrival(place)))
        // Saving checked the position already, without a fix.
        location.checks = 0
        location.here = false
        assertTrue(engine.checkPlaces().isEmpty())
        location.here = true
        val rings = engine.checkPlaces()
        assertEquals(2, rings.size)
        assertEquals(id, rings.first().reminder.id)
        // One position check per place.
        assertEquals(2, location.checks)
        assertTrue(engine.checkPlaces().isEmpty())
    }

    @Test
    fun `saving it while already at home rings in the save itself`() = runTest {
        val place = homeId()
        location.here = true
        val result = engine.save(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(place)))
        val ring = (result as SaveResult.Saved).ring!!
        assertEquals(result.id, ring.reminder.id)
        assertEquals(OccurrenceState.DUE, open(result.id).state)
    }

    @Test
    fun `saving a waiting reminder again keeps its occurrence`() = runTest {
        val place = homeId()
        val id = create(reminder(Schedule.OnArrival).copy(title = "Paket", arrival = Arrival(place)))
        val before = open(id)
        clock.now = at(27, 9)
        create(repo.reminders[id]!!.copy(title = "Paket abholen"))
        assertEquals(before, open(id))
    }

    // --- Checklist -------------------------------------------------------------------------

    private val items = listOf(ChecklistItem(title = "Brunnen 1"), ChecklistItem(title = "Brunnen 2"), ChecklistItem(title = "Futterautomat"))

    private suspend fun fountains(r: Reminder = reminder().copy(checklist = items, postponeLimit = 1)): Pair<Long, List<Long>> {
        val id = create(r)
        return id to repo.reminders[id]!!.checklist.map { it.id }
    }

    @Test
    fun `items are done one by one and the last completes the block`() = runTest {
        val (id, item) = fountains()
        clock.now = at(26, 18)
        val ring = engine.onAlarm(open(id).id)!!
        assertEquals("Brunnen 1", ring.nextItem!!.title)
        val o = ring.occurrence.id

        clock.now = at(26, 18, 5)
        engine.act(o, Action.CheckItem(item[0]))
        assertEquals(OccurrenceState.DUE, repo.occurrences[o]!!.state)
        assertEquals(at(26, 18, 15), alarms.set[o]!!.first)
        assertEquals(listOf(item[0]), engine.itemsDone(o).map { it.itemId })
        clock.now = at(26, 18, 15)
        assertEquals("Brunnen 2", engine.onAlarm(o)!!.nextItem!!.title)

        engine.act(o, Action.CheckItem(item[1]))
        engine.act(o, Action.CheckItem(item[2]))
        assertEquals(Resolution.GOAL, repo.occurrences[o]!!.resolution)
        assertEquals(2, repo.events.count { it.occurrenceId == o && it.type == EventType.ITEM_DONE })
    }

    @Test
    fun `proof per item is stored with the item`() = runTest {
        val (id, item) = fountains(reminder().copy(checklist = items, proof = ProofSpec.PHOTO))
        val o = open(id).id
        engine.act(o, Action.CheckItem(item[0], Evidence(photoPath = "/p/1.jpg")))
        engine.act(o, Action.CheckItem(item[1]))
        assertEquals(listOf(true, false), engine.itemsDone(o).map { it.withProof })
        val proof = repo.proofs.single()
        assertEquals(ProofType.PHOTO, proof.type)
        assertEquals(item[0], proof.itemId)
        engine.act(o, Action.CheckItem(item[2], Evidence(photoPath = "/p/3.jpg")))
        assertEquals(Resolution.NO_PROOF, repo.occurrences[o]!!.resolution)
    }

    @Test
    fun `all done at once marks the open items with the block's proof`() = runTest {
        val (id, item) = fountains(reminder().copy(checklist = items, proof = ProofSpec.PHOTO))
        val o = open(id).id
        engine.act(o, Action.CheckItem(item[0], Evidence(photoPath = "/p/1.jpg")))
        engine.act(o, Action.Complete(false, Evidence(photoPath = "/p/all.jpg")))
        assertEquals(Resolution.GOAL, repo.occurrences[o]!!.resolution)
        assertEquals(item, engine.itemsDone(o).map { it.itemId })
        assertNull(repo.proofs.last().itemId)
    }

    @Test
    fun `the minimum closes the block without doing the items`() = runTest {
        val (id, _) = fountains(reminder().copy(checklist = items, minimum = "ein Brunnen"))
        val o = open(id).id
        engine.act(o, Action.Complete(true))
        assertEquals(Resolution.MINIMUM, repo.occurrences[o]!!.resolution)
        assertTrue(engine.itemsDone(o).isEmpty())
    }

    @Test
    fun `the rest comes back tomorrow with only the open items`() = runTest {
        val (id, item) = fountains()
        clock.now = at(26, 18)
        val o = engine.onAlarm(open(id).id)!!.occurrence.id
        engine.act(o, Action.CheckItem(item[0]))
        val result = engine.act(o, Action.DeferRest(at(27, 18)))
        assertTrue(result is TransitionResult.Ok)
        assertEquals(0, repo.occurrences[o]!!.postponeCount)
        assertEquals(at(27, 18), alarms.set[o]!!.first)

        clock.now = at(27, 18)
        assertEquals("Brunnen 2", engine.onAlarm(o)!!.nextItem!!.title)
        // No item today: deferring again counts (limit 1), then the limit is reached.
        assertTrue(engine.act(o, Action.DeferRest(at(28, 18))) is TransitionResult.Ok)
        clock.now = at(28, 18)
        engine.onAlarm(o)
        assertTrue(engine.act(o, Action.DeferRest(at(29, 18))) is TransitionResult.Rejected)
    }

    @Test
    fun `progress that day carries the rest over at the end of the day`() = runTest {
        val (id, item) = fountains()
        clock.now = at(26, 18)
        val o = engine.onAlarm(open(id).id)!!.occurrence.id
        engine.act(o, Action.CheckItem(item[0]))
        clock.now = at(26, 23)
        assertNull(engine.onAlarm(o))
        val carried = repo.occurrences[o]!!
        assertEquals(OccurrenceState.PLANNED, carried.state)
        assertEquals(at(27, 18), carried.currentAt)
        assertEquals(0, carried.postponeCount)

        // The next day without progress: missed, and the next round brings all items again.
        clock.now = at(27, 18)
        engine.onAlarm(o)
        clock.now = at(27, 23)
        engine.onAlarm(o)
        assertEquals(Resolution.MISSED, repo.occurrences[o]!!.resolution)
        assertTrue(engine.itemsDone(open(id).id).isEmpty())
    }

    @Test
    fun `after a reboot past the end of the day the rest is carried over too`() = runTest {
        val (id, item) = fountains()
        clock.now = at(26, 18)
        val o = engine.onAlarm(open(id).id)!!.occurrence.id
        engine.act(o, Action.CheckItem(item[0]))
        clock.now = at(27, 18, 30)
        assertTrue(engine.sync().isEmpty())
        assertEquals(at(27, 18, 32), repo.occurrences[o]!!.currentAt)
    }

    @Test
    fun `after done counts from the first item`() = runTest {
        val (id, item) = fountains(reminder(Schedule.AfterDone(7, LocalTime.of(18, 0))).copy(checklist = items))
        val o = open(id).id
        clock.now = at(26, 9)
        engine.act(o, Action.CheckItem(item[0]))
        clock.now = at(28, 9)
        engine.act(o, Action.Complete(false))
        assertEquals(at(2, 18, month = 11), open(id).dueAt)
    }
}
