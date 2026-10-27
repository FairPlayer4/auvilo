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

enum class Resolution { GOAL, MINIMUM, NO_PROOF, SKIPPED, MISSED }

enum class EventType {
    CREATED, DUE, ESCALATED, SNOOZED, POSTPONED, COMPLETED, SKIPPED, MISSED, MISSED_DETECTED,
    PROOF_STARTED, PROOF_ABORTED,
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
}

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
