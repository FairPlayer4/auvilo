package com.avlreminder.core.usecases

import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.model.severalADay
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.PlaceResult
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.ArrivalDecision
import com.avlreminder.core.rules.ArrivalWindow
import com.avlreminder.core.rules.Deadlines
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.PreNotices
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.SessionStep
import com.avlreminder.core.rules.Sessions
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.WeeklyGoals
import com.avlreminder.core.rules.Weeks
import java.time.Duration
import java.time.Instant

/** Time: creates the next occurrence, plans its alarm, fires, escalates and ends the day. */
internal class Scheduler(private val c: EngineContext, private val views: AlarmViews) {
    private val repo get() = c.repo
    private val zone get() = c.zone
    private fun now() = c.now()

    /** An alarm fired. Returns what rings, or null if nothing (stale alarm). */
    suspend fun onAlarm(occurrenceId: Long): Ring? {
        val occurrence = repo.occurrence(occurrenceId) ?: return null
        if (!occurrence.isOpen) return null
        val reminder = repo.reminder(occurrence.reminderId) ?: return null
        if (!reminder.active) return null
        val now = now()
        if (occurrence.state == OccurrenceState.PLANNED) {
            if (occurrence.awaitsArrival) {
                awaitArrival(reminder, occurrence)
                return null
            }
            if (occurrence.currentAt > now.plus(ReminderEngine.EARLY_TOLERANCE)) {
                plan(reminder, occurrence)
                return null
            }
            return fire(reminder, occurrence)
        }
        if (occurrence.state == OccurrenceState.RUNNING) return session(reminder, occurrence)
        val paused = occurrence.pausedUntil
        if (paused != null && paused > now.plus(ReminderEngine.EARLY_TOLERANCE)) {
            plan(reminder, occurrence)
            return null
        }
        if (now >= views.missedAt(reminder, occurrence)) {
            endDay(reminder, occurrence)
            return null
        }
        val escalated = apply(reminder, occurrence, Action.Escalate)
        plan(reminder, escalated)
        return views.ring(reminder, escalated)
    }

    /**
     * The alarm of a running session (Anf. 7.11): a check, the last reminder or the end. Not yet
     * due (alarm early, a sync, the proof screen open) it is only planned again. Also after a
     * reboot: a check missed meanwhile rings now, a maximum passed brings the last reminder (D7).
     */
    private suspend fun session(reminder: Reminder, occurrence: Occurrence): Ring? {
        val now = now()
        val at = Sessions.nextAlarm(reminder, occurrence)
        val proof = occurrence.pausedUntil
        if (at != null && (at > now.plus(ReminderEngine.EARLY_TOLERANCE) || (proof != null && proof > now.plus(ReminderEngine.EARLY_TOLERANCE)))) {
            plan(reminder, occurrence)
            return null
        }
        val paused = c.settings().alarmsPaused(now)
        // Without its settings (switched off while running, e.g. a restored backup) it ends at once.
        val step = if (at == null) SessionStep.TimeOut else Sessions.step(
            reminder, occurrence, now, quiet = c.device.isDoNotDisturb() && !reminder.ignoreDnd, paused = paused,
        )
        return when (step) {
            is SessionStep.Check -> {
                val asked = apply(reminder, occurrence, Action.AskCheck(step.again))
                plan(reminder, asked)
                views.ring(reminder, asked)
            }
            is SessionStep.FinalCall -> {
                val called = apply(reminder, occurrence, Action.FinalCall(step.audible, step.again))
                plan(reminder, called)
                views.ring(reminder, called)
            }
            SessionStep.TimeOut -> {
                close(reminder, occurrence, Action.EndSession(paused))
                createNext(reminder)?.let { plan(reminder, it) }
                null
            }
        }
    }

    suspend fun sync(): List<Ring> {
        val rings = repo.reminders().mapNotNull { syncReminder(it) }
        watchPlaces()
        return rings
    }

    suspend fun resume(): List<Ring> {
        val rings = sync().toMutableList()
        for (open in repo.openOccurrences()) {
            if (open.state == OccurrenceState.PLANNED || rings.any { it.occurrence.id == open.id }) continue
            val reminder = repo.reminder(open.reminderId) ?: continue
            if (views.strictness(reminder, open) == null) continue
            onAlarm(open.id)?.let { rings += it }
        }
        return rings
    }

    /** Arrived at [placeId], reported by the geofence or the WLAN connection. */
    suspend fun onArrival(placeId: Long): List<Ring> =
        waitingAt(placeId).mapNotNull { (r, o) -> arrived(r, o, byCheck = false, plannedCheck = null) }

    /**
     * The check alarm of [placeId] (7.29 OZ2): one check for all occurrences in their window or
     * waiting for the arrival there, from the WLAN alone. Only "there" changes anything.
     * [plannedAt]: the time the check was planned for, null if unknown; the check planned for the
     * start of a window decides F2 even when Doze delivers it late.
     */
    suspend fun onPlaceCheck(placeId: Long, plannedAt: Instant?): List<Ring> {
        val place = repo.places().firstOrNull { it.id == placeId } ?: return emptyList()
        val now = now()
        val waiting = waitingAt(placeId).filter { (r, o) -> ArrivalWindow.waits(r, o, now, views.missedAt(r, o)) }
        if (waiting.isEmpty()) return emptyList()
        val check = c.check(place, fallback = false)
        val late = plannedAt?.let { Duration.between(it, now).toMinutes() }?.takeIf { it > 0 }?.let { " late=${it}min" }.orEmpty()
        c.log("place check place=$placeId ${ArrivalWindow.describe(check)}$late for ${waiting.joinToString { "r${it.first.id}" }}")
        if (check.result != PlaceResult.AT) return emptyList()
        return waiting.mapNotNull { (r, o) -> arrived(r, o, byCheck = true, plannedCheck = plannedAt) }
    }

    suspend fun checkPlaces(): List<Ring> {
        val places = repo.places().associateBy { it.id }
        return repo.reminders().filter { it.active && it.arrival != null }.mapNotNull { r ->
            val place = places[r.arrival?.placeId] ?: return@mapNotNull null
            val o = repo.openOccurrence(r.id)
                ?.takeIf { it.state == OccurrenceState.PLANNED && (r.schedule == Schedule.OnArrival || it.awaitsArrival) }
                ?: return@mapNotNull null
            if (c.check(place, fallback = true).result == PlaceResult.AT) arrived(r, o, byCheck = true, plannedCheck = null) else null
        }
    }

    /** Active reminders with an arrival at [placeId] and their open occurrence. */
    private suspend fun waitingAt(placeId: Long): List<Pair<Reminder, Occurrence>> =
        repo.reminders().filter { it.active && it.arrival?.placeId == placeId }
            .mapNotNull { r -> repo.openOccurrence(r.id)?.let { r to it } }

    /** What the arrival means (see [ArrivalWindow.decide]); rings what is due right away. */
    private suspend fun arrived(reminder: Reminder, occurrence: Occurrence, byCheck: Boolean, plannedCheck: Instant?): Ring? {
        val now = now()
        return when (val d = ArrivalWindow.decide(reminder, occurrence, now, zone, plannedCheck)) {
            ArrivalDecision.Ignore -> null
            is ArrivalDecision.TooEarly -> {
                c.log("arrival r${reminder.id}: too early, window from ${local(d.windowStart)}")
                null
            }
            ArrivalDecision.Now -> arrive(reminder, occurrence)
            is ArrivalDecision.At -> {
                val arrived = apply(reminder, occurrence, Action.Arrived(d.at))
                c.log("arrival r${reminder.id}: rings at ${local(d.at)}" + if (byCheck) " (check)" else "")
                if (d.at <= now) {
                    fire(reminder, arrived)
                } else {
                    plan(reminder, arrived)
                    null
                }
            }
        }
    }

    private fun local(at: Instant) = at.atZone(zone).toLocalTime().withNano(0)

    /**
     * The next check of each place (design.md 6): the earliest of all occurrences waiting there, so
     * the shortest check interval wins. Runs at the end of every command. The check at the start
     * of a window is exact also in Doze: it decides F2.
     */
    suspend fun planChecks() {
        val now = now()
        val next = mutableMapOf<Long, Instant>()
        val starts = mutableMapOf<Long, MutableSet<Instant>>()
        for (r in repo.reminders()) {
            val placeId = r.arrival?.placeId ?: continue
            if (!r.active) continue
            val o = repo.openOccurrence(r.id) ?: continue
            val at = ArrivalWindow.nextCheck(r, o, now, views.missedAt(r, o)) ?: continue
            next.merge(placeId, at, ::minOf)
            ArrivalWindow.windowStart(r, o)?.let { starts.getOrPut(placeId) { mutableSetOf() } += it }
        }
        for (place in repo.places()) {
            val at = next[place.id]
            if (at != null) c.placeAlarms.set(place.id, at, exact = starts[place.id].orEmpty().contains(at)) else c.placeAlarms.cancel(place.id)
        }
    }

    /** Saved a reminder that waits for an arrival while already there: the geofence would not report it. */
    suspend fun arriveIfThere(reminder: Reminder): Ring? {
        if (!reminder.active || reminder.schedule != Schedule.OnArrival) return null
        val place = repo.places().firstOrNull { it.id == reminder.arrival?.placeId } ?: return null
        val o = repo.openOccurrence(reminder.id)?.takeIf { it.state == OccurrenceState.PLANNED } ?: return null
        return if (c.check(place, fallback = true).result == PlaceResult.AT) arrive(reminder, o) else null
    }

    suspend fun syncReminder(reminder: Reminder): Ring? {
        var open = repo.openOccurrence(reminder.id)
        if (!reminder.active) {
            open?.let { cancel(it) }
            return null
        }
        if (open == null) {
            open = createNext(reminder) ?: return null
        }
        val now = now()
        return when {
            // Waits for the arrival, has no alarm of its own.
            open.state == OccurrenceState.PLANNED && reminder.schedule == Schedule.OnArrival -> {
                cancel(open)
                null
            }
            open.state == OccurrenceState.PLANNED && open.awaitsArrival -> {
                awaitArrival(reminder, open)
                null
            }
            // A session is not missed at the end of the day; it ends through an answer or its maximum (7.11 D7).
            open.state == OccurrenceState.RUNNING -> session(reminder, open)
            open.state == OccurrenceState.PLANNED && open.currentAt < now.minus(ReminderEngine.GRACE) -> {
                repo.addEvent(Event(occurrenceId = open.id, at = now, type = EventType.MISSED_DETECTED, detail = open.currentAt.toString()))
                if (now >= views.missedAt(reminder, open)) {
                    endDay(reminder, open)
                    null
                } else {
                    fire(reminder, open)
                }
            }
            open.state != OccurrenceState.PLANNED && now >= views.missedAt(reminder, open) -> {
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
     * drops the occurrence; an unknown position rings, so nothing is lost. Snoozed or postponed it
     * rang already and the decision is made (7.29 OZ5): only "there" rings, away or unknown waits
     * for the arrival until the end of the day. After an arrival it rings wherever one is now (OZ4).
     */
    private suspend fun fire(reminder: Reminder, occurrence: Occurrence): Ring? {
        val arrival = reminder.arrival
        // A rescue reminder rings wherever one is.
        if (arrival?.whenAway == WhenAway.DROP && occurrence.arrivedAt == null && !views.isRescue(reminder, occurrence)) {
            val place = repo.places().firstOrNull { it.id == arrival.placeId }
            if (place != null) {
                val check = c.check(place, fallback = true)
                c.log("place check place=${place.id} ${ArrivalWindow.describe(check)} at the time of r${reminder.id}")
                val here = check.result
                if (occurrence.rangOrMoved && here != PlaceResult.AT) {
                    awaitArrival(reminder, apply(reminder, occurrence, Action.AwaitArrival))
                    return null
                }
                if (!occurrence.rangOrMoved && here == PlaceResult.AWAY) {
                    close(reminder, occurrence, Action.Drop)
                    createNext(reminder)?.let { plan(reminder, it) }
                    return null
                }
            }
        }
        val fired = apply(reminder, occurrence, Action.Fire)
        plan(reminder, fired)
        return views.ring(reminder, fired)
    }

    /** Waiting for the arrival: no alarm but the end of the day, then it is missed. */
    private suspend fun awaitArrival(reminder: Reminder, occurrence: Occurrence) {
        if (now() >= views.missedAt(reminder, occurrence)) endDay(reminder, occurrence) else plan(reminder, occurrence)
    }

    private suspend fun arrive(reminder: Reminder, occurrence: Occurrence): Ring {
        val arrived = apply(reminder, occurrence, Action.Arrive)
        plan(reminder, arrived)
        return views.ring(reminder, arrived)
    }

    /**
     * The end of the day passed without completion. A checklist with an item done that day comes
     * back the next day with the rest (C3, does not count as postponing); anything else is missed.
     */
    private suspend fun endDay(reminder: Reminder, occurrence: Occurrence) {
        // Today it rings no more: missed, or quiet until its next window or tomorrow (Review-Lauf 5, Frage 3).
        c.end(occurrence.id)
        val schedule = reminder.schedule
        val missedAt = views.missedAt(reminder, occurrence)
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
        val day = missedAt.atZone(zone).toLocalDate()
        // Several a day: the next time takes over, the rest is not carried to tomorrow (VS1).
        if (reminder.checklist.isNotEmpty() && !reminder.schedule.severalADay) {
            val limits = views.limits(reminder, occurrence, day)
            if (limits.itemDoneToday) {
                val time = occurrence.dueAt.atZone(zone).toLocalTime()
                val to = maxOf(day.plusDays(1).atTime(time).atZone(zone).toInstant(), now().plus(ReminderEngine.GRACE))
                plan(reminder, apply(reminder, occurrence, Action.DeferRest(to), limits))
                return
            }
        }
        // With the weekly target reached a further offer that was not taken up is no miss;
        // neither is one left open while all alarms were paused by hand (PS7) – paused before it
        // was due and still at the end of the day, not when that is handled (phone off over night).
        val reached = views.weekStatus(reminder, occurrence.dueAt.atZone(zone).toLocalDate())?.reached == true
        val action = when {
            c.settings().excusedByPause(occurrence.dueAt, missedAt) -> Action.Skip(SkipReasons.EMERGENCY)
            reached -> Action.Skip(SkipReasons.GOAL_REACHED)
            else -> Action.Miss
        }
        close(reminder, occurrence, action)
        createNext(reminder)?.let { plan(reminder, it) }
    }

    /** Geofences for the places that active reminders wait for. */
    suspend fun watchPlaces() {
        val needed = repo.reminders().filter { it.active }.mapNotNull { it.arrival?.placeId }.toSet()
        c.geofences.watch(repo.places().filter { it.id in needed })
    }

    /**
     * Sets the alarm for an open occurrence: its time if planned; else the end of a running proof
     * or the next round, at most the miss deadline.
     */
    suspend fun plan(reminder: Reminder, occurrence: Occurrence) {
        if (occurrence.state == OccurrenceState.RUNNING) {
            planSession(reminder, occurrence)
            return
        }
        val now = now()
        val missedAt = views.missedAt(reminder, occurrence)
        val settings = c.settings()
        val at = if (occurrence.state == OccurrenceState.PLANNED && occurrence.awaitsArrival) {
            missedAt
        } else if (occurrence.state == OccurrenceState.PLANNED) {
            occurrence.currentAt
        } else if (views.strictness(reminder, occurrence) == null) {
            // Weekly target reached: no further rounds, only the end of the day.
            missedAt
        } else if (settings.alarmsPaused(now)) {
            // Paused by hand: no rounds; when the pause ends it rings as usual (PS7).
            minOf(settings.alarmsPausedUntil!!, missedAt)
        } else {
            val paused = occurrence.pausedUntil
            // KRITISCH: The next round counts from the last ring or checked item, not from now. plan() also runs
            // on every sync (app start, time change, reboot); counting from now would push the
            // round further with each sync, and a phone used often would never ring again.
            val rang = lastRound(occurrence) ?: now
            // A deadline rings again after its interval.
            val round = (reminder.schedule as? Schedule.Deadline)?.let { rang.plus(Duration.ofMinutes(it.intervalMinutes.toLong())) }
            // A round missed (phone off) rings right away.
            val next = if (paused != null && paused > now) paused else maxOf(round ?: Escalation.nextRound(rang), now)
            minOf(next, missedAt)
        }
        val kind = if (reminder.strictness == Strictness.RELAXED) AlarmKind.EXACT else AlarmKind.ALARM_CLOCK
        c.alarms.set(occurrence.id, at, kind)
        val notice = PreNotices.at(reminder, occurrence, zone)
        if (notice != null && notice > now && notice < occurrence.currentAt) c.preNotices.set(occurrence.id, notice)
        else c.preNotices.cancel(occurrence.id)
    }

    /**
     * The next alarm of a running session: check, last reminder or the end of the time to react,
     * after the proof screen if it is open. Checks of a relaxed session are exact alarms; the last
     * reminder is an alarm clock, as strict as possible (D5).
     */
    private fun planSession(reminder: Reminder, occurrence: Occurrence) {
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

    /** When the occurrence last rang or progressed; null: never. */
    private suspend fun lastRound(occurrence: Occurrence): Instant? =
        repo.events(occurrence.id).filter { it.type in ROUND_EVENTS }.maxOfOrNull { it.at }

    /** No alarm and no notice before for the occurrence. */
    fun cancel(occurrence: Occurrence) {
        c.alarms.cancel(occurrence.id)
        c.preNotices.cancel(occurrence.id)
    }

    suspend fun createNext(reminder: Reminder): Occurrence? {
        val due = nextDue(reminder, repo.lastResolved(reminder.id)) ?: return null
        val draft = Occurrence(reminderId = reminder.id, dueAt = due)
        val id = repo.saveOccurrence(draft)
        repo.addEvent(Event(occurrenceId = id, at = now(), type = EventType.CREATED))
        return draft.copy(id = id)
    }

    suspend fun nextDue(reminder: Reminder, previous: Occurrence?): Instant? {
        // A checklist counts from its first done item (C5b).
        val lastDoneAt = repo.lastDone(reminder.id)?.let { it.firstProgressAt ?: it.resolvedAt }
        fun next(after: Instant) = ScheduleRules.nextDue(
            schedule = reminder.schedule,
            now = after,
            zone = zone,
            previousDueAt = previous?.dueAt,
            lastDoneAt = lastDoneAt,
            // After a session the next time not yet begun follows; times passed during it do not count extra (7.11 D7).
            previousWasDone = previous?.state == OccurrenceState.DONE || previous?.startedAt != null,
            previousMissed = previous?.resolution == Resolution.MISSED,
        )
        val now = now()
        val due = next(now) ?: return null
        val week = views.weekStatus(reminder) ?: return due
        val nextMonday = Weeks.range(now, zone).second
        return when {
            // Done as often as allowed: the next offer comes next week.
            week.full && due < nextMonday -> next(nextMonday.minusMillis(1))
            // Still at 0 at the weekend: a rescue reminder before the next offer.
            !week.minimumReached -> listOfNotNull(due, WeeklyGoals.nextRescue(reminder.schedule, now, zone, previous?.dueAt)).min()
            else -> due
        }
    }

    suspend fun apply(reminder: Reminder, occurrence: Occurrence, action: Action, limits: Limits? = null): Occurrence {
        val result = Lifecycle.transition(reminder, occurrence, action, now(), limits ?: views.limits(reminder, occurrence))
        check(result is TransitionResult.Ok) { "internal transition $action rejected: $result" }
        repo.saveOccurrence(result.occurrence)
        repo.addEvent(result.event)
        return result.occurrence
    }

    /** Closed, also by the system (missed, dropped, the end of a session): nothing of it rings any more (E17). */
    suspend fun close(reminder: Reminder, occurrence: Occurrence, action: Action) {
        apply(reminder, occurrence, action)
        cancel(occurrence)
        c.end(occurrence.id)
    }
}

/** Events the next round counts from (see [Scheduler.plan]): a ring, a checked item, "Zurücknehmen". */
private val ROUND_EVENTS = setOf(EventType.DUE, EventType.ESCALATED, EventType.ITEM_DONE, EventType.REOPENED)
