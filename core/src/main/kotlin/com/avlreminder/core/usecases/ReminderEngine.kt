package com.avlreminder.core.usecases

import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.Validation
import com.avlreminder.core.rules.Weeks
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Something that has to ring now; [snooze] are the buttons that stay before the end of the day. */
data class Ring(val reminder: Reminder, val occurrence: Occurrence, val level: AlarmLevel, val snooze: List<Int> = emptyList())

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
    private val device: DeviceStatePort = DeviceStatePort { false },
    private val settings: () -> AlertSettings = { AlertSettings() },
) {
    private val zone get() = clock.zone
    private fun now(): Instant = clock.instant()

    // --- Reminders -------------------------------------------------------------------------

    /**
     * [prep]: new or changed "Startklar" answers; unchanged ones are not stored again.
     * "Überwindung" needs all four answered (also when an older reminder is edited).
     */
    suspend fun save(reminder: Reminder, prep: List<PrepAnswer> = emptyList()): SaveResult {
        val problems = Validation.problems(reminder).toMutableList()
        val schedule = reminder.schedule
        if (reminder.id == 0L && schedule is Schedule.Once && schedule.at.atZone(zone).toInstant() <= now()) {
            problems += "Zeitpunkt liegt in der Vergangenheit"
        }
        val current = if (reminder.id == 0L) emptyMap() else prep(reminder.id)
        val changed = prep.filter { new ->
            val old = current[new.question]
            old == null || old.unknown != new.unknown || old.text?.trim() != new.text?.trim()
        }
        if (Prep.missing(reminder, current + changed.associateBy { it.question }).isNotEmpty()) {
            problems += "Startklar-Fragen beantworten"
        }
        if (problems.isNotEmpty()) return SaveResult.Invalid(problems)
        val id = repo.saveReminder(reminder)
        val saved = reminder.copy(id = id)
        changed.forEach { repo.addPrepAnswer(it.copy(id = 0, reminderId = id, text = it.text?.trim(), at = now())) }
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
    suspend fun act(occurrenceId: Long, action: Action): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        if (action is Action.Snooze && now().plus(action.duration) >= missedAt(occurrence)) {
            return TransitionResult.Rejected("snooze would pass the end of the day")
        }
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits(reminder))
        if (result !is TransitionResult.Ok) return result
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        if (action is Action.Complete) saveEvidence(occurrence, action)
        alarms.cancel(occurrence.id)
        syncReminder(reminder)
        return result
    }

    /** The proof screen opened: quiet for the reminder's proof window, at most until the end of the day. */
    suspend fun startProof(occurrenceId: Long): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        val window = now().plus(Duration.ofMinutes(reminder.proof.windowMinutes.toLong()))
        return act(occurrenceId, Action.StartProof(minOf(window, missedAt(occurrence))))
    }

    /** The proof screen was left: after a short grace the escalation goes on. */
    suspend fun abortProof(occurrenceId: Long): TransitionResult =
        act(occurrenceId, Action.AbortProof(now().plus(Escalation.PROOF_GRACE)))

    /** The reminder's snooze buttons that do not pass the end of the day. */
    fun snoozeOptions(reminder: Reminder, occurrence: Occurrence): List<Int> =
        Escalation.snoozeOptions(reminder.snoozeMinutes, now(), missedAt(occurrence))

    /** Current "Startklar" answers, the newest per question. */
    suspend fun prep(reminderId: Long): Map<Int, PrepAnswer> = Prep.latest(repo.prepAnswers(reminderId))

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
        val paused = occurrence.pausedUntil
        if (paused != null && paused > now.plus(EARLY_TOLERANCE)) {
            plan(reminder, occurrence)
            return null
        }
        if (now >= missedAt(occurrence)) {
            close(reminder, occurrence, Action.Miss)
            syncReminder(reminder)
            return null
        }
        val escalated = apply(reminder, occurrence, Action.Escalate)
        plan(reminder, escalated)
        return ring(reminder, escalated)
    }

    /**
     * Rebuilds all alarms from the database: on app start, boot, time change and after changes.
     * Detects occurrences whose alarm never fired and returns them to ring now.
     */
    suspend fun sync(): List<Ring> = repo.reminders().mapNotNull { syncReminder(it) }

    /** Limits that apply to [reminder] right now. */
    suspend fun limits(reminder: Reminder): Limits {
        val (from, until) = Weeks.range(now(), zone)
        return Limits(
            minimumsThisWeek = repo.countResolved(reminder.id, Resolution.MINIMUM, from, until),
            lastProofText = repo.lastProofText(reminder.id),
        )
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
        return ring(reminder, fired)
    }

    private suspend fun saveEvidence(occurrence: Occurrence, action: Action.Complete) {
        val e = action.evidence
        val at = now()
        e.photoPath?.let { repo.addProof(Proof(occurrenceId = occurrence.id, type = ProofType.PHOTO, filePath = it, takenAt = at)) }
        e.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
            repo.addProof(Proof(occurrenceId = occurrence.id, type = ProofType.TEXT, filePath = null, takenAt = at, text = it))
        }
        e.audioPath?.let { repo.addProof(Proof(occurrenceId = occurrence.id, type = ProofType.AUDIO, filePath = it, takenAt = at)) }
    }

    /**
     * Sets the alarm for an open occurrence: its time if planned; else the end of a running proof
     * or the next round, at most the miss deadline.
     */
    private fun plan(reminder: Reminder, occurrence: Occurrence) {
        val now = now()
        val at = if (occurrence.state == OccurrenceState.PLANNED) {
            occurrence.currentAt
        } else {
            val paused = occurrence.pausedUntil
            val next = if (paused != null && paused > now) paused else Escalation.nextRound(now)
            minOf(next, missedAt(occurrence))
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

    private fun ring(reminder: Reminder, occurrence: Occurrence) =
        Ring(reminder, occurrence, level(reminder, now()), snoozeOptions(reminder, occurrence))

    private fun missedAt(occurrence: Occurrence) = Escalation.missedAt(occurrence.currentAt, zone, settings())

    private fun level(reminder: Reminder, at: Instant) = Escalation.level(
        reminder.strictness, at.atZone(zone).toLocalTime(), settings(), device.isDoNotDisturb(), reminder.ignoreDnd,
    )

    companion object {
        /** A planned occurrence this far past its time without having fired counts as missed alarm. */
        val GRACE: Duration = Duration.ofMinutes(2)
        /** Alarms may fire slightly early. */
        val EARLY_TOLERANCE: Duration = Duration.ofMinutes(1)
    }
}
