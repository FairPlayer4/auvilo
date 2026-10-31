package com.avlreminder.core.usecases

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.WeekStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DigestTest {
    private val brunnen = reminder().copy(id = 1)
    private val training = reminder().copy(id = 2, title = "Training")
    private val post = reminder().copy(id = 3, title = "Post")

    private fun entry(r: com.avlreminder.core.model.Reminder, o: Occurrence) = Entry(r, o)

    @Test
    fun `morning lists what is still due today`() {
        val today = Today(
            dueNow = listOf(entry(post, Occurrence(id = 1, reminderId = 3, dueAt = at(27, 7), state = OccurrenceState.DUE))),
            laterToday = listOf(entry(brunnen, Occurrence(id = 2, reminderId = 1, dueAt = at(27, 18)))),
            resolved = emptyList(), upcoming = emptyList(),
            waiting = listOf(entry(training, Occurrence(id = 3, reminderId = 2, dueAt = at(27, 7)))),
        )
        assertEquals(
            Digest("Heute: 3 Reminder", listOf("jetzt: Post", "18:00 Katzenbrunnen", "zuhause: Training")),
            Digests.morning(today, ZONE),
        )
        assertEquals("Heute steht nichts an", Digests.morning(Today(emptyList(), emptyList(), emptyList(), emptyList()), ZONE).title)
    }

    @Test
    fun `evening looks back, at the weekly goals and on tomorrow`() {
        fun closed(id: Long, r: Resolution) = Occurrence(
            id = id, reminderId = 1, dueAt = at(27, 8), resolvedAt = at(27, 9), resolution = r,
            state = if (r == Resolution.GOAL) OccurrenceState.DONE else OccurrenceState.CLOSED,
        )
        val today = Today(
            dueNow = emptyList(),
            laterToday = listOf(entry(post, Occurrence(id = 5, reminderId = 3, dueAt = at(27, 22)))),
            resolved = listOf(entry(brunnen, closed(1, Resolution.GOAL)), entry(brunnen, closed(2, Resolution.MISSED))),
            upcoming = listOf(
                entry(brunnen, Occurrence(id = 3, reminderId = 1, dueAt = at(28, 18))),
                entry(post, Occurrence(id = 4, reminderId = 3, dueAt = at(29, 18))),
            ),
        )
        val week = WeekStatus(WeeklyGoal(3, 4, 1), done = 3, minimums = 1, offersLeft = 2)
        assertEquals(
            Digest(
                "Heute 1 erledigt, 1 verpasst, 1 offen",
                listOf("Training: 3 von 3–4, Ziel erreicht", "morgen 18:00 Katzenbrunnen"),
            ),
            Digests.evening(today, listOf(training to week), at(27, 20), ZONE),
        )
        assertEquals("0 von 2", Digests.week(WeekStatus(WeeklyGoal(2, 2, 1), 0, 0, 5)))
        assertEquals("0 von 2, knapp", Digests.week(WeekStatus(WeeklyGoal(2, 2, 1), 0, 0, 2)))
    }
}
