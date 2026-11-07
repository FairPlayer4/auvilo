package com.avlreminder.core.usecases

import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.rules.ScheduleRules
import com.avlreminder.core.rules.WeekStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A silent notification with the day at a glance (Anf. O1, design 12.3 point 8). */
data class Digest(val title: String, val lines: List<String>)

object Digests {
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    /** What is still due today; several times a day each time still to come (VS1). */
    fun morning(today: Today, zone: ZoneId): Digest {
        val day = { e: Entry -> e.occurrence.currentAt.atZone(zone).toLocalDate() }
        val later = today.dueNow.flatMap { e -> times(e, day(e), zone).drop(1).map { it to e } } +
            today.laterToday.flatMap { e -> times(e, day(e), zone).map { it to e } }
        val lines = today.dueNow.map { "jetzt: ${it.reminder.title}" } +
            today.overdue.map { "überfällig: ${it.reminder.title}" } +
            later.sortedBy { it.first }.map { (at, e) -> "${time.format(at.atZone(zone))} ${e.reminder.title}" } +
            today.waiting.map { "zuhause: ${it.reminder.title}" }
        val reminders = (today.dueNow + today.overdue + today.laterToday + today.waiting).map { it.reminder.id }.distinct()
        val title = when (val n = reminders.size) {
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
        val next = (today.dueNow + today.laterToday + today.upcoming).flatMap { e -> times(e, tomorrow, zone).map { it to e } }
        val lines = weeks.map { (r, w) -> "${r.title}: ${week(w)}" } +
            next.sortedBy { it.first }.map { (at, e) -> "morgen ${time.format(at.atZone(zone))} ${e.reminder.title}" }
        return Digest(title, lines)
    }

    /**
     * The times of [e] on [day], from its open occurrence on: once a day just that one, several
     * times a day also the following ones from the schedule (VS1).
     */
    private fun times(e: Entry, day: LocalDate, zone: ZoneId): List<Instant> =
        generateSequence(e.occurrence.currentAt) { ScheduleRules.nextTime(e.reminder.schedule, it, zone) }
            .takeWhile { it.atZone(zone).toLocalDate() <= day }
            .filter { it.atZone(zone).toLocalDate() == day }
            .toList()

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
