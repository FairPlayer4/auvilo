package com.avlreminder.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.cloud.CloudPhase
import com.avlreminder.cloud.CloudStatus
import com.avlreminder.ui.screens.CloudActions
import com.avlreminder.ui.screens.CloudBackupSetting
import com.avlreminder.ui.theme.AvlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** "Cloud-Backup (experimentell)" in the settings (A-14): setting up, backing up by hand, diagnosis. */
@RunWith(AndroidJUnit4::class)
class CloudBackupUiTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recorder : CloudActions {
        val calls = mutableListOf<String>()
        override fun setEnabled(on: Boolean) { calls += "enabled $on" }
        override fun signIn() { calls += "signIn" }
        override fun continueSetup() { calls += "continue" }
        override fun confirmKey(writtenDown: Boolean) { calls += "confirm $writtenDown" }
        override fun submitKey(text: String?) { calls += "key $text" }
        override fun saveKey() { calls += "saveKey" }
        override fun fetchKey() { calls += "fetchKey" }
        override fun reset() { calls += "reset" }
        override fun backUp() { calls += "backUp" }
        override fun sendDiagnosis() { calls += "diagnosis" }
        override fun answerOverwrite(yes: Boolean) { calls += "overwrite $yes" }
    }

    private fun show(status: CloudStatus): Recorder {
        val actions = Recorder()
        compose.setContent { AvlTheme(dark = false) { CloudBackupSetting(status, actions) } }
        return actions
    }

    @Test
    fun switchingOnAsksFirstAndCancelLeavesItOff() {
        val actions = show(CloudStatus(CloudPhase.Off))
        compose.onNodeWithTag("cloudSwitch").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithTag("cloudSwitch").performClick()
        compose.onNodeWithTag("cloudOk").performClick()
        assertEquals(listOf("enabled true"), actions.calls)
    }

    // KRITISCH: one tap is not enough to confirm the recovery key; after that it is never shown again.
    @Test
    fun theRecoveryKeyNeedsTwoTapsToConfirm() {
        val actions = show(CloudStatus(CloudPhase.ShowKey("ABCD-EFGH")))
        compose.onNodeWithTag("cloudKey").assertExists()
        compose.onNodeWithTag("cloudWritten").performClick()
        compose.onNodeWithText("Nochmal ansehen").performClick()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithTag("cloudWritten").performClick()
        compose.onNodeWithTag("cloudWrittenOk").performClick()
        assertEquals(listOf("confirm true"), actions.calls)
    }

    @Test
    fun whileTheKeyShowsTheSwitchCannotBeTurnedOff() {
        show(CloudStatus(CloudPhase.ShowKey("ABCD-EFGH")))
        compose.onNodeWithTag("cloudSwitch").assertIsNotEnabled()
    }

    @Test
    fun theTypedKeyIsHandedOnAndAnEmptyOneCannotBeSent() {
        val actions = show(CloudStatus(CloudPhase.AskKey, notes = listOf("Dieser Schlüssel passt nicht zum Backup dieses Kontos.")))
        compose.onNodeWithText("Dieser Schlüssel passt nicht zum Backup dieses Kontos.").assertExists()
        compose.onNodeWithTag("cloudKeySubmit").assertIsNotEnabled()
        compose.onNodeWithTag("cloudKeyInput").performTextInput("abcd efgh")
        compose.onNodeWithTag("cloudKeySubmit").performClick()
        assertEquals(listOf("key abcd efgh"), actions.calls)
    }

    @Test
    fun readyShowsTheFingerprintAndAnError() {
        show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-123456789", "AB12 CD34", unlocked = false), error = "Server gerade nicht erreichbar"))
        compose.onNodeWithTag("cloudFingerprint").assertExists()
        compose.onNodeWithText("Fingerabdruck AB12 CD34").assertExists()
        compose.onNodeWithText("Nicht freigeschaltet: dieses Gerät kann nichts sichern.").assertExists()
        compose.onNodeWithTag("cloudError").assertExists()
    }

    // KRITISCH: one tap is enough only when the key is in the password service (7.33).
    @Test
    fun aSavedKeyNeedsOneTapAndTellsAboutDeviceEncryption() {
        val actions = show(CloudStatus(CloudPhase.ShowKey("ABCD-EFGH", saved = true)))
        compose.onNodeWithTag("cloudKeySaved").assertExists()
        compose.onNodeWithText("Geräteverschlüsselung", substring = true).assertExists()
        compose.onNodeWithTag("cloudWritten").assertDoesNotExist()
        compose.onNodeWithTag("cloudDone").performClick()
        assertEquals(listOf("confirm true"), actions.calls)
    }

    @Test
    fun anUnsavedKeyCanBeOfferedToThePasswordServiceAgain() {
        val actions = show(CloudStatus(CloudPhase.ShowKey("ABCD-EFGH")))
        compose.onNodeWithTag("cloudDone").assertDoesNotExist()
        compose.onNodeWithTag("cloudSaveKey").performClick()
        assertEquals(listOf("saveKey"), actions.calls)
    }

    @Test
    fun resettingAsksFirstAndCancelDoesNothing() {
        val actions = show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true)))
        compose.onNodeWithTag("cloudReset").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithTag("cloudReset").performClick()
        compose.onNodeWithTag("cloudResetOk").performClick()
        assertEquals(listOf("reset"), actions.calls)
    }

    @Test
    fun noResetWhileOff() {
        show(CloudStatus(CloudPhase.Off))
        compose.onNodeWithTag("cloudReset").assertDoesNotExist()
    }

    @Test
    fun noResetWhileTheKeyShows() {
        show(CloudStatus(CloudPhase.ShowKey("ABCD-EFGH")))
        compose.onNodeWithTag("cloudReset").assertDoesNotExist()
    }

    @Test
    fun theKeyCanBeFetchedFromThePasswordService() {
        val actions = show(CloudStatus(CloudPhase.AskKey))
        compose.onNodeWithTag("cloudFetchKey").performClick()
        assertEquals(listOf("fetchKey"), actions.calls)
    }

    @Test
    fun readyAndUnlockedBacksUpWithOneTapAndSaysWhenItLastDid() {
        val actions = show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true, lastBackup = Instant.parse("2026-11-08T10:00:00Z"))))
        compose.onNodeWithText("Zuletzt gesichert:", substring = true).assertExists()
        compose.onNodeWithTag("cloudBackUp").performClick()
        assertEquals(listOf("backUp"), actions.calls)
    }

    @Test
    fun neverBackedUpSaysSo() {
        show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true)))
        compose.onNodeWithText("Noch nicht gesichert").assertExists()
    }

    // KRITISCH: a device that is not unlocked offers neither backing up nor a diagnosis.
    @Test
    fun notUnlockedOffersNoBackupAndNoDiagnosis() {
        show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = false)))
        compose.onNodeWithTag("cloudBackUp").assertDoesNotExist()
        compose.onNodeWithTag("cloudDiagnosis").assertDoesNotExist()
    }

    @Test
    fun aDiagnosisIsSentOnlyAfterAskingAndSaysWhatGoesOut() {
        val actions = show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true)))
        compose.onNodeWithTag("cloudDiagnosis").performClick()
        compose.onNodeWithText("Keine Reminder", substring = true).assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithTag("cloudDiagnosis").performClick()
        compose.onNodeWithTag("cloudDiagnosisOk").performClick()
        assertEquals(listOf("diagnosis"), actions.calls)
    }

    // KRITISCH: overwriting another device's backup needs the person's tap (E-B2b-6).
    @Test
    fun anotherDevicesBackupIsOverwrittenOrKeptByTap() {
        val actions = show(CloudStatus(CloudPhase.AskOverwrite(Instant.parse("2026-11-01T08:00:00Z"))))
        compose.onNodeWithTag("cloudOverwriteText").assertExists()
        compose.onNodeWithTag("cloudSwitch").assertIsNotEnabled()
        compose.onNodeWithTag("cloudReset").assertDoesNotExist()
        compose.onNodeWithTag("cloudKeep").performClick()
        compose.onNodeWithTag("cloudOverwrite").performClick()
        assertEquals(listOf("overwrite false", "overwrite true"), actions.calls)
    }
}
