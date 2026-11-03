package com.avlreminder.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.TemplateEntity
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmDevices
import com.avlreminder.ui.screens.PreNoticeKind
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.ScheduleKind
import com.avlreminder.ui.screens.toTemplate
import com.avlreminder.ui.screens.withTemplate
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** V1.2c: deadlines, templates, the notice before, blockers and "Was hält dich ab?". */
@RunWith(AndroidJUnit4::class)
class DeadlineAndBlockerUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-02T13:00:00Z") // Monday 14:00

    private class FakeActions : AppActions {
        val saved = mutableListOf<Reminder>()
        val templates = mutableListOf<TemplateEntity>()
        val discarded = mutableListOf<Long>()
        val blockers = mutableListOf<Pair<Long, String>>()
        val planned = mutableListOf<Pair<Long, LocalDateTime>>()
        val solved = mutableListOf<Long>()
        var used: Long? = null
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
        override fun discard(occurrenceId: Long) { discarded += occurrenceId }
        override fun addBlocker(reminderId: Long, text: String) { blockers += reminderId to text }
        override fun solveBlocker(blockerId: Long) { solved += blockerId }
        override fun planTask(blockerId: Long, at: LocalDateTime) { planned += blockerId to at }
        override fun saveTemplate(template: TemplateEntity) { templates += template }
        override fun templateUsed(id: Long) { used = id }
    }

    private val tax = Reminder(
        id = 1, title = "Steuer", character = Character.OVERCOME,
        schedule = Schedule.Deadline(
            LocalDateTime.of(2026, 11, 6, 12, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            LocalTime.of(9, 0), LocalTime.of(17, 0), 60,
        ),
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun state(
        reminders: List<Reminder> = listOf(tax),
        today: Today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        blockers: Map<Long, List<Blocker>> = emptyMap(),
        templates: List<TemplateEntity>? = emptyList(),
        open: Map<Long, Occurrence> = emptyMap(),
    ) = AppState(
        today = today, reminders = reminders, openByReminder = open, history = emptyList(), proofs = emptyMap(),
        theme = ThemeMode.SYSTEM, permissions = emptyList(), blockers = blockers, templates = templates,
    )

    @Test
    fun formMapsDeadlineAndNoticeBothWays() {
        val form = ReminderForm.new(now, zone).copy(
            title = "Steuer", kind = ScheduleKind.DEADLINE, date = java.time.LocalDate.of(2026, 11, 6), time = LocalTime.NOON,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), windowFrom = LocalTime.of(8, 0), windowUntil = LocalTime.of(10, 0),
            remindEvery = 30, preNotice = PreNoticeKind.EVENING, preNoticeEvening = LocalTime.of(20, 0), atHome = true,
        )
        val r = form.toReminder(now)!!
        assertEquals(
            Schedule.Deadline(LocalDateTime.of(2026, 11, 6, 12, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(8, 0), LocalTime.of(10, 0), 30),
            r.schedule,
        )
        assertEquals(PreNotice.Evening(LocalTime.of(20, 0)), r.preNotice)
        // A deadline rings in its windows, not on arrival.
        assertNull(r.arrival)
        val back = ReminderForm.of(r, java.time.LocalDate.of(2026, 11, 2))
        assertEquals(form.windowFrom, back.windowFrom)
        assertEquals(form.remindEvery, back.remindEvery)
        assertEquals(PreNoticeKind.EVENING, back.preNotice)
        assertNull(form.copy(days = emptySet()).toReminder(now))
    }

    @Test
    fun templateKeepsSettingsButNotTitleOrDate() {
        val form = ReminderForm.new(now, zone).withStrictness(Strictness.STRICT).copy(
            title = "Training", why = "fit bleiben", kind = ScheduleKind.WEEKDAYS,
            days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), time = LocalTime.of(18, 30), minimum = "10 min",
            minimumLimitPerWeek = 2, snoozeMinutes = listOf(10, 30), postponeLimit = 1,
        )
        val template = form.toTemplate(" Abendsport ", now)
        assertEquals("Abendsport", template.name)
        val fresh = ReminderForm.new(now, zone).withTemplate(template)
        assertEquals("", fresh.title)
        assertEquals("", fresh.why)
        assertEquals(Strictness.STRICT, fresh.strictness)
        assertEquals(ScheduleKind.WEEKDAYS, fresh.kind)
        assertEquals(form.days, fresh.days)
        assertEquals(LocalTime.of(18, 30), fresh.time)
        assertEquals("10 min", fresh.minimum)
        assertEquals(2, fresh.minimumLimitPerWeek)
        assertEquals(form.proof, fresh.proof)
        assertEquals(listOf(10, 30), fresh.snoozeMinutes)
        assertEquals(1, fresh.postponeLimit)

        val deadline = ReminderForm.new(now, zone).copy(kind = ScheduleKind.DEADLINE, windowFrom = LocalTime.of(7, 0), remindEvery = 120)
            .toTemplate("Frist", now)
        val fromDeadline = ReminderForm.new(now, zone).withTemplate(deadline)
        assertEquals(LocalTime.of(7, 0), fromDeadline.windowFrom)
        assertEquals(120, fromDeadline.remindEvery)
    }

    @Test
    fun newReminderStartsFromATemplate() {
        val actions = FakeActions()
        val template = ReminderForm.new(now, zone).copy(strictness = Strictness.RELAXED, kind = ScheduleKind.DAILY, time = LocalTime.of(7, 15))
            .withStrictness(Strictness.RELAXED).toTemplate("Morgens", now).copy(id = 4)
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(templates = listOf(template)), actions, now, zone) } }

        compose.onNodeWithContentDescription("Neuer Reminder").performClick()
        compose.onNodeWithTag("template4").performClick()
        assertEquals(4L, actions.used)
        compose.onNodeWithTag("title").performTextInput("Vitamin D")
        compose.onNodeWithText("Speichern").performClick()

        val r = actions.saved.single()
        assertEquals("Vitamin D", r.title)
        assertEquals(Schedule.Daily(LocalTime.of(7, 15)), r.schedule)
        assertEquals(Strictness.RELAXED, r.strictness)
    }

    @Test
    fun formSavesItselfAsTemplate() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(), actions, now, zone, startRoute = Routes.edit(1)) } }

        compose.onNodeWithTag("saveTemplate").performScrollTo().performClick()
        compose.onNodeWithTag("templateSave").performClick()
        assertEquals("Steuer", actions.templates.single().name)
        assertEquals(com.avlreminder.data.ScheduleType.DEADLINE, actions.templates.single().scheduleType)
        compose.onNodeWithText("✓ Als Vorlage gespeichert").assertExists()
    }

    @Test
    fun blockersInTheFormArePlannedSolvedAndAdded() {
        val actions = FakeActions()
        val blocker = Blocker(id = 3, reminderId = 1, text = "Login fehlt", createdAt = now, origin = BlockerOrigin.STARTKLAR)
        val open = Occurrence(id = 9, reminderId = 1, dueAt = Instant.parse("2026-11-04T08:00:00Z"), state = OccurrenceState.PLANNED)
        compose.setContent {
            AvlTheme(dark = false) { AvlAppUi(state(blockers = mapOf(1L to listOf(blocker)), open = mapOf(1L to open)), actions, now, zone, startRoute = Routes.edit(1)) }
        }

        compose.onNodeWithTag("foldBlocker").performScrollTo().performClick()
        compose.onNodeWithText("Login fehlt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("planTask3").performScrollTo().performClick()
        compose.onNodeWithTag("planOk").performClick()
        // The evening before the next due day.
        assertEquals(3L to LocalDateTime.of(2026, 11, 3, 19, 0), actions.planned.single())

        compose.onNodeWithTag("solve3").performScrollTo().performClick()
        assertEquals(listOf(3L), actions.solved)

        compose.onNodeWithTag("newBlocker").performScrollTo().performTextInput("Belege fehlen")
        compose.onNodeWithText("+").performClick()
        assertEquals(1L to "Belege fehlen", actions.blockers.single())
    }

    @Test
    fun overdueDeadlineCanBeDiscarded() {
        val actions = FakeActions()
        val occurrence = Occurrence(id = 7, reminderId = 1, dueAt = Instant.parse("2026-10-30T11:00:00Z"), state = OccurrenceState.PLANNED)
        val today = Today(emptyList(), emptyList(), emptyList(), emptyList(), overdue = listOf(Entry(tax, occurrence)))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state(today = today), actions, now, zone) } }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ÜBERFÄLLIG"))
        compose.onNodeWithTag("discard7").performScrollTo().performClick()
        compose.onNodeWithTag("discardOk").performClick()
        assertEquals(listOf(7L), actions.discarded)
    }

    private val devices = AlarmDevices(camera = { Text("Kamera") }, recorder = { Text("Mikrofon") }, play = {})

    private fun alarmUi(reminder: Reminder = tax) = AlarmView(
        reminder = reminder,
        occurrence = Occurrence(id = 9, reminderId = 1, dueAt = now.minusSeconds(600), state = OccurrenceState.DUE),
        minimumAllowed = false, minimumsLeft = null, canPostpone = true, postponesLeft = null,
    )

    @Test
    fun alarmAsksWhatHoldsYouBackAndPlansATask() {
        var answer: Pair<String?, Boolean>? = null
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi().copy(askBlocker = true), now = now, zone = zone,
                    onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {},
                    onStartProof = {}, onAbortProof = {}, devices = devices,
                    onAnswerAsk = { text, plan -> answer = text to plan },
                )
            }
        }
        compose.onNodeWithText("Was hält dich gerade ab?").assertIsDisplayed()
        compose.onNodeWithTag("askText").performTextInput("Belege fehlen")
        compose.onNodeWithTag("askPlan").performClick()
        assertEquals("Belege fehlen" to true, answer)
        compose.onNodeWithText("Was hält dich gerade ab?").assertDoesNotExist()
    }

    @Test
    fun alarmOffersSolvedBlockerTomorrowAndKeepsPostponingBeforeTheDeadline() {
        var solved = 0
        var tomorrow: Instant? = null
        val next = Instant.parse("2026-11-04T08:00:00Z")
        val deadline = Instant.parse("2026-11-02T15:30:00Z")
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(tax.copy(proof = tax.proof.copy(kind = ProofKind.NONE))).copy(
                        blockerSolved = true, tomorrow = next, deadline = deadline,
                        blockers = listOf(Blocker(id = 3, reminderId = 1, text = "Login fehlt", createdAt = now, origin = BlockerOrigin.ASKED)),
                    ),
                    now = now, zone = zone,
                    onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {},
                    onStartProof = {}, onAbortProof = {}, devices = devices,
                    onBlockerSolved = { solved++ }, onTomorrow = { tomorrow = it },
                )
            }
        }
        compose.onNodeWithText("Login fehlt").assertExists()
        compose.onNodeWithTag("blockerSolved").performScrollTo().performClick()
        assertEquals(1, solved)
        compose.onNodeWithTag("tomorrow").performScrollTo().performClick()
        assertEquals(next, tomorrow)

        // 16:30 deadline: "In 1 Stunde" (15:00) fits, "Heute Abend" and "Morgen" do not.
        compose.onNodeWithText("Verschieben", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("In 1 Stunde").assertExists()
        compose.onNodeWithText("Heute Abend").assertDoesNotExist()
        compose.onNodeWithText("Morgen").assertDoesNotExist()
    }
}
