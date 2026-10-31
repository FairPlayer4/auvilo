package com.avlreminder.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.alarm.OverviewNotifications
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.ReminderSort
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.OverviewTimes
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.ScheduleKind
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** V1.2b: weekly goal in the form, "Diese Woche" on Today, statistics and the overview setting. */
@RunWith(AndroidJUnit4::class)
class WeeklyGoalUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-29T13:00:00Z") // Thursday 14:00

    private class FakeActions : AppActions {
        val saved = mutableListOf<Reminder>()
        var chosen: OverviewTimes? = null
        override fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) {
            saved += reminder
            onResult(SaveResult.Saved(reminder.id))
        }
        override fun delete(reminderId: Long) {}
        override fun setActive(reminderId: Long, active: Boolean) {}
        override fun setTheme(mode: ThemeMode) {}
        override fun setAlert(settings: AlertSettings) {}
        override fun openAlarm(occurrenceId: Long) {}
        override fun completeEarly(occurrenceId: Long) {}
        override fun play(path: String) {}
        override fun setReminderSort(sort: ReminderSort) {}
        override fun setArchived(reminderId: Long, archived: Boolean) {}
        override fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) {}
        override fun updateSuggestion(suggestion: SuggestionEntity) {}
        override fun setOverview(times: OverviewTimes) { chosen = times }
    }

    private val training = Reminder(
        id = 1, title = "Training", schedule = Schedule.Daily(LocalTime.of(19, 0)), createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        weeklyGoal = WeeklyGoal(target = 3, max = 4, minimum = 1),
    )

    private fun done(id: Long, day: Int) = LocalDate.of(2026, 10, day).atTime(19, 30).atZone(zone).toInstant().let {
        Occurrence(id = id, reminderId = 1, dueAt = it, state = OccurrenceState.DONE, resolvedAt = it, resolution = Resolution.GOAL)
    }

    // Monday and Tuesday done: 2 of 3–4.
    private fun state(reminder: Reminder = training) = AppState(
        today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        reminders = listOf(reminder),
        openByReminder = emptyMap(),
        history = listOf(
            HistoryDay(LocalDate.of(2026, 10, 27), listOf(Entry(reminder, done(11, 27)))),
            HistoryDay(LocalDate.of(2026, 10, 26), listOf(Entry(reminder, done(10, 26)))),
        ),
        proofs = emptyMap(), theme = ThemeMode.SYSTEM, permissions = emptyList(),
    )

    @Test
    fun formMapsTheWeeklyGoalOnlyForOfferDays() {
        val form = ReminderForm.new(now, zone).copy(title = "Training", weekly = true, weekTarget = 2, weekMax = 3, weekMinimum = 1)
        assertEquals(WeeklyGoal(2, 3, 1), form.toReminder(now)!!.weeklyGoal)
        assertEquals(form.weekly, ReminderForm.of(form.toReminder(now)!!, LocalDate.of(2026, 10, 29)).weekly)
        assertNull(form.copy(kind = ScheduleKind.ONCE).toReminder(now)!!.weeklyGoal)
    }

    @Test
    fun weeklyGoalIsSwitchedOnAndKeptConsistent() {
        val actions = FakeActions()
        val plain = training.copy(weeklyGoal = null)
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(plain), actions, now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithText("Wochenziel").performScrollTo().performClick()
        // Second row is "Höchstens": 2× pulls the target down with it.
        compose.onAllNodesWithText("2×")[1].performScrollTo().performClick()
        compose.onNodeWithText("Speichern").performClick()

        assertEquals(WeeklyGoal(target = 2, max = 2, minimum = 1), actions.saved.single().weeklyGoal)
    }

    @Test
    fun todayShowsThisWeek() {
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), FakeActions(), now, zone) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("DIESE WOCHE"))
        compose.onNodeWithText("2 von 3", substring = true).assertExists()
    }

    @Test
    fun statisticsFromTheEditScreen() {
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), FakeActions(), now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithTag("stats").performScrollTo().performClick()
        compose.onNodeWithTag("week").assertTextContains("2 von 3", substring = true)
        compose.onNodeWithTag("streak").assertExists()
        compose.onNodeWithText("Gesamt").assertExists()
    }

    @Test
    fun overviewCanBeMovedAndSwitchedOff() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.SETTINGS) } }

        compose.onNodeWithText("08:00").performScrollTo().performClick()
        assertEquals(OverviewTimes(morning = LocalTime.of(8, 0)), actions.chosen)
        // The second "aus" belongs to the evening.
        compose.onAllNodesWithText("aus")[1].performScrollTo().performClick()
        assertEquals(OverviewTimes(evening = null), actions.chosen)
    }

    @Test
    fun overviewComesAtTheNextTime() {
        val berlin = zone
        // 14:00: the evening comes today, the morning tomorrow.
        assertEquals(Instant.parse("2026-10-29T19:30:00Z"), OverviewNotifications.next(LocalTime.of(20, 30), now, berlin))
        assertEquals(Instant.parse("2026-10-30T06:30:00Z"), OverviewNotifications.next(LocalTime.of(7, 30), now, berlin))
        // Exactly at the time: the next day.
        assertEquals(Instant.parse("2026-10-30T13:00:00Z"), OverviewNotifications.next(LocalTime.of(14, 0), now, berlin))
    }
}
