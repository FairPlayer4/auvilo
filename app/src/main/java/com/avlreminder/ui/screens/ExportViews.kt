package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.restore.ExportFile
import com.avlreminder.restore.LocalStatus
import com.avlreminder.ui.theme.Avl

/** Which password dialog is open: before exporting (twice) or before importing (once). */
private enum class FileAsk { EXPORT, IMPORT }

/**
 * "Sicherungsdatei" (Anf. 7.28 PS14, playstore.md N4): data and settings as a file of the person's
 * own, encrypted with a password, without photos and audio. The password comes first; then
 * Android's file dialog chooses where. Importing asks like any restore ([RestoreQuestion]).
 */
@Composable
fun ExportFileSetting(status: LocalStatus, actions: LocalActions) {
    val c = Avl.colors
    var ask by remember { mutableStateOf<FileAsk?>(null) }
    Column(Modifier.padding(top = 12.dp)) {
        Text("Sicherungsdatei", color = c.text, fontSize = 15.sp)
        Text(
            "Daten und Einstellungen als Datei, mit einem eigenen Passwort verschlüsselt; ohne Fotos und Audio. " +
                "Zum Umzug auf ein anderes Handy oder als zusätzliche Sicherung.",
            color = c.muted, fontSize = 13.sp,
        )
        status.exported?.let { name ->
            Text(
                "Gespeichert: $name. Ohne das Passwort lässt sich die Datei nicht öffnen.",
                Modifier.padding(top = 4.dp).testTag("fileExported"), color = c.text, fontSize = 14.sp,
            )
            TextButton(onClick = actions::dismissExported) { Text("OK") }
        }
        Row(Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { ask = FileAsk.EXPORT }, Modifier.testTag("fileExport")) { Text("Exportieren …") }
            TextButton(onClick = { ask = FileAsk.IMPORT }, Modifier.padding(start = 8.dp).testTag("fileImport")) { Text("Importieren …") }
        }
    }
    when (ask) {
        FileAsk.EXPORT -> FilePasswordDialog(twice = true, onCancel = { ask = null }) { ask = null; actions.exportFile(it) }
        FileAsk.IMPORT -> FilePasswordDialog(twice = false, onCancel = { ask = null }) { ask = null; actions.importFile(it) }
        null -> Unit
    }
}

/** The password for an export file; [twice] for a new one, which must be typed again. */
@Composable
private fun FilePasswordDialog(twice: Boolean, onCancel: () -> Unit, onOk: (CharArray) -> Unit) {
    val c = Avl.colors
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val problem = when {
        !twice -> if (first.isEmpty()) "" else null
        first.length < ExportFile.MIN_PASSWORD -> "Mindestens ${ExportFile.MIN_PASSWORD} Zeichen."
        second != first -> "Die beiden Eingaben sind verschieden."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("filePassword"),
        title = { Text(if (twice) "Passwort für die Datei" else "Passwort der Datei") },
        text = {
            Column {
                if (twice) {
                    Text(
                        "Mindestens ${ExportFile.MIN_PASSWORD} Zeichen. Speichere es im Passwortmanager: Ohne das Passwort " +
                            "lässt sich die Datei nicht öffnen.",
                        color = c.muted, fontSize = 13.sp,
                    )
                }
                PasswordField(first, "Passwort", "filePassword1") { first = it }
                if (twice) PasswordField(second, "Noch einmal", "filePassword2") { second = it }
                if (twice && problem != null && first.isNotEmpty()) Text(problem, color = c.strict, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onOk(first.toCharArray()) },
                enabled = problem == null,
                modifier = Modifier.testTag("filePasswordOk"),
            ) { Text("Datei wählen …") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

@Composable
private fun PasswordField(value: String, label: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(tag),
    )
}
