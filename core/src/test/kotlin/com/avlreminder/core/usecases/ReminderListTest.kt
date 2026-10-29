package com.avlreminder.core.usecases

import com.avlreminder.core.at
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.reminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReminderListTest {
    private val once = Schedule.Once(LocalDateTime.of(2026, 10, 27, 9, 0))
    private val brunnen = reminder().copy(id = 1, title = "katzenbrunnen", createdAt = at(20, 8))
    private val linsen = reminder().copy(id = 2, title = "Kontaktlinsen", createdAt = at(22, 8))
    private val arzt = reminder(once).copy(id = 3, title = "Arzt anrufen", createdAt = at(24, 8))
    private val pause = reminder().copy(id = 4, title = "Wartung", active = false)
    private val alt = reminder().copy(id = 5, title = "Alt", active = false, archivedAt = at(25, 8))
    private val post = reminder(Schedule.OnArrival).copy(id = 6, title = "Post", createdAt = at(25, 9))
    private val open = mapOf(
        1L to Occurrence(reminderId = 1, dueAt = at(27, 18)),
        2L to Occurrence(reminderId = 2, dueAt = at(26, 18)),
        4L to Occurrence(reminderId = 4, dueAt = at(26, 7)),
        6L to Occurrence(reminderId = 6, dueAt = at(25, 9)),
    )
    private val all = listOf(brunnen, linsen, arzt, pause, alt, post)

    private fun titles(l: List<com.avlreminder.core.model.Reminder>) = l.map { it.title }

    @Test
    fun `finished one-offs and archived reminders are closed, paused ones apart`() {
        val g = ReminderList.group(all, open, ReminderSort.NAME)
        assertEquals(listOf("katzenbrunnen", "Kontaktlinsen", "Post"), titles(g.active))
        assertEquals(listOf("Wartung"), titles(g.paused))
        assertEquals(listOf("Alt", "Arzt anrufen"), titles(g.closed))
    }

    @Test
    fun `next due first, reminders without a time at the end`() {
        val g = ReminderList.group(all + linsen.copy(id = 7, title = "Ohne"), open, ReminderSort.NEXT_DUE)
        assertEquals(listOf("Post", "Kontaktlinsen", "katzenbrunnen", "Ohne"), titles(g.active))
    }

    @Test
    fun `newest first`() {
        assertEquals(listOf("Post", "Kontaktlinsen", "katzenbrunnen"), titles(ReminderList.group(all, open, ReminderSort.NEWEST).active))
    }

    @Test
    fun `only one-offs without an open occurrence are finished`() {
        assertTrue(ReminderList.finished(arzt, null))
        assertTrue(ReminderList.finished(post, null))
        assertFalse(ReminderList.finished(post, open[6L]))
        assertFalse(ReminderList.finished(brunnen, null))
    }
}
