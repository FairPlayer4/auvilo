package com.avlreminder.core.rules

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.SessionSpec
import java.time.Duration
import java.time.Instant

/** The answers to a check (Anf. 7.11 D3); [Sessions.suggestion] picks the one to suggest (D4). */
enum class SessionAnswer { STILL_ON, DONE, MINIMUM, ABORT }

/** What the alarm of a running session does now (see [Sessions.step]). */
sealed interface SessionStep {
    /** Ask "Noch dran?"; unanswered it asks again at [again]. */
    data class Check(val again: Instant) : SessionStep
    /**
     * The maximum duration is reached: the last reminder (D5). Not [audible] ("Nicht stören"),
     * its time to react does not start yet (D7); it asks again at [again].
     */
    data class FinalCall(val audible: Boolean, val again: Instant) : SessionStep
    /** No answer in time, or the maximum reached during the pause by hand (PS7): the session ends. */
    data object TimeOut : SessionStep
}

/**
 * The session with timer and checks (Anf. 7.11, design.md E.3): when its alarm comes, what it
 * does then and what the answers suggest. A session ends only through an answer or its maximum
 * duration; the end of the day does not apply to it (D7).
 */
object Sessions {
    /** Sessions looked at for the average (D6). */
    const val HISTORY = 10

    fun canStart(reminder: Reminder, occurrence: Occurrence): Boolean =
        reminder.session != null && occurrence.state in STARTABLE

    /** When the maximum duration is reached (D5). */
    fun maxAt(spec: SessionSpec, startedAt: Instant): Instant = startedAt.plus(minutes(spec.maxMinutes))

    /**
     * When the alarm of the running [occurrence] comes: the end of the time to react after the last
     * reminder, otherwise the next check. Checks never lie after the maximum duration (see
     * [Lifecycle]); only a silent last reminder asks again after it. Null if it is not running.
     */
    fun nextAlarm(reminder: Reminder, occurrence: Occurrence): Instant? {
        val spec = reminder.session ?: return null
        val started = occurrence.startedAt ?: return null
        if (occurrence.state != OccurrenceState.RUNNING) return null
        occurrence.finalCallAt?.let { return it.plus(minutes(spec.finalMinutes)) }
        return occurrence.nextCheckAt ?: maxAt(spec, started)
    }

    /**
     * The alarm came. Which step is due follows from the alarm's target, not from the clock: an
     * alarm a little early (Android may fire up to a minute before) must not ask a check instead
     * of the last reminder.
     * [quiet]: "Nicht stören" keeps it silent; [paused]: all alarms paused by hand (PS7).
     */
    fun step(reminder: Reminder, occurrence: Occurrence, now: Instant, quiet: Boolean, paused: Boolean): SessionStep {
        val spec = requireNotNull(reminder.session) { "no session" }
        val started = requireNotNull(occurrence.startedAt) { "not started" }
        // KRITISCH: the reaction time after an audible last reminder is over – the session ends.
        if (occurrence.finalCallAt != null) return SessionStep.TimeOut
        val max = maxAt(spec, started)
        val target = occurrence.nextCheckAt ?: max
        // A check found after the maximum (phone off) is the last reminder.
        if (target < max && now < max) return SessionStep.Check(minOf(now.plus(minutes(minOf(spec.checkMinutes, REPEAT_MINUTES))), max))
        // D7: paused by hand the session ends at its maximum without a last alarm.
        if (paused) return SessionStep.TimeOut
        return SessionStep.FinalCall(audible = !quiet, again = now.plus(minutes(spec.finalMinutes)))
    }

    /**
     * The answer to suggest (D4): the goal duration reached → "Fertig"; without a goal the minimum
     * duration reached → "Teilschritt"; otherwise "Noch dran". Only a suggestion, nothing is locked.
     */
    fun suggestion(spec: SessionSpec, startedAt: Instant, now: Instant): SessionAnswer {
        val elapsed = Duration.between(startedAt, now)
        val goal = spec.goalMinutes
        val minimum = spec.minimumMinutes
        return when {
            goal != null && elapsed >= minutes(goal) -> SessionAnswer.DONE
            goal == null && minimum != null && elapsed >= minutes(minimum) -> SessionAnswer.MINIMUM
            else -> SessionAnswer.STILL_ON
        }
    }

    /**
     * The average duration of the last [HISTORY] sessions completed with goal or minimum (D6),
     * newest first in [resolved]; null without any. Aborted and missed ones say nothing about the pace.
     */
    fun average(resolved: List<Occurrence>): Duration? {
        val durations = resolved.asSequence()
            .filter { it.state == OccurrenceState.DONE }
            .mapNotNull { it.sessionDuration }
            .take(HISTORY).toList()
        if (durations.isEmpty()) return null
        return Duration.ofSeconds(durations.sumOf { it.seconds } / durations.size)
    }

    private fun minutes(m: Int): Duration = Duration.ofMinutes(m.toLong())

    /** An unanswered check asks again after this at the latest, like the rounds of an alarm. */
    private val REPEAT_MINUTES = Escalation.REPEAT.toMinutes().toInt()

    /** "Ich starte jetzt" in the alarm, and before it is due (Anf. 7.20 V2). */
    private val STARTABLE = setOf(OccurrenceState.PLANNED, OccurrenceState.DUE, OccurrenceState.ESCALATING)
}
