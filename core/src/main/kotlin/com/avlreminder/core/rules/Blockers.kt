package com.avlreminder.core.rules

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Skip reasons the app sets itself; they say nothing about whether it got stuck. */
object SkipReasons {
    const val GOAL_REACHED = "Wochenziel erreicht"
    const val PAUSED = "pausiert"
    const val SCHEDULE_CHANGED = "Zeitplan geändert"
    /** An overdue deadline that is no longer to be done. */
    const val DISCARDED = "verworfen"
    /** Left open while all alarms were paused by hand (PS7). */
    const val EMERGENCY = "Notaus"

    val SYSTEM = setOf(GOAL_REACHED, PAUSED, SCHEDULE_CHANGED, DISCARDED, EMERGENCY)
}

/** Blockers as tasks (S5), solved as the minimum (S7) and asking when it gets stuck (S8). */
object Blockers {
    /** Preparation tasks are suggested for the evening before at this time. */
    val TASK_TIME: LocalTime = LocalTime.of(19, 0)
    /** Short snoozes in one occurrence that count as one postponement. */
    const val SNOOZES_AS_POSTPONE = 3

    /** The answer to "was fehlt?" that is a blocker; null if nothing is missing or not known yet. */
    fun fromReady(answer: PrepAnswer): String? =
        answer.text?.trim()?.takeIf { answer.question == Prep.READY && !answer.unknown && it.isNotEmpty() && it != Prep.READY_YES }

    /**
     * When to do a preparation task: the evening before [nextDue]; if that has passed, the next
     * full hour.
     */
    fun suggestTaskTime(nextDue: Instant?, now: Instant, zone: ZoneId): LocalDateTime {
        val evening = nextDue?.atZone(zone)?.toLocalDate()?.minusDays(1)?.atTime(TASK_TIME)
        if (evening != null && evening.atZone(zone).toInstant() > now) return evening
        return now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
    }

    /** A blocker solved after [since] (the last resolved occurrence, or the reminder's creation). */
    fun solvedSince(blockers: List<Blocker>, since: Instant): Boolean = blockers.any { b -> b.solvedAt?.let { it > since } == true }

    private enum class Mark { STUCK, NEUTRAL, DONE }

    private fun mark(o: Occurrence, snoozes: Int): Mark = when {
        o.state == OccurrenceState.DONE -> Mark.DONE
        o.resolution == Resolution.MISSED -> Mark.STUCK
        o.resolution == Resolution.SKIPPED && o.note !in SkipReasons.SYSTEM -> Mark.STUCK
        o.postponeCount > 0 || snoozes >= SNOOZES_AS_POSTPONE -> Mark.STUCK
        else -> Mark.NEUTRAL
    }

    /**
     * How many occurrences in a row got stuck (postponed, skipped, missed; three snoozes count as
     * one postponement), newest first; [occurrences] with their snooze counts, the open one
     * first. Neutral ones are passed over, a completion ends the streak.
     */
    fun stuckStreak(occurrences: List<Pair<Occurrence, Int>>): Int {
        var streak = 0
        for ((o, snoozes) in occurrences) {
            when (mark(o, snoozes)) {
                Mark.STUCK -> streak++
                Mark.NEUTRAL -> {}
                Mark.DONE -> return streak
            }
        }
        return streak
    }
}

/** The silent notice before a reminder (T6, S6). */
object PreNotices {
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    /** When to show it for a planned occurrence; null if the reminder has none. */
    fun at(reminder: Reminder, occurrence: Occurrence, zone: ZoneId): Instant? {
        val notice = reminder.preNotice ?: return null
        if (occurrence.state != OccurrenceState.PLANNED || reminder.schedule == Schedule.OnArrival) return null
        return when (notice) {
            // Moved in advance, it moves along (Anf. 7.32 VB5).
            is PreNotice.Evening -> occurrence.plannedAt.atZone(zone).toLocalDate().minusDays(1).atTime(notice.time).atZone(zone).toInstant()
            is PreNotice.Before -> occurrence.plannedAt.minusSeconds(notice.minutes * 60L)
        }
    }

    /** "Morgen 08:00: Training" with the first step and the open blockers. */
    fun text(reminder: Reminder, occurrence: Occurrence, firstStep: String?, blockers: List<Blocker>, now: Instant, zone: ZoneId): Pair<String, List<String>> {
        val due = occurrence.plannedAt.atZone(zone)
        val day = when (due.toLocalDate()) {
            now.atZone(zone).toLocalDate() -> "Heute"
            now.atZone(zone).toLocalDate().plusDays(1) -> "Morgen"
            else -> due.toLocalDate().toString()
        }
        val lines = listOfNotNull(firstStep?.takeIf { it.isNotBlank() }?.let { "Erster Schritt: $it" }) +
            blockers.filter { it.isOpen }.map { "Noch offen: ${it.text}" }
        return "$day ${time.format(due)}: ${reminder.title}" to lines
    }
}
