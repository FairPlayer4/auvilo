package com.avlreminder.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Strictness { RELAXED, NORMAL, STRICT }

/** Why a reminder exists: to push through something unpleasant, or just to keep a record. */
enum class Character { OVERCOME, DOCUMENT }

enum class ProofType { NONE, CONFIRM, PHOTO }

enum class OccurrenceState { PLANNED, DUE, ESCALATING, DONE, CLOSED }

enum class Resolution { GOAL, MINIMUM, NO_PROOF, SKIPPED, MISSED }

enum class EventType { CREATED, DUE, ESCALATED, SNOOZED, POSTPONED, COMPLETED, SKIPPED, MISSED, MISSED_DETECTED }

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
    val proof: ProofType = ProofType.CONFIRM,
    /** Max. postponements per occurrence; null = unlimited. */
    val postponeLimit: Int? = null,
    val active: Boolean = true,
    val createdAt: Instant,
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
) {
    val isOpen: Boolean get() = state != OccurrenceState.DONE && state != OccurrenceState.CLOSED
}

data class Proof(
    val id: Long = 0,
    val occurrenceId: Long,
    val type: ProofType,
    val filePath: String?,
    val takenAt: Instant,
)

data class Event(
    val id: Long = 0,
    val occurrenceId: Long,
    val at: Instant,
    val type: EventType,
    val detail: String? = null,
)
