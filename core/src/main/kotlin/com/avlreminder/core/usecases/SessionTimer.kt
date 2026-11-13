package com.avlreminder.core.usecases

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.SessionStep
import com.avlreminder.core.rules.Sessions

/**
 * The timer of a running session for the [Scheduler] (Anf. 7.11, design.md 3.1): checks, the
 * last reminder and the end, and when its next alarm comes.
 */
internal class SessionTimer(private val c: EngineContext, private val views: AlarmViews, private val s: Scheduler) {
    private fun now() = c.now()

    /**
     * The alarm of a running session (Anf. 7.11): a check, the last reminder or the end. Not yet
     * due (alarm early, a sync, the proof screen open) it is only planned again. Also after a
     * reboot: a check missed meanwhile rings now, a maximum passed brings the last reminder (D7).
     */
    suspend fun onAlarm(reminder: Reminder, occurrence: Occurrence): Ring? {
        val now = now()
        val at = Sessions.nextAlarm(reminder, occurrence)
        val proof = occurrence.pausedUntil
        if (at != null && (at > now.plus(ReminderEngine.EARLY_TOLERANCE) || (proof != null && proof > now.plus(ReminderEngine.EARLY_TOLERANCE)))) {
            s.plan(reminder, occurrence)
            return null
        }
        val paused = c.settings().alarmsPaused(now)
        // Without its settings (switched off while running, e.g. a restored backup) it ends at once.
        val step = if (at == null) SessionStep.TimeOut else Sessions.step(
            reminder, occurrence, now, quiet = c.device.isDoNotDisturb() && !reminder.ignoreDnd, paused = paused,
        )
        return when (step) {
            is SessionStep.Check -> {
                val asked = s.apply(reminder, occurrence, Action.AskCheck(step.again))
                s.plan(reminder, asked)
                views.ring(reminder, asked)
            }
            is SessionStep.FinalCall -> {
                val called = s.apply(reminder, occurrence, Action.FinalCall(step.audible, step.again))
                s.plan(reminder, called)
                views.ring(reminder, called)
            }
            SessionStep.TimeOut -> {
                s.close(reminder, occurrence, Action.EndSession(paused))
                s.createNext(reminder)?.let { s.plan(reminder, it) }
                null
            }
        }
    }

    /**
     * The next alarm of a running session: check, last reminder or the end of the time to react,
     * after the proof screen if it is open. Checks of a relaxed session are exact alarms; the last
     * reminder is an alarm clock, as strict as possible (D5).
     */
    fun plan(reminder: Reminder, occurrence: Occurrence) {
        val next = Sessions.nextAlarm(reminder, occurrence) ?: now()
        val proof = occurrence.pausedUntil
        val at = if (proof != null && proof > next) proof else next
        val spec = reminder.session
        val started = occurrence.startedAt
        val final = spec == null || started == null || occurrence.finalCallAt != null || next >= Sessions.maxAt(spec, started)
        val kind = if (!final && spec?.checkStrictness == Strictness.RELAXED) AlarmKind.EXACT else AlarmKind.ALARM_CLOCK
        c.alarms.set(occurrence.id, at, kind)
        c.preNotices.cancel(occurrence.id)
    }
}
