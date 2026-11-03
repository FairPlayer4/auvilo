package com.avlreminder.core.rules

import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.severalADay
import java.time.Duration
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
     * @param previousMissed whether the last occurrence was missed (a deadline then stays to be done).
     * @return null if there is no further occurrence (a one-off reminder that already happened).
     */
    fun nextDue(
        schedule: Schedule,
        now: Instant,
        zone: ZoneId,
        previousDueAt: Instant? = null,
        lastDoneAt: Instant? = null,
        previousWasDone: Boolean = true,
        previousMissed: Boolean = false,
    ): Instant? {
        // Never create a backlog: the next occurrence lies after both now and the previous one.
        // Several a day: a time missed because the next one just started is that next one (VS1).
        val after = when {
            previousDueAt == null -> now
            schedule.severalADay -> maxOf(previousDueAt, now.minus(SLOT_TOLERANCE))
            previousDueAt > now -> previousDueAt
            else -> now
        }
        return when (schedule) {
            // Due again only when its time was changed after the last occurrence.
            is Schedule.Once -> {
                val at = schedule.at.atZone(zone).toInstant()
                if (previousDueAt == null || at != previousDueAt) at else null
            }

            // Waits for the arrival; the due time only orders it.
            Schedule.OnArrival -> if (previousDueAt == null) now else null

            // Once; a missed deadline keeps ringing in the windows until done or discarded.
            is Schedule.Deadline -> when {
                previousDueAt == null -> Deadlines.firstDue(schedule, now, zone)
                previousMissed -> Deadlines.nextWindowStart(schedule, now, zone)
                else -> null
            }

            is Schedule.Daily -> nextAtTimes(schedule.times, after, zone) { true }

            is Schedule.Weekdays -> nextAtTimes(schedule.times, after, zone) { it.dayOfWeek in schedule.days }

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

    /**
     * The time after the one due at [dueAt] for a reminder due several times a day; null for one
     * time a day. Snoozing and postponing must stay before it, and it is missed when it comes (VS1).
     */
    fun nextTime(schedule: Schedule, dueAt: Instant, zone: ZoneId): Instant? = when {
        !schedule.severalADay -> null
        schedule is Schedule.Daily -> nextAtTimes(schedule.times, dueAt, zone) { true }
        schedule is Schedule.Weekdays -> nextAtTimes(schedule.times, dueAt, zone) { it.dayOfWeek in schedule.days }
        else -> null
    }

    /** A time missed because the next one started is still found when the alarm comes a bit late. */
    private val SLOT_TOLERANCE: Duration = Duration.ofMinutes(2)

    /** First instant strictly after [after] at [time] on a day accepted by [dayOk]. */
    private fun nextAtTime(time: LocalTime, after: Instant, zone: ZoneId, dayOk: (LocalDate) -> Boolean): Instant =
        nextAtTimes(listOf(time), after, zone, dayOk)

    /** First instant strictly after [after] at one of [times] on a day accepted by [dayOk]. */
    private fun nextAtTimes(times: List<LocalTime>, after: Instant, zone: ZoneId, dayOk: (LocalDate) -> Boolean): Instant {
        var date = after.atZone(zone).toLocalDate()
        while (true) {
            if (dayOk(date)) {
                // A time in the gap of the clock change moves behind later ones: sort again.
                times.map { date.atTime(it).atZone(zone).toInstant() }.sorted().firstOrNull { it > after }?.let { return it }
            }
            date = date.plusDays(1)
        }
    }
}
