package com.avlreminder.core.usecases

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.rules.Sessions
import java.time.Duration
import com.avlreminder.core.rules.WeekStatus
import java.time.Instant

/**
 * Everything the alarm screen and the notification show about an open occurrence, with the
 * limits already evaluated: one place for what one may do now.
 */
data class AlarmView(
    val reminder: Reminder,
    val occurrence: Occurrence,
    val minimumAllowed: Boolean,
    /** null = unlimited. */
    val minimumsLeft: Int?,
    val canPostpone: Boolean,
    /** null = unlimited. */
    val postponesLeft: Int?,
    /** Snooze buttons that stay before the end of the day. */
    val snooze: List<Int> = emptyList(),
    /** Current "Startklar" answers. */
    val prep: Map<Int, PrepAnswer> = emptyMap(),
    val lastProofText: String? = null,
    /** Opened before the reminder is due ("vorab erledigen"): only done and minimum. */
    val early: Boolean = false,
    /** Checklist items done: in this occurrence; several times a day over the times (VS1). */
    val done: Set<Long> = emptySet(),
    /** "Rest auf morgen": free after an item today, otherwise a postponement; not several times a day. */
    val canDeferRest: Boolean = false,
    /** The week so far, for a reminder with a weekly goal. */
    val week: WeekStatus? = null,
    /** "Blocker gelöst" may close it as the minimum (S7). */
    val blockerSolved: Boolean = false,
    /** It got stuck several times in a row: ask "Was hält dich gerade ab?" (S8). */
    val askBlocker: Boolean = false,
    /** A deadline's next window from tomorrow on ("Auf morgen", not a postponement). */
    val tomorrow: Instant? = null,
    /** A deadline not yet passed: postponing must stay before it. */
    val deadline: Instant? = null,
    /** Several times a day (VS1): postponing must stay before the next time. */
    val nextTime: Instant? = null,
    /** Open blockers of the reminder. */
    val blockers: List<Blocker> = emptyList(),
    /** When it would count as missed (end of the day, next time, end of the window): "heute später" stays before (7.32 VB2). */
    val missedAt: Instant? = null,
    /** Taken back after a completion with complete proof: done again without a new one (Anf. 7.31). */
    val proofCarried: Boolean = false,
    /** "Ich starte jetzt" is offered (Anf. 7.11 D1), also before it is due (7.20 V2). */
    val canStart: Boolean = false,
    /** The running session; the alarm is then a check or the last reminder. */
    val session: SessionView? = null,
    /** A limit per week or month is used up: from then on postponing works again (Z2b). */
    val postponeAgainAt: Instant? = null,
) {
    /** The first open checklist item. */
    val nextItem: ChecklistItem? get() = reminder.checklist.firstOrNull { it.id !in done }

    /**
     * Done without the alarm screen (notification, watch): photo, text or audio cannot be given
     * there, so "done" would always count as without proof.
     */
    val quickDone: Boolean get() = !reminder.proof.needsEvidence

    /** "Teilschritt" without the alarm screen: allowed and, as [quickDone], no proof to give (Review-Lauf 6). */
    val quickMinimum: Boolean get() = minimumAllowed && quickDone

    /** The reminder has a minimum, but its weekly limit is used up. */
    val minimumUsedUp: Boolean get() = reminder.hasMinimum && !minimumAllowed

    /**
     * The answers of the running session ([Sessions.answers]); "Teilschritt" with a minimum even
     * when its weekly limit is used up – the screen shows it, [minimumAllowed] says whether it works.
     */
    val sessionAnswers: List<SessionAnswer>
        get() = session?.let { Sessions.answers(it.final, it.suggestion, reminder.hasMinimum) }.orEmpty()
}

/** A running session as the check, the session screen and its notification show it (7.11). */
data class SessionView(
    val startedAt: Instant,
    /** When the maximum duration is reached (D5). */
    val maxAt: Instant,
    /** The last reminder: only the answers that end it (D5). */
    val final: Boolean,
    /** The answer the durations suggest (D4). */
    val suggestion: SessionAnswer,
    /** The average of the last sessions done (D6); null without any. */
    val average: Duration?,
    /** Its interval: "Noch dran" asks again after it. */
    val checkMinutes: Int,
)

/** Something that has to ring now, and how loud. */
data class Ring(val view: AlarmView, val level: AlarmLevel) {
    val reminder: Reminder get() = view.reminder
    val occurrence: Occurrence get() = view.occurrence
    val snooze: List<Int> get() = view.snooze
    val nextItem: ChecklistItem? get() = view.nextItem
    val week: WeekStatus? get() = view.week
    val blockerSolved: Boolean get() = view.blockerSolved
    val askBlocker: Boolean get() = view.askBlocker
    val tomorrow: Instant? get() = view.tomorrow
}

/**
 * What the user notices of an alarm: the engine rings and ends it, once the command is stored
 * (design.md 1, Review-Lauf 5, E17). Nobody else stops a ringing alarm or removes its notification.
 */
fun interface RingPort {
    fun ring(ring: Ring)

    /**
     * [occurrenceId] rings no more: stop the sound and take its notification away. After an
     * action on it, when it is closed (also by the system: missed, the end of the day, the end of
     * a session), deleted or paused. Not for one that rings again in the same command.
     */
    fun end(occurrenceId: Long) {}
}
