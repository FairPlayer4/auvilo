package com.avlreminder.core.usecases

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.reminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OverviewTest {
    private val a = reminder().copy(id = 1, title = "A")
    private val b = reminder().copy(id = 2, title = "B")
    private val c = reminder().copy(id = 3, title = "C")
    private val paused = reminder().copy(id = 4, title = "Pause", active = false)
    private val now = at(26, 18, 30)

    private val due = Occurrence(id = 10, reminderId = 1, dueAt = at(26, 18), state = OccurrenceState.DUE)
    private val later = Occurrence(id = 11, reminderId = 2, dueAt = at(26, 21))
    private val tomorrow = Occurrence(id = 12, reminderId = 3, dueAt = at(27, 9))
    private val pausedOcc = Occurrence(id = 13, reminderId = 4, dueAt = at(26, 19))
    private val doneToday = Occurrence(
        id = 14, reminderId = 2, dueAt = at(26, 8), state = OccurrenceState.DONE,
        resolution = Resolution.GOAL, resolvedAt = at(26, 8, 4),
    )
    private val skippedToday = doneToday.copy(id = 15, reminderId = 3, resolution = Resolution.SKIPPED,
        state = OccurrenceState.CLOSED, resolvedAt = at(26, 11, 30))
    private val doneYesterday = doneToday.copy(id = 16, resolvedAt = at(25, 8))
    private val orphan = Occurrence(id = 17, reminderId = 99, dueAt = at(26, 19))

    private val all = listOf(due, later, tomorrow, pausedOcc, doneToday, skippedToday, doneYesterday, orphan)

    @Test
    fun `today groups occurrences into sections`() {
        val t = Overview.today(listOf(a, b, c, paused), all, now, ZONE)
        assertEquals(listOf(10L), t.dueNow.map { it.occurrence.id })
        assertEquals(listOf(11L), t.laterToday.map { it.occurrence.id })
        assertEquals(listOf(15L, 14L), t.resolved.map { it.occurrence.id })
        assertEquals(listOf(12L), t.upcoming.map { it.occurrence.id })
    }

    @Test
    fun `next prefers what is due now`() {
        val t = Overview.today(listOf(a, b, c), all, now, ZONE)
        assertEquals(10L, Overview.next(t)!!.occurrence.id)
        assertEquals(11L, Overview.next(t.copy(dueNow = emptyList()))!!.occurrence.id)
        assertEquals(12L, Overview.next(t.copy(dueNow = emptyList(), laterToday = emptyList()))!!.occurrence.id)
        assertNull(Overview.next(Today(emptyList(), emptyList(), emptyList(), emptyList())))
    }

    @Test
    fun `history groups by day newest first`() {
        val h = Overview.history(listOf(a, b, c), all, ZONE)
        assertEquals(listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 25)), h.map { it.date })
        assertEquals(listOf(15L, 14L), h[0].entries.map { it.occurrence.id })
        assertEquals(listOf(16L), h[1].entries.map { it.occurrence.id })
    }
}
