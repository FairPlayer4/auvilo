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
import com.avlreminder.core.model.severalADay
import java.time.Duration
import java.time.Instant

sealed interface Action {
    /** The planned time was reached. */
    data object Fire : Action
    /** Arrived at the trigger's place: due now, before the planned time. */
    data object Arrive : Action
    /** Not at the trigger's place at the planned time: the chance did not come (neutral). */
    data object Drop : Action
    /** Snoozed or postponed and not at the place at the new time: waits for the arrival (7.29 OZ5). */
    data object AwaitArrival : Action
    /** Another ringing round without reaction. */
    data object Escalate : Action
    data class Snooze(val duration: Duration) : Action
    data class Postpone(val to: Instant) : Action
    /** The proof screen opened: the alarm pauses until [until]. */
    data class StartProof(val until: Instant) : Action
    /** The proof screen was left without completing: the escalation goes on at [until]. */
    data class AbortProof(val until: Instant) : Action
    /**
     * Incomplete [evidence] for a reminder that asks for proof resolves as [Resolution.NO_PROOF].
     * [blockerSolved]: a solved blocker counts as the minimum (S7), without proof.
     */
    data class Complete(
        val minimumOnly: Boolean,
        val evidence: Evidence = Evidence(),
        val note: String? = null,
        val blockerSolved: Boolean = false,
    ) : Action
    data class Skip(val reason: String?) : Action
    data object Miss : Action
    /** One checklist item done; the last one completes the occurrence. */
    data class CheckItem(val itemId: Long, val evidence: Evidence = Evidence()) : Action
    /** The open checklist items come back at [to]. */
    data class DeferRest(val to: Instant) : Action
    /** A deadline's window ended: quiet until the next window at [to]; not a postponement. */
    data class CloseWindow(val to: Instant) : Action
    /**
     * "Zurücknehmen" (Anf. 7.31): a done or skipped occurrence is open again and rings at [at].
     * Not a postponement; whether it may happen at all decides [com.avlreminder.core.usecases.ReminderEngine.reopen].
     */
    data class Reopen(val at: Instant) : Action
}

/** Facts beyond the occurrence itself that limit what is allowed. */
data class Limits(
    val minimumsThisWeek: Int = 0,
    /** Several times a day the limit counts days: the minimum was already used today (VS1). */
    val minimumToday: Boolean = false,
    /** The previous text proof of this reminder; the same text again does not count. */
    val lastProofText: String? = null,
    /** Checklist items already done in this occurrence (several a day: in the list's round) → whether their proof was complete. */
    val itemsDone: Map<Long, Boolean> = emptyMap(),
    /** An item was done today: deferring the rest then does not count as postponing (C5c). */
    val itemDoneToday: Boolean = false,
    /** A deadline not yet passed: postponing must stay before it. */
    val deadline: Instant? = null,
    /** A blocker was solved since the last occurrence and may count as the minimum (S7). */
    val blockerSolved: Boolean = false,
    /** Several times a day: the next time; postponing must stay before it (VS1). */
    val nextTime: Instant? = null,
    /** Taken back after a completion with complete proof: that proof counts again (Anf. 7.31). */
    val proofCarried: Boolean = false,
)

sealed interface TransitionResult {
    data class Ok(val occurrence: Occurrence, val event: Event) : TransitionResult
    data class Rejected(val reason: String) : TransitionResult
}

object Lifecycle {
    /** Note of an occurrence closed with a solved blocker as the minimum. */
    const val BLOCKER_SOLVED = "Blocker gelöst"

    /** Done in any way, or skipped by hand (not by the system: goal reached, pause, …); missed and dropped stay. */
    fun canReopen(occurrence: Occurrence): Boolean =
        occurrence.state == DONE ||
            (occurrence.state == CLOSED && occurrence.resolution == Resolution.SKIPPED && occurrence.note !in SkipReasons.SYSTEM)

    fun canPostpone(reminder: Reminder, occurrence: Occurrence): Boolean =
        reminder.postponeLimit == null || occurrence.postponeCount < reminder.postponeLimit

    fun postponesLeft(reminder: Reminder, occurrence: Occurrence): Int? =
        reminder.postponeLimit?.let { (it - occurrence.postponeCount).coerceAtLeast(0) }

    fun canUseMinimum(reminder: Reminder, limits: Limits): Boolean =
        reminder.minimum != null && minimumLeft(reminder, limits)

    /** A solved blocker as the minimum (S7); the weekly limit for the minimum applies. */
    fun canUseSolvedBlocker(reminder: Reminder, limits: Limits): Boolean =
        reminder.solvedBlockerIsMinimum && limits.blockerSolved && minimumLeft(reminder, limits)

    private fun minimumLeft(reminder: Reminder, limits: Limits): Boolean =
        reminder.minimumLimitPerWeek == null || limits.minimumsThisWeek < reminder.minimumLimitPerWeek || limits.minimumToday

    fun minimumsLeft(reminder: Reminder, limits: Limits): Int? =
        reminder.minimumLimitPerWeek?.let { (it - limits.minimumsThisWeek).coerceAtLeast(0) }

    fun transition(
        reminder: Reminder,
        occurrence: Occurrence,
        action: Action,
        now: Instant,
        limits: Limits = Limits(),
    ): TransitionResult {
        fun ok(o: Occurrence, type: EventType, detail: String? = null) =
            TransitionResult.Ok(o, Event(occurrenceId = occurrence.id, at = now, type = type, detail = detail))
        if (action is Action.Reopen) {
            if (!canReopen(occurrence)) return TransitionResult.Rejected("only a done occurrence or one skipped by hand can be taken back")
            if (action.at <= now) return TransitionResult.Rejected("must ring again in the future")
            // What the completion itself set goes back: its first progress, its note.
            val progress = occurrence.firstProgressAt?.takeIf { it != occurrence.resolvedAt }
            return ok(
                occurrence.copy(
                    state = PLANNED, currentAt = action.at, resolution = null, resolvedAt = null, note = null,
                    pausedUntil = null, awaitsArrival = false, firstProgressAt = progress,
                ),
                EventType.REOPENED, occurrence.resolution?.name,
            )
        }
        if (!occurrence.isOpen) return TransitionResult.Rejected("occurrence is already resolved")

        return when (action) {
            Action.Fire ->
                if (occurrence.state != PLANNED) TransitionResult.Rejected("only a planned occurrence can fire")
                else ok(occurrence.copy(state = DUE, firedAt = occurrence.firedAt ?: now, awaitsArrival = false), EventType.DUE)

            Action.Arrive ->
                if (occurrence.state != PLANNED) TransitionResult.Rejected("only a planned occurrence can fire")
                else ok(
                    occurrence.copy(state = DUE, currentAt = now, firedAt = occurrence.firedAt ?: now, awaitsArrival = false),
                    EventType.DUE, "Ankunft",
                )

            // Once it rang the chance has come: snoozed or postponed it is not dropped any more (7.29 OZ5).
            Action.Drop ->
                if (occurrence.state != PLANNED || occurrence.rangOrMoved) TransitionResult.Rejected("only a planned occurrence that never rang or moved can be dropped")
                else ok(
                    occurrence.copy(state = CLOSED, resolution = Resolution.DROPPED, resolvedAt = now),
                    EventType.DROPPED,
                )

            Action.AwaitArrival ->
                if (occurrence.state != PLANNED || !occurrence.rangOrMoved) TransitionResult.Rejected("only a snoozed or postponed occurrence awaits the arrival")
                else ok(occurrence.copy(awaitsArrival = true), EventType.AWAITING_ARRIVAL)

            Action.Escalate ->
                if (occurrence.state == PLANNED) TransitionResult.Rejected("a planned occurrence cannot escalate")
                else ok(occurrence.copy(state = ESCALATING, pausedUntil = null), EventType.ESCALATED)

            is Action.Snooze ->
                if (occurrence.state == PLANNED) TransitionResult.Rejected("nothing to snooze yet")
                else ok(
                    occurrence.copy(state = PLANNED, currentAt = now.plus(action.duration), pausedUntil = null, awaitsArrival = false),
                    EventType.SNOOZED, "${action.duration.toMinutes()} min",
                )

            is Action.Postpone -> when {
                !canPostpone(reminder, occurrence) -> TransitionResult.Rejected("postpone limit reached")
                action.to <= now -> TransitionResult.Rejected("postpone target must lie in the future")
                limits.deadline != null && action.to >= limits.deadline -> TransitionResult.Rejected("postpone target after the deadline")
                limits.nextTime != null && action.to >= limits.nextTime -> TransitionResult.Rejected("postpone target after the next time")
                else -> ok(
                    occurrence.copy(
                        state = PLANNED, currentAt = action.to, postponeCount = occurrence.postponeCount + 1, pausedUntil = null, awaitsArrival = false,
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
                if (action.blockerSolved) {
                    if (!canUseSolvedBlocker(reminder, limits)) return TransitionResult.Rejected("no solved blocker")
                    return ok(
                        occurrence.copy(state = DONE, resolution = Resolution.MINIMUM, resolvedAt = now, note = action.note ?: BLOCKER_SOLVED),
                        EventType.COMPLETED, Resolution.MINIMUM.name,
                    )
                }
                if (action.minimumOnly && !canUseMinimum(reminder, limits)) {
                    return TransitionResult.Rejected("minimum not available")
                }
                val proofMissing = !limits.proofCarried && ProofCheck.problems(reminder.proof, action.evidence, limits.lastProofText).isNotEmpty()
                val resolution = when {
                    proofMissing -> Resolution.NO_PROOF
                    action.minimumOnly -> Resolution.MINIMUM
                    else -> Resolution.GOAL
                }
                val progress = if (reminder.checklist.isEmpty()) occurrence.firstProgressAt else occurrence.firstProgressAt ?: now
                ok(
                    occurrence.copy(state = DONE, resolution = resolution, resolvedAt = now, note = action.note, firstProgressAt = progress),
                    EventType.COMPLETED, resolution.name,
                )
            }

            is Action.CheckItem -> {
                val item = reminder.checklist.firstOrNull { it.id == action.itemId }
                    ?: return TransitionResult.Rejected("unknown checklist item")
                if (item.id in limits.itemsDone) return TransitionResult.Rejected("item already done")
                val withProof = limits.proofCarried || ProofCheck.problems(reminder.proof, action.evidence, limits.lastProofText).isEmpty()
                val done = limits.itemsDone + (item.id to withProof)
                val progressed = occurrence.copy(firstProgressAt = occurrence.firstProgressAt ?: now, pausedUntil = null)
                // Several times a day one item is the part of this time; the rest comes at the next (VS1).
                val several = reminder.schedule.severalADay
                if (several || reminder.checklist.all { it.id in done }) {
                    val proofs = if (several) listOf(withProof) else done.values
                    val resolution = if (proofs.all { it }) Resolution.GOAL else Resolution.NO_PROOF
                    ok(progressed.copy(state = DONE, resolution = resolution, resolvedAt = now), EventType.COMPLETED, resolution.name)
                } else {
                    ok(progressed, EventType.ITEM_DONE, item.title)
                }
            }

            is Action.DeferRest -> {
                val counts = !limits.itemDoneToday
                when {
                    reminder.checklist.isEmpty() -> TransitionResult.Rejected("no checklist")
                    limits.nextTime != null -> TransitionResult.Rejected("no rest for tomorrow several times a day")
                    action.to <= now -> TransitionResult.Rejected("defer target must lie in the future")
                    counts && !canPostpone(reminder, occurrence) -> TransitionResult.Rejected("postpone limit reached")
                    else -> ok(
                        occurrence.copy(
                            state = PLANNED, currentAt = action.to, pausedUntil = null, awaitsArrival = false,
                            postponeCount = occurrence.postponeCount + if (counts) 1 else 0,
                        ),
                        EventType.DEFERRED, action.to.toString(),
                    )
                }
            }

            is Action.CloseWindow ->
                if (action.to <= now) TransitionResult.Rejected("next window must lie in the future")
                else ok(occurrence.copy(state = PLANNED, currentAt = action.to, pausedUntil = null, awaitsArrival = false), EventType.WINDOW_CLOSED, action.to.toString())

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
