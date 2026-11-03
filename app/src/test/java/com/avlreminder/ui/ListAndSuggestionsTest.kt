package com.avlreminder.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.ReminderSort
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.data.SuggestionExport
import com.avlreminder.data.SuggestionStatus
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmDevices
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** V1.2a: suggestions from anywhere, reminder list with groups, sorting and the archive. */
@RunWith(AndroidJUnit4::class)
class ListAndSuggestionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-29T13:00:00Z") // Thursday 14:00

    private class FakeActions : AppActions {
        val suggestions = mutableListOf<List<Any?>>()
        val updated = mutableListOf<SuggestionEntity>()
        val archived = mutableListOf<Pair<Long, Boolean>>()
        var sort: ReminderSort? = null
        var shared: String? = null
        override fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) = onResult(SaveResult.Saved(1))
        override fun delete(reminderId: Long) {}
        override fun setActive(reminderId: Long, active: Boolean) {}
        override fun setTheme(mode: ThemeMode) {}
        override fun setAlert(settings: AlertSettings) {}
        override fun openAlarm(occurrenceId: Long) {}
        override fun completeEarly(occurrenceId: Long) {}
        override fun play(path: String) {}
        override fun setReminderSort(sort: ReminderSort) { this.sort = sort }
        override fun setArchived(reminderId: Long, archived: Boolean) { this.archived += reminderId to archived }
        override fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) {
            suggestions += listOf(text, important, screen, reminderId)
        }
        override fun updateSuggestion(suggestion: SuggestionEntity) { updated += suggestion }
        override fun share(text: String) { shared = text }
    }

    private val training = Reminder(id = 1, title = "Training", schedule = Schedule.Daily(LocalTime.of(19, 0)), createdAt = now)
    private val arzt = Reminder(id = 2, title = "Arzt anrufen", schedule = Schedule.Once(LocalDateTime.of(2026, 10, 28, 9, 0)), createdAt = now)
    private val alt = Reminder(id = 3, title = "Alte Wartung", schedule = Schedule.Daily(LocalTime.of(9, 0)), createdAt = now, active = false, archivedAt = now)

    private fun state(suggestions: List<SuggestionEntity> = emptyList()) = AppState(
        today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        reminders = listOf(training, arzt, alt),
        openByReminder = mapOf(1L to Occurrence(id = 5, reminderId = 1, dueAt = now.plusSeconds(18000))),
        history = emptyList(), proofs = emptyMap(), theme = ThemeMode.SYSTEM, permissions = emptyList(),
        suggestions = suggestions,
    )

    private fun suggestion(id: Long, text: String, important: Boolean = false, status: SuggestionStatus = SuggestionStatus.OPEN) =
        SuggestionEntity(
            id = id, text = text, important = important, createdAt = now, screen = "Alarm", reminderId = 1,
            reminderTitle = "Training", appVersion = "1.2a-prototyp", status = status,
        )

    @Test
    fun suggestionFromTodayKeepsTheScreen() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone) } }

        compose.onNodeWithContentDescription("Verbesserung vorschlagen").performClick()
        compose.onNodeWithTag("suggestionText").performTextInput("Erledigen-Knopf zu klein")
        compose.onNodeWithTag("important").performClick()
        compose.onNodeWithTag("suggestionSave").performClick()

        assertEquals(listOf("Erledigen-Knopf zu klein", true, "Heute", null), actions.suggestions.single())
        // Back where it was.
        compose.onNodeWithContentDescription("Einstellungen").assertExists()
        compose.onNodeWithTag("suggestionText").assertDoesNotExist()
    }

    @Test
    fun suggestionWhileEditingNamesTheReminder() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithContentDescription("Verbesserung vorschlagen").performClick()
        compose.onNodeWithTag("suggestionText").performTextInput("Uhrzeit oben anzeigen")
        compose.onNodeWithTag("suggestionSave").performClick()

        assertEquals(listOf("Uhrzeit oben anzeigen", false, "Bearbeiten", 1L), actions.suggestions.single())
        compose.onNodeWithText("Reminder bearbeiten").assertExists()
    }

    @Test
    fun listGroupsSortsAndRestores() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.REMINDERS) } }

        compose.onNodeWithText("Training").assertExists()
        // Finished one-off and archived one are folded away.
        compose.onNodeWithText("Arzt anrufen").assertDoesNotExist()
        compose.onNodeWithTag("closed").performClick()
        compose.onNodeWithText("abgeschlossen – neue Zeit wählen", substring = true).assertExists()
        compose.onNodeWithText("Wiederherstellen").performScrollTo().performClick()
        assertEquals(listOf(3L to false), actions.archived)

        compose.onNodeWithText("Name").performClick()
        assertEquals(ReminderSort.NAME, actions.sort)
    }

    @Test
    fun archiveFromTheEditScreen() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.edit(1)) } }
        compose.onNodeWithText("Archivieren").performScrollTo().performClick()
        assertEquals(listOf(1L to true), actions.archived)
    }

    @Test
    fun suggestionsListMarksDoneAndShares() {
        val actions = FakeActions()
        val list = listOf(suggestion(1, "Knopf größer", important = true), suggestion(2, "Alt", status = SuggestionStatus.DONE))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(list), actions, now, zone, startRoute = Routes.SETTINGS) } }

        compose.onNodeWithText("1 offen").assertExists()
        compose.onNodeWithText("Vorschläge ansehen").performClick()
        compose.onNodeWithText("Wichtig: Knopf größer").assertExists()
        compose.onNodeWithText("Alt").assertDoesNotExist()

        compose.onNodeWithText("Teilen").performClick()
        assertTrue(actions.shared!!.contains("**wichtig:** Knopf größer"))

        compose.onNodeWithText("Wichtig: Knopf größer").performClick()
        compose.onNodeWithText("Umgesetzt").performClick()
        assertEquals(SuggestionStatus.DONE, actions.updated.single().status)
    }

    @Test
    fun alarmOffersASuggestionAtTheEnd() {
        var noted: Pair<String, Boolean>? = null
        val ui = AlarmView(
            reminder = training, occurrence = Occurrence(id = 5, reminderId = 1, dueAt = now),
            minimumAllowed = false, minimumsLeft = 0, canPostpone = true, postponesLeft = null, snooze = listOf(10),
        )
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = ui, now = now, zone = zone, onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {},
                    onStartProof = {}, onAbortProof = {}, devices = AlarmDevices(camera = {}, recorder = {}, play = {}),
                    onSuggest = { t, i -> noted = t to i },
                )
            }
        }
        compose.onNodeWithTag("suggest").performScrollTo().performClick()
        compose.onNodeWithTag("suggestionText").performTextInput("Ton zu leise")
        compose.onNodeWithTag("suggestionSave").performClick()
        assertEquals("Ton zu leise" to false, noted)
    }

    @Test
    fun exportListsOnlyOpenOnesWithContext() {
        val text = SuggestionExport.markdown(
            listOf(suggestion(1, "Knopf\ngrößer", important = true), suggestion(2, "Erledigt", status = SuggestionStatus.DISCARDED)),
            zone,
        )
        assertEquals(
            "# Verbesserungsvorschläge avlreminder (1 offen)\n\n" +
                "- **wichtig:** Knopf größer\n  _Alarm · „Training“ · 29.10.2026 14:00 · 1.2a-prototyp_\n",
            text,
        )
    }
}
