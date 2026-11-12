package com.avlreminder.core.model

import java.time.DayOfWeek
import java.time.Duration
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
) : java.io.Serializable {
    val needsEvidence: Boolean get() = kind == ProofKind.EVIDENCE

    companion object {
        /** Limits the validation checks and the form offers (Review-Lauf 5). */
        const val MAX_TEXT_CHARS = 2000
        const val MAX_AUDIO_SECONDS = 600
        const val MAX_WINDOW_MINUTES = 60
        val NONE = ProofSpec(ProofKind.NONE)
        val CONFIRM = ProofSpec(ProofKind.CONFIRM)
        val PHOTO = ProofSpec(ProofKind.EVIDENCE, photo = true)
    }
}

/** Type of one stored proof. */
enum class ProofType { PHOTO, TEXT, AUDIO }

enum class OccurrenceState {
    PLANNED, DUE, ESCALATING,
    /** "Ich starte jetzt" (Anf. 7.11): a session with timer and checks is running. */
    RUNNING,
    DONE, CLOSED,
}

enum class Resolution {
    GOAL, MINIMUM, NO_PROOF, SKIPPED, MISSED,
    /** The chance did not come (not at the place at the fallback time); neutral. */
    DROPPED,
    /** A session ended without the minimum (7.11 D3): no success, breaks the series like a miss. */
    ABORTED,
}

enum class EventType {
    CREATED, DUE, ESCALATED, SNOOZED, POSTPONED, COMPLETED, SKIPPED, MISSED, MISSED_DETECTED,
    PROOF_STARTED, PROOF_ABORTED,
    DROPPED, ITEM_DONE, DEFERRED,
    /** A deadline's window ended without completion; it rings again in the next window. */
    WINDOW_CLOSED,
    /** "Was hält dich gerade ab?" was asked (answered or skipped) for the occurrence. */
    BLOCKER_ASKED,
    /** Snoozed or postponed with an arrival: not at the place at the new time, it waits for the arrival (7.29 OZ5). */
    AWAITING_ARRIVAL,
    /** "Zurücknehmen" after a misclick (Anf. 7.31): open again; detail = the resolution taken back. */
    REOPENED,
    /** Arrived in the window or while waiting for it (7.29); detail = when it rings. */
    ARRIVED,
    /** "Ich starte jetzt" (7.11 D1): the session runs. */
    STARTED,
    /** A check of the running session rang (D3). */
    CHECK_ASKED,
    /** "Noch dran" (D3); the other answers complete or abort. */
    CHECK_ANSWERED,
    /** The maximum duration is reached: the last reminder rang (D5); detail "still" = not audible, its time did not start. */
    FINAL_CALL,
    /** "Abbruch" or the session ended without an answer (D3, D5); detail = the resolution. */
    ABORTED,
}

/** When a reminder is due. */
sealed interface Schedule {
    data class Once(val at: LocalDateTime) : Schedule
    /** Every day at [times] (VS1: several a day, ascending). */
    data class Daily(override val times: List<LocalTime>) : Schedule, AtTimes {
        constructor(time: LocalTime) : this(listOf(time))
        init { requireTimes(times) }
    }
    data class Weekdays(override val times: List<LocalTime>, val days: Set<DayOfWeek>) : Schedule, AtTimes {
        constructor(time: LocalTime, days: Set<DayOfWeek>) : this(listOf(time), days)
        init {
            requireTimes(times)
            require(days.isNotEmpty()) { "at least one weekday" }
        }
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

        companion object {
            const val MIN_INTERVAL = 5
            const val MAX_INTERVAL = 480
        }
    }
}

/** A schedule with one or more fixed times a day (VS1). */
interface AtTimes {
    val times: List<LocalTime>
    /** The first time of the day; the only one for most reminders. */
    val time: LocalTime get() = times.first()
    /** Several times a day: each counts as missed when the next one starts. */
    val severalADay: Boolean get() = times.size > 1
}

private fun requireTimes(times: List<LocalTime>) {
    require(times.isNotEmpty()) { "at least one time" }
    require(times.zipWithNext().all { (a, b) -> a < b }) { "times ascending without duplicates" }
}

/** Several times a day (VS1)? */
val Schedule.severalADay: Boolean get() = (this as? AtTimes)?.severalADay == true

/** A silent notice before the reminder is due (T6), naming its open blockers (S6). */
sealed interface PreNotice {
    /** The evening before the due day, at [time]. */
    data class Evening(val time: LocalTime) : PreNotice
    data class Before(val minutes: Int) : PreNotice {
        companion object {
            const val MIN_MINUTES = 5
            const val MAX_MINUTES = 1440
        }
    }
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

/** A place for arrival triggers: "Zuhause" and one more (Anf. 7.29 OZ6). */
data class Place(
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int = 150,
    /** Its WLAN access points (OZ3); empty: only the geofence knows it. */
    val wifis: List<PlaceWifi> = emptyList(),
) {
    /** Connected to one of these access points means being there. */
    fun hasWifi(bssid: String): Boolean = wifis.any { it.bssid.equals(bssid, ignoreCase = true) }
}

/**
 * One access point of a place. The [bssid] identifies it; names like "FRITZ!Box" exist many times.
 * [ssid] is only shown. Both are private: never in the diagnostic log.
 */
data class PlaceWifi(val bssid: String, val ssid: String? = null)

/** What happens at the scheduled time if the arrival has not come. */
enum class WhenAway {
    /** Rings anyway. */
    RING,
    /** Rings only when at the place, otherwise the occurrence is dropped (neutral). */
    DROP,
}

/**
 * The schedule picks the day, the arrival the moment (Anf. 7.29): the scheduled time is the latest
 * time, the window starts [leadMinutes] before it. Arriving in the window rings [bufferMinutes]
 * later; before it the arrival does not count. In the window the place is checked every
 * [checkMinutes]. At the latest time [whenAway] applies. [leadMinutes] 0: no window, an arrival
 * on the day rings right away as before 7.29.
 */
data class Arrival(
    val placeId: Long,
    val whenAway: WhenAway = WhenAway.DROP,
    val leadMinutes: Int = DEFAULT_LEAD,
    val bufferMinutes: Int = DEFAULT_BUFFER,
    val checkMinutes: Int = DEFAULT_CHECK,
) {
    init {
        require(leadMinutes in 0..MAX_LEAD) { "lead must be 0–$MAX_LEAD minutes" }
        require(bufferMinutes in 0..MAX_BUFFER) { "buffer must be 0–$MAX_BUFFER minutes" }
        require(checkMinutes in MIN_CHECK..MAX_CHECK) { "check interval must be $MIN_CHECK–$MAX_CHECK minutes" }
    }

    /** With a window (7.29); without it an arrival rings right away as before. */
    val hasWindow: Boolean get() = leadMinutes > 0

    companion object {
        /** F3 (2026-11-04): 90 min before, 5 min buffer, checked every 20 min. */
        const val DEFAULT_LEAD = 90
        const val DEFAULT_BUFFER = 5
        const val DEFAULT_CHECK = 20
        const val MAX_LEAD = 12 * 60
        const val MAX_BUFFER = 30
        const val MIN_CHECK = 15
        const val MAX_CHECK = 30
    }
}

/**
 * "3–4× pro Woche, mindestens 1×" (Anf. Z1c) on a daily or weekday reminder; weeks run Monday
 * to Sunday. From [target] on further offers come silently, from [max] on none for the week.
 */
data class WeeklyGoal(val target: Int, val max: Int = target, val minimum: Int = 1)

/**
 * "Ich starte jetzt" with timer and checks (Anf. 7.11). Checks every [checkMinutes] with their own
 * [checkStrictness] (D3); goal and minimum as a duration only suggest the answer (D4); after
 * [maxMinutes] the last reminder comes with [finalMinutes] to react (D5).
 */
data class SessionSpec(
    val checkMinutes: Int = DEFAULT_CHECK,
    val checkStrictness: Strictness = Strictness.RELAXED,
    val goalMinutes: Int? = null,
    val minimumMinutes: Int? = null,
    val maxMinutes: Int = DEFAULT_MAX,
    val finalMinutes: Int = DEFAULT_FINAL,
) {
    companion object {
        /** Bestätigt 2026-11-02 (anforderungen.md Kap. 9): every 10 min, relaxed, at most 90 min, 5 min to react. */
        const val DEFAULT_CHECK = 10
        const val DEFAULT_MAX = 90
        const val DEFAULT_FINAL = 5
        const val MIN_CHECK = 2
        const val MAX_CHECK = 120
        const val MAX_MAX = 12 * 60
        const val MAX_FINAL = 30
    }
}

/** One point of a checklist; the list order is the order of the points. */
/** Serializable for the form draft (Review-Lauf 5). */
data class ChecklistItem(val id: Long = 0, val title: String) : java.io.Serializable

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
    /** The hint about snoozing (NT2) counts only occurrences due from here on; null = all. */
    val hintQuietUntil: Instant? = null,
    /** "Ich starte jetzt" with timer and checks (7.11); null = done in one go as before. */
    val session: SessionSpec? = null,
) {
    companion object {
        /** Longest snooze button. */
        const val MAX_SNOOZE_MINUTES = 240
    }
}

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
    /**
     * When it first became due (fired or arrived). Null: it never rang yet. A planned occurrence
     * with it set was snoozed, postponed, deferred or its window closed (E5, Review 2026-11-05).
     */
    val firedAt: Instant? = null,
    /** Planned again after it rang, not at the place at the new time: rings on arrival, missed at the end of the day (7.29 OZ5). */
    val awaitsArrival: Boolean = false,
    /**
     * The arrival that counts for the next ring (7.29 OZ1, OZ4): in the window or while waiting for
     * it. Set, the occurrence rings at [currentAt] wherever one is then; a new time (snoozed,
     * postponed) clears it, as the decision is made anew.
     */
    val arrivedAt: Instant? = null,
    /** "Ich starte jetzt" (7.11): the session began; it stays after the end, for its duration (D6). */
    val startedAt: Instant? = null,
    /** While running: when the next check comes (an unanswered one asks again). */
    val nextCheckAt: Instant? = null,
    /** The last reminder rang audibly (D5): the time to react runs from here. */
    val finalCallAt: Instant? = null,
) {
    val isOpen: Boolean get() = state != OccurrenceState.DONE && state != OccurrenceState.CLOSED

    /** It rang already; planned again it keeps its day and its time. */
    val hasFired: Boolean get() = firedAt != null

    /**
     * It rang or was moved (also in advance, Anf. 7.32 VB4): its time is decided. An arrival
     * before it does not ring it, and away at its time it waits for the arrival instead of
     * being dropped.
     */
    val rangOrMoved: Boolean get() = hasFired || postponeCount > 0

    /** Where it stands now: planned and never rung, the possibly moved time; otherwise its due time. */
    val plannedAt: Instant get() = if (hasFired) dueAt else currentAt

    val isRunning: Boolean get() = state == OccurrenceState.RUNNING

    /**
     * When a resolved occurrence counts, for its day and week (goal, limits, statistics): a
     * session at its start (7.11 D7, 2026-11-12), otherwise when it was resolved; null while open.
     */
    val countedAt: Instant? get() = resolvedAt?.let { startedAt ?: it }

    /** How long the session took (D6); null without a finished session. */
    val sessionDuration: Duration?
        get() = if (startedAt != null && resolvedAt != null) Duration.between(startedAt, resolvedAt) else null
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
