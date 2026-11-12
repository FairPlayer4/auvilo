package com.avlreminder.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.SessionView
import com.avlreminder.core.usecases.Today
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmDevices
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.SessionMain
import com.avlreminder.ui.screens.TodayScreen
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** V1.3b: Sitzung mit Timer (Anf. 7.11) – Sitzungsbildschirm, Alarm, *Heute*, Formular, Texte. */
@RunWith(AndroidJUnit4::class)
class SessionUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val start = Instant.parse("2026-11-11T17:42:00Z") // 18:42 in Berlin
    private val spec = SessionSpec(checkMinutes = 10, goalMinutes = 45, maxMinutes = 90)
    private val training = Reminder(
        id = 1, title = "Training", schedule = Schedule.Daily(LocalTime.of(18, 30)), session = spec,
        proof = ProofSpec.CONFIRM, createdAt = start,
    )
    private val devices = AlarmDevices(camera = { Text("Kamera") }, recorder = { Text("Mikrofon") }, play = {})

    private fun view(
        reminder: Reminder = training,
        suggestion: SessionAnswer = SessionAnswer.STILL_ON,
        final: Boolean = false,
        average: Duration? = null,
        minimumAllowed: Boolean = false,
    ) = AlarmView(
        reminder = reminder,
        occurrence = Occurrence(id = 9, reminderId = 1, dueAt = start, state = OccurrenceState.RUNNING, startedAt = start),
        minimumAllowed = minimumAllowed, minimumsLeft = null, canPostpone = true, postponesLeft = null,
        session = SessionView(start, start.plus(Duration.ofMinutes(90)), final, suggestion, average, checkMinutes = 10),
    )

    private class Answers {
        val log = mutableListOf<String>()
    }

    private fun show(ui: AlarmView, at: Instant): Answers {
        val answers = Answers()
        compose.setContent {
            AvlTheme(dark = true) {
                // In the app the alarm screen scrolls around it.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                SessionMain(
                    ui, ui.session!!, zone,
                    onStillOn = { answers.log += "stillOn" },
                    onComplete = { answers.log += if (it) "minimum" else "done" },
                    onAbort = { answers.log += "abort" },
                    clock = { at },
                )
                }
            }
        }
        return answers
    }

    private data class Fall(val name: String, val ui: AlarmView, val gross: String, val fehlt: String?)

    // KRITISCH: Der vorgeschlagene Knopf ist der große (D4); am letzten Reminder gibt es kein "Noch dran" (D5).
    @Test
    fun `der Vorschlag ist der große Knopf, am Ende fehlt Noch dran`() {
        val minimum = training.copy(session = spec.copy(goalMinutes = null, minimumMinutes = 10))
        val faelle = listOf(
            Fall("ohne Ziel erreicht: Noch dran", view(), "stillOn", null),
            Fall("Ziel erreicht: Fertig", view(suggestion = SessionAnswer.DONE), "sessionDone", null),
            Fall("Minimum erreicht: Teilschritt", view(minimum, SessionAnswer.MINIMUM, minimumAllowed = true), "sessionMinimum", null),
            Fall("letzter Reminder: kein Noch dran", view(final = true), "sessionDone", "stillOn"),
        )
        // One composition for all cases: setContent works once per test.
        var current by mutableStateOf(faelle.first().ui)
        val answers = mutableListOf<String>()
        compose.setContent {
            AvlTheme(dark = true) {
                // In the app the alarm screen scrolls around it.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                SessionMain(
                    current, current.session!!, zone,
                    onStillOn = { answers += "stillOn" },
                    onComplete = { answers += if (it) "minimum" else "done" },
                    onAbort = { answers += "abort" },
                    clock = { start.plus(Duration.ofMinutes(20)) },
                )
                }
            }
        }
        val answer = mapOf("stillOn" to "stillOn", "sessionDone" to "done", "sessionMinimum" to "minimum")
        for (f in faelle) {
            current = f.ui
            answers.clear()
            compose.waitForIdle()
            // The big button comes first in the column.
            assertEquals(f.name, f.gross, firstAnswerTag())
            compose.onNodeWithTag(f.gross).performScrollTo().performClick()
            assertEquals(f.name, listOf(answer[f.gross]), answers)
            f.fehlt?.let { compose.onNodeWithTag(it).assertDoesNotExist() }
        }
    }

    /** The tag of the topmost answer button. */
    private fun firstAnswerTag(): String = listOf("stillOn", "sessionDone", "sessionMinimum")
        .mapNotNull { tag ->
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { tag to it.positionInRoot.y }
        }
        .minBy { it.second }.first

    @Test
    fun `Teilschritt nur mit Minimum`() {
        show(view(), start.plus(Duration.ofMinutes(5)))
        compose.onNodeWithTag("sessionMinimum").assertDoesNotExist()
    }

    @Test
    fun `der Timer zählt ab dem Start, der Schnitt steht daneben`() {
        show(view(average = Duration.ofMinutes(48)), start.plus(Duration.ofSeconds(42 * 60 + 5)))
        compose.onNodeWithTag("timer").assertExists()
        compose.onNodeWithText("42:05").assertExists()
        compose.onNodeWithText("48 min · 6 min schneller als im Schnitt").assertExists()
        compose.onNodeWithText("Läuft seit 18:42").assertExists()
    }

    // KRITISCH: Abbruch zählt wie verpasst – erst nach Rückfrage.
    @Test
    fun `Abbruch erst nach Rückfrage`() {
        val answers = show(view(), start.plus(Duration.ofMinutes(5)))
        compose.onNodeWithTag("abort").performScrollTo().performClick()
        assertEquals(emptyList<String>(), answers.log)
        compose.onNodeWithText("Weiter").performClick()
        assertEquals(emptyList<String>(), answers.log)
        compose.onNodeWithTag("abort").performScrollTo().performClick()
        compose.onNodeWithTag("abortConfirm").performClick()
        assertEquals(listOf("abort"), answers.log)
    }

    @Test
    fun `der Alarm bietet Ich starte jetzt nur mit Sitzung an`() {
        var canStartNow by mutableStateOf(true)
        var started = 0
        compose.setContent {
            AvlTheme(dark = true) {
                AlarmContent(
                    ui = AlarmView(
                        reminder = training, occurrence = Occurrence(id = 9, reminderId = 1, dueAt = start, state = OccurrenceState.DUE),
                        minimumAllowed = false, minimumsLeft = null, canPostpone = true, postponesLeft = null, canStart = canStartNow,
                    ),
                    now = start, zone = zone, onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {},
                    onStartProof = {}, onAbortProof = {}, devices = devices, onStart = { started++ },
                )
            }
        }
        for ((name, canStart) in listOf("mit Sitzung" to true, "ohne Sitzung" to false)) {
            canStartNow = canStart
            started = 0
            compose.waitForIdle()
            if (canStart) {
                compose.onNodeWithTag("start").performScrollTo().performClick()
                assertEquals(name, 1, started)
            } else {
                compose.onNodeWithTag("start").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Heute zeigt die laufende Sitzung, ein Tipp führt hinein`() {
        val running = Entry(training, Occurrence(id = 9, reminderId = 1, dueAt = start, state = OccurrenceState.RUNNING, startedAt = start))
        var opened: Entry? = null
        compose.setContent {
            AvlTheme(dark = false) {
                TodayScreen(
                    Today(emptyList(), emptyList(), emptyList(), emptyList(), running = listOf(running)),
                    LocalDate.of(2026, 11, 11), zone, start, onOpenDue = { opened = it }, onEdit = {},
                )
            }
        }
        compose.onNodeWithText("läuft seit 18:42").assertExists()
        compose.onNodeWithText("Heute steht nichts mehr an", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("running9").performClick()
        assertEquals(running, opened)
    }

    /** An aborted session goes on with its old start, only before its maximum (Review-Lauf 5). */
    @Test
    fun `Heute bietet Zurücknehmen einer abgebrochenen Sitzung nur vor dem Maximum an`() {
        fun aborted(id: Long) = Occurrence(
            id = id, reminderId = 1, dueAt = start, state = OccurrenceState.CLOSED, resolution = Resolution.ABORTED,
            startedAt = start, resolvedAt = start.plus(Duration.ofMinutes(5)),
        )
        var now by mutableStateOf(start.plus(Duration.ofMinutes(30)))
        compose.setContent {
            AvlTheme(dark = false) {
                TodayScreen(Today(emptyList(), emptyList(), listOf(Entry(training, aborted(9))), emptyList()), LocalDate.of(2026, 11, 11), zone, now, onOpenDue = {}, onEdit = {})
            }
        }
        compose.onNodeWithTag("reopen9").performScrollTo().performClick()
        compose.onNodeWithText("läuft mit ihrem Start um 18:42 weiter", substring = true).assertExists()
        compose.onNodeWithText("Abbrechen").performClick()

        now = start.plus(Duration.ofMinutes(spec.maxMinutes.toLong()))
        compose.onNodeWithTag("reopen9").assertDoesNotExist()
    }

    @Test
    fun `Heute zeigt bei einer erledigten Sitzung die Dauer`() {
        val done = Occurrence(
            id = 9, reminderId = 1, dueAt = start, state = OccurrenceState.DONE, resolution = Resolution.GOAL,
            startedAt = start, resolvedAt = start.plus(Duration.ofMinutes(45)),
        )
        compose.setContent {
            AvlTheme(dark = false) {
                TodayScreen(Today(emptyList(), emptyList(), listOf(Entry(training, done)), emptyList()), LocalDate.of(2026, 11, 11), zone, start, onOpenDue = {}, onEdit = {})
            }
        }
        compose.onNodeWithText("19:27 · 45 min").assertExists()
    }

    // --- Formular und Texte ------------------------------------------------------------------

    @Test
    fun `das Formular übernimmt die Sitzung und behält die Zahlen beim Ausschalten`() {
        val now = Instant.parse("2026-11-11T10:00:00Z")
        val custom = SessionSpec(15, Strictness.NORMAL, goalMinutes = 45, minimumMinutes = 10, maxMinutes = 60, finalMinutes = 3)
        val form = ReminderForm.of(training.copy(session = custom), LocalDate.of(2026, 11, 11))
        assertEquals(custom, form.toReminder(now)!!.session)
        val off = form.copy(session = false)
        assertNull(off.toReminder(now)!!.session)
        assertEquals(custom, off.copy(session = true).toReminder(now)!!.session)
        assertNull(ReminderForm.of(training.copy(session = null), LocalDate.of(2026, 11, 11)).toReminder(now)!!.session)
    }

    @Test
    fun `Dauer, Timer und Tempo als Text`() {
        val faelle = listOf(
            Format.elapsed(Duration.ofSeconds(0)) to "00:00",
            Format.elapsed(Duration.ofSeconds(59 * 60 + 59)) to "59:59",
            Format.elapsed(Duration.ofSeconds(3600 + 2 * 60 + 3)) to "1:02:03",
            Format.elapsed(Duration.ofSeconds(-5)) to "00:00",
            Format.minutes(45) to "45 min",
            Format.minutes(60) to "1 h",
            Format.minutes(90) to "1 h 30 min",
            Format.duration(Duration.ofSeconds(44 * 60 + 30)) to "45 min",
            Format.duration(Duration.ofSeconds(44 * 60 + 29)) to "44 min",
            Format.pace(Duration.ofMinutes(42), Duration.ofMinutes(48)) to "6 min schneller als im Schnitt",
            Format.pace(Duration.ofMinutes(51), Duration.ofMinutes(48)) to "3 min länger als im Schnitt",
            Format.pace(Duration.ofSeconds(48 * 60 + 20), Duration.ofMinutes(48)) to "wie im Schnitt",
            Format.resolution(Resolution.ABORTED) to "abgebrochen",
        )
        faelle.forEachIndexed { i, (ist, soll) -> assertEquals("Fall $i", soll, ist) }
    }
}
