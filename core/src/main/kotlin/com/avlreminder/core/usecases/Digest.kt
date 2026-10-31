package com.avlreminder.core.usecases

import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.rules.WeekStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A silent notification with the day at a glance (Anf. O1, design 12.3 point 8). */
data class Digest(val title: String, val lines: List<String>)

object Digests {
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    /** What is still due today. */
    fun morning(today: Today, zone: ZoneId): Digest {
        val lines = today.dueNow.map { "jetzt: ${it.reminder.title}" } +
            today.laterToday.map { "${time.format(it.occurrence.currentAt.atZone(zone))} ${it.reminder.title}" } +
            today.waiting.map { "zuhause: ${it.reminder.title}" }
        val title = when (val n = lines.size) {
            0 -> "Heute steht nichts an"
            1 -> "Heute: 1 Reminder"
            else -> "Heute: $n Reminder"
        }
        return Digest(title, lines)
    }

    /** Looking back on today, the weekly goals and what comes tomorrow. */
    fun evening(today: Today, weeks: List<Pair<Reminder, WeekStatus>>, now: Instant, zone: ZoneId): Digest {
        val done = today.resolved.count { it.occurrence.state == OccurrenceState.DONE }
        val missed = today.resolved.count { it.occurrence.resolution == Resolution.MISSED }
        val open = today.dueNow.size + today.laterToday.size
        val title = buildList {
            add("Heute $done erledigt")
            if (missed > 0) add("$missed verpasst")
            if (open > 0) add("$open offen")
        }.joinToString(", ")
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        val next = today.upcoming.filter { it.occurrence.currentAt.atZone(zone).toLocalDate() == tomorrow }
        val lines = weeks.map { (r, w) -> "${r.title}: ${week(w)}" } +
            next.map { "morgen ${time.format(it.occurrence.currentAt.atZone(zone))} ${it.reminder.title}" }
        return Digest(title, lines)
    }

    /** "2 von 3–4, Minimum erreicht". */
    fun week(w: WeekStatus): String {
        val target = if (w.goal.max > w.goal.target) "${w.goal.target}–${w.goal.max}" else "${w.goal.target}"
        val state = when {
            w.reached -> "Ziel erreicht"
            w.minimumReached -> "Minimum erreicht"
            w.tight -> "knapp"
            else -> null
        }
        return listOfNotNull("${w.done} von $target", state).joinToString(", ")
    }
}
