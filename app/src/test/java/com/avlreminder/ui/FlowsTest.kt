package com.avlreminder.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmDevices
import com.avlreminder.ui.screens.HistoryScreen
import com.avlreminder.ui.theme.AvlTheme
import androidx.compose.ui.test.assertIsEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val savedPrep = mutableListOf<PrepAnswer>()
        var chosenTheme: ThemeMode? = null
        var chosenAlert: AlertSettings? = null
        var early: Long? = null
        override fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) {
            saved += reminder
            savedPrep += prep
            onResult(SaveResult.Saved(1))
        }
        override fun delete(reminderId: Long) {}
        override fun setActive(reminderId: Long, active: Boolean) {}
        override fun setTheme(mode: ThemeMode) { chosenTheme = mode }
        override fun setAlert(settings: AlertSettings) { chosenAlert = settings }
        override fun openAlarm(occurrenceId: Long) {}
        override fun completeEarly(occurrenceId: Long) { early = occurrenceId }
        override fun play(path: String) {}
    }

    private val emptyState = AppState(
        today = Today(emptyList(), emptyList(), emptyList(), emptyList()),
        reminders = emptyList(), openByReminder = emptyMap(), history = emptyList(), proofs = emptyMap(),
        theme = ThemeMode.SYSTEM, permissions = emptyList(),
    )

    private val devices = AlarmDevices(
        camera = { onPhoto -> Button(onClick = { onPhoto("/proofs/x.jpg") }) { Text("Fake-Foto") } },
        recorder = { onRecorded -> Button(onClick = { onRecorded("/audio/a.m4a", 12) }) { Text("Fake-Aufnahme") } },
        play = {},
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
        assertEquals(ProofSpec.PHOTO, r.proof)
        // First due today, not only after the first interval.
        assertEquals(Schedule.AfterDone(14, LocalTime.of(19, 0), firstOn = LocalDate.of(2026, 10, 24)), r.schedule)
        // Saved: back on "Heute".
        compose.onNodeWithText("Speichern").assertDoesNotExist()
    }

    private val strict = Reminder(
        id = 1, title = "Katzenbrunnen reinigen", why = "Sauberes Wasser", strictness = Strictness.STRICT,
        schedule = Schedule.Daily(LocalTime.of(18, 0)), goal = "komplett reinigen", minimum = "Wasser wechseln",
        minimumLimitPerWeek = 1, proof = ProofSpec.PHOTO, postponeLimit = 1, createdAt = now,
    )

    private fun alarmUi(minimumAllowed: Boolean = true, reminder: Reminder = strict) = AlarmView(
        reminder = reminder,
        occurrence = Occurrence(id = 9, reminderId = 1, dueAt = now.minusSeconds(1800), state = OccurrenceState.DUE),
        minimumAllowed = minimumAllowed, minimumsLeft = if (minimumAllowed) 1 else 0,
        canPostpone = true, postponesLeft = 1, snooze = listOf(10, 30),
    )

    @Test
    fun alarmDoneWithPhoto() {
        var completed: Triple<Boolean, Evidence, String?>? = null
        var started = 0
        var aborted = 0
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(), now = now, zone = zone,
                    onComplete = { m, e, n -> completed = Triple(m, e, n) },
                    onSnooze = {}, onPostpone = {}, onSkip = {},
                    onStartProof = { started++ }, onAbortProof = { aborted++ }, devices = devices,
                )
            }
        }
        compose.onNodeWithText("fällig seit 18:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Erledigt").performClick()
        // The alarm pauses while the proof is open; "Zurück" lets it ring again.
        assertEquals(1, started)
        compose.onNodeWithText("Alarm pausiert 10 min", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Abschließen").assertIsNotEnabled()
        compose.onNodeWithText("Zurück").performScrollTo().performClick()
        assertEquals(1, aborted)

        compose.onNodeWithText("Erledigt").performClick()
        compose.onNodeWithText("Fake-Foto").performClick()
        compose.onNodeWithText("Abschließen").performScrollTo().performClick()
        assertEquals(Triple(false, Evidence(photoPath = "/proofs/x.jpg"), null), completed)
    }

    @Test
    fun textAndAudioProofNeedTheirMinimum() {
        var completed: Evidence? = null
        val reminder = strict.copy(proof = ProofSpec(ProofKind.EVIDENCE, textMinChars = 10, audioMinSeconds = 10))
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(reminder = reminder).copy(lastProofText = "Brunnen gereinigt"), now = now, zone = zone,
                    onComplete = { _, e, _ -> completed = e },
                    onSnooze = {}, onPostpone = {}, onSkip = {}, onStartProof = {}, onAbortProof = {}, devices = devices,
                )
            }
        }
        compose.onNodeWithText("Erledigt").performClick()
        compose.onNodeWithTag("proofText").performTextInput("Brunnen gereinigt")
        compose.onNodeWithText("Gleicher Text wie beim letzten Mal").assertExists()
        compose.onNodeWithText("Aufnahme fehlt").assertExists()
        compose.onNodeWithText("Abschließen").assertIsNotEnabled()

        compose.onNodeWithTag("proofText").performTextInput(", Filter neu")
        compose.onNodeWithText("Fake-Aufnahme").performScrollTo().performClick()
        compose.onNodeWithText("Abschließen").performScrollTo().assertIsEnabled().performClick()
        assertEquals(Evidence(text = "Brunnen gereinigt, Filter neu", audioPath = "/audio/a.m4a", audioSeconds = 12), completed)
    }

    @Test
    fun checklistItemsAreDoneOneByOneAndTheRestCanWait() {
        val checked = mutableListOf<Pair<Long, Evidence>>()
        var deferred: Instant? = null
        val list = strict.copy(
            proof = ProofSpec.CONFIRM,
            checklist = listOf(ChecklistItem(1, "Brunnen 1"), ChecklistItem(2, "Brunnen 2"), ChecklistItem(3, "Futterautomat")),
        )
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(reminder = list).copy(done = setOf(1L), canDeferRest = true), now = now, zone = zone,
                    onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {}, onStartProof = {}, onAbortProof = {},
                    devices = devices,
                    onCheckItem = { id, e -> checked += id to e },
                    onDeferRest = { deferred = it },
                )
            }
        }
        compose.onNodeWithText("Checkliste · 1 von 3").assertExists()
        compose.onNodeWithTag("done1").assertExists()
        compose.onNodeWithTag("item1").assertDoesNotExist()
        compose.onNodeWithText("Alles erledigt").assertExists()
        // Not in "Vorab erledigen": leaving without completing is for early checking only.
        compose.onNodeWithTag("leave").assertDoesNotExist()
        compose.onNodeWithTag("item2").performScrollTo().performClick()
        assertEquals(listOf(2L to Evidence()), checked)
        compose.onNodeWithText("Rest auf morgen 18:00").performScrollTo().performClick()
        assertEquals(Instant.parse("2026-10-25T17:00:00Z"), deferred)
    }

    /** Items can be checked any time; "Fertig für jetzt" leaves and the reminder keeps its time. */
    @Test
    fun earlyACheckedItemCanBeLeftWithoutCompleting() {
        val checked = mutableListOf<Long>()
        var left = false
        var completed = false
        val list = strict.copy(proof = ProofSpec.CONFIRM, checklist = listOf(ChecklistItem(1, "Brunnen 1"), ChecklistItem(2, "Brunnen 2")))
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(reminder = list).copy(early = true), now = now, zone = zone,
                    onComplete = { _, _, _ -> completed = true }, onSnooze = {}, onPostpone = {}, onSkip = {}, onStartProof = {}, onAbortProof = {},
                    devices = devices,
                    onCheckItem = { id, _ -> checked += id },
                    onLeave = { left = true },
                )
            }
        }
        compose.onNodeWithTag("item1").performScrollTo().performClick()
        compose.onNodeWithTag("leave").performScrollTo().performClick()
        assertEquals(listOf(1L), checked)
        assertTrue(left)
        assertFalse(completed)
    }

    @Test
    fun severalTimesADayTheChecklistOffersTheNextItemAndNoRestForTomorrow() {
        val checked = mutableListOf<Long>()
        var all = 0
        val list = strict.copy(
            proof = ProofSpec.CONFIRM,
            schedule = Schedule.Daily(listOf(LocalTime.of(12, 30), LocalTime.of(19, 15))),
            checklist = listOf(ChecklistItem(1, "Test 1"), ChecklistItem(2, "Test 2"), ChecklistItem(3, "Test 3")),
        )
        val nextTime = now.atZone(zone).toLocalDate().plusDays(1).atTime(12, 30).atZone(zone).toInstant()
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(reminder = list).copy(done = setOf(1L), nextTime = nextTime), now = now, zone = zone,
                    onComplete = { _, _, _ -> all++ }, onSnooze = {}, onPostpone = {}, onSkip = {}, onStartProof = {}, onAbortProof = {},
                    devices = devices,
                    onCheckItem = { id, _ -> checked += id },
                )
            }
        }
        compose.onNodeWithText("Danach: Rest um morgen 12:30").assertExists()
        compose.onNodeWithText("Rest auf morgen", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Alles erledigt").assertDoesNotExist()
        compose.onNodeWithTag("doItem").performScrollTo().performClick()
        assertEquals(listOf(2L), checked)
        compose.onNodeWithTag("allDone").performScrollTo().performClick()
        assertEquals(1, all)
    }

    @Test
    fun checklistItemWithProofOpensTheProofForThatItem() {
        var checked: Pair<Long, Evidence>? = null
        var started = 0
        val list = strict.copy(checklist = listOf(ChecklistItem(1, "Brunnen 1"), ChecklistItem(2, "Brunnen 2")))
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(reminder = list), now = now, zone = zone,
                    onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {},
                    onStartProof = { started++ }, onAbortProof = {}, devices = devices,
                    onCheckItem = { id, e -> checked = id to e },
                )
            }
        }
        compose.onNodeWithTag("item2").performScrollTo().performClick()
        assertEquals(1, started)
        compose.onNodeWithText("Katzenbrunnen reinigen · Brunnen 2", substring = true).assertExists()
        compose.onNodeWithText("Fake-Foto").performClick()
        compose.onNodeWithText("Abschließen").performScrollTo().performClick()
        assertEquals(2L to Evidence(photoPath = "/proofs/x.jpg"), checked)
        // Back on the checklist for the next item.
        compose.onNodeWithText("Alles erledigt").assertExists()
    }

    @Test
    fun snoozeButtonsComeFromTheReminder() {
        var snoozed: Int? = null
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(), now = now, zone = zone, onComplete = { _, _, _ -> }, onSnooze = { snoozed = it },
                    onPostpone = {}, onSkip = {}, onStartProof = {}, onAbortProof = {}, devices = devices,
                )
            }
        }
        compose.onNodeWithText("+10 min", substring = true).assertExists()
        compose.onNodeWithText("+30 min", substring = true).performScrollTo().performClick()
        assertEquals(30, snoozed)
    }

    @Test
    fun laterTodayCanBeCompletedEarly() {
        val actions = FakeActions()
        val later = Occurrence(id = 21, reminderId = 1, dueAt = now.plusSeconds(3600))
        val state = emptyState.copy(today = Today(emptyList(), listOf(Entry(strict, later)), emptyList(), emptyList()))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state, actions, now, zone) } }
        compose.onNodeWithText("Vorab").performClick()
        assertEquals(21L, actions.early)
    }

    @Test
    fun historyShowsProofsAndEarlyCompletion() {
        val o = Occurrence(
            id = 30, reminderId = 1, dueAt = now, state = OccurrenceState.DONE,
            resolution = Resolution.GOAL, resolvedAt = now.minusSeconds(3600),
        )
        val proofs = mapOf(
            30L to listOf(
                Proof(occurrenceId = 30, type = ProofType.PHOTO, filePath = "/p.jpg", takenAt = now),
                Proof(occurrenceId = 30, type = ProofType.TEXT, filePath = null, takenAt = now, text = "Filter neu"),
            ),
        )
        compose.setContent {
            AvlTheme(dark = false) { HistoryScreen(listOf(HistoryDay(now.atZone(zone).toLocalDate(), listOf(Entry(strict, o)))), proofs, now.atZone(zone).toLocalDate(), zone) }
        }
        compose.onNodeWithText("vor dem Reminder erledigt", substring = true).assertExists()
        compose.onNodeWithText("mit Foto + Text", substring = true).assertExists()
        compose.onNodeWithText("„Filter neu“", substring = true).assertExists()
    }

    @Test
    fun startklarAnswersAreSavedWithTheReminder() {
        val actions = FakeActions()
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(emptyState, actions, now, zone) } }
        compose.onNodeWithContentDescription("Neuer Reminder").performClick()
        compose.onNodeWithTag("title").performTextInput("Steuererklärung")
        // "Überwindung" makes the questions required and opens them.
        compose.onNodeWithText("Überwindung").performClick()
        compose.onNodeWithTag("prep1").performScrollTo().performTextInput("Ruhe im Kopf")
        compose.onNodeWithText("Ja, sofort").performScrollTo().performClick()
        compose.onNodeWithText("Speichern").performClick()
        assertEquals(listOf(1 to "Ruhe im Kopf", 3 to "Ja, sofort"), actions.savedPrep.map { it.question to it.text })
    }

    @Test
    fun alarmMinimumLimitAndPostpone() {
        var postponedTo: Instant? = null
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = alarmUi(minimumAllowed = false), now = now, zone = zone,
                    onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = { postponedTo = it }, onSkip = {},
                    onStartProof = {}, onAbortProof = {}, devices = devices,
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
        compose.onNodeWithText("Dunkel").performScrollTo().performClick()
        assertEquals(ThemeMode.DARK, actions.chosenTheme)
        compose.onNodeWithTag("loudFrom").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("19")
        compose.onNodeWithTag("timeOk").performClick()
        assertEquals(AlertSettings(loudFrom = LocalTime.of(19, 0)), actions.chosenAlert)
        assertTrue(actions.saved.isEmpty())
    }

    // --- In advance, "heute später" (Anf. 7.32) ---------------------------------------------------

    private fun earlyUi(reminder: Reminder) = AlarmView(
        reminder = reminder,
        // Saturday 18:30 now, planned for 20:00.
        occurrence = Occurrence(id = 9, reminderId = 1, dueAt = now.plusSeconds(5400)),
        minimumAllowed = true, minimumsLeft = 1, canPostpone = true, postponesLeft = 1,
        early = true, missedAt = Instant.parse("2026-10-24T21:00:00Z"),
    )

    @Test
    fun inAdvanceLaterTodayCountsFromThePlannedTimeAndStaysBeforeTheEndOfTheDay() {
        var postponed: Instant? = null
        var skipped: String? = null
        val relaxed = strict.copy(strictness = Strictness.NORMAL)
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = earlyUi(relaxed), now = now, zone = zone, onComplete = { _, _, _ -> }, onSnooze = {},
                    onPostpone = { postponed = it }, onSkip = { skipped = it }, onStartProof = {}, onAbortProof = {}, devices = devices,
                )
            }
        }
        compose.onNodeWithText("Vorab · fällig um 20:00").assertExists()
        compose.onNodeWithText("Später", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("postpone").performScrollTo().performClick()
        // 20:00 + 30 min and + 1 h stay before 23:00; + 2 h would be 22:00 too, + more not offered.
        compose.onNodeWithTag("postpone+30 min").assertExists()
        compose.onNodeWithTag("postpone+2 Std").assertExists()
        compose.onNodeWithTag("postponeIn 1 Stunde").assertDoesNotExist()
        compose.onNodeWithTag("postpone+1 Std").performClick()
        assertEquals(Instant.parse("2026-10-24T19:00:00Z"), postponed)
    }

    @Test
    fun inAdvanceLaterTodayOnlyBeforeTheEndOfTheDayAndStrictAsksFirst() {
        var postponed: Instant? = null
        val late = earlyUi(strict).let { it.copy(occurrence = it.occurrence.copy(dueAt = Instant.parse("2026-10-24T20:00:00Z"), currentAt = Instant.parse("2026-10-24T20:00:00Z"))) }
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = late, now = now, zone = zone, onComplete = { _, _, _ -> }, onSnooze = {},
                    onPostpone = { postponed = it }, onSkip = {}, onStartProof = {}, onAbortProof = {}, devices = devices,
                )
            }
        }
        compose.onNodeWithTag("postpone").performScrollTo().performClick()
        // Planned 22:00, the day ends at 23:00: only + 30 min fits today.
        compose.onNodeWithTag("postpone+30 min").assertExists()
        compose.onNodeWithTag("postpone+1 Std").assertDoesNotExist()
        compose.onNodeWithTag("postpone+30 min").performClick()
        assertEquals(null, postponed)
        compose.onNodeWithText("Wirklich jetzt schon auf", substring = true).assertExists()
        compose.onNodeWithTag("postponeOk").performClick()
        assertEquals(Instant.parse("2026-10-24T20:30:00Z"), postponed)
    }

    @Test
    fun inAdvanceItCanBeSkipped() {
        var skipped: String? = null
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = earlyUi(strict), now = now, zone = zone, onComplete = { _, _, _ -> }, onSnooze = {},
                    onPostpone = {}, onSkip = { skipped = it }, onStartProof = {}, onAbortProof = {}, devices = devices,
                )
            }
        }
        compose.onNodeWithTag("skip").performScrollTo().performClick()
        // On the skip step now: the main buttons are gone.
        compose.onNodeWithTag("skip").assertDoesNotExist()
        compose.onNodeWithTag("postpone").assertDoesNotExist()
    }

    @Test
    fun todayShowsWhereAMovedOneCameFrom() {
        val moved = Occurrence(id = 22, reminderId = 1, dueAt = now.plusSeconds(1800), currentAt = now.plusSeconds(5400), postponeCount = 1)
        val state = emptyState.copy(today = Today(emptyList(), listOf(Entry(strict, moved)), emptyList(), emptyList()))
        compose.setContent { AvlTheme(dark = false) { AvlAppUi(state, FakeActions(), now, zone) } }
        compose.onNodeWithText("verschoben von 19:00", substring = true).assertExists()
    }
}
