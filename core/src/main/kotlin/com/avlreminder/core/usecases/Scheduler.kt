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
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Deadlines
import com.avlreminder.core.rules.Escalation
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.PreNotices
import com.avlreminder.core.rules.ScheduleRules
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

    suspend fun onArrival(placeId: Long): List<Ring> {
        val now = now()
        val today = now.atZone(zone).toLocalDate()
        return repo.reminders().filter { it.active && it.arrival?.placeId == placeId }.mapNotNull { r ->
            val o = repo.openOccurrence(r.id) ?: return@mapNotNull null
            // Waits for it: only on arrival, today's not yet rung, or snoozed/postponed and away at its time.
            // Snoozed or postponed ones before their new time keep it.
            val waiting = o.state == OccurrenceState.PLANNED && (
                r.schedule == Schedule.OnArrival || o.awaitsArrival ||
                    (!o.hasFired && o.currentAt > now && o.currentAt.atZone(zone).toLocalDate() == today)
                )
            if (waiting) arrive(r, o) else null
        }
    }

    suspend fun checkPlaces(): List<Ring> {
        val places = repo.places().associateBy { it.id }
        return repo.reminders().filter { it.active && it.arrival != null }.mapNotNull { r ->
            val place = places[r.arrival?.placeId] ?: return@mapNotNull null
            val o = repo.openOccurrence(r.id)
                ?.takeIf { it.state == OccurrenceState.PLANNED && (r.schedule == Schedule.OnArrival || it.awaitsArrival) }
                ?: return@mapNotNull null
            if (c.isAt(place) == true) arrive(r, o) else null
        }
    }

    /** Saved a reminder that waits for an arrival while already there: the geofence would not report it. */
    suspend fun arriveIfThere(reminder: Reminder): Ring? {
        if (!reminder.active || reminder.schedule != Schedule.OnArrival) return null
        val place = repo.places().firstOrNull { it.id == reminder.arrival?.placeId } ?: return null
        val o = repo.openOccurrence(reminder.id)?.takeIf { it.state == OccurrenceState.PLANNED } ?: return null
        return if (c.isAt(place) == true) arrive(reminder, o) else null
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
     * for the arrival until the end of the day.
     */
    private suspend fun fire(reminder: Reminder, occurrence: Occurrence): Ring? {
        val arrival = reminder.arrival
        // A rescue reminder rings wherever one is.
        if (arrival?.whenAway == WhenAway.DROP && !views.isRescue(reminder, occurrence)) {
            val place = repo.places().firstOrNull { it.id == arrival.placeId }
            if (place != null) {
                val here = c.isAt(place)
                if (occurrence.hasFired && here != true) {
                    awaitArrival(reminder, apply(reminder, occurrence, Action.AwaitArrival))
                    return null
                }
                if (!occurrence.hasFired && here == false) {
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
            // A deadline rings again after its interval.
            val round = (reminder.schedule as? Schedule.Deadline)?.let { now.plus(Duration.ofMinutes(it.intervalMinutes.toLong())) }
            val next = if (paused != null && paused > now) paused else round ?: Escalation.nextRound(now)
            minOf(next, missedAt)
        }
        val kind = if (reminder.strictness == Strictness.RELAXED) AlarmKind.EXACT else AlarmKind.ALARM_CLOCK
        c.alarms.set(occurrence.id, at, kind)
        val notice = PreNotices.at(reminder, occurrence, zone)
        if (notice != null && notice > now && notice < occurrence.currentAt) c.preNotices.set(occurrence.id, notice)
        else c.preNotices.cancel(occurrence.id)
    }

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
            previousWasDone = previous?.state == OccurrenceState.DONE,
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

    suspend fun close(reminder: Reminder, occurrence: Occurrence, action: Action) {
        apply(reminder, occurrence, action)
        cancel(occurrence)
    }
}
