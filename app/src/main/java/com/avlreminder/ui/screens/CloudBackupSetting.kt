package com.avlreminder.ui.screens

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.cloud.BackupReport
import com.avlreminder.cloud.CloudPhase
import com.avlreminder.cloud.CloudStatus
import com.avlreminder.ui.theme.Avl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the settings can do with the cloud backup; the app wires it to `CloudBackup`. */
interface CloudActions {
    fun setEnabled(on: Boolean) {}
    fun signIn() {}
    fun continueSetup() {}
    fun confirmKey(writtenDown: Boolean) {}
    fun submitKey(text: String?) {}
    fun saveKey() {}
    fun fetchKey() {}
    fun backUp() {}
    fun sendDiagnosis() {}
    fun answerOverwrite(yes: Boolean) {}
    fun check() {}
    fun backUpAndCheck() {}
    fun restore() {}
    fun answerRestore(yes: Boolean) {}
    fun answerBrake(yes: Boolean) {}
    fun deleteBackup() {}
    fun signOut() {}
    fun deleteCloudData() {}
}

/**
 * "Cloud-Backup (experimentell)" (A-14): setting up, backing up, checking and restoring
 * (cloud-entscheidungen.md W, R-3), deleting and sending a diagnosis.
 */
@Composable
fun CloudBackupSetting(status: CloudStatus, actions: CloudActions) {
    val c = Avl.colors
    val phase = status.phase
    var asking by remember { mutableStateOf(false) }
    var deletingCloud by remember { mutableStateOf(false) }
    var resettingPhone by remember { mutableStateOf(false) }
    var deletingBackup by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var diagnosing by remember { mutableStateOf(false) }
    val busy = phase is CloudPhase.Busy || phase is CloudPhase.ShowKey || phase is CloudPhase.AskKey || phase is CloudPhase.AskOverwrite ||
        phase is CloudPhase.AskRestore || phase is CloudPhase.AskBrake
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(headline(phase), color = c.text, fontSize = 15.sp, fontWeight = if (phase is CloudPhase.Ready) FontWeight.SemiBold else null)
                Text("Ende-zu-Ende verschlüsselt, Server in der EU", color = c.muted, fontSize = 13.sp)
            }
            Switch(
                phase != CloudPhase.Off, { on -> if (on) asking = true else actions.setEnabled(false) },
                Modifier.testTag("cloudSwitch"), enabled = !busy,
            )
        }
        when (phase) {
            CloudPhase.Off -> Unit
            CloudPhase.SignedOut -> Button(onClick = actions::signIn, Modifier.padding(top = 8.dp).testTag("cloudSignIn")) { Text("Mit Google anmelden") }
            is CloudPhase.Busy -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(phase.what, Modifier.padding(start = 8.dp), color = c.muted, fontSize = 14.sp)
            }
            CloudPhase.NotSetUp -> {
                Text("Die Einrichtung ist nicht abgeschlossen.", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
                Button(onClick = actions::continueSetup, Modifier.padding(top = 4.dp).testTag("cloudContinue")) { Text("Einrichtung fortsetzen") }
            }
            is CloudPhase.ShowKey -> ShowKey(phase.key, phase.saved, actions)
            CloudPhase.AskKey -> AskKey(actions)
            is CloudPhase.AskOverwrite -> AskOverwrite(phase.created, actions)
            is CloudPhase.AskRestore -> RestoreQuestion(
                "Sicherung vom ${shortTime(phase.report.created)}, " +
                    (if (phase.report.fromThisDevice) "von diesem Gerät." else "von einem anderen Gerät."),
                phase.report.comparison,
                actions::answerRestore,
            )
            is CloudPhase.AskBrake -> AskBrake(phase.before, phase.now, actions)
            is CloudPhase.Ready -> {
                Text("Fingerabdruck ${phase.fingerprint}", Modifier.padding(top = 8.dp).testTag("cloudFingerprint"), color = c.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text("Gerät ${phase.device.take(12)}", color = c.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(
                    phase.lastBackup?.let { "Zuletzt gesichert: ${shortTime(it)}" } ?: "Noch nicht gesichert",
                    Modifier.padding(top = 4.dp).testTag("cloudLastBackup"), color = c.text, fontSize = 14.sp,
                )
                when {
                    phase.braked -> Text(
                        "Automatisches Sichern pausiert: Seit der letzten Sicherung fehlen viele Reminder. „Jetzt sichern“ " +
                            "fragt nach, ob das so stimmt.",
                        Modifier.testTag("cloudBraked"), color = c.strict, fontSize = 13.sp,
                    )
                    phase.paused -> Text(
                        "Automatisches Sichern pausiert: Ein anderes Gerät hat zuletzt gesichert. „Jetzt sichern“ fragt, " +
                            "ob seine Sicherung ersetzt werden soll.",
                        Modifier.testTag("cloudPaused"), color = c.strict, fontSize = 13.sp,
                    )
                    phase.automatic -> Text(
                        "Sichert stündlich von selbst, wenn sich etwas geändert hat und Netz da ist.",
                        Modifier.testTag("cloudAutomatic"), color = c.muted, fontSize = 13.sp,
                    )
                }
                if (phase.unlocked) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions::backUp, Modifier.testTag("cloudBackUp")) { Text("Jetzt sichern") }
                        OutlinedButton(onClick = actions::check, Modifier.testTag("cloudCheck")) { Text("Sicherung prüfen") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = actions::restore, Modifier.testTag("cloudRestore")) { Text("Wiederherstellen") }
                        OutlinedButton(onClick = { diagnosing = true }, Modifier.testTag("cloudDiagnosis")) { Text("Diagnose senden") }
                    }
                    status.report?.let { Report(it, actions) }
                } else {
                    Text("Nicht freigeschaltet: dieses Gerät kann nichts sichern.", color = c.strict, fontSize = 13.sp)
                    Text(
                        "Ein freigeschaltetes Gerät des Kontos kann es freischalten, oder hier mit dem Wiederherstellungsschlüssel.",
                        color = c.muted, fontSize = 13.sp,
                    )
                    // R3 of Review-Lauf 9: without this the only way out was signing out and in again.
                    OutlinedButton(onClick = actions::continueSetup, Modifier.padding(top = 4.dp).testTag("cloudUnlock")) {
                        Text("Mit Wiederherstellungsschlüssel freischalten")
                    }
                }
            }
        }
        if (phase is CloudPhase.Ready && phase.unlocked) {
            TextButton(onClick = { deletingBackup = true }, Modifier.padding(top = 4.dp).testTag("cloudDeleteBackup")) {
                Text("Sicherung löschen", color = c.strict)
            }
        }
        if (phase is CloudPhase.Ready || phase == CloudPhase.NotSetUp || phase == CloudPhase.SignedOut) {
            if (phase != CloudPhase.SignedOut) {
                TextButton(onClick = { signingOut = true }, Modifier.testTag("cloudSignOut")) { Text("Dieses Gerät abmelden") }
            }
            if (phase == CloudPhase.SignedOut) {
                // Without a session nothing can be deleted on the server (Review-Lauf 10, 2026-11-09).
                TextButton(onClick = { resettingPhone = true }, Modifier.testTag("cloudResetPhone")) {
                    Text("Nur dieses Handy zurücksetzen", color = c.strict)
                }
            } else {
                TextButton(onClick = { deletingCloud = true }, Modifier.testTag("cloudDeleteAll")) {
                    Text("Cloud-Daten löschen", color = c.strict)
                }
            }
        }
        status.notes.forEach { Text(it, Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp) }
        status.error?.let { Text(it, Modifier.padding(top = 6.dp).testTag("cloudError"), color = c.strict, fontSize = 14.sp) }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Cloud-Backup einschalten?") },
            text = {
                Text(
                    "Experimentell. Nach der Anmeldung mit Google entstehen Schlüssel auf diesem Handy; Reminder und " +
                        "Einstellungen verlassen es nur verschlüsselt. Ohne Netz oder bei Ausfall des Servers läuft die " +
                        "App normal weiter.",
                )
            },
            confirmButton = { TextButton(onClick = { actions.setEnabled(true); asking = false }, Modifier.testTag("cloudOk")) { Text("Einschalten") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Abbrechen") } },
        )
    }
    if (diagnosing) {
        AlertDialog(
            onDismissRequest = { diagnosing = false },
            title = { Text("Diagnose senden?") },
            text = {
                Text(
                    "Sendet das Protokoll des Cloud-Backups an den Server: Zeitpunkte, Schritte, Fehlerarten, Größen und " +
                        "App-Version. Keine Reminder, keine Einstellungen, keine Schlüssel. Der Server löscht es nach 14 Tagen.",
                )
            },
            confirmButton = {
                TextButton(onClick = { diagnosing = false; actions.sendDiagnosis() }, Modifier.testTag("cloudDiagnosisOk")) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { diagnosing = false }) { Text("Abbrechen") } },
        )
    }
    if (signingOut) {
        AlertDialog(
            onDismissRequest = { signingOut = false },
            title = { Text("Dieses Gerät abmelden?") },
            text = {
                Text(
                    "Sperrt dieses Handy beim Server und löscht hier Schlüssel und Anmeldung. Die Sicherung und das " +
                        "Schlüssel-Backup auf dem Server bleiben, für andere Geräte und zum Wiederherstellen. Reminder und " +
                        "Einstellungen bleiben.",
                )
            },
            confirmButton = {
                TextButton(onClick = { signingOut = false; actions.signOut() }, Modifier.testTag("cloudSignOutOk")) { Text("Abmelden") }
            },
            dismissButton = { TextButton(onClick = { signingOut = false }) { Text("Abbrechen") } },
        )
    }
    if (deletingBackup) {
        val last = (phase as? CloudPhase.Ready)?.lastBackup
        AlertDialog(
            onDismissRequest = { deletingBackup = false },
            title = { Text("Sicherung löschen?") },
            text = {
                Text(
                    (last?.let { "Löscht die Sicherung vom ${shortTime(it)} auf dem Server. " } ?: "Löscht die Sicherung auf dem Server. ") +
                        "Auch die älteren Fassungen der letzten 7 Tage werden gelöscht, das lässt sich nicht rückgängig machen. " +
                        "Der Schlüssel bleibt; automatisch gesichert wird erst wieder nach „Jetzt sichern“.",
                )
            },
            confirmButton = {
                TextButton(onClick = { deletingBackup = false; actions.deleteBackup() }, Modifier.testTag("cloudDeleteBackupOk")) {
                    Text("Löschen", color = c.strict)
                }
            },
            dismissButton = { TextButton(onClick = { deletingBackup = false }) { Text("Abbrechen") } },
        )
    }
    if (deletingCloud) DeleteCloudData(onDone = { deletingCloud = false }, actions)
    if (resettingPhone) {
        AlertDialog(
            onDismissRequest = { resettingPhone = false },
            title = { Text("Nur dieses Handy zurücksetzen?") },
            text = {
                Text(
                    "Ohne Anmeldung kann die App auf dem Server nichts löschen. Gelöscht werden nur Schlüssel und Anmeldung " +
                        "des Cloud-Backups auf diesem Handy. Die Sicherung auf dem Server bleibt und ist mit dem " +
                        "Wiederherstellungsschlüssel erreichbar. Um sie zu löschen, erst neu anmelden. Reminder und " +
                        "Einstellungen bleiben.",
                )
            },
            confirmButton = {
                TextButton(onClick = { resettingPhone = false; actions.deleteCloudData() }, Modifier.testTag("cloudResetPhoneOk")) {
                    Text("Zurücksetzen", color = c.strict)
                }
            },
            dismissButton = { TextButton(onClick = { resettingPhone = false }) { Text("Abbrechen") } },
        )
    }
}

/** The word to type before "Cloud-Daten löschen" (R-3). */
const val DELETE_WORD = "löschen"

/**
 * "Cloud-Daten löschen" (R-3): deletes the backups of every device of the account for good, so it
 * asks for the word [DELETE_WORD] and says that "Dieses Gerät abmelden" is the way for a test device.
 */
@Composable
private fun DeleteCloudData(onDone: () -> Unit, actions: CloudActions) {
    val c = Avl.colors
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Cloud-Daten endgültig löschen?") },
        text = {
            Column {
                Text(
                    "Löscht auf dem Server die Sicherung mit allen älteren Fassungen und das Schlüssel-Backup, für alle Geräte " +
                        "dieses Kontos, und sperrt dort dieses Handy. Das lässt sich nicht rückgängig machen. Reminder und " +
                        "Einstellungen auf diesem Handy bleiben. Nur dieses Handy abmelden geht mit „Dieses Gerät abmelden“.",
                )
                Text("Zum Bestätigen „$DELETE_WORD“ eingeben.", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
                OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth().testTag("cloudDeleteWord"), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(); actions.deleteCloudData() },
                Modifier.testTag("cloudDeleteAllOk"),
                enabled = typed.trim().equals(DELETE_WORD, ignoreCase = true),
            ) { Text("Endgültig löschen", color = c.strict) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}

/** The result of "Sicherung prüfen" (W-2). */
@Composable
private fun Report(report: BackupReport, actions: CloudActions) {
    val c = Avl.colors
    Column(Modifier.padding(top = 8.dp).testTag("cloudReport")) {
        val from = if (report.fromThisDevice) "" else " (von einem anderen Gerät)"
        // Found on the emulator: a backup of another device never held the data here, so
        // "changed since" is wrong, and backing up here would offer to replace the other device's
        // backup. That device's backup is taken over with "Wiederherstellen" instead.
        if (!report.comparison.same && !report.fromThisDevice) {
            Text(
                "Sicherung vom ${shortTime(report.created)} von einem anderen Gerät:",
                Modifier.testTag("cloudOtherDevice"), color = c.muted, fontSize = 13.sp,
            )
        } else if (!report.comparison.same && !report.unchanged) {
            Text("Seit der Sicherung geändert – Abweichungen sind zu erwarten.", Modifier.testTag("cloudChangedSince"), color = c.muted, fontSize = 13.sp)
        }
        ComparisonLines(report.comparison, "Sicherung vom ${shortTime(report.created)}$from entspricht den Daten auf diesem Gerät.")
        if (!report.unchanged && report.fromThisDevice) {
            TextButton(onClick = actions::backUpAndCheck, Modifier.testTag("cloudBackUpAndCheck")) { Text("Jetzt sichern und erneut prüfen") }
        }
    }
}

/** Many reminders are missing since the last backup (R-2). */
@Composable
private fun AskBrake(before: Int, now: Int, actions: CloudActions) {
    val c = Avl.colors
    Text(
        "Bei der letzten Sicherung gab es $before Reminder, jetzt $now. Trotzdem sichern? Die Sicherung auf dem Server " +
            "hat dann auch nur noch $now.",
        Modifier.padding(top = 8.dp).testTag("cloudBrakeText"), color = c.text, fontSize = 14.sp,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { actions.answerBrake(true) }, Modifier.testTag("cloudBrakeYes")) { Text("Trotzdem sichern") }
        OutlinedButton(onClick = { actions.answerBrake(false) }, Modifier.testTag("cloudBrakeNo")) { Text("Nicht sichern") }
    }
}

private fun headline(phase: CloudPhase): String = when (phase) {
    CloudPhase.Off -> "Aus"
    CloudPhase.SignedOut -> "Nicht angemeldet"
    is CloudPhase.Busy -> "Bitte warten"
    is CloudPhase.AskOverwrite, is CloudPhase.AskBrake -> "Sichern"
    is CloudPhase.AskRestore -> "Wiederherstellen"
    CloudPhase.NotSetUp, is CloudPhase.ShowKey, CloudPhase.AskKey -> "Einrichtung"
    is CloudPhase.Ready -> "Eingerichtet"
}

/** No screenshots and no recent-apps preview while a recovery key is on screen. */
@Composable
private fun SecureScreen() {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * A new recovery key; nothing is stored until it is kept (Einrichtung, KRITISCH). In the password
 * service ([saved], 7.33) one tap confirms; written down only, a second question makes sure.
 */
@Composable
private fun ShowKey(key: String, saved: Boolean, actions: CloudActions) {
    val c = Avl.colors
    var sure by remember { mutableStateOf(false) }
    SecureScreen()
    if (saved) {
        Text(
            "Der Wiederherstellungsschlüssel ist im Passwortmanager gespeichert. Ohne ihn sind die gesicherten Daten " +
                "weg, wenn das Handy verloren geht.",
            Modifier.padding(top = 8.dp).testTag("cloudKeySaved"), color = c.text, fontSize = 14.sp,
        )
        Text(
            "Im Google Passwortmanager ist er nur mit eingeschalteter Geräteverschlüsselung Ende-zu-Ende geschützt. " +
                "Zusätzlich auf Papier aufschreiben ist am sichersten.",
            Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp,
        )
    } else {
        Text(
            "Diesen Wiederherstellungsschlüssel im Passwortmanager speichern oder auf Papier aufschreiben. Ohne ihn " +
                "sind die gesicherten Daten weg, wenn das Handy verloren geht. Nicht fotografieren.",
            Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp,
        )
    }
    Text(key, Modifier.padding(vertical = 12.dp).testTag("cloudKey"), color = c.text, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (saved) {
            Button(onClick = { actions.confirmKey(true) }, Modifier.testTag("cloudDone")) { Text("Fertig") }
        } else {
            Button(onClick = { sure = true }, Modifier.testTag("cloudWritten")) { Text("Aufgeschrieben") }
        }
        OutlinedButton(onClick = { actions.confirmKey(false) }, Modifier.testTag("cloudKeyCancel")) { Text("Abbrechen") }
    }
    if (!saved) {
        TextButton(onClick = actions::saveKey, Modifier.testTag("cloudSaveKey")) { Text("Im Passwortmanager speichern") }
    }
    if (sure) {
        AlertDialog(
            onDismissRequest = { sure = false },
            title = { Text("Wirklich aufgeschrieben?") },
            text = { Text("Danach zeigt die App den Schlüssel nie wieder.") },
            confirmButton = { TextButton(onClick = { sure = false; actions.confirmKey(true) }, Modifier.testTag("cloudWrittenOk")) { Text("Ja") } },
            dismissButton = { TextButton(onClick = { sure = false }) { Text("Nochmal ansehen") } },
        )
    }
}

/** The account has keys: the recovery key unlocks them on this device. */
@Composable
private fun AskKey(actions: CloudActions) {
    val c = Avl.colors
    var text by remember { mutableStateOf("") }
    SecureScreen()
    Text("Wiederherstellungsschlüssel eingeben (Bindestriche und Leerzeichen sind egal).", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
    OutlinedTextField(
        text, { text = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("cloudKeyInput"),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
        // No suggestions and no learning by the keyboard.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { actions.submitKey(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("cloudKeySubmit")) { Text("Prüfen") }
        OutlinedButton(onClick = { actions.submitKey(null) }) { Text("Abbrechen") }
    }
    TextButton(onClick = actions::fetchKey, Modifier.testTag("cloudFetchKey")) { Text("Aus dem Passwortmanager holen") }
}

private val SHORT_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun shortTime(t: Instant): String = SHORT_TIME.format(t.atZone(ZoneId.systemDefault()))

/**
 * Another device backed up last (E-B2b-6). Overwriting replaces its backup on the server; its data
 * are then only on that device.
 */
@Composable
private fun AskOverwrite(created: Instant, actions: CloudActions) {
    val c = Avl.colors
    Text(
        "Die Sicherung auf dem Server stammt von einem anderen Gerät (${shortTime(created)}). Mit diesem Handy " +
            "überschreiben? Die Daten des anderen Geräts sind dann nur noch dort.",
        Modifier.padding(top = 8.dp).testTag("cloudOverwriteText"), color = c.text, fontSize = 14.sp,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { actions.answerOverwrite(true) }, Modifier.testTag("cloudOverwrite")) { Text("Überschreiben") }
        OutlinedButton(onClick = { actions.answerOverwrite(false) }, Modifier.testTag("cloudKeep")) { Text("Nicht sichern") }
    }
}
