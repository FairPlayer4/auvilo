package com.avlreminder.core.rules

import com.avlreminder.core.at
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState.CLOSED
import com.avlreminder.core.model.OccurrenceState.DONE
import com.avlreminder.core.model.OccurrenceState.DUE
import com.avlreminder.core.model.OccurrenceState.ESCALATING
import com.avlreminder.core.model.OccurrenceState.PLANNED
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.reminder
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifecycleTest {
    private val now = at(26, 18, 5)
    private val r = reminder().copy(id = 1, minimum = "Wasser wechseln", postponeLimit = 1, minimumLimitPerWeek = 1)
    private val planned = Occurrence(id = 7, reminderId = 1, dueAt = at(26, 18))
    private val due = planned.copy(state = DUE)

    private fun ok(result: TransitionResult) = (result as TransitionResult.Ok)
    private fun rejected(result: TransitionResult) = assertTrue(result is TransitionResult.Rejected, "expected rejection: $result")

    @Test
    fun `fire makes a planned occurrence due`() {
        val res = ok(Lifecycle.transition(r, planned, Action.Fire, now))
        assertEquals(DUE, res.occurrence.state)
        assertEquals(EventType.DUE, res.event.type)
        assertEquals(7, res.event.occurrenceId)
        assertEquals(now, res.event.at)
    }

    @Test
    fun `fire only works on planned occurrences`() {
        rejected(Lifecycle.transition(r, due, Action.Fire, now))
    }

    @Test
    fun `escalate needs a fired occurrence`() {
        rejected(Lifecycle.transition(r, planned, Action.Escalate, now))
        assertEquals(ESCALATING, ok(Lifecycle.transition(r, due, Action.Escalate, now)).occurrence.state)
    }

    @Test
    fun `snooze plans the occurrence again later`() {
        val res = ok(Lifecycle.transition(r, due, Action.Snooze(Duration.ofMinutes(10)), now))
        assertEquals(PLANNED, res.occurrence.state)
        assertEquals(now.plus(Duration.ofMinutes(10)), res.occurrence.currentAt)
        assertEquals(at(26, 18), res.occurrence.dueAt)
        assertEquals("10 min", res.event.detail)
        rejected(Lifecycle.transition(r, planned, Action.Snooze(Duration.ofMinutes(10)), now))
    }

    @Test
    fun `postpone counts against the limit`() {
        val res = ok(Lifecycle.transition(r, due, Action.Postpone(at(27, 18)), now))
        assertEquals(1, res.occurrence.postponeCount)
        assertEquals(at(27, 18), res.occurrence.currentAt)
        assertEquals(PLANNED, res.occurrence.state)
        assertEquals(0, Lifecycle.postponesLeft(r, res.occurrence))
        rejected(Lifecycle.transition(r, res.occurrence.copy(state = DUE), Action.Postpone(at(28, 18)), now))
    }

    @Test
    fun `postpone without limit is always possible`() {
        val unlimited = r.copy(postponeLimit = null)
        assertTrue(Lifecycle.canPostpone(unlimited, due.copy(postponeCount = 99)))
        assertNull(Lifecycle.postponesLeft(unlimited, due))
    }

    @Test
    fun `postpone must point into the future`() {
        rejected(Lifecycle.transition(r, due, Action.Postpone(now), now))
    }

    @Test
    fun `complete reaches the goal`() {
        val res = ok(Lifecycle.transition(r, due, Action.Complete(minimumOnly = false, note = "Filter"), now))
        assertEquals(DONE, res.occurrence.state)
        assertEquals(Resolution.GOAL, res.occurrence.resolution)
        assertEquals(now, res.occurrence.resolvedAt)
        assertEquals("Filter", res.occurrence.note)
    }

    @Test
    fun `complete is possible before the alarm`() {
        assertEquals(DONE, ok(Lifecycle.transition(r, planned, Action.Complete(false), now)).occurrence.state)
    }

    @Test
    fun `missing photo counts as not successful`() {
        val photo = r.copy(proof = ProofSpec.PHOTO)
        val res = ok(Lifecycle.transition(photo, due, Action.Complete(minimumOnly = false), now))
        assertEquals(Resolution.NO_PROOF, res.occurrence.resolution)
        assertEquals(DONE, res.occurrence.state)
    }

    @Test
    fun `confirm needs no extra proof`() {
        val res = ok(Lifecycle.transition(r, due, Action.Complete(minimumOnly = false), now))
        assertEquals(Resolution.GOAL, res.occurrence.resolution)
    }

    @Test
    fun `minimum counts as minimum`() {
        val res = ok(Lifecycle.transition(r, due, Action.Complete(minimumOnly = true), now))
        assertEquals(Resolution.MINIMUM, res.occurrence.resolution)
    }

    @Test
    fun `minimum is disabled when weekly limit is reached`() {
        assertFalse(Lifecycle.canUseMinimum(r, Limits(minimumsThisWeek = 1)))
        assertEquals(0, Lifecycle.minimumsLeft(r, Limits(minimumsThisWeek = 3)))
        rejected(Lifecycle.transition(r, due, Action.Complete(true), now, Limits(minimumsThisWeek = 1)))
    }

    @Test
    fun `minimum needs a minimum to be defined`() {
        val noMin = r.copy(minimum = null, minimumLimitPerWeek = null)
        assertFalse(Lifecycle.canUseMinimum(noMin, Limits()))
        assertNull(Lifecycle.minimumsLeft(noMin, Limits()))
        assertTrue(Lifecycle.canUseMinimum(r.copy(minimumLimitPerWeek = null), Limits(minimumsThisWeek = 50)))
    }

    @Test
    fun `skip is neutral and keeps the reason`() {
        val res = ok(Lifecycle.transition(r, due, Action.Skip("krank"), now))
        assertEquals(CLOSED, res.occurrence.state)
        assertEquals(Resolution.SKIPPED, res.occurrence.resolution)
        assertEquals("krank", res.occurrence.note)
        assertEquals("krank", res.event.detail)
    }

    @Test
    fun `miss closes the occurrence`() {
        val res = ok(Lifecycle.transition(r, due, Action.Miss, now))
        assertEquals(Resolution.MISSED, res.occurrence.resolution)
        assertEquals(EventType.MISSED, res.event.type)
    }

    @Test
    fun `resolved occurrences accept no further action`() {
        val done = ok(Lifecycle.transition(r, due, Action.Miss, now)).occurrence
        assertFalse(done.isOpen)
        rejected(Lifecycle.transition(r, done, Action.Complete(false), now))
    }

    @Test
    fun `starting the proof pauses until the window ends`() {
        val res = ok(Lifecycle.transition(r, due, Action.StartProof(at(26, 18, 15)), now))
        assertEquals(at(26, 18, 15), res.occurrence.pausedUntil)
        assertEquals(DUE, res.occurrence.state)
        assertEquals(EventType.PROOF_STARTED, res.event.type)
        rejected(Lifecycle.transition(r, planned, Action.StartProof(at(26, 18, 15)), now))
        rejected(Lifecycle.transition(r, due, Action.StartProof(now), now))
    }

    @Test
    fun `aborting the proof shortens the pause`() {
        val proving = due.copy(pausedUntil = at(26, 18, 15))
        val res = ok(Lifecycle.transition(r, proving, Action.AbortProof(at(26, 18, 6)), now))
        assertEquals(at(26, 18, 6), res.occurrence.pausedUntil)
        assertEquals(EventType.PROOF_ABORTED, res.event.type)
        rejected(Lifecycle.transition(r, due, Action.AbortProof(at(26, 18, 6)), now))
    }

    @Test
    fun `escalating or snoozing ends the pause`() {
        val proving = due.copy(pausedUntil = at(26, 18, 15))
        assertNull(ok(Lifecycle.transition(r, proving, Action.Escalate, now)).occurrence.pausedUntil)
        assertNull(ok(Lifecycle.transition(r, proving, Action.Snooze(Duration.ofMinutes(10)), now)).occurrence.pausedUntil)
        assertNull(ok(Lifecycle.transition(r, proving, Action.Postpone(at(27, 9)), now)).occurrence.pausedUntil)
    }

    @Test
    fun `text proof counts only when complete and new`() {
        val textual = r.copy(proof = ProofSpec(ProofKind.EVIDENCE, textMinChars = 5))
        fun resolve(text: String, last: String? = null) =
            ok(Lifecycle.transition(textual, due, Action.Complete(false, Evidence(text = text)), now, Limits(lastProofText = last)))
                .occurrence.resolution
        assertEquals(Resolution.GOAL, resolve("Filter gewechselt"))
        assertEquals(Resolution.NO_PROOF, resolve("kurz"))
        assertEquals(Resolution.NO_PROOF, resolve("Filter gewechselt", last = "Filter gewechselt"))
    }
}
