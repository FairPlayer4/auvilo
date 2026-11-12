package com.avlreminder.core.rules

import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.severalADay

object Checklists {
    /**
     * Several times a day a checklist runs over the times, one item each (VS1): the items done
     * since the list was last complete. [done] are all items done for the reminder, in order.
     */
    fun round(checklist: List<ChecklistItem>, done: List<ItemDone>): List<ItemDone> {
        val ids = checklist.map { it.id }.toSet()
        var round = mutableListOf<ItemDone>()
        for (d in done) {
            if (d.itemId !in ids || round.any { it.itemId == d.itemId }) continue
            round += d
            if (round.size == ids.size) round = mutableListOf()
        }
        return round
    }

    /**
     * The done items the screens show per occurrence: those of each occurrence, and for the open
     * occurrence of a list over several times a day its [round] over all occurrences instead.
     */
    fun doneByOccurrence(
        reminders: List<Reminder>,
        open: List<Occurrence>,
        resolved: List<Occurrence>,
        done: List<ItemDone>,
    ): Map<Long, List<ItemDone>> {
        val reminderOf = (open + resolved).associate { it.id to it.reminderId }
        val rounds = open.mapNotNull { o ->
            val reminder = reminders.firstOrNull { it.id == o.reminderId }?.takeIf { it.checklist.isNotEmpty() && it.schedule.severalADay }
                ?: return@mapNotNull null
            o.id to round(reminder.checklist, done.filter { reminderOf[it.occurrenceId] == reminder.id })
        }
        return done.groupBy { it.occurrenceId } + rounds
    }
}
