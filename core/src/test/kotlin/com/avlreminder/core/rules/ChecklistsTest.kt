package com.avlreminder.core.rules

import com.avlreminder.core.at
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** Checklists over several times a day (VS1): which items the screens show as done. */
class ChecklistsTest {
    private val items = listOf(ChecklistItem(1, "Wohnzimmer"), ChecklistItem(2, "Küche"), ChecklistItem(3, "Bad"))
    private val threeTimes = reminder(Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0))))
        .copy(id = 1, checklist = items)
    private val once = reminder().copy(id = 2, checklist = listOf(ChecklistItem(4, "Futter"), ChecklistItem(5, "Wasser")))
    private var minute = 0L

    private fun done(occurrence: Long, item: Long) = ItemDone(occurrenceId = occurrence, itemId = item, doneAt = at(26, 8).plusSeconds(60 * minute++))
    private fun occurrence(id: Long, reminder: Long, state: OccurrenceState) = Occurrence(id = id, reminderId = reminder, dueAt = at(26, 8), state = state)

    @Test
    fun `a round starts again once every item was done, removed items do not count`() {
        val all = listOf(done(10, 1), done(10, 2), done(10, 3), done(10, 1), done(11, 99), done(11, 2))
        assertEquals(listOf(all[3], all[5]), Checklists.round(items, all))
        assertEquals(emptyList<ItemDone>(), Checklists.round(items, all.take(3)))
    }

    @Test
    fun `the open occurrence of a list over several times shows its round, the others their own items`() {
        val day1 = listOf(done(10, 1), done(10, 2), done(10, 3), done(10, 1))
        val today = done(11, 2)
        val futter = done(20, 4)
        val shown = Checklists.doneByOccurrence(
            reminders = listOf(threeTimes, once),
            open = listOf(occurrence(11, 1, OccurrenceState.DUE), occurrence(20, 2, OccurrenceState.DUE)),
            resolved = listOf(occurrence(10, 1, OccurrenceState.DONE)),
            done = day1 + today + futter,
        )
        // Item 1 from the earlier time and item 2 from now are this round; 3 is still open.
        assertEquals(listOf(day1[3], today), shown[11])
        assertEquals(day1, shown[10])
        // Once a day: only what was done in this occurrence.
        assertEquals(listOf(futter), shown[20])
    }
}
