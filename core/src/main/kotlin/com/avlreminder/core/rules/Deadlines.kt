package com.avlreminder.core.rules

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Deadlines with windows (T1d, design 12.4 points 9–10). */
object Deadlines {

    fun dueAt(d: Schedule.Deadline, zone: ZoneId): Instant = d.due.atZone(zone).toInstant()

    private fun isWindowDay(d: Schedule.Deadline, date: LocalDate) = date.dayOfWeek in d.days

    private fun start(d: Schedule.Deadline, date: LocalDate, zone: ZoneId) = date.atTime(d.from).atZone(zone).toInstant()

    private fun end(d: Schedule.Deadline, date: LocalDate, zone: ZoneId) = date.atTime(d.until).atZone(zone).toInstant()

    /** Whether [at] lies inside a window. */
    fun inWindow(d: Schedule.Deadline, at: Instant, zone: ZoneId): Boolean {
        val local = at.atZone(zone)
        val time = local.toLocalTime()
        return isWindowDay(d, local.toLocalDate()) && time >= d.from && time < d.until
    }

    /** The first window start strictly after [after]. */
    fun nextWindowStart(d: Schedule.Deadline, after: Instant, zone: ZoneId): Instant {
        var date = after.atZone(zone).toLocalDate()
        while (true) {
            if (isWindowDay(d, date)) start(d, date, zone).let { if (it > after) return it }
            date = date.plusDays(1)
        }
    }

    /** The end of the window that contains [at], or of the next one. */
    fun windowEnd(d: Schedule.Deadline, at: Instant, zone: ZoneId): Instant =
        if (inWindow(d, at, zone)) end(d, at.atZone(zone).toLocalDate(), zone)
        else end(d, nextWindowStart(d, at, zone).atZone(zone).toLocalDate(), zone)

    /**
     * When a new deadline reminder rings first: inside a window one interval from now (not right
     * after saving), else at the start of the next window.
     */
    fun firstDue(d: Schedule.Deadline, now: Instant, zone: ZoneId): Instant {
        if (inWindow(d, now, zone)) {
            val next = now.plusSeconds(d.intervalMinutes * 60L)
            if (next < windowEnd(d, now, zone)) return next
        }
        return nextWindowStart(d, now, zone)
    }

    /** Whether a window starts before the deadline, counted from [now]. */
    fun hasWindowBefore(d: Schedule.Deadline, now: Instant, zone: ZoneId): Boolean = firstDue(d, now, zone) < dueAt(d, zone)

    /** The last day with a window that starts before the deadline; null if there is none. */
    fun lastWindowDay(d: Schedule.Deadline, zone: ZoneId): LocalDate? {
        val due = dueAt(d, zone)
        return generateSequence(d.due.toLocalDate()) { it.minusDays(1) }.take(8)
            .firstOrNull { isWindowDay(d, it) && start(d, it, zone) < due }
    }

    /** An occurrence planned after the deadline: the deadline was missed, it is still to be done. */
    fun isOverdue(d: Schedule.Deadline, occurrence: Occurrence, zone: ZoneId): Boolean = occurrence.dueAt >= dueAt(d, zone)

    /**
     * "Auf morgen": the first window start on a later day than [now]. Before the deadline it must
     * still start before it; null if it does not.
     */
    fun tomorrow(d: Schedule.Deadline, now: Instant, zone: ZoneId, overdue: Boolean): Instant? {
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val next = nextWindowStart(d, midnight.minusMillis(1), zone)
        return next.takeIf { overdue || it < dueAt(d, zone) }
    }
}
