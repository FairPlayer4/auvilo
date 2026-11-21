package com.avlreminder.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.usecases.WatchAction
import com.avlreminder.core.usecases.WatchResult
import com.avlreminder.core.usecases.WatchScreen
import com.avlreminder.wear.Fixtures.app
import com.avlreminder.wear.Fixtures.receive
import com.avlreminder.wear.Fixtures.running
import com.avlreminder.wear.Fixtures.training
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Die Ansicht der Uhr (design.md 17): Knöpfe wie im Zustand, gesperrt ohne Verbindung und während ein Befehl wartet. */
@Config(sdk = [34, 36])
@RunWith(AndroidJUnit4::class)
class WatchRouteTest {
    @get:Rule val compose = createComposeRule()

    private val link get() = app.link

    @Before
    fun setUp() = link.reset()

    private fun show(start: Long? = null, timeoutMs: Long = 2_000) {
        compose.setContent { WatchRoute(app.graph, start, tick = false, timeoutMs = timeoutMs) }
        compose.waitForIdle()
    }

    private fun waitForCommands(n: Int) = compose.waitUntil(5_000) { link.commands().size >= n }

    @Test
    fun `laufende Sitzung – Antworten in ihrer Reihenfolge, Abbruch zuletzt`() {
        receive(running)
        show()
        compose.onNodeWithText("Training").assertIsDisplayed()
        val labels = WatchScreen.buttons(training).map(WatchScreen::label)
        assertEquals(listOf("Noch dran", "Fertig", "Teilschritt", "Abbruch"), labels)
        labels.forEach { compose.onNodeWithText(it).assertIsEnabled() }
        compose.onNodeWithText("Fertig").performClick()
        waitForCommands(1)
        assertEquals(WatchAction.DONE to training.occurrenceId, link.commands().single().let { it.action to it.occurrenceId })
    }

    @Test
    fun `ein Doppeltipp schickt den Befehl einmal`() {
        receive(running)
        link.answer = null // the phone has not answered yet
        show()
        compose.onNodeWithText("Noch dran").performClick()
        compose.onNodeWithText("Noch dran").performClick()
        waitForCommands(1)
        compose.waitForIdle()
        assertEquals(1, link.commands().size)
        compose.onNodeWithText("Fertig").assertIsNotEnabled()
    }

    @Test
    fun `ohne Antwort vom Handy wird der Knopf wieder frei`() {
        receive(running)
        link.answer = null
        show(timeoutMs = 200)
        compose.onNodeWithText("Fertig").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(WatchScreen.NO_ANSWER).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Fertig").assertIsEnabled()
    }

    @Test
    fun `ohne Verbindung gesperrt, mit dem Alter des Stands`() {
        receive(running)
        link.phone = null
        app.graph.connected(false)
        show()
        compose.onNodeWithText(WatchScreen.connection(false, app.clock.now, java.time.ZoneId.systemDefault())!!).assertIsDisplayed()
        WatchScreen.buttons(training).map(WatchScreen::label).forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
    }

    @Test
    fun `abgelehnt sagt das Handy warum`() {
        receive(running)
        link.answer = { WatchResult(it.requestId, false, WatchScreen.NOT_POSSIBLE) }
        show()
        compose.onNodeWithText("Noch dran").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(WatchScreen.NOT_POSSIBLE).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `Abbruch ohne Rueckfrage, danach Rueckgaengig`() {
        receive(running)
        show()
        compose.onNodeWithText("Abbruch").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Rückgängig").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("„Training“ abgebrochen").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").performClick()
        waitForCommands(2)
        assertEquals(listOf(WatchAction.ABORT, WatchAction.REOPEN), link.commands().map { it.action })
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Rückgängig").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `mit Foto-Nachweis Fertig am Handy`() {
        receive(running.copy(sessions = listOf(training.copy(answers = listOf(com.avlreminder.core.rules.SessionAnswer.STILL_ON), proofOnPhone = true))))
        show()
        compose.onNodeWithText(WatchScreen.DONE_ON_PHONE).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Fertig").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Noch dran").assertIsEnabled()
    }

    @Test
    fun `Ich starte jetzt, auch gleich von der Kachel`() {
        receive(Fixtures.startable)
        show()
        compose.onNodeWithText("Aufräumen").assertIsDisplayed()
        compose.onNodeWithText("klingelt").assertIsDisplayed()
        compose.onNodeWithText("Ich starte jetzt").performClick()
        waitForCommands(1)
        assertEquals(WatchAction.START to 9L, link.commands().single().let { it.action to it.occurrenceId })
    }

    @Test
    fun `von der Kachel gestartet`() {
        receive(Fixtures.startable)
        show(start = 9)
        waitForCommands(1)
        assertEquals(WatchAction.START to 9L, link.commands().single().let { it.action to it.occurrenceId })
    }

    @Test
    fun `ohne Sitzung und Start der naechste Reminder`() {
        receive(Fixtures.nextOnly)
        show()
        compose.onNodeWithText(WatchScreen.next(Fixtures.nextOnly, java.time.ZoneId.systemDefault())).assertIsDisplayed()
    }
}
