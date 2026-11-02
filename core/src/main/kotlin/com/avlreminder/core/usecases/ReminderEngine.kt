package com.avlreminder.core.usecases

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.Character
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
import com.avlreminder.core.ports.PreNoticePort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Blockers
import com.avlreminder.core.rules.Deadlines
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.PreNotices
import com.avlreminder.core.rules.Presets
import com.avlreminder.core.rules.ProofCheck
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.Validation
import com.avlreminder.core.rules.WeekStatus
import com.avlreminder.core.rules.WeeklyGoals
import com.avlreminder.core.rules.Weeks
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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
    /** The week so far, for a reminder with a weekly goal. */
    val week: WeekStatus? = null,
    /** A solved blocker may close it as the minimum (S7). */
    val blockerSolved: Boolean = false,
    /** It got stuck several times: ask what keeps one from it (S8). */
    val askBlocker: Boolean = false,
    /** "Auf morgen" of a deadline: the next window on a later day; null = not offered. */
    val tomorrow: Instant? = null,
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
    private val preNotices: PreNoticePort = PreNoticePort.NONE,
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
        val stored = if (edited.id == 0L) null else repo.reminder(edited.id)
        val moved = (schedule is Schedule.Once || schedule is Schedule.Deadline) && stored != null && stored.schedule != schedule
        // The form does not know the archive; moving a one-off brings it back.
        val reminder = if (moved) edited.copy(active = true, archivedAt = null) else edited.copy(archivedAt = stored?.archivedAt)
        if (schedule is Schedule.Once) {
            val past = when {
                reminder.id == 0L -> schedule.at.atZone(zone).toInstant() <= now()
                // Today still counts and rings right away, e.g. to do it again today.
                moved -> schedule.at.toLocalDate() < now().atZone(zone).toLocalDate()
                else -> false
            }
            if (past) problems += "Zeitpunkt liegt in der Vergangenheit"
        }
        if (schedule is Schedule.Deadline && (stored == null || moved) && !Deadlines.hasWindowBefore(schedule, now(), zone)) {
            problems += "Kein Ausführungsfenster vor der Frist"
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
        changed.firstOrNull { it.question == Prep.READY }?.let { readyChanged(id, it) }
        // A moved deadline starts anew in its windows, also when it was overdue or done.
        if (moved && schedule is Schedule.Deadline) {
            repo.openOccurrence(id)?.let { close(saved, it, Action.Skip(SkipReasons.SCHEDULE_CHANGED)) }
            val due = Deadlines.firstDue(schedule, now(), zone)
            val created = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = due))
            repo.addEvent(Event(occurrenceId = created, at = now(), type = EventType.CREATED))
        }
        val open = repo.openOccurrence(id)
        // A changed schedule moves an occurrence that has not started yet.
        if (open != null && open.state == OccurrenceState.PLANNED && open.postponeCount == 0 && schedule != Schedule.OnArrival && schedule !is Schedule.Deadline) {
            val due = nextDue(saved, repo.lastResolved(id))
            if (due == null) {
                close(saved, open, Action.Skip(SkipReasons.SCHEDULE_CHANGED))
            } else if (due != open.dueAt) {
                repo.saveOccurrence(open.copy(dueAt = due, currentAt = due))
            }
        }
        val ring = syncReminder(saved)
        watchPlaces()
        return SaveResult.Saved(id, ring)
    }

    /** Also deletes its preparation tasks; deleting a task leaves its blocker open. */
    suspend fun delete(reminderId: Long) {
        val reminder = repo.reminder(reminderId)
        repo.reminders().filter { it.prepFor == reminderId }.forEach { delete(it.id) }
        reminder?.prepFor?.let { main ->
            repo.blockers(main).filter { it.taskReminderId == reminderId }.forEach { repo.saveBlocker(it.copy(taskReminderId = null)) }
        }
        repo.openOccurrence(reminderId)?.let {
            alarms.cancel(it.id)
            preNotices.cancel(it.id)
        }
        repo.deleteReminder(reminderId)
        watchPlaces()
    }

    /** Switching a reminder on also takes it out of the archive. */
    suspend fun setActive(reminderId: Long, active: Boolean) {
        val reminder = repo.reminder(reminderId) ?: return
        repo.saveReminder(reminder.copy(active = active, archivedAt = if (active) null else reminder.archivedAt))
        val open = repo.openOccurrence(reminderId)
        if (!active) {
            open?.let {
                alarms.cancel(it.id)
                preNotices.cancel(it.id)
            }
            watchPlaces()
            return
        }
        // Resuming must not report the paused time as missed: plan the open occurrence anew.
        if (open != null && open.currentAt < now()) {
            val due = nextDue(reminder, repo.lastResolved(reminderId))
            if (due == null || due <= now()) close(reminder, open, Action.Skip(SkipReasons.PAUSED))
            else repo.saveOccurrence(open.copy(state = OccurrenceState.PLANNED, dueAt = due, currentAt = due))
        }
        syncReminder(reminder.copy(active = true))
        watchPlaces()
    }

    /** Archiving pauses the reminder; restoring keeps it paused until it is switched on. */
    suspend fun setArchived(reminderId: Long, archived: Boolean) {
        if (archived) setActive(reminderId, false)
        val reminder = repo.reminder(reminderId) ?: return
        repo.saveReminder(reminder.copy(archivedAt = if (archived) now() else null))
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
        if (action is Action.Snooze && now().plus(action.duration) >= missedAt(reminder, occurrence)) {
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
        // Doing a preparation task solves its blocker (S5).
        if (result.occurrence.state == OccurrenceState.DONE && reminder.prepFor != null) {
            repo.blockers(reminder.prepFor).filter { it.isOpen && it.taskReminderId == reminder.id }
                .forEach { repo.saveBlocker(it.copy(solvedAt = now())) }
        }
        alarms.cancel(occurrence.id)
        preNotices.cancel(occurrence.id)
        syncReminder(reminder)
        return result
    }

    /** The proof screen opened: quiet for the reminder's proof window, at most until the end of the day. */
    suspend fun startProof(occurrenceId: Long): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        val window = now().plus(Duration.ofMinutes(reminder.proof.windowMinutes.toLong()))
        return act(occurrenceId, Action.StartProof(minOf(window, missedAt(reminder, occurrence))))
    }

    /** The proof screen was left: after a short grace the escalation goes on. */
    suspend fun abortProof(occurrenceId: Long): TransitionResult =
        act(occurrenceId, Action.AbortProof(now().plus(Escalation.PROOF_GRACE)))

    /** The reminder's snooze buttons that do not pass the end of the day. */
    fun snoozeOptions(reminder: Reminder, occurrence: Occurrence): List<Int> =
        Escalation.snoozeOptions(reminder.snoozeMinutes, now(), missedAt(reminder, occurrence))

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
        if (now >= missedAt(reminder, occurrence)) {
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
        val deadline = (reminder.schedule as? Schedule.Deadline)
            ?.takeIf { d -> occurrence == null || !Deadlines.isOverdue(d, occurrence, zone) }
            ?.let { Deadlines.dueAt(it, zone) }
        val since = repo.lastResolved(reminder.id)?.resolvedAt ?: reminder.createdAt
        return Limits(
            minimumsThisWeek = repo.countResolved(reminder.id, Resolution.MINIMUM, from, until),
            lastProofText = repo.lastProofText(reminder.id),
            itemsDone = done.associate { it.itemId to it.withProof },
            itemDoneToday = done.any { it.doneAt.atZone(zone).toLocalDate() == day },
            deadline = deadline,
            blockerSolved = reminder.solvedBlockerIsMinimum && Blockers.solvedSince(repo.blockers(reminder.id), since),
        )
    }

    /** Checklist items done in the occurrence. */
    suspend fun itemsDone(occurrenceId: Long): List<ItemDone> = repo.itemsDone(occurrenceId)

    /** The week of [day] for a reminder with a weekly goal; offers counted from [day] on. */
    suspend fun weekStatus(reminder: Reminder, day: LocalDate = now().atZone(zone).toLocalDate()): WeekStatus? {
        val goal = reminder.weeklyGoal ?: return null
        val (from, until) = Weeks.range(day, zone)
        val goals = repo.countResolved(reminder.id, Resolution.GOAL, from, until)
        val minimums = repo.countResolved(reminder.id, Resolution.MINIMUM, from, until)
        return WeekStatus(goal, goals + minimums, minimums, WeeklyGoals.offersLeft(reminder.schedule, day))
    }

    /** All active reminders with a weekly goal and where they stand this week (overview, O1). */
    suspend fun weekGoals(): List<Pair<Reminder, WeekStatus>> =
        repo.reminders().filter { it.active && it.weeklyGoal != null }.mapNotNull { r -> weekStatus(r)?.let { r to it } }

    // --- Blockers, preparation tasks and the notice before (S5–S8, T6) ------------------------

    suspend fun blockers(reminderId: Long): List<Blocker> = repo.blockers(reminderId)

    /** Adds an open blocker unless the same one is open already; returns its id. */
    suspend fun addBlocker(reminderId: Long, text: String, origin: BlockerOrigin = BlockerOrigin.STARTKLAR): Long {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "blocker without text" }
        repo.blockers(reminderId).firstOrNull { it.isOpen && it.text.equals(clean, ignoreCase = true) }?.let { return it.id }
        return repo.saveBlocker(Blocker(reminderId = reminderId, text = clean, createdAt = now(), origin = origin))
    }

    /** Solved by hand; a planned task for it is not needed any more. */
    suspend fun solveBlocker(blockerId: Long) {
        val blocker = repo.blocker(blockerId)?.takeIf { it.isOpen } ?: return
        repo.saveBlocker(blocker.copy(solvedAt = now(), taskReminderId = null))
        blocker.taskReminderId?.let { delete(it) }
    }

    /** When to do the task for a blocker: the evening before the reminder is due next. */
    suspend fun suggestTaskTime(reminderId: Long): LocalDateTime =
        Blockers.suggestTaskTime(repo.openOccurrence(reminderId)?.currentAt, now(), zone)

    /** "Als Aufgabe planen": a relaxed one-off at [at]; doing it solves the blocker. */
    suspend fun planTask(blockerId: Long, at: LocalDateTime): SaveResult {
        val blocker = repo.blocker(blockerId)?.takeIf { it.isOpen } ?: return SaveResult.Invalid(listOf("Blocker nicht gefunden"))
        val task = Presets.apply(
            Reminder(
                title = blocker.text, character = Character.DOCUMENT, schedule = Schedule.Once(at),
                createdAt = now(), prepFor = blocker.reminderId,
            ),
            Strictness.RELAXED,
        )
        val result = save(task)
        if (result is SaveResult.Saved) {
            val old = blocker.taskReminderId
            repo.saveBlocker(blocker.copy(taskReminderId = result.id))
            old?.let { delete(it) }
        }
        return result
    }

    /** The answer to "Was hält dich gerade ab?" (S8); null or blank = skipped. Returns the blocker's id. */
    suspend fun answerAsk(occurrenceId: Long, text: String?): Long? {
        val occurrence = repo.occurrence(occurrenceId) ?: return null
        repo.addEvent(Event(occurrenceId = occurrenceId, at = now(), type = EventType.BLOCKER_ASKED))
        return text?.takeIf { it.isNotBlank() }?.let { addBlocker(occurrence.reminderId, it, BlockerOrigin.ASKED) }
    }

    /** What the silent notice before the occurrence shows; null if it is no longer planned. */
    suspend fun preNotice(occurrenceId: Long): Digest? {
        val occurrence = repo.occurrence(occurrenceId)?.takeIf { it.state == OccurrenceState.PLANNED } ?: return null
        val reminder = repo.reminder(occurrence.reminderId)?.takeIf { it.active } ?: return null
        val firstStep = prep(reminder.id)[Prep.FIRST_STEP]?.takeIf { !it.unknown }?.text
        val (title, lines) = PreNotices.text(reminder, occurrence, firstStep, repo.blockers(reminder.id), now(), zone)
        return Digest(title, lines)
    }

    /** "Auf morgen" for a deadline; null if it is no deadline or no window before it is left. */
    fun tomorrow(reminder: Reminder, occurrence: Occurrence): Instant? {
        val d = reminder.schedule as? Schedule.Deadline ?: return null
        return Deadlines.tomorrow(d, now(), zone, Deadlines.isOverdue(d, occurrence, zone))
    }

    /** "Startklar" question 3 changed: a missing thing is a blocker, "Ja, sofort" solves them. */
    private suspend fun readyChanged(reminderId: Long, answer: PrepAnswer) {
        val text = Blockers.fromReady(answer)
        if (text != null) {
            addBlocker(reminderId, text)
        } else if (answer.text?.trim() == Prep.READY_YES) {
            repo.blockers(reminderId).filter { it.isOpen && it.origin == BlockerOrigin.STARTKLAR }
                .forEach { repo.saveBlocker(it.copy(solvedAt = now())) }
        }
    }

    // --- Internals -------------------------------------------------------------------------

    private suspend fun syncReminder(reminder: Reminder): Ring? {
        var open = repo.openOccurrence(reminder.id)
        if (!reminder.active) {
            open?.let {
                alarms.cancel(it.id)
                preNotices.cancel(it.id)
            }
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
                preNotices.cancel(open.id)
                null
            }
            open.state == OccurrenceState.PLANNED && open.currentAt < now.minus(GRACE) -> {
                repo.addEvent(Event(occurrenceId = open.id, at = now, type = EventType.MISSED_DETECTED, detail = open.currentAt.toString()))
                if (now >= missedAt(reminder, open)) {
                    endDay(reminder, open)
                    null
                } else {
                    fire(reminder, open)
                }
            }
            open.state != OccurrenceState.PLANNED && now >= missedAt(reminder, open) -> {
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
        // A rescue reminder rings wherever one is.
        if (arrival?.whenAway == WhenAway.DROP && !isRescue(reminder, occurrence)) {
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
        val schedule = reminder.schedule
        if (schedule is Schedule.Deadline) {
            // A window ended: quiet until the next one before the deadline, an overdue one until done.
            // Found late inside a running window (phone off), it rings there after the interval.
            val next = Deadlines.firstDue(schedule, now(), zone)
            if (Deadlines.isOverdue(schedule, occurrence, zone) || next < Deadlines.dueAt(schedule, zone)) {
                plan(reminder, apply(reminder, occurrence, Action.CloseWindow(next)))
                return
            }
            // The deadline passed: missed, and it stays to be done as overdue.
            close(reminder, occurrence, Action.Miss)
            createNext(reminder)?.let { plan(reminder, it) }
            return
        }
        val day = missedAt(reminder, occurrence).atZone(zone).toLocalDate()
        if (reminder.checklist.isNotEmpty()) {
            val limits = limits(reminder, occurrence, day)
            if (limits.itemDoneToday) {
                val time = occurrence.dueAt.atZone(zone).toLocalTime()
                val to = maxOf(day.plusDays(1).atTime(time).atZone(zone).toInstant(), now().plus(GRACE))
                plan(reminder, apply(reminder, occurrence, Action.DeferRest(to), limits))
                return
            }
        }
        // With the weekly target reached a further offer that was not taken up is no miss.
        val reached = weekStatus(reminder, occurrence.dueAt.atZone(zone).toLocalDate())?.reached == true
        close(reminder, occurrence, if (reached) Action.Skip(SkipReasons.GOAL_REACHED) else Action.Miss)
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
    private suspend fun plan(reminder: Reminder, occurrence: Occurrence) {
        val now = now()
        val at = if (occurrence.state == OccurrenceState.PLANNED) {
            occurrence.currentAt
        } else if (strictness(reminder, occurrence) == null) {
            // Weekly target reached: no further rounds, only the end of the day.
            missedAt(reminder, occurrence)
        } else {
            val paused = occurrence.pausedUntil
            // A deadline rings again after its interval.
            val round = (reminder.schedule as? Schedule.Deadline)?.let { now.plus(Duration.ofMinutes(it.intervalMinutes.toLong())) }
            val next = if (paused != null && paused > now) paused else round ?: Escalation.nextRound(now)
            minOf(next, missedAt(reminder, occurrence))
        }
        val kind = if (reminder.strictness == Strictness.RELAXED) AlarmKind.EXACT else AlarmKind.ALARM_CLOCK
        alarms.set(occurrence.id, at, kind)
        val notice = PreNotices.at(reminder, occurrence, zone)
        if (notice != null && notice > now && notice < occurrence.currentAt) preNotices.set(occurrence.id, notice)
        else preNotices.cancel(occurrence.id)
    }

    private suspend fun createNext(reminder: Reminder): Occurrence? {
        val due = nextDue(reminder, repo.lastResolved(reminder.id)) ?: return null
        val draft = Occurrence(reminderId = reminder.id, dueAt = due)
        val id = repo.saveOccurrence(draft)
        repo.addEvent(Event(occurrenceId = id, at = now(), type = EventType.CREATED))
        return draft.copy(id = id)
    }

    private suspend fun nextDue(reminder: Reminder, previous: Occurrence?): Instant? {
        // A checklist counts from its first done item (C5b).
        val lastDoneAt = repo.lastDone(reminder.id)?.let { it.firstProgressAt ?: it.resolvedAt }
        fun next(after: Instant) = ScheduleRules.nextDue(
            schedule = reminder.schedule,
            now = after,
            zone = zone,
            previousDueAt = previous?.dueAt,
            lastDoneAt = lastDoneAt,
            previousWasDone = previous?.state == OccurrenceState.DONE,
            previousMissed = previous?.resolution == Resolution.MISSED,
        )
        val now = now()
        val due = next(now) ?: return null
        val week = weekStatus(reminder) ?: return due
        val nextMonday = Weeks.range(now, zone).second
        return when {
            // Done as often as allowed: the next offer comes next week.
            week.full && due < nextMonday -> next(nextMonday.minusMillis(1))
            // Still at 0 at the weekend: a rescue reminder before the next offer.
            !week.minimumReached -> listOfNotNull(due, WeeklyGoals.nextRescue(reminder.schedule, now, zone, previous?.dueAt)).min()
            else -> due
        }
    }

    private fun isRescue(reminder: Reminder, occurrence: Occurrence): Boolean =
        reminder.weeklyGoal != null && WeeklyGoals.isRescueDay(reminder.schedule, occurrence.dueAt.atZone(zone).toLocalDate())

    /** How strict the occurrence rings with its weekly goal; null = silently, without rounds. */
    private suspend fun strictness(reminder: Reminder, occurrence: Occurrence): Strictness? {
        (reminder.schedule as? Schedule.Deadline)?.let { d ->
            // The last window day and overdue ring one step stricter.
            val last = Deadlines.isOverdue(d, occurrence, zone) ||
                occurrence.currentAt.atZone(zone).toLocalDate() == Deadlines.lastWindowDay(d, zone)
            return if (last) stricter(reminder.strictness) else reminder.strictness
        }
        val week = weekStatus(reminder, occurrence.dueAt.atZone(zone).toLocalDate()) ?: return reminder.strictness
        return WeeklyGoals.strictness(reminder.strictness, week, isRescue(reminder, occurrence))
    }

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
        preNotices.cancel(occurrence.id)
    }

    private suspend fun ring(reminder: Reminder, occurrence: Occurrence): Ring {
        val done = if (reminder.checklist.isEmpty()) emptySet() else repo.itemsDone(occurrence.id).map { it.itemId }.toSet()
        val strictness = strictness(reminder, occurrence)
        val level = if (strictness == null) AlarmLevel.SILENT else Escalation.level(
            strictness, now().atZone(zone).toLocalTime(), settings(), device.isDoNotDisturb(), reminder.ignoreDnd,
        )
        return Ring(
            reminder, occurrence, level, snoozeOptions(reminder, occurrence),
            nextItem = reminder.checklist.firstOrNull { it.id !in done },
            week = weekStatus(reminder, occurrence.dueAt.atZone(zone).toLocalDate()),
            blockerSolved = Lifecycle.canUseSolvedBlocker(reminder, limits(reminder, occurrence)),
            askBlocker = askBlocker(reminder, occurrence),
            tomorrow = tomorrow(reminder, occurrence),
        )
    }

    /** It got stuck [AlertSettings.askBlockerAfter] times in a row and was not asked yet (S8). */
    suspend fun askBlocker(reminder: Reminder, occurrence: Occurrence): Boolean {
        if (reminder.character != Character.OVERCOME || reminder.prepFor != null) return false
        if (repo.events(occurrence.id).any { it.type == EventType.BLOCKER_ASKED }) return false
        val history = listOf(occurrence) + repo.recentResolved(reminder.id, ASK_HISTORY)
        val streak = Blockers.stuckStreak(history.map { o -> o to repo.events(o.id).count { it.type == EventType.SNOOZED } })
        return streak >= settings().askBlockerAfter
    }

    private fun stricter(s: Strictness) = when (s) {
        Strictness.RELAXED -> Strictness.NORMAL
        Strictness.NORMAL, Strictness.STRICT -> Strictness.STRICT
    }

    /** When an unanswered occurrence counts as missed: the end of the day, or of a deadline's window. */
    private fun missedAt(reminder: Reminder, occurrence: Occurrence): Instant {
        val d = reminder.schedule as? Schedule.Deadline ?: return Escalation.missedAt(occurrence.currentAt, zone, settings())
        val end = Deadlines.windowEnd(d, occurrence.currentAt, zone)
        return if (Deadlines.isOverdue(d, occurrence, zone)) end else minOf(end, Deadlines.dueAt(d, zone))
    }

    companion object {
        /** A planned occurrence this far past its time without having fired counts as missed alarm. */
        val GRACE: Duration = Duration.ofMinutes(2)
        /** Alarms may fire slightly early. */
        val EARLY_TOLERANCE: Duration = Duration.ofMinutes(1)
        /** Resolved occurrences looked at for the stuck streak (S8). */
        private const val ASK_HISTORY = 10
    }
}
