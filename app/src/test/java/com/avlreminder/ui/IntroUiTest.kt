package com.avlreminder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.Intro
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The introduction for testers (Anf. 7.28 PS15, playstore.md N3). */
@RunWith(AndroidJUnit4::class)
class IntroUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-15T13:30:00Z")

    private class Recorder : AppActions {
        var introDone = 0
        var probes = 0
        var mails = 0
        var probeAt: LocalDateTime? = LocalDateTime.of(2026, 11, 15, 14, 32)
        override fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) {}
        override fun delete(reminderId: Long) {}
        override fun setActive(reminderId: Long, active: Boolean) {}
        override fun setTheme(mode: ThemeMode) {}
        override fun setAlert(settings: AlertSettings) {}
        override fun openAlarm(occurrenceId: Long) {}
        override fun completeEarly(occurrenceId: Long) {}
        override fun play(path: String) {}
        override fun introDone() { introDone++ }
        override fun mailFeedback() { mails++ }
        /** Results held back until the test hands them over, as when the engine is still busy. */
        var hold = false
        val held = mutableListOf<(LocalDateTime?) -> Unit>()
        override fun probeAlarm(onResult: (LocalDateTime?) -> Unit) { probes++; if (hold) held += onResult else onResult(probeAt) }
        val paused = mutableListOf<Instant?>()
        override fun setAlarmsPaused(until: Instant?) { paused += until }
    }

    private val asked = mutableListOf<String>()
    private fun permission(label: String, granted: Boolean, required: Boolean = true) =
        PermissionItem(label, "wofür $label", granted, required) { asked += label }

    private fun state(intro: Boolean, permissions: List<PermissionItem> = emptyList(), alert: AlertSettings = AlertSettings()) = AppState(
        today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        reminders = emptyList(), openByReminder = emptyMap(), history = emptyList(), proofs = emptyMap(),
        theme = ThemeMode.SYSTEM, permissions = permissions, intro = intro, alert = alert,
    )

    private fun show(state: AppState, actions: Recorder = Recorder()): Recorder {
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state, actions, now, zone) } }
        return actions
    }

    private fun next() = compose.onNodeWithTag("introNext").performClick()

    // KRITISCH: after a fresh install it comes by itself; skipping counts as seen.
    @Test
    fun aFreshInstallStartsWithTheIntroductionAndSkippingEndsIt() {
        val actions = show(state(intro = true))
        compose.onNodeWithText("Willkommen").assertExists()
        compose.onNodeWithContentDescription("Neuer Reminder").assertDoesNotExist() // nothing over the introduction
        compose.onNodeWithTag("introSkip").performClick()
        assertEquals(1, actions.introDone)
        compose.onNodeWithTag("intro").assertDoesNotExist()
        compose.onNodeWithContentDescription("Neuer Reminder").assertExists()
    }

    @Test
    fun withRemindersOrSeenItDoesNotComeByItself() {
        show(state(intro = false))
        compose.onNodeWithTag("intro").assertDoesNotExist()
    }

    // Repeatable (Kevin, 2026-11-15): "Einstieg zeigen" in the settings opens it any time.
    @Test
    fun theSettingsShowItAgain() {
        show(state(intro = false))
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithTag("showIntro").performScrollTo().performClick()
        compose.onNodeWithText("Willkommen").assertExists()
    }

    @Test
    fun thePermissionsComeInOrderWithWhatTheyAreFor() {
        var granted by mutableStateOf(false)
        val actions = Recorder()
        compose.setContent {
            val permissions = listOf(
                permission("Wecker und Erinnerungen", granted),
                permission("Benachrichtigungen", true),
                permission("Kamera", false, required = false),
            )
            AvlTheme(dark = false) { AvlAppUi(state(intro = true, permissions), actions, now, zone) }
        }
        next()
        compose.onNodeWithText("wofür Wecker und Erinnerungen").assertExists()
        compose.onNodeWithText(Intro.LATER).assertExists()
        compose.onNodeWithTag("introPermissionsDone").assertDoesNotExist()
        compose.onNodeWithTag("grant:Wecker und Erinnerungen").performClick()
        assertEquals(listOf("Wecker und Erinnerungen"), asked)
        granted = true
        compose.onNodeWithTag("introPermissionsDone").assertExists()
    }

    @Test
    fun theTestAlarmSaysWhenItRingsAndCanBeRepeated() {
        val actions = show(state(intro = true))
        next(); next()
        compose.onNodeWithTag("introProbe").performClick()
        compose.onNodeWithText("Klingelt um 14:32.").assertExists()
        compose.onNodeWithText("Noch einen Probealarm").performClick()
        assertEquals(2, actions.probes)
        actions.probeAt = null
        compose.onNodeWithTag("introProbe").performClick()
        compose.onNodeWithText("Das hat nicht geklappt; der Grund steht unten im Hinweis.").assertExists()
    }

    // V1.3c14 U3 (Review-Lauf 7, E31): a double tap made two test alarms.
    @Test
    fun aDoubleTapSetsOneTestAlarmUntilTheResultIsThere() {
        val actions = show(state(intro = true))
        actions.hold = true
        next(); next()
        compose.onNodeWithTag("introProbe").performClick()
        compose.onNodeWithTag("introProbe").performClick()
        assertEquals(1, actions.probes)
        actions.held.single()(actions.probeAt)
        compose.onNodeWithText("Klingelt um 14:32.").assertExists()
        compose.onNodeWithTag("introProbe").performClick()
        assertEquals(2, actions.probes)
    }

    // Review 2026-11-18, Frage 6: under *Notaus* the test alarm stays silent; the page says so.
    @Test
    fun underNotausTheTestAlarmPageSaysItStaysSilent() {
        val actions = show(state(intro = true, alert = AlertSettings(alarmsPausedUntil = now.plusSeconds(3_600))))
        next(); next()
        compose.onNodeWithTag("introPaused").assertExists()
        compose.onNodeWithTag("introResume").performClick()
        assertEquals(listOf<Instant?>(null), actions.paused)
    }

    @Test
    fun withoutNotausThereIsNoHint() {
        show(state(intro = true))
        next(); next()
        compose.onNodeWithTag("introProbe").assertExists()
        compose.onNodeWithTag("introPaused").assertDoesNotExist()
    }

    @Test
    fun theLastPageLeadsToTheFirstReminder() {
        val actions = show(state(intro = true))
        next(); next(); next()
        compose.onNodeWithTag("introNext").assertDoesNotExist()
        compose.onNodeWithTag("introFirst").performClick()
        assertEquals(1, actions.introDone)
        compose.onNodeWithTag("title").assertExists() // the empty form, no templates yet
    }

    @Test
    fun theButtonsMoveBetweenThePagesAndFertigEndsIt() {
        val actions = show(state(intro = true))
        next()
        compose.onNodeWithTag("introBack").performClick()
        compose.onNodeWithText("Willkommen").assertExists()
        compose.onNodeWithTag("introDone").assertDoesNotExist()
        next(); next(); next()
        compose.onNodeWithTag("introDone").performClick()
        assertEquals(1, actions.introDone)
        compose.onNodeWithTag("intro").assertDoesNotExist()
    }

    @Test
    fun feedbackByMailFromTheIntroductionAndTheSettings() {
        val actions = show(state(intro = true))
        next(); next(); next()
        compose.onNodeWithText("kevin.bartik@gmail.com", substring = true).assertExists()
        compose.onNodeWithTag("introMail").performScrollTo().performClick()
        assertEquals(1, actions.mails)
        compose.onNodeWithTag("introSkip").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithTag("mailFeedback").performScrollTo().performClick()
        assertEquals(2, actions.mails)
        assertEquals("the introduction stays where it was", 1, actions.introDone)
    }
}
