package com.avlreminder.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmUi
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Core flows from the design: create a reminder, react to an alarm, switch the theme. */
@RunWith(AndroidJUnit4::class)
class FlowsTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-24T16:30:00Z") // Saturday 18:30

    private class FakeActions : AppActions {
        val saved = mutableListOf<Reminder>()
        var chosenTheme: ThemeMode? = null
        override fun save(reminder: Reminder, onResult: (SaveResult) -> Unit) {
            saved += reminder
            onResult(SaveResult.Saved(1))
        }
        override fun delete(reminderId: Long) {}
        override fun setActive(reminderId: Long, active: Boolean) {}
        override fun setTheme(mode: ThemeMode) { chosenTheme = mode }
        override fun openAlarm(occurrenceId: Long) {}
    }

    private val emptyState = AppState(
        today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        reminders = emptyList(), openByReminder = emptyMap(), history = emptyList(), photos = emptySet(),
        theme = ThemeMode.SYSTEM, permissions = emptyList(),
    )

    @Test
    fun createStrictReminder() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(emptyState, actions, now, zone) } }

        compose.onNodeWithContentDescription("Neuer Reminder").performClick()
        compose.onNodeWithTag("title").performTextInput("Katzenbrunnen reinigen")
        compose.onNodeWithText("Nach Erledigung").performClick()
        compose.onNodeWithText("streng").performScrollTo().performClick()
        compose.onNodeWithText("Speichern").performClick()

        val r = actions.saved.single()
        assertEquals("Katzenbrunnen reinigen", r.title)
        assertEquals(Strictness.STRICT, r.strictness)
        assertEquals(ProofType.PHOTO, r.proof)
        // First due today, not only after the first interval.
        assertEquals(Schedule.AfterDone(14, LocalTime.of(19, 0), firstOn = LocalDate.of(2026, 10, 24)), r.schedule)
        // Saved: back on "Heute".
        compose.onNodeWithText("Speichern").assertDoesNotExist()
    }

    private val strict = Reminder(
        id = 1, title = "Katzenbrunnen reinigen", why = "Sauberes Wasser", strictness = Strictness.STRICT,
        schedule = Schedule.Daily(LocalTime.of(18, 0)), goal = "komplett reinigen", minimum = "Wasser wechseln",
        minimumLimitPerWeek = 1, proof = ProofType.PHOTO, postponeLimit = 1, createdAt = now,
    )

    private fun alarmUi(minimumAllowed: Boolean = true) = AlarmUi(
        reminder = strict,
        occurrence = Occurrence(id = 9, reminderId = 1, dueAt = now.minusSeconds(1800), state = OccurrenceState.DUE),
        minimumAllowed = minimumAllowed, minimumsLeft = if (minimumAllowed) 1 else 0,
        canPostpone = true, postponesLeft = 1,
    )

    @Test
    fun alarmDoneWithPhoto() {
        var completed: Triple<Boolean, String?, String?>? = null
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(), now = now, zone = zone,
                    onComplete = { m, p, n -> completed = Triple(m, p, n) },
                    onSnooze = {}, onPostpone = {}, onSkip = {},
                    camera = { onPhoto -> Button(onClick = { onPhoto("/proofs/x.jpg") }) { Text("Fake-Foto") } },
                )
            }
        }
        compose.onNodeWithText("fällig seit 18:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Erledigt").performClick()
        compose.onNodeWithText("Foto-Nachweis").assertIsDisplayed()
        compose.onNodeWithText("Fake-Foto").performClick()
        assertEquals(Triple(false, "/proofs/x.jpg", null), completed)
    }

    @Test
    fun alarmMinimumLimitAndPostpone() {
        var postponedTo: Instant? = null
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(minimumAllowed = false), now = now, zone = zone,
                    onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = { postponedTo = it }, onSkip = {},
                    camera = {},
                )
            }
        }
        compose.onNodeWithText("Minimum diese Woche aufgebraucht").assertIsNotEnabled()
        compose.onNodeWithText("Verschieben", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("letzte Verschiebung", substring = true).assertExists()
        compose.onNodeWithText("In 1 Stunde").performScrollTo().performClick()
        assertEquals(now.plusSeconds(3600), postponedTo)
    }

    @Test
    fun switchThemeInSettings() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(emptyState, actions, now, zone) } }
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Dunkel").performClick()
        assertEquals(ThemeMode.DARK, actions.chosenTheme)
    }
}
