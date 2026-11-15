package com.avlreminder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.PrepAnswer as Answer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.alarm.AlarmPause
import com.avlreminder.alarm.OverviewNotifications
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.DayTimes
import com.avlreminder.core.rules.SnoozeHint
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.OverviewTimes
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.ScheduleKind
import com.avlreminder.ui.screens.templateSummary
import com.avlreminder.ui.screens.toTemplate
import com.avlreminder.ui.screens.withTemplate
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** V1.3a: several times a day (VS1), weekend times (VS2), snoozing hint (NT2), pausing all alarms (PS7). */
@RunWith(AndroidJUnit4::class)
class EverydayUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-29T13:00:00Z") // Thursday 14:00

    private class FakeActions : AppActions {
        val saved = mutableListOf<Reminder>()
        var chosenAlert: AlertSettings? = null
        var chosenOverview: OverviewTimes? = null
        val paused = mutableListOf<Instant?>()
        val answers = mutableListOf<Pair<Long, SnoozeHintChoice>>()
        val claude = mutableListOf<Boolean>()
        /** Set: the engine rejects saving with these problems. */
        var problems: List<String>? = null
        override fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) {
            problems?.let { onResult(SaveResult.Invalid(it)); return }
            saved += reminder
            onResult(SaveResult.Saved(reminder.id))
        }
        override fun delete(reminderId: Long) {}
        override fun setActive(reminderId: Long, active: Boolean) {}
        override fun setTheme(mode: ThemeMode) {}
        override fun setAlert(settings: AlertSettings) { chosenAlert = settings }
        override fun openAlarm(occurrenceId: Long) {}
        override fun completeEarly(occurrenceId: Long) {}
        override fun play(path: String) {}
        override fun setOverview(times: OverviewTimes) { chosenOverview = times }
        override fun setAlarmsPaused(until: Instant?) { paused += until }
        override fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice) { answers += reminderId to choice }
        override fun setClaudeAccess(on: Boolean) { claude += on }
    }

    private val t19 = LocalTime.of(19, 0)
    private val training = Reminder(id = 1, title = "Training", schedule = Schedule.Daily(t19), createdAt = Instant.parse("2026-10-01T00:00:00Z"))

    private fun state(alert: AlertSettings = AlertSettings(), hints: List<SnoozeHint> = emptyList()) = AppState(
        today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        reminders = listOf(training),
        openByReminder = emptyMap(),
        history = emptyList(),
        proofs = emptyMap(), theme = ThemeMode.SYSTEM, permissions = emptyList(),
        alert = alert, snoozeHints = hints,
    )

    @Test
    fun formMapsSeveralTimesAndLeavesOutWeeklyGoalAndArrival() {
        val form = ReminderForm.new(now, zone).copy(title = "Tabletten", kind = ScheduleKind.DAILY, time = LocalTime.of(20, 0), moreTimes = listOf(LocalTime.of(8, 0)))
        val r = form.toReminder(now)!!
        assertEquals(Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))), r.schedule)
        assertFalse(form.weeklyPossible)
        assertFalse(form.placePossible)
        assertNull(form.copy(weekly = true).toReminder(now)!!.weeklyGoal)
        val back = ReminderForm.of(r, LocalDate.of(2026, 10, 29))
        assertEquals(LocalTime.of(8, 0), back.time)
        assertEquals(listOf(LocalTime.of(20, 0)), back.moreTimes)
        // Other kinds ignore the further times.
        assertEquals(Schedule.AfterDone(14, LocalTime.of(20, 0), LocalDate.of(2026, 10, 29)), form.copy(kind = ScheduleKind.AFTER_DONE, date = LocalDate.of(2026, 10, 29)).toReminder(now)!!.schedule)
    }

    @Test
    fun templatesKeepTheFurtherTimes() {
        val form = ReminderForm.new(now, zone).copy(kind = ScheduleKind.DAILY, time = LocalTime.of(8, 0), moreTimes = listOf(LocalTime.of(13, 0), LocalTime.of(20, 0)))
        val template = form.toTemplate("Tabletten", now)
        assertEquals("780,1200", template.moreTimes)
        assertEquals("täglich 08:00, 13:00, 20:00 · normal", templateSummary(template))
        assertEquals(form.moreTimes, ReminderForm.new(now, zone).withTemplate(template).moreTimes)
        assertNull(form.copy(kind = ScheduleKind.ONCE).toTemplate("x", now).moreTimes)
    }

    /**
     * Saving rejected by the engine while the form is scrolled down: the problems are on screen,
     * not above it out of sight (Review-Lauf 5).
     */
    @Test
    fun aRejectedSaveShowsItsProblemsAlsoWhenScrolledDown() {
        val actions = FakeActions().apply { problems = listOf("Ort zuerst in den Einstellungen festlegen") }
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithTag("addTime").performScrollTo()
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Ort zuerst in den Einstellungen festlegen", substring = true).assertIsDisplayed()
        assertTrue(actions.saved.isEmpty())
    }

    /** The draft survives the activity being recreated (dark mode, process death; Review-Lauf 5, 6a). */
    @Test
    fun theDraftSurvivesTheActivityBeingRecreated() {
        val restore = StateRestorationTester(compose)
        restore.setContent { AvlTheme(dark = false) { AvlAppUi(state(), FakeActions(), now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithTag("title").performTextReplacement("Training lang")
        restore.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("title").assertTextContains("Training lang")
    }

    /**
     * Opened before the data is loaded (cold start on the edit route): the form is built once
     * reminders, "Startklar" answers and places are there, not from what was there first (Review-Lauf 5).
     */
    @Test
    fun theFormWaitsForTheData() {
        val overcome = training.copy(character = Character.OVERCOME)
        val loaded = state().copy(
            reminders = listOf(overcome),
            prep = mapOf(1L to mapOf(1 to Answer(reminderId = 1, question = 1, text = "Rücken stärken", at = now))),
        )
        var current by mutableStateOf(loaded.copy(prep = emptyMap(), formReady = false))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(current, FakeActions(), now, zone, startRoute = Routes.edit(1)) } }
        compose.waitForIdle()

        current = loaded
        compose.onNodeWithTag("prep1").performScrollTo().assertTextContains("Rücken stärken")
    }

    /** A "Startklar" answer can be overwritten, not emptied; the field says so (Review-Lauf 5, 6c). */
    @Test
    fun anEmptiedStartklarAnswerSaysItStays() {
        val overcome = training.copy(character = Character.OVERCOME)
        val prep = mapOf(1L to mapOf(1 to Answer(reminderId = 1, question = 1, text = "Rücken stärken", at = now)))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state().copy(reminders = listOf(overcome), prep = prep), FakeActions(), now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithTag("prepKept1").assertDoesNotExist()
        compose.onNodeWithTag("prep1").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("prepKept1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("prep1").performTextReplacement("Kraft")
        compose.onNodeWithTag("prepKept1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun settingsShowTheVersionOfTheBuild() {
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state().copy(appVersion = "1.3b3-backup5"), FakeActions(), now, zone, startRoute = Routes.SETTINGS) } }
        compose.onNodeWithText("AVL-Reminder 1.3b3-backup5").performScrollTo().assertExists()
    }

    @Test
    fun aFurtherTimeIsAddedInTheEditScreen() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithTag("addTime").performScrollTo().performClick()
        compose.onNodeWithTag("timeOk").performClick() // one hour after the last: 20:00
        compose.onNodeWithText("Jede Uhrzeit gilt als verpasst", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Speichern").performClick()

        assertEquals(Schedule.Daily(listOf(t19, LocalTime.of(20, 0))), actions.saved.single().schedule)
    }

    @Test
    fun theWeekendSwitchStartsWithTheWeekdayTimes() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.SETTINGS) } }

        compose.onNodeWithTag("weekend").performScrollTo().performClick()
        assertEquals(DayTimes(LocalTime.of(18, 0), LocalTime.of(23, 0)), actions.chosenAlert!!.weekend)
        assertEquals(OverviewTimes(), actions.chosenOverview!!.weekend)
    }

    @Test
    fun pausingAllAlarmsAsksFirst() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.SETTINGS) } }

        compose.onNodeWithTag("pauseForGood").performScrollTo().performClick()
        assertTrue(actions.paused.isEmpty())
        compose.onNodeWithTag("pauseOk").performClick()
        assertEquals(listOf<Instant?>(AlertSettings.PAUSED_FOR_GOOD), actions.paused)
    }

    @Test
    fun whilePausedTodayShowsTheBannerAndSwitchesOnAgain() {
        val actions = FakeActions()
        val until = now.plus(Duration.ofHours(1))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(AlertSettings(alarmsPausedUntil = until)), actions, now, zone) } }

        compose.onNodeWithText("Alle Alarme pausiert bis 15:00").assertExists()
        compose.onNodeWithTag("resumeAlarms").performClick()
        assertEquals(listOf<Instant?>(null), actions.paused)
    }

    @Test
    fun noBannerOnceThePauseIsOver() {
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(AlertSettings(alarmsPausedUntil = now)), FakeActions(), now, zone) } }
        compose.onNodeWithTag("pausedBanner").assertDoesNotExist()
    }

    @Test
    fun theSnoozeHintCardOffersTheTimeTheStrictnessAndNoThanks() {
        val actions = FakeActions()
        val hint = SnoozeHint(training, LocalTime.of(19, 15), Strictness.RELAXED)
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(hints = listOf(hint)), actions, now, zone) } }

        compose.onNodeWithText("„Training“ schiebst du oft – lieber um 19:15?").assertExists()
        compose.onNodeWithTag("hintRelax").assertExists()
        compose.onNodeWithTag("hintTime").performClick()
        compose.onNodeWithTag("hintDismiss").performClick()
        assertEquals(listOf(1L to SnoozeHintChoice.TIME, 1L to SnoozeHintChoice.DISMISS), actions.answers)
    }

    @Test
    fun theInterfaceForClaudeIsSwitchedOnOnlyAfterTheQuestion() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state().copy(claudeAccess = false), actions, now, zone, startRoute = Routes.SETTINGS) } }

        compose.onNodeWithTag("claudeAccess").performScrollTo().performClick()
        assertTrue(actions.claude.isEmpty())
        compose.onNodeWithTag("claudeOk").performClick()
        assertEquals(listOf(true), actions.claude)
    }

    @Test
    fun noInterfaceForClaudeInARelease() {
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), FakeActions(), now, zone, startRoute = Routes.SETTINGS) } }
        compose.onNodeWithTag("claudeAccess").assertDoesNotExist()
    }

    @Test
    fun theOverviewUsesTheTimesOfItsDay() {
        val times = OverviewTimes(morning = LocalTime.of(7, 30), weekend = OverviewTimes(morning = LocalTime.of(9, 0)))
        val fridayNight = Instant.parse("2026-10-30T21:00:00Z")
        assertEquals(Instant.parse("2026-10-31T08:00:00Z"), OverviewNotifications.next(times, { it.morning }, fridayNight, zone))
        // Off on the weekend: Monday morning.
        val offOnWeekend = times.copy(weekend = OverviewTimes(morning = null))
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), OverviewNotifications.next(offOnWeekend, { it.morning }, fridayNight, zone))
        assertNull(OverviewNotifications.next(OverviewTimes(morning = null), { it.morning }, fridayNight, zone))
    }

    @Test
    fun thePauseEndsAtTheRightMoment() {
        // 14:00 → tomorrow 06:00; 03:00 → today 06:00.
        assertEquals(Instant.parse("2026-10-30T05:00:00Z"), AlarmPause.untilMorning(now, zone))
        assertEquals(Instant.parse("2026-10-30T05:00:00Z"), AlarmPause.untilMorning(Instant.parse("2026-10-30T02:00:00Z"), zone))
        assertEquals("bis morgen 06:00", AlarmPause.untilText(Instant.parse("2026-10-30T05:00:00Z"), now, zone))
        assertEquals("bis du sie wieder einschaltest", AlarmPause.untilText(AlertSettings.PAUSED_FOR_GOOD, now, zone))
        assertEquals("bis 01.11. 10:00", AlarmPause.untilText(Instant.parse("2026-11-01T09:00:00Z"), now, zone))
    }

}
