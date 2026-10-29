package com.avlreminder.core.usecases

import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.ports.GeofencePort
import com.avlreminder.core.ports.LocationPort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.ProofCheck
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.Validation
import com.avlreminder.core.rules.Weeks
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Something that has to ring now; [snooze] are the buttons that stay before the end of the day,
 * [nextItem] the first open checklist item.
 */
data class Ring(
    val reminder: Reminder,
    val occurrence: Occurrence,
    val level: AlarmLevel,
    val snooze: List<Int> = emptyList(),
    val nextItem: ChecklistItem? = null,
)

sealed interface SaveResult {
    /** [ring]: the reminder is due right away, e.g. a one-off moved to a passed time today. */
    data class Saved(val id: Long, val ring: Ring? = null) : SaveResult
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
    private val geofences: GeofencePort = GeofencePort {},
    private val location: LocationPort = LocationPort { null },
) {
    private val zone get() = clock.zone
    private fun now(): Instant = clock.instant()

    // --- Reminders -------------------------------------------------------------------------

    /**
     * [prep]: new or changed "Startklar" answers; unchanged ones are not stored again.
     * "Überwindung" needs all four answered (also when an older reminder is edited).
     */
    suspend fun save(edited: Reminder, prep: List<PrepAnswer> = emptyList()): SaveResult {
        val problems = Validation.problems(edited).toMutableList()
        val schedule = edited.schedule
        // A one-off moved to another time is meant to happen again, also when it was paused.
        val moved = schedule is Schedule.Once && edited.id != 0L && repo.reminder(edited.id)?.schedule != schedule
        val reminder = if (moved) edited.copy(active = true) else edited
        if (schedule is Schedule.Once) {
            val past = when {
                reminder.id == 0L -> schedule.at.atZone(zone).toInstant() <= now()
                // Today still counts and rings right away, e.g. to do it again today.
                moved -> schedule.at.toLocalDate() < now().atZone(zone).toLocalDate()
                else -> false
            }
            if (past) problems += "Zeitpunkt liegt in der Vergangenheit"
        }
        val current = if (reminder.id == 0L) emptyMap() else prep(reminder.id)
        val changed = prep.filter { new ->
            val old = current[new.question]
            old == null || old.unknown != new.unknown || old.text?.trim() != new.text?.trim()
        }
        if (Prep.missing(reminder, current + changed.associateBy { it.question }).isNotEmpty()) {
            problems += "Startklar-Fragen beantworten"
        }
        reminder.arrival?.let { a ->
            if (repo.places().none { it.id == a.placeId }) problems += "Zuhause zuerst in den Einstellungen festlegen"
        }
        if (problems.isNotEmpty()) return SaveResult.Invalid(problems)
        val id = repo.saveReminder(reminder)
        val saved = reminder.copy(id = id)
        changed.forEach { repo.addPrepAnswer(it.copy(id = 0, reminderId = id, text = it.text?.trim(), at = now())) }
        val open = repo.openOccurrence(id)
        // A changed schedule moves an occurrence that has not started yet.
        if (open != null && open.state == OccurrenceState.PLANNED && open.postponeCount == 0 && schedule != Schedule.OnArrival) {
            val due = nextDue(saved, repo.lastResolved(id))
            if (due == null) {
                close(saved, open, Action.Skip("Zeitplan geändert"))
            } else if (due != open.dueAt) {
                repo.saveOccurrence(open.copy(dueAt = due, currentAt = due))
            }
        }
        val ring = syncReminder(saved)
        watchPlaces()
        return SaveResult.Saved(id, ring)
    }

    suspend fun delete(reminderId: Long) {
        repo.openOccurrence(reminderId)?.let { alarms.cancel(it.id) }
        repo.deleteReminder(reminderId)
        watchPlaces()
    }

    suspend fun setActive(reminderId: Long, active: Boolean) {
        val reminder = repo.reminder(reminderId) ?: return
        repo.saveReminder(reminder.copy(active = active))
        val open = repo.openOccurrence(reminderId)
        if (!active) {
            open?.let { alarms.cancel(it.id) }
            watchPlaces()
            return
        }
        // Resuming must not report the paused time as missed: plan the open occurrence anew.
        if (open != null && open.currentAt < now()) {
            val due = nextDue(reminder, repo.lastResolved(reminderId))
            if (due == null || due <= now()) close(reminder, open, Action.Skip("pausiert"))
            else repo.saveOccurrence(open.copy(state = OccurrenceState.PLANNED, dueAt = due, currentAt = due))
        }
        syncReminder(reminder.copy(active = true))
        watchPlaces()
    }

    // --- Places ----------------------------------------------------------------------------

    suspend fun places(): List<Place> = repo.places()

    /** Sets a place (V1.1b: "Zuhause" from the current position) and watches it if needed. */
    suspend fun savePlace(place: Place): Long {
        val id = repo.savePlace(place)
        watchPlaces()
        return id
    }

    /**
     * Arrived at [placeId] (geofence after the dwell time): today's occurrences of its reminders
     * ring now. Snoozed or postponed ones keep their time.
     */
    suspend fun onArrival(placeId: Long): List<Ring> {
        val now = now()
        val today = now.atZone(zone).toLocalDate()
        return repo.reminders().filter { it.active && it.arrival?.placeId == placeId }.mapNotNull { r ->
            val o = repo.openOccurrence(r.id) ?: return@mapNotNull null
            val waiting = o.state == OccurrenceState.PLANNED && (
                r.schedule == Schedule.OnArrival ||
                    (o.currentAt == o.dueAt && o.currentAt > now && o.currentAt.atZone(zone).toLocalDate() == today)
                )
            if (waiting) arrive(r, o) else null
        }
    }

    /**
     * Checks the position for reminders that only wait for an arrival: after saving one and after
     * a reboot, where the geofence does not report being inside already.
     */
    suspend fun checkPlaces(): List<Ring> {
        val places = repo.places().associateBy { it.id }
        val here = mutableMapOf<Long, Boolean?>()
        return repo.reminders().filter { it.active && it.schedule == Schedule.OnArrival }.mapNotNull { r ->
            val place = places[r.arrival?.placeId] ?: return@mapNotNull null
            val o = repo.openOccurrence(r.id)?.takeIf { it.state == OccurrenceState.PLANNED } ?: return@mapNotNull null
            if (here.getOrPut(place.id) { location.isAt(place) } == true) arrive(r, o) else null
        }
    }

    // --- Occurrences -----------------------------------------------------------------------

    /** Applies a user action. On success the next alarm or the next occurrence is planned. */
    suspend fun act(occurrenceId: Long, action: Action): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        if (action is Action.Snooze && now().plus(action.duration) >= missedAt(occurrence)) {
            return TransitionResult.Rejected("snooze would pass the end of the day")
        }
        val limits = limits(reminder, occurrence)
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits)
        if (result !is TransitionResult.Ok) return result
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        when (action) {
            is Action.Complete -> {
                saveEvidence(occurrence, action.evidence)
                // "Alles erledigt" on a checklist: the open items are done now, with the block's proof.
                // The minimum closes the block without doing them.
                val withProof = result.occurrence.resolution != Resolution.NO_PROOF
                if (!action.minimumOnly) reminder.checklist.filter { it.id !in limits.itemsDone }.forEach {
                    repo.addItemDone(ItemDone(occurrenceId = occurrence.id, itemId = it.id, doneAt = now(), withProof = withProof))
                }
            }
            is Action.CheckItem -> {
                saveEvidence(occurrence, action.evidence, action.itemId)
                val withProof = ProofCheck.problems(reminder.proof, action.evidence, limits.lastProofText).isEmpty()
                repo.addItemDone(ItemDone(occurrenceId = occurrence.id, itemId = action.itemId, doneAt = now(), withProof = withProof))
            }
            else -> {}
        }
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
            endDay(reminder, occurrence)
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
    suspend fun sync(): List<Ring> {
        val rings = repo.reminders().mapNotNull { syncReminder(it) }
        watchPlaces()
        return rings
    }

    /** Limits that apply to [reminder] right now; with [occurrence] also its checklist progress on [day]. */
    suspend fun limits(reminder: Reminder, occurrence: Occurrence? = null, day: LocalDate = now().atZone(zone).toLocalDate()): Limits {
        val (from, until) = Weeks.range(now(), zone)
        val done = occurrence?.let { repo.itemsDone(it.id) }.orEmpty()
        return Limits(
            minimumsThisWeek = repo.countResolved(reminder.id, Resolution.MINIMUM, from, until),
            lastProofText = repo.lastProofText(reminder.id),
            itemsDone = done.associate { it.itemId to it.withProof },
            itemDoneToday = done.any { it.doneAt.atZone(zone).toLocalDate() == day },
        )
    }

    /** Checklist items done in the occurrence. */
    suspend fun itemsDone(occurrenceId: Long): List<ItemDone> = repo.itemsDone(occurrenceId)

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
            // Waits for the arrival, has no alarm of its own.
            open.state == OccurrenceState.PLANNED && reminder.schedule == Schedule.OnArrival -> {
                alarms.cancel(open.id)
                null
            }
            open.state == OccurrenceState.PLANNED && open.currentAt < now.minus(GRACE) -> {
                repo.addEvent(Event(occurrenceId = open.id, at = now, type = EventType.MISSED_DETECTED, detail = open.currentAt.toString()))
                if (now >= missedAt(open)) {
                    endDay(reminder, open)
                    null
                } else {
                    fire(reminder, open)
                }
            }
            open.state != OccurrenceState.PLANNED && now >= missedAt(open) -> {
                endDay(reminder, open)
                null
            }
            else -> {
                plan(reminder, open)
                null
            }
        }
    }

    /**
     * The planned time is reached. With "sonst entfällt" the position decides: not at the place
     * drops the occurrence; an unknown position rings, so nothing is lost.
     */
    private suspend fun fire(reminder: Reminder, occurrence: Occurrence): Ring? {
        val arrival = reminder.arrival
        if (arrival?.whenAway == WhenAway.DROP) {
            val place = repo.places().firstOrNull { it.id == arrival.placeId }
            if (place != null && location.isAt(place) == false) {
                close(reminder, occurrence, Action.Drop)
                createNext(reminder)?.let { plan(reminder, it) }
                return null
            }
        }
        val fired = apply(reminder, occurrence, Action.Fire)
        plan(reminder, fired)
        return ring(reminder, fired)
    }

    private suspend fun arrive(reminder: Reminder, occurrence: Occurrence): Ring {
        val arrived = apply(reminder, occurrence, Action.Arrive)
        plan(reminder, arrived)
        return ring(reminder, arrived)
    }

    /**
     * The end of the day passed without completion. A checklist with an item done that day comes
     * back the next day with the rest (C3, does not count as postponing); anything else is missed.
     */
    private suspend fun endDay(reminder: Reminder, occurrence: Occurrence) {
        val day = missedAt(occurrence).atZone(zone).toLocalDate()
        if (reminder.checklist.isNotEmpty()) {
            val limits = limits(reminder, occurrence, day)
            if (limits.itemDoneToday) {
                val time = occurrence.dueAt.atZone(zone).toLocalTime()
                val to = maxOf(day.plusDays(1).atTime(time).atZone(zone).toInstant(), now().plus(GRACE))
                plan(reminder, apply(reminder, occurrence, Action.DeferRest(to), limits))
                return
            }
        }
        close(reminder, occurrence, Action.Miss)
        createNext(reminder)?.let { plan(reminder, it) }
    }

    private suspend fun saveEvidence(occurrence: Occurrence, e: Evidence, itemId: Long? = null) {
        val at = now()
        fun proof(type: ProofType, path: String?, text: String? = null) =
            Proof(occurrenceId = occurrence.id, type = type, filePath = path, takenAt = at, text = text, itemId = itemId)
        e.photoPath?.let { repo.addProof(proof(ProofType.PHOTO, it)) }
        e.text?.trim()?.takeIf { it.isNotEmpty() }?.let { repo.addProof(proof(ProofType.TEXT, null, it)) }
        e.audioPath?.let { repo.addProof(proof(ProofType.AUDIO, it)) }
    }

    /** Geofences for the places that active reminders wait for. */
    private suspend fun watchPlaces() {
        val needed = repo.reminders().filter { it.active }.mapNotNull { it.arrival?.placeId }.toSet()
        geofences.watch(repo.places().filter { it.id in needed })
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
            // A checklist counts from its first done item (C5b).
            lastDoneAt = repo.lastDone(reminder.id)?.let { it.firstProgressAt ?: it.resolvedAt },
            previousWasDone = previous?.state == OccurrenceState.DONE,
        )

    private suspend fun apply(
        reminder: Reminder,
        occurrence: Occurrence,
        action: Action,
        limits: Limits? = null,
    ): Occurrence {
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits ?: limits(reminder, occurrence))
        check(result is TransitionResult.Ok) { "internal transition $action rejected: $result" }
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        return result.occurrence
    }

    private suspend fun close(reminder: Reminder, occurrence: Occurrence, action: Action) {
        apply(reminder, occurrence, action)
        alarms.cancel(occurrence.id)
    }

    private suspend fun ring(reminder: Reminder, occurrence: Occurrence): Ring {
        val done = if (reminder.checklist.isEmpty()) emptySet() else repo.itemsDone(occurrence.id).map { it.itemId }.toSet()
        return Ring(
            reminder, occurrence, level(reminder, now()), snoozeOptions(reminder, occurrence),
            nextItem = reminder.checklist.firstOrNull { it.id !in done },
        )
    }

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
