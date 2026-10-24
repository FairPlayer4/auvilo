package com.avlreminder.core.usecases

import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.EscalationWindows
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.Validation
import com.avlreminder.core.rules.Weeks
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Something that has to ring now. */
data class Ring(val reminder: Reminder, val occurrence: Occurrence, val level: AlarmLevel)

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Invalid(val problems: List<String>) : SaveResult
}

/**
 * All use cases. Every change goes to the repository first; alarms are derived from it.
 * Not thread-safe: the app calls it from one coroutine scope at a time.
 */
class ReminderEngine(
    private val repo: ReminderRepository,
    private val alarms: AlarmPort,
    private val clock: Clock,
    private val windows: () -> EscalationWindows = { EscalationWindows() },
) {
    private val zone get() = clock.zone
    private fun now(): Instant = clock.instant()

    // --- Reminders -------------------------------------------------------------------------

    suspend fun save(reminder: Reminder): SaveResult {
        val problems = Validation.problems(reminder).toMutableList()
        val schedule = reminder.schedule
        if (reminder.id == 0L && schedule is Schedule.Once && schedule.at.atZone(zone).toInstant() <= now()) {
            problems += "Zeitpunkt liegt in der Vergangenheit"
        }
        if (problems.isNotEmpty()) return SaveResult.Invalid(problems)
        val id = repo.saveReminder(reminder)
        val saved = reminder.copy(id = id)
        val open = repo.openOccurrence(id)
        // A changed schedule moves an occurrence that has not started yet.
        if (open != null && open.state == OccurrenceState.PLANNED && open.postponeCount == 0) {
            val due = nextDue(saved, repo.lastResolved(id))
            if (due == null) {
                close(saved, open, Action.Skip("Zeitplan geändert"))
            } else if (due != open.dueAt) {
                repo.saveOccurrence(open.copy(dueAt = due, currentAt = due))
            }
        }
        syncReminder(saved)
        return SaveResult.Saved(id)
    }

    suspend fun delete(reminderId: Long) {
        repo.openOccurrence(reminderId)?.let { alarms.cancel(it.id) }
        repo.deleteReminder(reminderId)
    }

    suspend fun setActive(reminderId: Long, active: Boolean) {
        val reminder = repo.reminder(reminderId) ?: return
        repo.saveReminder(reminder.copy(active = active))
        val open = repo.openOccurrence(reminderId)
        if (!active) {
            open?.let { alarms.cancel(it.id) }
            return
        }
        // Resuming must not report the paused time as missed: plan the open occurrence anew.
        if (open != null && open.currentAt < now()) {
            val due = nextDue(reminder, repo.lastResolved(reminderId))
            if (due == null || due <= now()) close(reminder, open, Action.Skip("pausiert"))
            else repo.saveOccurrence(open.copy(state = OccurrenceState.PLANNED, dueAt = due, currentAt = due))
        }
        syncReminder(reminder.copy(active = true))
    }

    // --- Occurrences -----------------------------------------------------------------------

    /** Applies a user action. On success the next alarm or the next occurrence is planned. */
    suspend fun act(occurrenceId: Long, action: Action, photoPath: String? = null): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits(reminder))
        if (result !is TransitionResult.Ok) return result
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        if (photoPath != null && action is Action.Complete) {
            repo.addProof(Proof(occurrenceId = occurrence.id, type = ProofType.PHOTO, filePath = photoPath, takenAt = now()))
        }
        alarms.cancel(occurrence.id)
        syncReminder(reminder)
        return result
    }

    /** Called when an alarm fires. Returns what to show, or null if nothing (stale alarm). */
    suspend fun onAlarm(occurrenceId: Long): Ring? {
        val occurrence = repo.occurrence(occurrenceId) ?: return null
        if (!occurrence.isOpen) return null
        val reminder = repo.reminder(occurrence.reminderId) ?: return null
        if (!reminder.active) return null
        val now = now()
        if (occurrence.state == OccurrenceState.PLANNED) {
            if (occurrence.currentAt > now.plus(EARLY_TOLERANCE)) {
                plan(reminder, occurrence)
                return null
            }
            return fire(reminder, occurrence)
        }
        if (now >= missedAt(occurrence)) {
            close(reminder, occurrence, Action.Miss)
            syncReminder(reminder)
            return null
        }
        val escalated = apply(reminder, occurrence, Action.Escalate)
        plan(reminder, escalated)
        return Ring(reminder, escalated, level(reminder, now))
    }

    /**
     * Rebuilds all alarms from the database: on app start, boot, time change and after changes.
     * Detects occurrences whose alarm never fired and returns them to ring now.
     */
    suspend fun sync(): List<Ring> = repo.reminders().mapNotNull { syncReminder(it) }

    /** Limits that apply to [reminder] right now. */
    suspend fun limits(reminder: Reminder): Limits {
        val (from, until) = Weeks.range(now(), zone)
        return Limits(minimumsThisWeek = repo.countResolved(reminder.id, Resolution.MINIMUM, from, until))
    }

    // --- Internals -------------------------------------------------------------------------

    private suspend fun syncReminder(reminder: Reminder): Ring? {
        var open = repo.openOccurrence(reminder.id)
        if (!reminder.active) {
            open?.let { alarms.cancel(it.id) }
            return null
        }
        if (open == null) {
            open = createNext(reminder) ?: return null
        }
        val now = now()
        return when {
            open.state == OccurrenceState.PLANNED && open.currentAt < now.minus(GRACE) -> {
                repo.addEvent(Event(occurrenceId = open.id, at = now, type = EventType.MISSED_DETECTED, detail = open.currentAt.toString()))
                if (now >= missedAt(open)) {
                    close(reminder, open, Action.Miss)
                    createNext(reminder)?.let { plan(reminder, it) }
                    null
                } else {
                    fire(reminder, open)
                }
            }
            open.state != OccurrenceState.PLANNED && now >= missedAt(open) -> {
                close(reminder, open, Action.Miss)
                createNext(reminder)?.let { plan(reminder, it) }
                null
            }
            else -> {
                plan(reminder, open)
                null
            }
        }
    }

    private suspend fun fire(reminder: Reminder, occurrence: Occurrence): Ring {
        val fired = apply(reminder, occurrence, Action.Fire)
        plan(reminder, fired)
        return Ring(reminder, fired, level(reminder, now()))
    }

    /** Sets the alarm for an open occurrence: its time if planned, else the next round or the miss deadline. */
    private fun plan(reminder: Reminder, occurrence: Occurrence) {
        val at = if (occurrence.state == OccurrenceState.PLANNED) {
            occurrence.currentAt
        } else {
            val deadline = missedAt(occurrence)
            val round = Escalation.nextRound(now(), zone, windows())
            if (round > deadline) deadline else round
        }
        val kind = if (reminder.strictness == Strictness.RELAXED) AlarmKind.EXACT else AlarmKind.ALARM_CLOCK
        alarms.set(occurrence.id, at, kind)
    }

    private suspend fun createNext(reminder: Reminder): Occurrence? {
        val due = nextDue(reminder, repo.lastResolved(reminder.id)) ?: return null
        val draft = Occurrence(reminderId = reminder.id, dueAt = due)
        val id = repo.saveOccurrence(draft)
        repo.addEvent(Event(occurrenceId = id, at = now(), type = EventType.CREATED))
        return draft.copy(id = id)
    }

    private suspend fun nextDue(reminder: Reminder, previous: Occurrence?): Instant? =
        ScheduleRules.nextDue(
            schedule = reminder.schedule,
            now = now(),
            zone = zone,
            previousDueAt = previous?.dueAt,
            lastDoneAt = repo.lastDone(reminder.id)?.resolvedAt,
            previousWasDone = previous?.state == OccurrenceState.DONE,
        )

    private suspend fun apply(reminder: Reminder, occurrence: Occurrence, action: Action): Occurrence {
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits(reminder))
        check(result is TransitionResult.Ok) { "internal transition $action rejected: $result" }
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        return result.occurrence
    }

    private suspend fun close(reminder: Reminder, occurrence: Occurrence, action: Action) {
        apply(reminder, occurrence, action)
        alarms.cancel(occurrence.id)
    }

    private fun missedAt(occurrence: Occurrence) = Escalation.missedAt(occurrence.currentAt, zone, windows())

    private fun level(reminder: Reminder, at: Instant) =
        Escalation.level(reminder.strictness, at.atZone(zone).toLocalTime(), windows())

    companion object {
        /** A planned occurrence this far past its time without having fired counts as missed alarm. */
        val GRACE: Duration = Duration.ofMinutes(2)
        /** Alarms may fire slightly early. */
        val EARLY_TOLERANCE: Duration = Duration.ofMinutes(1)
    }
}
