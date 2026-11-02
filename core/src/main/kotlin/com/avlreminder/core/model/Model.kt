package com.avlreminder.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Strictness { RELAXED, NORMAL, STRICT }

/** Why a reminder exists: to push through something unpleasant, or just to keep a record. */
enum class Character { OVERCOME, DOCUMENT }

enum class ProofKind {
    NONE,
    /** Tap "done", nothing else. */
    CONFIRM,
    /** Photo, text and/or audio as set in [ProofSpec]. */
    EVIDENCE,
}

/** What a reminder asks for as proof. Photo, text and audio can be combined. */
data class ProofSpec(
    val kind: ProofKind = ProofKind.CONFIRM,
    val photo: Boolean = false,
    /** Min. characters without whitespace; null = no text asked. */
    val textMinChars: Int? = null,
    /** Min. length of the recording; null = no audio asked. */
    val audioMinSeconds: Int? = null,
    /** How long the alarm stays quiet while the proof is being made. */
    val windowMinutes: Int = 10,
) {
    val needsEvidence: Boolean get() = kind == ProofKind.EVIDENCE

    companion object {
        val NONE = ProofSpec(ProofKind.NONE)
        val CONFIRM = ProofSpec(ProofKind.CONFIRM)
        val PHOTO = ProofSpec(ProofKind.EVIDENCE, photo = true)
    }
}

/** Type of one stored proof. */
enum class ProofType { PHOTO, TEXT, AUDIO }

enum class OccurrenceState { PLANNED, DUE, ESCALATING, DONE, CLOSED }

enum class Resolution {
    GOAL, MINIMUM, NO_PROOF, SKIPPED, MISSED,
    /** The chance did not come (not at the place at the fallback time); neutral. */
    DROPPED,
}

enum class EventType {
    CREATED, DUE, ESCALATED, SNOOZED, POSTPONED, COMPLETED, SKIPPED, MISSED, MISSED_DETECTED,
    PROOF_STARTED, PROOF_ABORTED,
    DROPPED, ITEM_DONE, DEFERRED,
    /** A deadline's window ended without completion; it rings again in the next window. */
    WINDOW_CLOSED,
    /** "Was hält dich gerade ab?" was asked (answered or skipped) for the occurrence. */
    BLOCKER_ASKED,
}

/** When a reminder is due. */
sealed interface Schedule {
    data class Once(val at: LocalDateTime) : Schedule
    data class Daily(val time: LocalTime) : Schedule
    data class Weekdays(val time: LocalTime, val days: Set<DayOfWeek>) : Schedule {
        init { require(days.isNotEmpty()) { "at least one weekday" } }
    }
    /**
     * [intervalDays] after the last completion, at [time]. Before the first completion it is due
     * on [firstOn]; without [firstOn] one interval after creation.
     */
    data class AfterDone(val intervalDays: Int, val time: LocalTime, val firstOn: LocalDate? = null) : Schedule {
        init { require(intervalDays >= 1) { "interval must be at least one day" } }
    }
    /** Once, without a time: due only through its [Arrival] ("wenn ich zuhause bin"). */
    data object OnArrival : Schedule
    /**
     * Once, by [due] (T1d): rings only in its windows – on [days] from [from] to [until] – at the
     * start of each window and then every [intervalMinutes] until it is done.
     */
    data class Deadline(
        val due: LocalDateTime,
        val days: Set<DayOfWeek>,
        val from: LocalTime,
        val until: LocalTime,
        val intervalMinutes: Int = 60,
    ) : Schedule {
        init {
            require(days.isNotEmpty()) { "at least one weekday" }
            require(intervalMinutes >= 1) { "interval must be at least one minute" }
        }
    }
}

/** A silent notice before the reminder is due (T6), naming its open blockers (S6). */
sealed interface PreNotice {
    /** The evening before the due day, at [time]. */
    data class Evening(val time: LocalTime) : PreNotice
    data class Before(val minutes: Int) : PreNotice
}

enum class BlockerOrigin {
    /** "Was fehlt?" of the "Startklar" questions (question 3). */
    STARTKLAR,
    /** "Was hält dich gerade ab?" after it got stuck (S8). */
    ASKED,
}

/** Something that keeps one from starting (S5); solved by hand or by doing its [taskReminderId]. */
data class Blocker(
    val id: Long = 0,
    val reminderId: Long,
    val text: String,
    val createdAt: Instant,
    val solvedAt: Instant? = null,
    val origin: BlockerOrigin = BlockerOrigin.STARTKLAR,
    /** The preparation task planned for it, a one-off with [Reminder.prepFor]. */
    val taskReminderId: Long? = null,
) {
    val isOpen: Boolean get() = solvedAt == null
}

/** A place for arrival triggers; V1.1b knows only "Zuhause". */
data class Place(
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int = 150,
)

/** What happens at the scheduled time if the arrival has not come. */
enum class WhenAway {
    /** Rings anyway. */
    RING,
    /** Rings only when at the place, otherwise the occurrence is dropped (neutral). */
    DROP,
}

/**
 * The schedule picks the day, the arrival the moment: arriving at [placeId] makes the day's
 * occurrence due right away; at the scheduled time [whenAway] applies.
 */
data class Arrival(val placeId: Long, val whenAway: WhenAway = WhenAway.DROP)

/**
 * "3–4× pro Woche, mindestens 1×" (Anf. Z1c) on a daily or weekday reminder; weeks run Monday
 * to Sunday. From [target] on further offers come silently, from [max] on none for the week.
 */
data class WeeklyGoal(val target: Int, val max: Int = target, val minimum: Int = 1)

/** One point of a checklist; the list order is the order of the points. */
data class ChecklistItem(val id: Long = 0, val title: String)

data class Reminder(
    val id: Long = 0,
    val title: String,
    val why: String? = null,
    val character: Character = Character.OVERCOME,
    val strictness: Strictness = Strictness.NORMAL,
    val schedule: Schedule,
    val goal: String? = null,
    /** null = this reminder has no minimum. */
    val minimum: String? = null,
    /** null = unlimited. */
    val minimumLimitPerWeek: Int? = null,
    val proof: ProofSpec = ProofSpec.CONFIRM,
    /** Max. postponements per occurrence; null = unlimited. */
    val postponeLimit: Int? = null,
    val active: Boolean = true,
    val createdAt: Instant,
    /** Rings as set even while the phone is on "do not disturb". */
    val ignoreDnd: Boolean = false,
    /** The short snooze buttons (1–2), in minutes. */
    val snoozeMinutes: List<Int> = listOf(10),
    /** Recorded message to oneself, played on tap only. */
    val motivationAudio: String? = null,
    /** Arrival trigger; null = time only. */
    val arrival: Arrival? = null,
    /** Empty = a single task. */
    val checklist: List<ChecklistItem> = emptyList(),
    /** Put away by hand; an archived reminder is paused. null = not archived. */
    val archivedAt: Instant? = null,
    /** null = every offer counts on its own. */
    val weeklyGoal: WeeklyGoal? = null,
    val preNotice: PreNotice? = null,
    /** A preparation task (S5) for this reminder; doing it solves its blocker. */
    val prepFor: Long? = null,
    /** A blocker solved since the last occurrence may be used as the minimum (S7). */
    val solvedBlockerIsMinimum: Boolean = false,
)

/** One concrete time a reminder is due. */
data class Occurrence(
    val id: Long = 0,
    val reminderId: Long,
    /** Originally planned time. */
    val dueAt: Instant,
    /** Current time after snoozing or postponing. */
    val currentAt: Instant = dueAt,
    val state: OccurrenceState = OccurrenceState.PLANNED,
    val postponeCount: Int = 0,
    val resolvedAt: Instant? = null,
    val resolution: Resolution? = null,
    val note: String? = null,
    /** While a proof is being made the escalation pauses until then. */
    val pausedUntil: Instant? = null,
    /** First checked item; "after done" counts from here for a checklist. */
    val firstProgressAt: Instant? = null,
) {
    val isOpen: Boolean get() = state != OccurrenceState.DONE && state != OccurrenceState.CLOSED
}

data class Proof(
    val id: Long = 0,
    val occurrenceId: Long,
    val type: ProofType,
    val filePath: String?,
    val takenAt: Instant,
    val text: String? = null,
    /** The checklist item this proof is for; null = the whole occurrence. */
    val itemId: Long? = null,
)

/** A checklist item done within one occurrence. */
data class ItemDone(
    val id: Long = 0,
    val occurrenceId: Long,
    val itemId: Long,
    val doneAt: Instant,
    /** The proof the reminder asks for was complete. */
    val withProof: Boolean = true,
)

/**
 * One answer to a "Startklar" question (1 = what do I gain, 2 = what could stop me,
 * 3 = can I start right away, 4 = first step). Append-only: the newest per question counts.
 */
data class PrepAnswer(
    val id: Long = 0,
    val reminderId: Long,
    val question: Int,
    val text: String?,
    /** "Weiß ich noch nicht" – an open point. */
    val unknown: Boolean = false,
    val at: Instant,
) {
    init { require(question in 1..4) { "question 1..4" } }
}

data class Event(
    val id: Long = 0,
    val occurrenceId: Long,
    val at: Instant,
    val type: EventType,
    val detail: String? = null,
)
