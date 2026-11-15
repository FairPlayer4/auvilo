package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState.CLOSED
import com.avlreminder.core.model.OccurrenceState.DONE
import com.avlreminder.core.model.OccurrenceState.DUE
import com.avlreminder.core.model.OccurrenceState.PLANNED
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceAndChecklistRulesTest {
    private val now = at(26, 17)
    private val items = listOf(ChecklistItem(1, "Brunnen 1"), ChecklistItem(2, "Brunnen 2"), ChecklistItem(3, "Futterautomat"))
    private val r = reminder().copy(id = 1, checklist = items, postponeLimit = 1)
    private val planned = Occurrence(id = 7, reminderId = 1, dueAt = at(26, 18))
    private val due = planned.copy(state = DUE)

    private fun ok(result: TransitionResult) = (result as TransitionResult.Ok)
    private fun rejected(result: TransitionResult) = assertTrue(result is TransitionResult.Rejected, "expected rejection: $result")

    // --- Arrival ---------------------------------------------------------------------------

    @Test
    fun `arrival makes a planned occurrence due now`() {
        val res = ok(Lifecycle.transition(r, planned, Action.Arrive, now))
        assertEquals(DUE, res.occurrence.state)
        assertEquals(now, res.occurrence.currentAt)
        assertEquals(at(26, 18), res.occurrence.dueAt)
        assertEquals("Ankunft", res.event.detail)
        rejected(Lifecycle.transition(r, due, Action.Arrive, now))
    }

    @Test
    fun `drop closes a planned occurrence neutrally`() {
        val res = ok(Lifecycle.transition(r, planned, Action.Drop, now))
        assertEquals(CLOSED, res.occurrence.state)
        assertEquals(Resolution.DROPPED, res.occurrence.resolution)
        assertEquals(EventType.DROPPED, res.event.type)
        rejected(Lifecycle.transition(r, due, Action.Drop, now))
    }

    @Test
    fun `on arrival is due once, from now`() {
        assertEquals(now, ScheduleRules.nextDue(Schedule.OnArrival, now, ZONE))
        assertNull(ScheduleRules.nextDue(Schedule.OnArrival, now, ZONE, previousDueAt = at(25, 9)))
    }

    @Test
    fun `waiting for an arrival needs a place`() {
        assertEquals(
            listOf("„Wenn ich zuhause bin“ braucht einen Ort"),
            Validation.problems(reminder(Schedule.OnArrival)),
        )
        assertTrue(Validation.problems(reminder(Schedule.OnArrival).copy(arrival = Arrival(1))).isEmpty())
    }

    // --- Checklist -------------------------------------------------------------------------

    @Test
    fun `checking an item keeps the occurrence open and notes the first progress`() {
        val res = ok(Lifecycle.transition(r, due.copy(pausedUntil = at(26, 18, 10)), Action.CheckItem(1), now))
        assertEquals(DUE, res.occurrence.state)
        assertEquals(now, res.occurrence.firstProgressAt)
        assertNull(res.occurrence.pausedUntil)
        assertEquals(EventType.ITEM_DONE, res.event.type)
        assertEquals("Brunnen 1", res.event.detail)

        val later = ok(Lifecycle.transition(r, res.occurrence, Action.CheckItem(2), now.plusSeconds(60), Limits(itemsDone = mapOf(1L to true))))
        assertEquals(now, later.occurrence.firstProgressAt)
    }

    @Test
    fun `items can be checked before the reminder is due`() {
        assertEquals(PLANNED, ok(Lifecycle.transition(r, planned, Action.CheckItem(3), now)).occurrence.state)
    }

    @Test
    fun `the last item completes the block`() {
        val res = ok(Lifecycle.transition(r, due, Action.CheckItem(3), now, Limits(itemsDone = mapOf(1L to true, 2L to true))))
        assertEquals(DONE, res.occurrence.state)
        assertEquals(Resolution.GOAL, res.occurrence.resolution)
        assertEquals(EventType.COMPLETED, res.event.type)
    }

    @Test
    fun `an item without its proof makes the block not successful`() {
        val photo = r.copy(proof = ProofSpec.PHOTO)
        val res = ok(Lifecycle.transition(photo, due, Action.CheckItem(3, Evidence(photoPath = "/p/3.jpg")), now, Limits(itemsDone = mapOf(1L to true, 2L to false))))
        assertEquals(Resolution.NO_PROOF, res.occurrence.resolution)
        val all = ok(Lifecycle.transition(photo, due, Action.CheckItem(3, Evidence(photoPath = "/p/3.jpg")), now, Limits(itemsDone = mapOf(1L to true, 2L to true))))
        assertEquals(Resolution.GOAL, all.occurrence.resolution)
    }

    @Test
    fun `unknown or already done items are rejected`() {
        rejected(Lifecycle.transition(r, due, Action.CheckItem(99), now))
        rejected(Lifecycle.transition(r, due, Action.CheckItem(1), now, Limits(itemsDone = mapOf(1L to true))))
    }

    @Test
    fun `completing a checklist notes the progress, a single task does not`() {
        assertEquals(now, ok(Lifecycle.transition(r, due, Action.Complete(false), now)).occurrence.firstProgressAt)
        assertNull(ok(Lifecycle.transition(r.copy(checklist = emptyList()), due, Action.Complete(false), now)).occurrence.firstProgressAt)
    }

    @Test
    fun `deferring the rest after doing an item today is free`() {
        val to = at(27, 18)
        val res = ok(Lifecycle.transition(r, due, Action.DeferRest(to), now, Limits(itemsDone = mapOf(1L to true), itemDoneToday = true)))
        assertEquals(PLANNED, res.occurrence.state)
        assertEquals(to, res.occurrence.currentAt)
        assertEquals(0, res.occurrence.postponeCount)
        assertEquals(EventType.DEFERRED, res.event.type)
        assertEquals(to.toString(), res.event.detail)
    }

    @Test
    fun `deferring without progress today counts against the postpone limit`() {
        val res = ok(Lifecycle.transition(r, due, Action.DeferRest(at(27, 18)), now))
        assertEquals(1, res.occurrence.postponeCount)
        // Marked, so a limit per week or month counts it (Z2b).
        assertEquals(at(27, 18).toString() + Lifecycle.COUNTED, res.event.detail)
        rejected(Lifecycle.transition(r, res.occurrence.copy(state = DUE), Action.DeferRest(at(28, 18)), now))
    }

    @Test
    fun `deferring needs a checklist and a future time`() {
        rejected(Lifecycle.transition(r.copy(checklist = emptyList()), due, Action.DeferRest(at(27, 18)), now))
        rejected(Lifecycle.transition(r, due, Action.DeferRest(now), now))
    }

    @Test
    fun `checklist items need a text and there is a maximum`() {
        assertEquals(listOf("Checklisten-Punkt ohne Text"), Validation.problems(r.copy(checklist = listOf(ChecklistItem(title = " ")))))
        val many = (1..21).map { ChecklistItem(title = "Punkt $it") }
        assertEquals(listOf("Höchstens 20 Checklisten-Punkte"), Validation.problems(r.copy(checklist = many)))
    }
}
