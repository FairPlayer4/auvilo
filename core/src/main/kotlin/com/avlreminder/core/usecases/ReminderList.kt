package com.avlreminder.core.usecases

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule

enum class ReminderSort { NEXT_DUE, NAME, NEWEST }

/** The reminder list in its groups (L4); [closed] is shown folded. */
data class ReminderGroups(
    val active: List<Reminder>,
    val paused: List<Reminder>,
    /** Archived by hand or finished one-offs. */
    val closed: List<Reminder>,
)

object ReminderList {

    /** A one-off ("einmalig", "wenn ich zuhause bin") that already happened. Derived, not stored. */
    fun finished(reminder: Reminder, open: Occurrence?): Boolean =
        open == null && (reminder.schedule is Schedule.Once || reminder.schedule == Schedule.OnArrival)

    fun group(reminders: List<Reminder>, openByReminder: Map<Long, Occurrence>, sort: ReminderSort): ReminderGroups {
        val byName = compareBy<Reminder, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id }
        val order = when (sort) {
            // Without an open occurrence there is no next time: to the end.
            ReminderSort.NEXT_DUE -> compareBy<Reminder, java.time.Instant?>(nullsLast()) { openByReminder[it.id]?.currentAt }.then(byName)
            ReminderSort.NAME -> byName
            ReminderSort.NEWEST -> compareByDescending<Reminder> { it.createdAt }.then(byName)
        }
        val (closed, rest) = reminders.partition { it.archivedAt != null || finished(it, openByReminder[it.id]) }
        val (active, paused) = rest.partition { it.active }
        return ReminderGroups(active.sortedWith(order), paused.sortedWith(order), closed.sortedWith(order))
    }
}
