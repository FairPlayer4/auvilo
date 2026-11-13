package com.avlreminder.core.usecases

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Blockers
import com.avlreminder.core.rules.Deadlines
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.PreNotices
import com.avlreminder.core.rules.Presets
import com.avlreminder.core.rules.ProofCheck
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.SnoozeHint
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.rules.Snoozing
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.Validation
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/** What the user changes: reminders, places, actions on an occurrence, blockers and hints. */
internal class Changes(private val c: EngineContext, private val views: AlarmViews, private val scheduler: Scheduler) {
    private val repo get() = c.repo
    private val zone get() = c.zone
    private fun now() = c.now()

    // --- Reminders -------------------------------------------------------------------------

    suspend fun save(edited: Reminder, prep: List<PrepAnswer>): SaveResult {
        val problems = Validation.problems(edited).toMutableList()
        val schedule = edited.schedule
        // A one-off moved to another time is meant to happen again, also when it was paused.
        val stored = if (edited.id == 0L) null else repo.reminder(edited.id)
        // KRITISCH: a running session keeps its settings and its time; only the texts may change (7.11 D7).
        if (stored != null && running(stored.id) && !onlyTexts(stored, edited)) return SaveResult.Invalid(listOf(RUNNING))
        val moved = (schedule is Schedule.Once || schedule is Schedule.Deadline) && stored != null && stored.schedule != schedule
        // The form does not know the archive; moving a one-off brings it back. Nor the quiet time of
        // the card (NT2): it stays unless the answer to the card sets it (Review-Lauf 5).
        val quiet = edited.hintQuietUntil ?: stored?.hintQuietUntil
        val reminder = if (moved) {
            edited.copy(active = true, archivedAt = null, hintQuietUntil = quiet)
        } else {
            edited.copy(archivedAt = stored?.archivedAt, hintQuietUntil = quiet)
        }
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
        val current = if (reminder.id == 0L) emptyMap() else views.prep(reminder.id)
        val changed = prep.filter { new ->
            val old = current[new.question]
            old == null || old.unknown != new.unknown || old.text?.trim() != new.text?.trim()
        }
        if (Prep.missing(reminder, current + changed.associateBy { it.question }).isNotEmpty()) {
            problems += "Startklar-Fragen beantworten"
        }
        reminder.arrival?.let { a ->
            if (repo.places().none { it.id == a.placeId }) problems += "Ort zuerst in den Einstellungen festlegen"
        }
        if (problems.isNotEmpty()) return SaveResult.Invalid(problems)
        val id = repo.saveReminder(reminder)
        val saved = reminder.copy(id = id)
        changed.forEach { repo.addPrepAnswer(it.copy(id = 0, reminderId = id, text = it.text?.trim(), at = now())) }
        changed.firstOrNull { it.question == Prep.READY }?.let { readyChanged(id, it) }
        // A moved deadline starts anew in its windows, also when it was overdue or done.
        if (moved && schedule is Schedule.Deadline) {
            repo.openOccurrence(id)?.let { scheduler.close(saved, it, Action.Skip(SkipReasons.SCHEDULE_CHANGED)) }
            val due = Deadlines.firstDue(schedule, now(), zone)
            val created = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = due))
            repo.addEvent(Event(occurrenceId = created, at = now(), type = EventType.CREATED))
        }
        // KRITISCH: a moved one-off is due again here, explicitly. ScheduleRules only plans its
        // first occurrence, so a time zone change does not count as a move (stand.md, Zeitzone).
        if (moved && schedule is Schedule.Once) moveOnce(saved, schedule)
        val open = repo.openOccurrence(id)
        // A changed schedule moves an occurrence that never rang. One that rang and was snoozed or
        // postponed keeps its day: editing must not make today's occurrence vanish (Review B1).
        // One-offs and deadlines are handled above: unchanged, their occurrence stays as it is.
        if (open != null && open.state == OccurrenceState.PLANNED && !open.hasFired && open.postponeCount == 0 && schedule != Schedule.OnArrival && schedule !is Schedule.Deadline && schedule !is Schedule.Once) {
            val due = scheduler.nextDue(saved, repo.lastResolved(id))
            if (due == null) {
                scheduler.close(saved, open, Action.Skip(SkipReasons.SCHEDULE_CHANGED))
            } else if (due != open.dueAt) {
                repo.saveOccurrence(open.copy(dueAt = due, currentAt = due, arrivedAt = null))
            }
        }
        // A changed arrival rule (place, buffer, lead, rule gone) decides the arrival anew: the
        // occurrence goes back to its time; a mere text change keeps it (Review-Lauf 5, Frage 5).
        if (stored != null && stored.arrival != saved.arrival) {
            repo.openOccurrence(id)?.takeIf { it.state == OccurrenceState.PLANNED && it.arrivedAt != null && !it.rangOrMoved }
                ?.let { repo.saveOccurrence(it.copy(currentAt = it.dueAt, arrivedAt = null)) }
        }
        val ring = scheduler.syncReminder(saved) ?: scheduler.arriveIfThere(saved)
        scheduler.watchPlaces()
        return SaveResult.Saved(id, ring)
    }

    /**
     * The one-off [reminder] was moved to [schedule]: its occurrence is at the new time. One that
     * never rang just moves; one that rang ends as skipped (schedule changed), and a new one comes,
     * also after it was done, skipped or missed.
     */
    private suspend fun moveOnce(reminder: Reminder, schedule: Schedule.Once) {
        val due = schedule.at.atZone(zone).toInstant()
        val open = repo.openOccurrence(reminder.id)
        if (open != null && open.state == OccurrenceState.PLANNED && !open.hasFired && open.postponeCount == 0) {
            repo.saveOccurrence(open.copy(dueAt = due, currentAt = due, arrivedAt = null))
            return
        }
        open?.let { scheduler.close(reminder, it, Action.Skip(SkipReasons.SCHEDULE_CHANGED)) }
        val created = repo.saveOccurrence(Occurrence(reminderId = reminder.id, dueAt = due))
        repo.addEvent(Event(occurrenceId = created, at = now(), type = EventType.CREATED))
    }

    /** A session of the reminder is running (7.11 D7). */
    private suspend fun running(reminderId: Long): Boolean = repo.openOccurrence(reminderId)?.isRunning == true

    /** Only title, why, goal or minimum differ – what the app may change during a session. */
    private fun onlyTexts(stored: Reminder, edited: Reminder): Boolean =
        edited.copy(
            title = stored.title, why = stored.why, goal = stored.goal, minimum = stored.minimum,
            archivedAt = stored.archivedAt, hintQuietUntil = stored.hintQuietUntil,
        ) == stored

    suspend fun delete(reminderId: Long) {
        val reminder = repo.reminder(reminderId)
        repo.reminders().filter { it.prepFor == reminderId }.forEach { delete(it.id) }
        reminder?.prepFor?.let { main ->
            repo.blockers(main).filter { it.taskReminderId == reminderId }.forEach { repo.saveBlocker(it.copy(taskReminderId = null)) }
        }
        repo.openOccurrence(reminderId)?.let {
            scheduler.cancel(it)
            c.end(it.id)
        }
        repo.deleteReminder(reminderId)
        scheduler.watchPlaces()
    }

    /** False if it was not changed: unknown, or a session is running (7.11 D7). */
    suspend fun setActive(reminderId: Long, active: Boolean): Boolean {
        val reminder = repo.reminder(reminderId) ?: return false
        if (running(reminderId)) return false
        repo.saveReminder(reminder.copy(active = active, archivedAt = if (active) null else reminder.archivedAt))
        val open = repo.openOccurrence(reminderId)
        if (!active) {
            open?.let {
                scheduler.cancel(it)
                c.end(it.id)
            }
            scheduler.watchPlaces()
            return true
        }
        // Resuming must not report the paused time as missed: plan the open occurrence anew.
        if (open != null && open.currentAt < now()) {
            val due = scheduler.nextDue(reminder, repo.lastResolved(reminderId))
            if (due == null || due <= now()) scheduler.close(reminder, open, Action.Skip(SkipReasons.PAUSED))
            else repo.saveOccurrence(
                open.copy(state = OccurrenceState.PLANNED, dueAt = due, currentAt = due, pausedUntil = null, firedAt = null, awaitsArrival = false, arrivedAt = null),
            )
        }
        scheduler.syncReminder(reminder.copy(active = true))
        scheduler.watchPlaces()
        return true
    }

    /** False if it was not changed: unknown, or a session is running (7.11 D7). */
    suspend fun setArchived(reminderId: Long, archived: Boolean): Boolean {
        if (archived && !setActive(reminderId, false)) return false
        val reminder = repo.reminder(reminderId) ?: return false
        repo.saveReminder(reminder.copy(archivedAt = if (archived) now() else null))
        return true
    }

    suspend fun savePlace(place: Place): Long {
        val id = repo.savePlace(place)
        scheduler.watchPlaces()
        return id
    }

    // --- Occurrences -----------------------------------------------------------------------

    /**
     * After it the occurrence rings no more (E17), except after leaving the proof screen: then the
     * escalation goes on after a grace. An action on one that is closed or gone ends what is left
     * of it (an old notification); a rejected action on an open one leaves it ringing.
     */
    suspend fun act(occurrenceId: Long, action: Action): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return gone(occurrenceId, "unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return gone(occurrenceId, "unknown reminder")
        if (action is Action.Snooze && now().plus(action.duration) >= views.missedAt(reminder, occurrence)) {
            return TransitionResult.Rejected("snooze would pass the end of the day")
        }
        val limits = views.limits(reminder, occurrence)
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits)
        if (result !is TransitionResult.Ok) return result.also { if (!occurrence.isOpen) c.end(occurrenceId) }
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
                val withProof = limits.proofCarried || ProofCheck.problems(reminder.proof, action.evidence, limits.lastProofText).isEmpty()
                repo.addItemDone(ItemDone(occurrenceId = occurrence.id, itemId = action.itemId, doneAt = now(), withProof = withProof))
            }
            else -> {}
        }
        // Doing a preparation task solves its blocker (S5).
        if (result.occurrence.state == OccurrenceState.DONE && reminder.prepFor != null) {
            repo.blockers(reminder.prepFor).filter { it.isOpen && it.taskReminderId == reminder.id }
                .forEach { repo.saveBlocker(it.copy(solvedAt = now())) }
        }
        scheduler.cancel(occurrence)
        if (action !is Action.AbortProof) c.end(occurrence.id)
        scheduler.syncReminder(reminder)
        return result
    }

    private fun gone(occurrenceId: Long, reason: String): TransitionResult {
        c.end(occurrenceId)
        return TransitionResult.Rejected(reason)
    }

    /**
     * "Zurücknehmen" after a misclick (Anf. 7.31): the last done or skipped occurrence of the
     * reminder is open again and rings in [REOPEN_DELAY], without counting as a postponement.
     * The next occurrence, planned but not yet rung, goes; it comes again once this one is
     * resolved. What the completion itself did goes back: the checklist items it ticked and a
     * blocker its preparation task solved. Proofs stay (see [Limits.proofCarried]). An aborted
     * session runs again with its old start instead.
     */
    suspend fun reopen(occurrenceId: Long): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        val resolvedAt = occurrence.resolvedAt ?: return TransitionResult.Rejected("occurrence is open")
        if (!reminder.active) return TransitionResult.Rejected("reminder is paused")
        if (repo.lastResolved(reminder.id)?.id != occurrence.id) return TransitionResult.Rejected("only the last one can be taken back")
        val next = repo.openOccurrence(reminder.id)
        if (next != null && (next.state != OccurrenceState.PLANNED || next.hasFired || next.postponeCount > 0)) {
            return TransitionResult.Rejected("the next one has started already")
        }
        // Done or skipped in advance: back to its planned time, not sooner (Anf. 7.32 VB7).
        val at = maxOf(now().plus(REOPEN_DELAY), if (occurrence.hasFired) Instant.MIN else occurrence.currentAt)
        val result = Lifecycle.transition(reminder, occurrence, Action.Reopen(at), now())
        if (result !is TransitionResult.Ok) return result
        // Past the end of its day (several a day: the next time) it would only be missed (VS1).
        // Measured on the occurrence as it was: its day, not the day the new time falls on.
        // A session runs past the end of the day (7.11 D7); its maximum decides.
        if (!result.occurrence.isRunning && at >= views.missedAt(reminder, occurrence)) return TransitionResult.Rejected("too late for today")
        next?.let {
            scheduler.cancel(it)
            repo.deleteOccurrence(it.id)
        }
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        repo.deleteItemsDone(occurrence.id, resolvedAt)
        reminder.prepFor?.let { main ->
            repo.blockers(main).filter { it.taskReminderId == reminder.id && it.solvedAt == resolvedAt }
                .forEach { repo.saveBlocker(it.copy(solvedAt = null)) }
        }
        scheduler.syncReminder(reminder)
        return result
    }

    suspend fun startProof(occurrenceId: Long): TransitionResult {
        val occurrence = repo.occurrence(occurrenceId) ?: return TransitionResult.Rejected("unknown occurrence")
        val reminder = repo.reminder(occurrence.reminderId) ?: return TransitionResult.Rejected("unknown reminder")
        val window = now().plus(Duration.ofMinutes(reminder.proof.windowMinutes.toLong()))
        // A session has no end of the day (7.11 D7).
        val until = if (occurrence.isRunning) window else minOf(window, views.missedAt(reminder, occurrence))
        return act(occurrenceId, Action.StartProof(until))
    }

    suspend fun abortProof(occurrenceId: Long): TransitionResult =
        act(occurrenceId, Action.AbortProof(now().plus(Escalation.PROOF_GRACE)))

    private suspend fun saveEvidence(occurrence: Occurrence, e: Evidence, itemId: Long? = null) {
        val at = now()
        fun proof(type: ProofType, path: String?, text: String? = null) =
            Proof(occurrenceId = occurrence.id, type = type, filePath = path, takenAt = at, text = text, itemId = itemId)
        e.photoPath?.let { repo.addProof(proof(ProofType.PHOTO, it)) }
        e.text?.trim()?.takeIf { it.isNotEmpty() }?.let { repo.addProof(proof(ProofType.TEXT, null, it)) }
        e.audioPath?.let { repo.addProof(proof(ProofType.AUDIO, it)) }
    }

    // --- Blockers, preparation tasks and the notice before (S5–S8, T6) ------------------------

    suspend fun addBlocker(reminderId: Long, text: String, origin: BlockerOrigin): Long {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "blocker without text" }
        repo.blockers(reminderId).firstOrNull { it.isOpen && it.text.equals(clean, ignoreCase = true) }?.let { return it.id }
        return repo.saveBlocker(Blocker(reminderId = reminderId, text = clean, createdAt = now(), origin = origin))
    }

    suspend fun solveBlocker(blockerId: Long) {
        val blocker = repo.blocker(blockerId)?.takeIf { it.isOpen } ?: return
        repo.saveBlocker(blocker.copy(solvedAt = now(), taskReminderId = null))
        blocker.taskReminderId?.let { delete(it) }
    }

    suspend fun suggestTaskTime(reminderId: Long): LocalDateTime =
        Blockers.suggestTaskTime(repo.openOccurrence(reminderId)?.currentAt, now(), zone)

    suspend fun planTask(blockerId: Long, at: LocalDateTime): SaveResult {
        val blocker = repo.blocker(blockerId)?.takeIf { it.isOpen } ?: return SaveResult.Invalid(listOf("Blocker nicht gefunden"))
        val task = Presets.apply(
            Reminder(
                title = blocker.text, character = Character.DOCUMENT, schedule = Schedule.Once(at),
                createdAt = now(), prepFor = blocker.reminderId,
            ),
            Strictness.RELAXED,
        )
        val result = save(task, emptyList())
        if (result is SaveResult.Saved) {
            val old = blocker.taskReminderId
            repo.saveBlocker(blocker.copy(taskReminderId = result.id))
            old?.let { delete(it) }
        }
        return result
    }

    suspend fun answerAsk(occurrenceId: Long, text: String?): Long? {
        val occurrence = repo.occurrence(occurrenceId) ?: return null
        repo.addEvent(Event(occurrenceId = occurrenceId, at = now(), type = EventType.BLOCKER_ASKED))
        return text?.takeIf { it.isNotBlank() }?.let { addBlocker(occurrence.reminderId, it, BlockerOrigin.ASKED) }
    }

    suspend fun preNotice(occurrenceId: Long): Digest? {
        val occurrence = repo.occurrence(occurrenceId)?.takeIf { it.state == OccurrenceState.PLANNED } ?: return null
        val reminder = repo.reminder(occurrence.reminderId)?.takeIf { it.active } ?: return null
        val firstStep = views.prep(reminder.id)[Prep.FIRST_STEP]?.takeIf { !it.unknown }?.text
        val (title, lines) = PreNotices.text(reminder, occurrence, firstStep, repo.blockers(reminder.id), now(), zone)
        return Digest(title, lines)
    }

    /** "Startklar" question 3 changed: a missing thing is a blocker, "Ja, sofort" solves them. */
    private suspend fun readyChanged(reminderId: Long, answer: PrepAnswer) {
        val text = Blockers.fromReady(answer)
        if (text != null) {
            addBlocker(reminderId, text, BlockerOrigin.STARTKLAR)
        } else if (answer.text?.trim() == Prep.READY_YES) {
            repo.blockers(reminderId).filter { it.isOpen && it.origin == BlockerOrigin.STARTKLAR }
                .forEach { repo.saveBlocker(it.copy(solvedAt = now())) }
        }
    }

    // --- Snoozing again and again (NT2) -----------------------------------------------------

    suspend fun snoozeHints(): List<SnoozeHint> = repo.reminders().mapNotNull { r ->
        val recent = repo.recentResolved(r.id, Snoozing.IN_A_ROW + Snoozing.IN_A_ROW).map { o ->
            // Postponing counts too, also in advance (Anf. 7.32 VB7).
            o to repo.events(o.id).count { it.type == EventType.SNOOZED || it.type == EventType.POSTPONED }
        }
        Snoozing.hint(r, recent, r.hintQuietUntil, now(), zone)
    }

    suspend fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice): SaveResult {
        val reminder = repo.reminder(reminderId) ?: return SaveResult.Invalid(listOf("Reminder nicht gefunden"))
        val hint = snoozeHints().firstOrNull { it.reminder.id == reminderId }
        val now = now()
        val changed = when (choice) {
            SnoozeHintChoice.TIME -> hint?.time?.let { reminder.copy(schedule = withTime(reminder.schedule, it)) }
            SnoozeHintChoice.RELAX -> hint?.relax?.let { reminder.copy(strictness = it) }
            SnoozeHintChoice.DISMISS -> null
        }
        // The card was gone already: no change, and no silent "Nein danke".
        if (changed == null && choice != SnoozeHintChoice.DISMISS) return SaveResult.Invalid(listOf("Hinweis gilt nicht mehr"))
        val quiet = if (changed == null) now.plus(Duration.ofDays(Snoozing.QUIET_DAYS)) else now
        return save((changed ?: reminder).copy(hintQuietUntil = quiet), emptyList())
    }

    private fun withTime(schedule: Schedule, time: LocalTime): Schedule = when (schedule) {
        is Schedule.Daily -> Schedule.Daily(time)
        is Schedule.Weekdays -> Schedule.Weekdays(time, schedule.days)
        is Schedule.AfterDone -> schedule.copy(time = time)
        else -> schedule
    }

    companion object {
        /** Why a change was refused during a session. */
        const val RUNNING = "läuft gerade – nur Texte änderbar"
        /** A taken-back occurrence rings again after this, not right away: one is in the app just now. */
        val REOPEN_DELAY: Duration = Duration.ofMinutes(10)
    }
}
