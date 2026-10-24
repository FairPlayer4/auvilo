package com.avlreminder.core.usecases

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Entry(val reminder: Reminder, val occurrence: Occurrence)

/** The "Heute" screen. */
data class Today(
    /** Fired and waiting for a reaction. */
    val dueNow: List<Entry>,
    /** Planned for the rest of today. */
    val laterToday: List<Entry>,
    /** Resolved today, newest first. */
    val resolved: List<Entry>,
    /** Planned for after today, soonest first (for the reminder list and the widget). */
    val upcoming: List<Entry>,
)

/** One day in the history. */
data class HistoryDay(val date: LocalDate, val entries: List<Entry>)

object Overview {

    fun today(reminders: List<Reminder>, occurrences: List<Occurrence>, now: Instant, zone: ZoneId): Today {
        val byId = reminders.associateBy { it.id }
        val entries = occurrences.mapNotNull { o -> byId[o.reminderId]?.let { Entry(it, o) } }
        val today = now.atZone(zone).toLocalDate()
        fun Instant.isToday() = atZone(zone).toLocalDate() == today

        val open = entries.filter { it.occurrence.isOpen && it.reminder.active }
        val fired = open.filter { it.occurrence.state != OccurrenceState.PLANNED }
        val planned = open.filter { it.occurrence.state == OccurrenceState.PLANNED }
        return Today(
            dueNow = fired.sortedBy { it.occurrence.currentAt },
            laterToday = planned.filter { it.occurrence.currentAt.isToday() }.sortedBy { it.occurrence.currentAt },
            resolved = entries
                .filter { !it.occurrence.isOpen && it.occurrence.resolvedAt?.isToday() == true }
                .sortedByDescending { it.occurrence.resolvedAt },
            upcoming = planned.filter { !it.occurrence.currentAt.isToday() }.sortedBy { it.occurrence.currentAt },
        )
    }

    /** The next thing that will ring, for the widget. */
    fun next(today: Today): Entry? = today.dueNow.firstOrNull() ?: today.laterToday.firstOrNull() ?: today.upcoming.firstOrNull()

    /** Resolved occurrences grouped by day, newest day and entry first. */
    fun history(reminders: List<Reminder>, resolved: List<Occurrence>, zone: ZoneId): List<HistoryDay> {
        val byId = reminders.associateBy { it.id }
        return resolved
            .filter { !it.isOpen && it.resolvedAt != null }
            .mapNotNull { o -> byId[o.reminderId]?.let { Entry(it, o) } }
            .groupBy { it.occurrence.resolvedAt!!.atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) -> HistoryDay(date, list.sortedByDescending { it.occurrence.resolvedAt }) }
    }
}
