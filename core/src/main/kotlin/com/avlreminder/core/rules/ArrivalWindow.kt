package com.avlreminder.core.rules

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.ports.PlaceCheck
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** What an arrival means for one occurrence (Anf. 7.29, design.md Z.2). */
sealed interface ArrivalDecision {
    /** Nothing changes: no arrival trigger, not waiting, or the first arrival counted already. */
    data object Ignore : ArrivalDecision
    /** Before the window: does not count (OZ1), only the diagnostic log notes it. */
    data class TooEarly(val windowStart: Instant) : ArrivalDecision
    /** Rings right away: "wenn ich zuhause bin", or a reminder without a window as before 7.29. */
    data object Now : ArrivalDecision
    /** Rings at [at]: after the buffer, or at the latest time when already there at the start (F2). */
    data class At(val at: Instant) : ArrivalDecision
}

/**
 * The arrival window of an occurrence: from [com.avlreminder.core.model.Arrival.leadMinutes] before
 * its time (the latest time) up to it. Snoozed or postponed and away at the new time, it waits for
 * the arrival from then on until the end of the day (OZ5).
 */
object ArrivalWindow {
    /**
     * A check this soon after the start of the window that finds one there means "already there"
     * (F2, Homeoffice): it rings at the latest time. Later it counts as an arrival.
     */
    val START_TOLERANCE: Duration = Duration.ofMinutes(5)

    /** From when to when the place is checked for the occurrence; null: not at all. */
    fun checking(reminder: Reminder, occurrence: Occurrence, missedAt: Instant): Pair<Instant, Instant>? {
        val arrival = reminder.arrival ?: return null
        if (!arrival.hasWindow || reminder.schedule == Schedule.OnArrival) return null
        if (occurrence.state != OccurrenceState.PLANNED || occurrence.arrivedAt != null) return null
        return when {
            occurrence.awaitsArrival -> occurrence.currentAt to missedAt
            occurrence.rangOrMoved -> null
            else -> occurrence.currentAt.minus(Duration.ofMinutes(arrival.leadMinutes.toLong())) to occurrence.currentAt
        }
    }

    /**
     * The next check after [now]: at the start of the window, then every check interval. The
     * latest time itself has the occurrence's own alarm, which checks too.
     */
    fun nextCheck(reminder: Reminder, occurrence: Occurrence, now: Instant, missedAt: Instant): Instant? {
        val (start, end) = checking(reminder, occurrence, missedAt) ?: return null
        val interval = Duration.ofMinutes(reminder.arrival!!.checkMinutes.toLong())
        val next = if (now < start) start else start.plus(interval.multipliedBy(Duration.between(start, now).dividedBy(interval) + 1))
        return next.takeIf { it < end }
    }

    /** Whether a check at [now] concerns the occurrence. */
    fun waits(reminder: Reminder, occurrence: Occurrence, now: Instant, missedAt: Instant): Boolean =
        checking(reminder, occurrence, missedAt)?.let { (start, end) -> now >= start && now < end } == true

    /**
     * Arrived at [now]; [byCheck]: found there by a check, not reported by the WLAN connection or
     * the geofence. The first arrival in the window counts; a snoozed or postponed occurrence keeps
     * its new time unless it waits for the arrival.
     */
    fun decide(reminder: Reminder, occurrence: Occurrence, now: Instant, zone: ZoneId, byCheck: Boolean): ArrivalDecision {
        val arrival = reminder.arrival ?: return ArrivalDecision.Ignore
        if (occurrence.state != OccurrenceState.PLANNED) return ArrivalDecision.Ignore
        if (reminder.schedule == Schedule.OnArrival) return ArrivalDecision.Now
        if (!arrival.hasWindow) {
            // As before 7.29: today's occurrence before its time, or one waiting for the arrival.
            val today = now.atZone(zone).toLocalDate()
            val beforeToday = !occurrence.rangOrMoved && occurrence.currentAt > now && occurrence.currentAt.atZone(zone).toLocalDate() == today
            return if (occurrence.awaitsArrival || beforeToday) ArrivalDecision.Now else ArrivalDecision.Ignore
        }
        if (occurrence.arrivedAt != null) return ArrivalDecision.Ignore
        val afterBuffer = now.plus(Duration.ofMinutes(arrival.bufferMinutes.toLong()))
        if (occurrence.awaitsArrival) return ArrivalDecision.At(afterBuffer)
        if (occurrence.rangOrMoved || now >= occurrence.currentAt) return ArrivalDecision.Ignore
        val start = occurrence.currentAt.minus(Duration.ofMinutes(arrival.leadMinutes.toLong()))
        return when {
            now < start -> ArrivalDecision.TooEarly(start)
            // KRITISCH: Already there when the window starts (Homeoffice): the latest time, not now (F2).
            byCheck && now <= start.plus(START_TOLERANCE) -> ArrivalDecision.At(occurrence.currentAt)
            else -> ArrivalDecision.At(afterBuffer)
        }
    }

    /** One check for the diagnostic log (OZ8): source, result and age of the scan, no names. */
    fun describe(check: PlaceCheck): String =
        "${check.result} source=${check.source ?: "-"} scan=${check.scanAge?.let { "${it.seconds}s" } ?: "-"}"
}
