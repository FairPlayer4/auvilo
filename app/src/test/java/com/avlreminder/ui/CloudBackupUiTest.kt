package com.avlreminder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.cloud.BackupReport
import com.avlreminder.cloud.CloudPhase
import com.avlreminder.cloud.CloudStatus
import com.avlreminder.restore.oneReminderChanged
import com.avlreminder.restore.sameEverywhere
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
        override fun backUp() { calls += "backUp" }
        override fun sendDiagnosis() { calls += "diagnosis" }
        override fun answerOverwrite(yes: Boolean) { calls += "overwrite $yes" }
        override fun check() { calls += "check" }
        override fun backUpAndCheck() { calls += "backUpAndCheck" }
        override fun restore() { calls += "restore" }
        override fun answerRestore(yes: Boolean) { calls += "restore $yes" }
        override fun answerBrake(yes: Boolean) { calls += "brake $yes" }
        override fun deleteBackup() { calls += "deleteBackup" }
        override fun signOut() { calls += "signOut" }
        override fun deleteCloudData() { calls += "deleteCloudData" }
    }

    private fun show(status: CloudStatus): Recorder {
        val actions = Recorder()
        // Scrollable like the settings, so that buttons further down can be reached.
        compose.setContent { AvlTheme(dark = false) { Column(Modifier.verticalScroll(rememberScrollState())) { CloudBackupSetting(status, actions) } } }
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

    private val readyUnlocked = CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true, lastBackup = Instant.parse("2026-11-08T10:00:00Z"))

    // KRITISCH: deleting the cloud data for good (R-3) needs the typed word; cancel does nothing.
    @Test
    fun deletingTheCloudDataNeedsTheTypedWord() {
        val actions = show(CloudStatus(readyUnlocked))
        compose.onNodeWithTag("cloudDeleteAll").performScrollTo().performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithTag("cloudDeleteAll").performScrollTo().performClick()
        compose.onNodeWithTag("cloudDeleteAllOk").assertIsNotEnabled()
        compose.onNodeWithTag("cloudDeleteWord").performTextInput("lösch")
        compose.onNodeWithTag("cloudDeleteAllOk").assertIsNotEnabled()
        compose.onNodeWithTag("cloudDeleteWord").performTextInput("en")
        compose.onNodeWithTag("cloudDeleteAllOk").performClick()
        assertEquals(listOf("deleteCloudData"), actions.calls)
    }

    // KRITISCH: signing out and deleting the backup ask first (R-3, W-8).
    @Test
    fun signingOutAndDeletingTheBackupAskFirst() {
        val actions = show(CloudStatus(readyUnlocked))
        for ((button, ok, call) in listOf(
            Triple("cloudSignOut", "cloudSignOutOk", "signOut"),
            Triple("cloudDeleteBackup", "cloudDeleteBackupOk", "deleteBackup"),
        )) {
            actions.calls.clear()
            compose.onNodeWithTag(button).performScrollTo().performClick()
            compose.onNodeWithText("Abbrechen").performClick()
            assertEquals(button, emptyList<String>(), actions.calls)
            compose.onNodeWithTag(button).performScrollTo().performClick()
            compose.onNodeWithTag(ok).performClick()
            assertEquals(button, listOf(call), actions.calls)
        }
    }

    @Test
    fun deletingTheBackupSaysThatTheOlderVersionsGoToo() {
        show(CloudStatus(readyUnlocked))
        compose.onNodeWithTag("cloudDeleteBackup").performScrollTo().performClick()
        compose.onNodeWithText("älteren Fassungen der letzten 7 Tage", substring = true).assertExists()
    }

    @Test
    fun nothingToDeleteWhileOffOrWhileTheKeyShows() {
        var shown by mutableStateOf(CloudStatus(CloudPhase.Off))
        compose.setContent { AvlTheme(dark = false) { CloudBackupSetting(shown, Recorder()) } }
        for (phase in listOf(CloudPhase.Off, CloudPhase.ShowKey("ABCD-EFGH"))) {
            shown = CloudStatus(phase)
            compose.waitForIdle()
            for (tag in listOf("cloudDeleteAll", "cloudSignOut", "cloudDeleteBackup")) {
                compose.onNodeWithTag(tag).assertDoesNotExist()
            }
        }
    }

    // A device that is not unlocked cannot delete the backup, but can sign out.
    @Test
    fun notUnlockedCanSignOutButNotDeleteTheBackup() {
        show(CloudStatus(CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = false)))
        compose.onNodeWithTag("cloudDeleteBackup").assertDoesNotExist()
        compose.onNodeWithTag("cloudSignOut").assertExists()
        compose.onNodeWithTag("cloudRestore").assertDoesNotExist()
        compose.onNodeWithTag("cloudCheck").assertDoesNotExist()
    }

    @Test
    fun checkAndRestoreAreOneTapAway() {
        val actions = show(CloudStatus(readyUnlocked))
        compose.onNodeWithTag("cloudCheck").performClick()
        compose.onNodeWithTag("cloudRestore").performClick()
        assertEquals(listOf("check", "restore"), actions.calls)
    }

    // W-2: equal says so; differing lists the tables without contents; a change since the backup
    // offers to back up and check again.
    @Test
    fun theReportSaysWhetherTheBackupMatches() {
        val at = Instant.parse("2026-11-08T10:00:00Z")
        val actions = Recorder()
        var status by mutableStateOf(CloudStatus(readyUnlocked, report = BackupReport(at, fromThisDevice = true, unchanged = true, comparison = sameEverywhere())))
        compose.setContent { AvlTheme(dark = false) { CloudBackupSetting(status, actions) } }
        compose.onNodeWithTag("restoreSame").assertExists()
        compose.onNodeWithTag("cloudBackUpAndCheck").assertDoesNotExist()

        status = CloudStatus(readyUnlocked, report = BackupReport(at, true, unchanged = false, comparison = oneReminderChanged()))
        compose.waitForIdle()
        compose.onNodeWithTag("restoreDiff").assertExists()
        compose.onNodeWithText("Reminder: 1 geändert").assertExists()
        compose.onNodeWithTag("cloudChangedSince").assertExists()
        compose.onNodeWithTag("cloudOtherDevice").assertDoesNotExist()
        compose.onNodeWithTag("cloudBackUpAndCheck").performClick()
        assertEquals(listOf("backUpAndCheck"), actions.calls)

        // KRITISCH: another device's backup is no "change since", and checking it never offers to
        // back up over it (found on the emulator).
        status = CloudStatus(readyUnlocked, report = BackupReport(at, fromThisDevice = false, unchanged = false, comparison = oneReminderChanged()))
        compose.waitForIdle()
        compose.onNodeWithTag("cloudOtherDevice").assertExists()
        compose.onNodeWithTag("restoreDiff").assertExists()
        compose.onNodeWithTag("cloudChangedSince").assertDoesNotExist()
        compose.onNodeWithTag("cloudBackUpAndCheck").assertDoesNotExist()
    }

    // KRITISCH: replacing the data needs two taps (W-5); cancel at either step answers no or nothing.
    @Test
    fun restoringNeedsTwoTaps() {
        val report = BackupReport(Instant.parse("2026-11-08T10:00:00Z"), fromThisDevice = false, unchanged = true, comparison = oneReminderChanged())
        val actions = show(CloudStatus(CloudPhase.AskRestore(report)))
        compose.onNodeWithText("von einem anderen Gerät", substring = true).assertExists()
        compose.onNodeWithTag("restoreCounts").assertTextEquals("Reminder: 1 (hier: 1), Termine: 1 (hier: 1)")
        compose.onNodeWithTag("cloudSwitch").assertIsNotEnabled()
        compose.onNodeWithTag("restoreYes").performClick()
        compose.onNodeWithTag("restoreSureNo").performClick()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithTag("restoreYes").performClick()
        compose.onNodeWithTag("restoreSure").performClick()
        compose.onNodeWithTag("restoreNo").performClick()
        assertEquals(listOf("restore true", "restore false"), actions.calls)
    }

    // KRITISCH: the brake (R-2) shows the numbers and uploads only on "Trotzdem sichern".
    @Test
    fun theBrakeAsksWithTheNumbers() {
        val actions = show(CloudStatus(CloudPhase.AskBrake(before = 20, now = 5)))
        compose.onNodeWithText("20 Reminder, jetzt 5", substring = true).assertExists()
        compose.onNodeWithTag("cloudBrakeNo").performClick()
        compose.onNodeWithTag("cloudBrakeYes").performClick()
        assertEquals(listOf("brake false", "brake true"), actions.calls)
    }

    @Test
    fun aBrakedBackupSaysWhy() {
        show(CloudStatus(readyUnlocked.copy(braked = true)))
        compose.onNodeWithTag("cloudBraked").assertExists()
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
        compose.onNodeWithTag("cloudDeleteAll").assertDoesNotExist()
        compose.onNodeWithTag("cloudKeep").performClick()
        compose.onNodeWithTag("cloudOverwrite").performClick()
        assertEquals(listOf("overwrite false", "overwrite true"), actions.calls)
    }

    // Step 4: the status says whether it backs up by itself, or rests because of another device.
    @Test
    fun theStatusSaysWhetherItBacksUpByItself() {
        val at = Instant.parse("2026-11-08T10:00:00Z")
        var shown by mutableStateOf(CloudStatus(CloudPhase.Off))
        compose.setContent { AvlTheme(dark = false) { CloudBackupSetting(shown, Recorder()) } }
        for ((case, phase, tag) in listOf(
            Triple("noch nie von Hand gesichert", CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true), null),
            Triple("stündlich", CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true, lastBackup = at), "cloudAutomatic"),
            Triple("pausiert", CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = true, lastBackup = at, paused = true), "cloudPaused"),
            Triple("nicht freigeschaltet", CloudPhase.Ready("konto-1", "geraet-1", "AB12", unlocked = false, lastBackup = at), null),
        )) {
            shown = CloudStatus(phase)
            compose.waitForIdle()
            for (t in listOf("cloudAutomatic", "cloudPaused")) {
                assertEquals("$case: $t", if (t == tag) 1 else 0, compose.onAllNodesWithTag(t).fetchSemanticsNodes().size)
            }
        }
    }
}
