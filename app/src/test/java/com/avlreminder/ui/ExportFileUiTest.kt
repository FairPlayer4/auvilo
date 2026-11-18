package com.avlreminder.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.restore.LocalStatus
import com.avlreminder.ui.screens.ExportFileSetting
import com.avlreminder.ui.screens.LocalActions
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Sicherungsdatei" (Anf. 7.28 PS14): the password comes first, then Android's file dialog. */
@RunWith(AndroidJUnit4::class)
class ExportFileUiTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recorder : LocalActions {
        val exported = mutableListOf<String>()
        val imported = mutableListOf<String>()
        var dismissed = 0
        var dismissedExport = 0
        override fun exportFile(password: CharArray) { exported += String(password) }
        override fun importFile(password: CharArray) { imported += String(password) }
        override fun dismiss() { dismissed++ }
        override fun dismissExported() { dismissedExport++ }
    }

    private fun show(status: LocalStatus = LocalStatus()): Recorder {
        val actions = Recorder()
        compose.setContent { AvlTheme(dark = false) { ExportFileSetting(status, actions) } }
        return actions
    }

    // KRITISCH: a new file needs a password of 12 characters, typed twice the same.
    @Test
    fun exportingNeedsALongPasswordTypedTwice() {
        val actions = show()
        compose.onNodeWithTag("fileExport").performClick()
        val ok = compose.onNodeWithTag("filePasswordOk")
        ok.assertIsNotEnabled()
        compose.onNodeWithTag("filePassword1").performTextInput("zu-kurz")
        compose.onNodeWithText("Mindestens 12 Zeichen.").assertExists()
        ok.assertIsNotEnabled()
        compose.onNodeWithTag("filePassword1").performTextInput("-und-jetzt-lang")
        compose.onNodeWithText("Die beiden Eingaben sind verschieden.").assertExists()
        ok.assertIsNotEnabled()
        compose.onNodeWithTag("filePassword2").performTextInput("zu-kurz-und-jetzt-lang")
        ok.assertIsEnabled().performClick()
        assertEquals(listOf("zu-kurz-und-jetzt-lang"), actions.exported)
        compose.onNodeWithTag("filePassword").assertDoesNotExist()
    }

    @Test
    fun importingAsksThePasswordOnce() {
        val actions = show()
        compose.onNodeWithTag("fileImport").performClick()
        compose.onNodeWithTag("filePassword2").assertDoesNotExist()
        compose.onNodeWithTag("filePasswordOk").assertIsNotEnabled()
        compose.onNodeWithTag("filePassword1").performTextInput("altes")
        compose.onNodeWithTag("filePasswordOk").assertIsEnabled().performClick()
        assertEquals(listOf("altes"), actions.imported)
        assertTrue(actions.exported.isEmpty())
    }

    @Test
    fun cancellingDoesNothing() {
        val actions = show()
        compose.onNodeWithTag("fileExport").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithTag("filePassword").assertDoesNotExist()
        assertTrue(actions.exported.isEmpty() && actions.imported.isEmpty())
    }

    @Test
    fun aWrittenFileIsConfirmed() {
        val actions = show(LocalStatus(exported = "AuViLo-Sicherung-2026-11-15.auvilo"))
        compose.onNodeWithTag("fileExported").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, actions.dismissedExport)
        assertEquals("a failed swap is not confirmed on the side", 0, actions.dismissed)
    }
}
