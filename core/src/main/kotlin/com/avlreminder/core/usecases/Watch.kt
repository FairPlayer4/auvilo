package com.avlreminder.core.usecases

import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.rules.Sessions
import java.time.Instant

/**
 * What the watch app shows (Anf. 7.14 G5–G9, design.md 17): the phone builds it from the
 * engine's views and sends it; the watch only computes the display from it (G9).
 */
data class WatchState(
    /** Running sessions, started first. */
    val sessions: List<WatchSession> = emptyList(),
    /** Reminders with a session to start now: ringing first, then later today ("vorab", 7.20 V2). */
    val startable: List<WatchStart> = emptyList(),
    /** The next reminder at all, only without [sessions] and [startable] (for the tile). */
    val next: WatchNext? = null,
    /** All alarms paused by hand until then (PS7). */
    val pausedUntil: Instant? = null,
)

/** A running session on the watch (G5 without steps). */
data class WatchSession(
    val occurrenceId: Long,
    val title: String,
    val startedAt: Instant,
    /** The maximum duration is reached then (D5). */
    val maxAt: Instant,
    val goalMinutes: Int?,
    val minimumMinutes: Int?,
    /** The answers without the alarm screen, the suggested one first ([Sessions.quickAnswers]); "Abbruch" is always there. */
    val answers: List<SessionAnswer>,
    /** The last reminder rang (D5): no "Noch dran". */
    val final: Boolean,
    /** "Fertig" needs photo, text or audio: only on the phone (G4). */
    val proofOnPhone: Boolean,
    /** The reminder has a minimum, but its weekly limit is used up. */
    val minimumUsedUp: Boolean,
)

/** A reminder with a session that "Ich starte jetzt" starts (G7). */
data class WatchStart(val occurrenceId: Long, val title: String, val at: Instant, val ringing: Boolean)

/** The next reminder, shown only (G8). */
data class WatchNext(val title: String, val at: Instant)

/** A check of a running session rings (D3, D5); not [audible]: paused or "Nicht stören", the watch stays quiet too. */
data class WatchCheck(val occurrenceId: Long, val title: String, val audible: Boolean, val final: Boolean)

/** A button on the watch. */
enum class WatchAction {
    START, STILL_ON, DONE, MINIMUM, ABORT,
    /** "Rückgängig" after done or aborted (7.31); the engine's own command, not an [Action]. */
    REOPEN,
    ;

    /** The engine's action for the button; null for [REOPEN]. */
    fun toAction(): Action? = when (this) {
        START -> Action.Start
        STILL_ON -> Action.StillOn
        DONE -> Action.Complete(minimumOnly = false)
        MINIMUM -> Action.Complete(minimumOnly = true)
        ABORT -> Action.Abort
        REOPEN -> null
    }

    companion object {
        /** The button for an answer of the session. */
        fun of(answer: SessionAnswer): WatchAction = when (answer) {
            SessionAnswer.STILL_ON -> STILL_ON
            SessionAnswer.DONE -> DONE
            SessionAnswer.MINIMUM -> MINIMUM
            SessionAnswer.ABORT -> ABORT
        }
    }
}

/** A button pressed on the watch; [requestId] pairs it with its [WatchResult]. */
data class WatchCommand(val requestId: Long, val occurrenceId: Long, val action: WatchAction)

/** The phone's answer: done, or rejected with what the watch says. */
data class WatchResult(val requestId: Long, val ok: Boolean, val message: String? = null)

object Watch {
    /** At most this many reminders to start. */
    const val STARTABLE = 3

    /** Without an answer from the phone the button is free again after this (design.md 17). */
    const val ANSWER_TIMEOUT_MS = 10_000L

    /** "Rückgängig" on the watch, as long as on the phone (`Undo.TIMEOUT_MS`, 7.31). */
    const val UNDO_MS = 60_000L

    // Messages only, no data items: those the Data Layer would also sync over Google's servers (G9b).
    const val PATH_STATE = "/avl/state"
    const val PATH_HELLO = "/avl/hello"
    const val PATH_ACT = "/avl/act"
    const val PATH_RESULT = "/avl/result"
    const val PATH_CHECK = "/avl/check"
    const val PATH_CHECK_END = "/avl/check-end"

    /** Capabilities in `res/values/wear.xml`: how phone and watch find each other. */
    const val CAPABILITY_WATCH = "avl_watch"
    const val CAPABILITY_PHONE = "avl_phone"

    /**
     * The state for the watch from *Heute* and the alarm views of its open occurrences (one per
     * occurrence, any order). Answers and their order come from [Sessions.quickAnswers] (E18).
     */
    fun state(today: Today, views: List<AlarmView>, pausedUntil: Instant?): WatchState {
        val byId = views.associateBy { it.occurrence.id }
        val sessions = today.running.mapNotNull { byId[it.occurrence.id] }.mapNotNull { view ->
            val session = view.session ?: return@mapNotNull null
            val spec = view.reminder.session
            WatchSession(
                occurrenceId = view.occurrence.id,
                title = view.reminder.title,
                startedAt = session.startedAt,
                maxAt = session.maxAt,
                goalMinutes = spec?.goalMinutes,
                minimumMinutes = spec?.minimumMinutes,
                answers = Sessions.quickAnswers(session.final, session.suggestion, view.quickMinimum, view.quickDone),
                final = session.final,
                proofOnPhone = !view.quickDone,
                minimumUsedUp = view.minimumUsedUp,
            )
        }
        fun startable(entries: List<Entry>, ringing: Boolean) = entries.mapNotNull { byId[it.occurrence.id] }
            .filter { it.canStart && it.session == null }
            .map { WatchStart(it.occurrence.id, it.reminder.title, it.occurrence.currentAt, ringing) }
        val startable = (startable(today.dueNow, ringing = true) + startable(today.laterToday, ringing = false)).take(STARTABLE)
        val next = Overview.next(today)?.takeIf { sessions.isEmpty() && startable.isEmpty() }
            ?.let { WatchNext(it.reminder.title, it.occurrence.currentAt) }
        return WatchState(sessions, startable, next, pausedUntil)
    }

    /** The check of a running session for the watch; null if [ring] is not one. */
    fun check(ring: Ring): WatchCheck? = ring.view.session?.let {
        WatchCheck(ring.occurrence.id, ring.reminder.title, audible = ring.level != AlarmLevel.SILENT, final = it.final)
    }
}
