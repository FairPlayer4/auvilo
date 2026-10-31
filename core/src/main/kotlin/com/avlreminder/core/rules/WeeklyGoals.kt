package com.avlreminder.core.rules

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Where a reminder with a weekly goal stands in one week. [done] counts goal and minimum
 * (design 12.3, point 3), [minimums] the minimum alone; [offersLeft] the offer days from the
 * day in question to Sunday, that day included.
 */
data class WeekStatus(val goal: WeeklyGoal, val done: Int, val minimums: Int, val offersLeft: Int) {
    val reached: Boolean get() = done >= goal.target
    /** No further offers this week. */
    val full: Boolean get() = done >= goal.max
    val minimumReached: Boolean get() = done >= goal.minimum
    /** The offers left are only just enough for the target, or not even that (point 4). */
    val tight: Boolean get() = !reached && offersLeft <= goal.target - done
}

object WeeklyGoals {
    /** Saturday and Sunday at this time, if the week is still at 0 (point 5). */
    val RESCUE_TIME: LocalTime = LocalTime.of(10, 0)

    /** Offer days per week; null for schedules a weekly goal does not fit. */
    fun offerDays(schedule: Schedule): Int? = when (schedule) {
        is Schedule.Daily -> 7
        is Schedule.Weekdays -> schedule.days.size
        else -> null
    }

    fun isOfferDay(schedule: Schedule, date: LocalDate): Boolean = when (schedule) {
        is Schedule.Daily -> true
        is Schedule.Weekdays -> date.dayOfWeek in schedule.days
        else -> false
    }

    /** The week of [day] from already loaded occurrences of [reminder] (for the screens). */
    fun status(reminder: Reminder, resolved: List<Occurrence>, day: LocalDate, zone: ZoneId): WeekStatus? {
        val goal = reminder.weeklyGoal ?: return null
        val week = Weeks.start(day)
        val own = resolved.filter { o ->
            o.reminderId == reminder.id && o.resolvedAt?.let { Weeks.start(it.atZone(zone).toLocalDate()) } == week
        }
        val minimums = own.count { it.resolution == Resolution.MINIMUM }
        return WeekStatus(goal, own.count { it.resolution == Resolution.GOAL } + minimums, minimums, offersLeft(reminder.schedule, day))
    }

    /** Offer days from [from] to the Sunday of its week, [from] included. */
    fun offersLeft(schedule: Schedule, from: LocalDate): Int {
        val sunday = Weeks.start(from).plusDays(6)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= sunday }.count { isOfferDay(schedule, it) }
    }

    /** A weekend day the schedule does not offer: there the occurrence is a rescue reminder. */
    fun isRescueDay(schedule: Schedule, date: LocalDate): Boolean =
        schedule is Schedule.Weekdays && date.dayOfWeek in WEEKEND && !isOfferDay(schedule, date)

    /**
     * The next rescue time of the week containing [now], strictly after [now] and [previousDueAt];
     * null if there is none left. The caller checks that the minimum is not reached.
     */
    fun nextRescue(schedule: Schedule, now: Instant, zone: ZoneId, previousDueAt: Instant?): Instant? {
        val after = if (previousDueAt != null && previousDueAt > now) previousDueAt else now
        val monday = Weeks.start(now.atZone(zone).toLocalDate())
        return WEEKEND.map { monday.with(TemporalAdjusters.nextOrSame(it)) }
            .filter { isRescueDay(schedule, it) }
            .map { it.atTime(RESCUE_TIME).atZone(zone).toInstant() }
            .firstOrNull { it > after }
    }

    /**
     * How strict an occurrence rings: a rescue as strict as possible, a tight week one step
     * stricter; null once the target is reached (silent, without escalation).
     */
    fun strictness(base: Strictness, status: WeekStatus, rescue: Boolean): Strictness? = when {
        rescue -> Strictness.STRICT
        status.reached -> null
        status.tight -> Strictness.entries[minOf(base.ordinal + 1, Strictness.STRICT.ordinal)]
        else -> base
    }

    private val WEEKEND = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
