package com.avlreminder.voice

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.rules.SpokenReminder
import com.avlreminder.core.rules.VoiceReminders
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Der Bildschirm der Schnellerinnerung (Anf. 7.35): Muster beim Zuhören, Zusammenfassung mit drei Wegen. */
@RunWith(AndroidJUnit4::class)
class VoiceScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()
    private var saved: SpokenReminder? = null

    private fun show(state: VoiceState, needsPermission: Boolean = false) = compose.setContent {
        AvlTheme(dark = false) {
            VoiceScreen(
                state = state, needsPermission = needsPermission, onDevice = true,
                onAllow = { clicks += "erlauben" }, onFinish = { clicks += "fertig" }, onSave = { saved = it },
                onRetry = { clicks += "nochmal" }, onCancel = { clicks += "abbrechen" },
            )
        }
    }

    @Test
    fun `beim Zuhoeren steht das Muster da und Fertig beendet`() {
        show(VoiceState.Listening("Zeit in 5"))
        compose.onNodeWithText("„${VoiceReminders.PATTERN}“").assertExists()
        compose.onNodeWithText("Zeit in 5").assertExists()
        compose.onNodeWithText("Fertig").performClick()
        assertEquals(listOf("fertig"), clicks)
    }

    @Test
    fun `die Zusammenfassung zeigt Name und Zeit, Anlegen legt genau das an`() {
        show(VoiceState.Heard("Zeit in 20 Minuten Name Anna zurückrufen"))
        compose.onNodeWithText("Anna zurückrufen").assertExists()
        compose.onNodeWithText("in 20 min").assertExists()
        compose.onNodeWithText("Anlegen").performClick()
        assertEquals(SpokenReminder("Anna zurückrufen", 20, timeHeard = true, titleHeard = true), saved)
    }

    @Test
    fun `ohne erkannte Zeit sagt die Zusammenfassung, dass der Standard gilt`() {
        show(VoiceState.Heard("Name Tee"))
        compose.onNodeWithText("Keine Zeit erkannt, Standard 10 min").assertExists()
        compose.onNodeWithText("in 10 min").assertExists()
    }

    @Test
    fun `Nochmal und Abbrechen legen nichts an`() {
        show(VoiceState.Heard("Zeit in 5 Minuten Name Tee"))
        compose.onNodeWithText("Nochmal").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(listOf("nochmal", "abbrechen"), clicks)
        assertEquals(null, saved)
    }

    @Test
    fun `nach einem Fehler gibt es nur Nochmal und Abbrechen`() {
        show(VoiceState.Failed("Nichts verstanden."))
        compose.onNodeWithText("Nichts verstanden.").assertExists()
        compose.onNodeWithText("Anlegen").assertDoesNotExist()
        compose.onNodeWithText("Nochmal").performClick()
        assertEquals(listOf("nochmal"), clicks)
    }

    @Test
    fun `ohne Mikrofon-Erlaubnis erst fragen`() {
        show(VoiceState.Listening(""), needsPermission = true)
        compose.onNodeWithText("Fertig").assertDoesNotExist()
        compose.onNodeWithText("Mikrofon erlauben").performClick()
        assertEquals(listOf("erlauben"), clicks)
    }
}
