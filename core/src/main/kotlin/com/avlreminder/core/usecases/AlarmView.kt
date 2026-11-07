package com.avlreminder.core.usecases

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.AlarmLevel
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
) {
    /** The first open checklist item. */
    val nextItem: ChecklistItem? get() = reminder.checklist.firstOrNull { it.id !in done }
}

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

/** Rings what the engine found due; called once the command that found it is stored. */
fun interface RingPort {
    fun ring(ring: Ring)
}
