package com.avlreminder.core.rules

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How occurrences ended in one period (Anf. P1); [early] were done before the reminder (V5). */
data class Counts(
    val goal: Int = 0,
    val minimum: Int = 0,
    val noProof: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
    val dropped: Int = 0,
    val early: Int = 0,
) {
    val done: Int get() = goal + minimum
}

enum class StreakUnit { DAYS, WEEKS, TIMES }

/** [current] includes a running week that already reached its minimum. */
data class Streak(val unit: StreakUnit, val current: Int, val best: Int)

data class ReminderStats(
    val week: Counts,
    val month: Counts,
    val year: Counts,
    val total: Counts,
    /** null for one-off reminders. */
    val streak: Streak?,
)

object Stats {

    /** [resolved]: the reminder's resolved occurrences, in any order. */
    fun of(reminder: Reminder, resolved: List<Occurrence>, now: Instant, zone: ZoneId): ReminderStats {
        val own = resolved.filter { it.reminderId == reminder.id && !it.isOpen && it.resolvedAt != null }
        val today = now.atZone(zone).toLocalDate()
        fun since(from: LocalDate) = counts(own.filter { !it.resolvedAt!!.atZone(zone).toLocalDate().isBefore(from) })
        return ReminderStats(
            week = since(Weeks.start(today)),
            month = since(today.withDayOfMonth(1)),
            year = since(today.withDayOfYear(1)),
            total = counts(own),
            streak = streak(reminder, own, now, zone),
        )
    }

    fun counts(occurrences: List<Occurrence>): Counts = Counts(
        goal = occurrences.count { it.resolution == Resolution.GOAL },
        minimum = occurrences.count { it.resolution == Resolution.MINIMUM },
        noProof = occurrences.count { it.resolution == Resolution.NO_PROOF },
        skipped = occurrences.count { it.resolution == Resolution.SKIPPED },
        missed = occurrences.count { it.resolution == Resolution.MISSED },
        dropped = occurrences.count { it.resolution == Resolution.DROPPED },
        early = occurrences.count { it.state == OccurrenceState.DONE && it.resolvedAt!! < it.dueAt },
    )

    /**
     * Serien (design 12.3, point 6; Anf. P1b). Goal and minimum extend the series; skipped,
     * dropped and done without proof keep it. A miss uses the joker of its period (week for days,
     * month for weeks and times), a second one in the same period ends the series.
     */
    fun streak(reminder: Reminder, resolved: List<Occurrence>, now: Instant, zone: ZoneId): Streak? {
        val goal = reminder.weeklyGoal
        return when {
            goal != null -> weekStreak(reminder, resolved, now, zone)
            reminder.schedule is Schedule.Daily || reminder.schedule is Schedule.Weekdays ->
                run(StreakUnit.DAYS, resolved.sortedBy { it.dueAt }.map { o ->
                    val day = o.dueAt.atZone(zone).toLocalDate()
                    Step(outcome(o.resolution), Weeks.start(day))
                })
            reminder.schedule is Schedule.AfterDone ->
                run(StreakUnit.TIMES, resolved.sortedBy { it.dueAt }.map { o ->
                    val due = o.dueAt.atZone(zone).toLocalDate()
                    val late = o.state == OccurrenceState.DONE &&
                        ChronoUnit.DAYS.between(due, o.resolvedAt!!.atZone(zone).toLocalDate()) > 1
                    Step(if (late) Outcome.MISS else outcome(o.resolution), due.withDayOfMonth(1))
                })
            else -> null
        }
    }

    private enum class Outcome { COUNT, KEEP, MISS }

    /** One unit of a series and the period its joker belongs to. */
    private data class Step(val outcome: Outcome, val period: LocalDate)

    private fun outcome(resolution: Resolution?) = when (resolution) {
        Resolution.GOAL, Resolution.MINIMUM -> Outcome.COUNT
        Resolution.MISSED -> Outcome.MISS
        else -> Outcome.KEEP
    }

    /** Weeks with the minimum count; a week below it with a miss is a miss, otherwise neutral. */
    private fun weekStreak(reminder: Reminder, resolved: List<Occurrence>, now: Instant, zone: ZoneId): Streak {
        val goal = reminder.weeklyGoal!!
        val thisWeek = Weeks.start(now.atZone(zone).toLocalDate())
        val byWeek = resolved.groupBy { Weeks.start(it.resolvedAt!!.atZone(zone).toLocalDate()) }
        val steps = byWeek.keys.sorted().mapNotNull { week ->
            val list = byWeek.getValue(week)
            val done = list.count { it.resolution == Resolution.GOAL || it.resolution == Resolution.MINIMUM }
            when {
                done >= goal.minimum -> Step(Outcome.COUNT, week.withDayOfMonth(1))
                // The running week can still make it.
                week == thisWeek -> null
                list.any { it.resolution == Resolution.MISSED } -> Step(Outcome.MISS, week.withDayOfMonth(1))
                else -> Step(Outcome.KEEP, week.withDayOfMonth(1))
            }
        }
        return run(StreakUnit.WEEKS, steps)
    }

    private fun run(unit: StreakUnit, steps: List<Step>): Streak {
        var current = 0
        var best = 0
        var jokerUsedIn: LocalDate? = null
        for (step in steps) {
            when (step.outcome) {
                Outcome.COUNT -> current++
                Outcome.KEEP -> {}
                Outcome.MISS -> if (jokerUsedIn != step.period) jokerUsedIn = step.period else current = 0
            }
            best = maxOf(best, current)
        }
        return Streak(unit, current, best)
    }
}
