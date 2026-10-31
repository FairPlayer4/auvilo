package com.avlreminder.core.rules

import com.avlreminder.core.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ScheduleRules {

    /**
     * When the next occurrence is due.
     *
     * @param previousDueAt due time of the last occurrence; null if there was none yet.
     * @param lastDoneAt when the reminder was last actually done (for [Schedule.AfterDone]).
     * @param previousWasDone whether the last occurrence was done (not skipped or missed).
     * @return null if there is no further occurrence (a one-off reminder that already happened).
     */
    fun nextDue(
        schedule: Schedule,
        now: Instant,
        zone: ZoneId,
        previousDueAt: Instant? = null,
        lastDoneAt: Instant? = null,
        previousWasDone: Boolean = true,
    ): Instant? {
        // Never create a backlog: the next occurrence lies after both now and the previous one.
        val after = if (previousDueAt != null && previousDueAt > now) previousDueAt else now
        return when (schedule) {
            // Due again only when its time was changed after the last occurrence.
            is Schedule.Once -> {
                val at = schedule.at.atZone(zone).toInstant()
                if (previousDueAt == null || at != previousDueAt) at else null
            }

            // Waits for the arrival; the due time only orders it.
            Schedule.OnArrival -> if (previousDueAt == null) now else null

            is Schedule.Daily -> nextAtTime(schedule.time, after, zone) { true }

            is Schedule.Weekdays -> nextAtTime(schedule.time, after, zone) { it.dayOfWeek in schedule.days }

            // Counts from the actual completion, even when it came before the planned time.
            is Schedule.AfterDone -> {
                // Skipped or missed means not done: remind again the next possible day.
                if (previousDueAt != null && !previousWasDone) return nextAtTime(schedule.time, now, zone) { true }
                // Never done yet: the chosen first day, or the next possible time if that has passed.
                if (lastDoneAt == null && schedule.firstOn != null) {
                    val first = schedule.firstOn.atTime(schedule.time).atZone(zone).toInstant()
                    return if (first > now) first else nextAtTime(schedule.time, now, zone) { true }
                }
                val base = (lastDoneAt ?: now).atZone(zone).toLocalDate()
                val planned = base.plusDays(schedule.intervalDays.toLong()).atTime(schedule.time).atZone(zone).toInstant()
                if (planned > now) planned else nextAtTime(schedule.time, now, zone) { true }
            }
        }
    }

    /** First instant strictly after [after] at [time] on a day accepted by [dayOk]. */
    private fun nextAtTime(time: LocalTime, after: Instant, zone: ZoneId, dayOk: (LocalDate) -> Boolean): Instant {
        var date = after.atZone(zone).toLocalDate()
        while (true) {
            if (dayOk(date)) {
                val candidate = date.atTime(time).atZone(zone).toInstant()
                if (candidate > after) return candidate
            }
            date = date.plusDays(1)
        }
    }
}
