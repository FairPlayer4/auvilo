package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.restore.BackupTable
import com.avlreminder.restore.Comparison
import com.avlreminder.restore.LocalPhase
import com.avlreminder.restore.LocalStatus
import com.avlreminder.restore.RestoreKind
import com.avlreminder.restore.SwapFailure
import com.avlreminder.restore.TableDiff
import com.avlreminder.ui.theme.Avl
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The tables as the person knows them. */
fun BackupTable.label(): String = when (this) {
    BackupTable.REMINDER -> "Reminder"
    BackupTable.OCCURRENCE -> "Termine"
    BackupTable.EVENT -> "Ereignisse"
    BackupTable.PROOF -> "Nachweise"
    BackupTable.PREP_ANSWER -> "Vorbereitung"
    BackupTable.PLACE -> "Orte"
    BackupTable.CHECKLIST_ITEM -> "Checklisten"
    BackupTable.ITEM_DONE -> "Abgehakte Punkte"
    BackupTable.SUGGESTION -> "Verbesserungen"
    BackupTable.BLOCKER -> "Hindernisse"
    BackupTable.TEMPLATE -> "Vorlagen"
}

/** One line per differing table, without contents (W-2). */
fun TableDiff.describe(): String = buildList {
    if (changed > 0) add("$changed geändert")
    if (onlyHere > 0) add("$onlyHere nur hier")
    if (onlyBackup > 0) add("$onlyBackup nur in der Sicherung")
}.joinToString(", ", prefix = "${table.label()}: ")

/** How a backup compares with the data here: equal, or what differs (W-2). */
@Composable
fun ComparisonLines(comparison: Comparison, sameText: String) {
    val c = Avl.colors
    if (comparison.same) {
        Text(sameText, Modifier.testTag("restoreSame"), color = c.text, fontSize = 14.sp)
        return
    }
    Column(Modifier.testTag("restoreDiff")) {
        comparison.tables.filter { !it.same }.forEach { Text(it.describe(), color = c.text, fontSize = 13.sp) }
        if (!comparison.settingsSame) Text("Einstellungen: abweichend", color = c.text, fontSize = 13.sp)
    }
}

/**
 * Swap in? Shows the numbers (W-5, step 2), then asks twice: a tap on "Wiederherstellen" and a
 * dialog that says what happens.
 */
@Composable
fun RestoreQuestion(title: String, comparison: Comparison, onAnswer: (Boolean) -> Unit) {
    val c = Avl.colors
    var sure by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, color = c.text, fontSize = 14.sp)
        val reminders = comparison.count(BackupTable.REMINDER)
        Text(
            "${reminders.backup} Reminder (hier: ${reminders.here}), ${comparison.count(BackupTable.OCCURRENCE).backup} Termine " +
                "(hier: ${comparison.count(BackupTable.OCCURRENCE).here})",
            Modifier.padding(top = 4.dp).testTag("restoreCounts"), color = c.text, fontSize = 14.sp,
        )
        ComparisonLines(comparison, "Entspricht den Daten auf diesem Gerät.")
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { sure = true }, Modifier.testTag("restoreYes")) { Text("Wiederherstellen") }
            OutlinedButton(onClick = { onAnswer(false) }, Modifier.testTag("restoreNo")) { Text("Abbrechen") }
        }
    }
    if (sure) {
        AlertDialog(
            onDismissRequest = { sure = false },
            title = { Text("Daten auf diesem Handy ersetzen?") },
            text = {
                Text(
                    "Die App startet neu und ersetzt dabei alle Reminder, Termine und den Verlauf. Die jetzigen Daten bleiben " +
                        "als Kopie auf dem Handy; 7 Tage lang lässt sich das in den Einstellungen rückgängig machen. Fotos und " +
                        "Aufnahmen bleiben, wie sie sind.",
                )
            },
            confirmButton = {
                TextButton(onClick = { sure = false; onAnswer(true) }, Modifier.testTag("restoreSure")) { Text("Ersetzen", color = c.strict) }
            },
            dismissButton = { TextButton(onClick = { sure = false }, Modifier.testTag("restoreSureNo")) { Text("Abbrechen") } },
        )
    }
}

/** What the section "Lokale Kopien" can do (R-1, W-6); the defaults do nothing (previews, tests). */
interface LocalActions {
    fun fromSnapshot(date: LocalDate) {}
    fun undo() {}
    fun answer(yes: Boolean) {}
    fun dismiss() {}
}

private val DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val UNTIL = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

/** The person's words for why a swap at the start failed (W-5, step 5). */
fun SwapFailure.text(): String = when (this) {
    SwapFailure.ORDER_UNREADABLE -> "der Auftrag war unlesbar"
    SwapFailure.FILE_CHANGED -> "die vorbereitete Datei hatte sich verändert"
    SwapFailure.FILE_MISSING -> "die vorbereitete Datei fehlte"
    SwapFailure.COPY_FAILED -> "die Kopie der jetzigen Daten ließ sich nicht anlegen"
    SwapFailure.SWAP_FAILED -> "das Austauschen schlug fehl"
}

/**
 * "Lokale Kopien": the daily copies on the phone (R-1) and undoing the last restore (W-6). Both
 * check the copy and ask like a restore from the cloud; the app restarts to swap in.
 */
@Composable
fun LocalRestoreSetting(status: LocalStatus, actions: LocalActions, zone: ZoneId = ZoneId.systemDefault()) {
    val c = Avl.colors
    Column(Modifier.padding(16.dp)) {
        status.failed?.let {
            Text(
                "Wiederherstellen fehlgeschlagen: ${it.text()}. Die Daten sind unverändert. Automatisches Cloud-Sichern " +
                    "ruht, bis das bestätigt ist.",
                Modifier.testTag("localFailed"), color = c.strict, fontSize = 14.sp,
            )
            TextButton(onClick = actions::dismiss, Modifier.testTag("localFailedOk")) { Text("Verstanden") }
        }
        status.restored?.let {
            Text(
                when (it) {
                    RestoreKind.CLOUD -> "Daten aus der Cloud-Sicherung wiederhergestellt."
                    RestoreKind.SNAPSHOT -> "Daten aus der lokalen Kopie wiederhergestellt."
                    RestoreKind.UNDO -> "Wiederherstellen rückgängig gemacht."
                },
                Modifier.testTag("localRestored"), color = c.text, fontSize = 14.sp,
            )
            TextButton(onClick = actions::dismiss) { Text("OK") }
        }
        status.error?.let { Text(it, Modifier.testTag("localError"), color = c.strict, fontSize = 14.sp) }
        when (val phase = status.phase) {
            is LocalPhase.Busy -> Text(phase.what, color = c.muted, fontSize = 14.sp)
            is LocalPhase.Ask -> RestoreQuestion(phase.label, phase.comparison, actions::answer)
            LocalPhase.Idle -> {
                Text(
                    "Einmal am Tag legt die App eine Kopie der Daten auf dem Handy ab (die letzten 7 Tage, dazu 4 Wochen je eine).",
                    color = c.muted, fontSize = 13.sp,
                )
                if (status.snapshots.isEmpty()) {
                    Text("Noch keine Kopie.", Modifier.padding(top = 4.dp).testTag("localNone"), color = c.text, fontSize = 14.sp)
                }
                status.snapshots.forEach { date ->
                    TextButton(onClick = { actions.fromSnapshot(date) }, Modifier.testTag("localSnapshot-$date")) {
                        Text("Kopie vom ${DAY.format(date)} wiederherstellen")
                    }
                }
                status.undoUntil?.let { until ->
                    OutlinedButton(onClick = actions::undo, Modifier.padding(top = 4.dp).testTag("localUndo")) {
                        Text("Wiederherstellen rückgängig machen (bis ${UNTIL.format(until.atZone(zone))})")
                    }
                }
            }
        }
    }
}
