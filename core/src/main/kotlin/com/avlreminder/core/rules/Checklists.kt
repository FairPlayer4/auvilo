package com.avlreminder.core.rules

import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ItemDone

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
}
