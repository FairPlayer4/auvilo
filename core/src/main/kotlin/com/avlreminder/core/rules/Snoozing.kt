package com.avlreminder.core.rules

import com.avlreminder.core.model.AtTimes
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Training schiebst du oft – lieber um 19:30?" (NT2): a reminder planned stricter than one keeps
 * up with. [time] is when it was really done, if that differs; [relax] offers a lower strictness.
 */
data class SnoozeHint(val reminder: Reminder, val time: LocalTime?, val relax: Strictness?)

/** What the hint card was answered with. */
enum class SnoozeHintChoice { TIME, RELAX, DISMISS }

object Snoozing {
    /** This many resolved occurrences in a row … */
    const val IN_A_ROW = 3
    /** … each snoozed at least this often; or one of them this often. */
    const val EACH = 2
    const val ONE = 4
    /** "Nein danke" keeps the card away this long. */
    const val QUIET_DAYS = 14L

    /** Snoozed again and again lately? [recent]: resolved occurrences with their snooze counts, newest first. */
    fun often(recent: List<Pair<Occurrence, Int>>): Boolean {
        val last = recent.take(IN_A_ROW)
        if (last.size < IN_A_ROW) return false
        return last.all { it.second >= EACH } || last.any { it.second >= ONE }
    }

    /**
     * The hint for [reminder], or null. Only occurrences due after [quietUntil] count, so a change
     * or "Nein danke" starts the count anew. One time a day only: several times have no single
     * time to move.
     */
    fun hint(reminder: Reminder, recent: List<Pair<Occurrence, Int>>, quietUntil: Instant?, now: Instant, zone: ZoneId): SnoozeHint? {
        val schedule = reminder.schedule
        val current = when (schedule) {
            is AtTimes -> schedule.time.takeIf { !schedule.severalADay }
            is Schedule.AfterDone -> schedule.time
            else -> null
        } ?: return null
        if (!reminder.active || reminder.prepFor != null) return null
        if (quietUntil != null && now < quietUntil) return null
        val counted = recent.filter { quietUntil == null || it.first.dueAt >= quietUntil }
        if (!often(counted)) return null
        val time = doneAt(counted.take(IN_A_ROW).map { it.first }, zone)?.takeIf { it > current }
        val relax = lower(reminder.strictness)
        return if (time == null && relax == null) null else SnoozeHint(reminder, time, relax)
    }

    /** The median time of day the occurrences were done, rounded to a quarter hour; null if none was done. */
    fun doneAt(occurrences: List<Occurrence>, zone: ZoneId): LocalTime? {
        val minutes = occurrences.filter { it.state == OccurrenceState.DONE && it.resolvedAt != null }
            .map { it.resolvedAt!!.atZone(zone).toLocalTime().let { t -> t.hour * 60 + t.minute } }
            .sorted()
        if (minutes.isEmpty()) return null
        val median = minutes[minutes.size / 2]
        val rounded = minOf((median + 7) / 15 * 15, 23 * 60 + 45)
        return LocalTime.of(rounded / 60, rounded % 60)
    }

    fun lower(s: Strictness): Strictness? = when (s) {
        Strictness.STRICT -> Strictness.NORMAL
        Strictness.NORMAL -> Strictness.RELAXED
        Strictness.RELAXED -> null
    }
}
