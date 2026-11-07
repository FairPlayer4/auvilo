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
import com.avlreminder.cloud.CloudPhase
import com.avlreminder.cloud.CloudStatus
import com.avlreminder.ui.theme.Avl

/** What the settings can do with the cloud backup; the app wires it to `CloudBackup`. */
interface CloudActions {
    fun setEnabled(on: Boolean) {}
    fun signIn() {}
    fun continueSetup() {}
    fun confirmKey(writtenDown: Boolean) {}
    fun submitKey(text: String?) {}
}

/**
 * "Cloud-Backup (experimentell)" (A-14), up to "eingerichtet". Backing up, restoring and
 * deleting follow in the next steps.
 */
@Composable
fun CloudBackupSetting(status: CloudStatus, actions: CloudActions) {
    val c = Avl.colors
    val phase = status.phase
    var asking by remember { mutableStateOf(false) }
    val busy = phase is CloudPhase.Busy || phase is CloudPhase.ShowKey || phase is CloudPhase.AskKey
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
            is CloudPhase.ShowKey -> ShowKey(phase.key, actions)
            CloudPhase.AskKey -> AskKey(actions)
            is CloudPhase.Ready -> {
                Text("Fingerabdruck ${phase.fingerprint}", Modifier.padding(top = 8.dp).testTag("cloudFingerprint"), color = c.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text("Gerät ${phase.device.take(12)}", color = c.muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                if (!phase.unlocked) {
                    Text("Nicht freigeschaltet: dieses Gerät kann nichts sichern.", color = c.strict, fontSize = 13.sp)
                }
                Text("Sichern folgt mit dem nächsten Schritt.", Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp)
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
}

private fun headline(phase: CloudPhase): String = when (phase) {
    CloudPhase.Off -> "Aus"
    CloudPhase.SignedOut -> "Nicht angemeldet"
    is CloudPhase.Busy -> "Bitte warten"
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

/** A new recovery key; nothing is stored until it is written down (Einrichtung, KRITISCH). */
@Composable
private fun ShowKey(key: String, actions: CloudActions) {
    val c = Avl.colors
    var sure by remember { mutableStateOf(false) }
    SecureScreen()
    Text(
        "Diesen Wiederherstellungsschlüssel auf Papier aufschreiben. Ohne ihn sind die gesicherten Daten weg, wenn " +
            "das Handy verloren geht. Nicht fotografieren, nicht in die Cloud.",
        Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp,
    )
    Text(key, Modifier.padding(vertical = 12.dp).testTag("cloudKey"), color = c.text, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { sure = true }, Modifier.testTag("cloudWritten")) { Text("Aufgeschrieben") }
        OutlinedButton(onClick = { actions.confirmKey(false) }, Modifier.testTag("cloudKeyCancel")) { Text("Abbrechen") }
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
}
