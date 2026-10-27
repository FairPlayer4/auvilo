package com.avlreminder.core.rules

import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState.CLOSED
import com.avlreminder.core.model.OccurrenceState.DONE
import com.avlreminder.core.model.OccurrenceState.DUE
import com.avlreminder.core.model.OccurrenceState.ESCALATING
import com.avlreminder.core.model.OccurrenceState.PLANNED
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import java.time.Duration
import java.time.Instant

sealed interface Action {
    /** The planned time was reached. */
    data object Fire : Action
    /** Another ringing round without reaction. */
    data object Escalate : Action
    data class Snooze(val duration: Duration) : Action
    data class Postpone(val to: Instant) : Action
    /** The proof screen opened: the alarm pauses until [until]. */
    data class StartProof(val until: Instant) : Action
    /** The proof screen was left without completing: the escalation goes on at [until]. */
    data class AbortProof(val until: Instant) : Action
    /** Incomplete [evidence] for a reminder that asks for proof resolves as [Resolution.NO_PROOF]. */
    data class Complete(val minimumOnly: Boolean, val evidence: Evidence = Evidence(), val note: String? = null) : Action
    data class Skip(val reason: String?) : Action
    data object Miss : Action
}

/** Facts beyond the occurrence itself that limit what is allowed. */
data class Limits(
    val minimumsThisWeek: Int = 0,
    /** The previous text proof of this reminder; the same text again does not count. */
    val lastProofText: String? = null,
)

sealed interface TransitionResult {
    data class Ok(val occurrence: Occurrence, val event: Event) : TransitionResult
    data class Rejected(val reason: String) : TransitionResult
}

object Lifecycle {

    fun canPostpone(reminder: Reminder, occurrence: Occurrence): Boolean =
        reminder.postponeLimit == null || occurrence.postponeCount < reminder.postponeLimit

    fun postponesLeft(reminder: Reminder, occurrence: Occurrence): Int? =
        reminder.postponeLimit?.let { (it - occurrence.postponeCount).coerceAtLeast(0) }

    fun canUseMinimum(reminder: Reminder, limits: Limits): Boolean =
        reminder.minimum != null &&
            (reminder.minimumLimitPerWeek == null || limits.minimumsThisWeek < reminder.minimumLimitPerWeek)

    fun minimumsLeft(reminder: Reminder, limits: Limits): Int? =
        reminder.minimumLimitPerWeek?.let { (it - limits.minimumsThisWeek).coerceAtLeast(0) }

    fun transition(
        reminder: Reminder,
        occurrence: Occurrence,
        action: Action,
        now: Instant,
        limits: Limits = Limits(),
    ): TransitionResult {
        if (!occurrence.isOpen) return TransitionResult.Rejected("occurrence is already resolved")
        fun ok(o: Occurrence, type: EventType, detail: String? = null) =
            TransitionResult.Ok(o, Event(occurrenceId = occurrence.id, at = now, type = type, detail = detail))

        return when (action) {
            Action.Fire ->
                if (occurrence.state != PLANNED) TransitionResult.Rejected("only a planned occurrence can fire")
                else ok(occurrence.copy(state = DUE), EventType.DUE)

            Action.Escalate ->
                if (occurrence.state == PLANNED) TransitionResult.Rejected("a planned occurrence cannot escalate")
                else ok(occurrence.copy(state = ESCALATING, pausedUntil = null), EventType.ESCALATED)

            is Action.Snooze ->
                if (occurrence.state == PLANNED) TransitionResult.Rejected("nothing to snooze yet")
                else ok(
                    occurrence.copy(state = PLANNED, currentAt = now.plus(action.duration), pausedUntil = null),
                    EventType.SNOOZED, "${action.duration.toMinutes()} min",
                )

            is Action.Postpone -> when {
                !canPostpone(reminder, occurrence) -> TransitionResult.Rejected("postpone limit reached")
                action.to <= now -> TransitionResult.Rejected("postpone target must lie in the future")
                else -> ok(
                    occurrence.copy(
                        state = PLANNED, currentAt = action.to, postponeCount = occurrence.postponeCount + 1, pausedUntil = null,
                    ),
                    EventType.POSTPONED, action.to.toString(),
                )
            }

            is Action.StartProof -> when {
                occurrence.state == PLANNED -> TransitionResult.Rejected("nothing rings yet")
                action.until <= now -> TransitionResult.Rejected("proof window must lie in the future")
                else -> ok(occurrence.copy(pausedUntil = action.until), EventType.PROOF_STARTED, action.until.toString())
            }

            is Action.AbortProof ->
                if (occurrence.state == PLANNED || occurrence.pausedUntil == null) {
                    TransitionResult.Rejected("no proof running")
                } else {
                    ok(occurrence.copy(pausedUntil = action.until), EventType.PROOF_ABORTED)
                }

            is Action.Complete -> {
                if (action.minimumOnly && !canUseMinimum(reminder, limits)) {
                    return TransitionResult.Rejected("minimum not available")
                }
                val proofMissing = ProofCheck.problems(reminder.proof, action.evidence, limits.lastProofText).isNotEmpty()
                val resolution = when {
                    proofMissing -> Resolution.NO_PROOF
                    action.minimumOnly -> Resolution.MINIMUM
                    else -> Resolution.GOAL
                }
                ok(
                    occurrence.copy(state = DONE, resolution = resolution, resolvedAt = now, note = action.note),
                    EventType.COMPLETED, resolution.name,
                )
            }

            is Action.Skip -> ok(
                occurrence.copy(state = CLOSED, resolution = Resolution.SKIPPED, resolvedAt = now, note = action.reason),
                EventType.SKIPPED, action.reason,
            )

            Action.Miss -> ok(
                occurrence.copy(state = CLOSED, resolution = Resolution.MISSED, resolvedAt = now),
                EventType.MISSED,
            )
        }
    }
}
