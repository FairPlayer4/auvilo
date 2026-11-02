package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.data.ScheduleType
import com.avlreminder.data.TemplateEntity
import com.avlreminder.data.toBits
import com.avlreminder.data.toDays
import com.avlreminder.data.toMinutes
import com.avlreminder.data.toTime
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.EntryCard
import com.avlreminder.ui.Format
import com.avlreminder.ui.theme.Avl
import java.time.Instant

/*
 * Templates (design 12.4 point 11): character, strictness, proof, minimum with its limit, the
 * snooze buttons, "Nicht stören" and the schedule kind with its times. Not taken: title, why,
 * "Startklar" answers, motivation audio and a concrete date.
 */

fun ReminderForm.toTemplate(name: String, now: Instant) = TemplateEntity(
    name = name.trim(),
    usedAt = now,
    character = character,
    strictness = strictness,
    scheduleType = ScheduleType.valueOf(kind.name),
    timeMinutes = (if (kind == ScheduleKind.DEADLINE) windowFrom else time).toMinutes(),
    weekdays = days.toBits(),
    intervalDays = intervalDays,
    windowUntilMinutes = windowUntil.toMinutes(),
    remindEveryMinutes = remindEvery,
    minimum = minimum.trim().ifEmpty { null },
    minimumLimitPerWeek = minimumLimitPerWeek,
    proofKind = proof.kind,
    proofPhoto = proof.photo,
    proofTextMinChars = proof.textMinChars,
    proofAudioMinSeconds = proof.audioMinSeconds,
    proofWindowMinutes = proof.windowMinutes,
    postponeLimit = postponeLimit,
    ignoreDnd = ignoreDnd,
    snoozeMinutes = snoozeMinutes.joinToString(","),
)

/** A new form with the template's settings; date and the rest stay as they are. */
fun ReminderForm.withTemplate(t: TemplateEntity): ReminderForm {
    val kind = ScheduleKind.valueOf(t.scheduleType.name)
    val time = t.timeMinutes?.toTime()
    return copy(
        character = t.character,
        strictness = t.strictness,
        kind = kind,
        time = if (kind == ScheduleKind.DEADLINE) this.time else time ?: this.time,
        windowFrom = if (kind == ScheduleKind.DEADLINE && time != null) time else windowFrom,
        windowUntil = t.windowUntilMinutes?.toTime() ?: windowUntil,
        remindEvery = t.remindEveryMinutes ?: remindEvery,
        days = t.weekdays.toDays().ifEmpty { days },
        intervalDays = t.intervalDays ?: intervalDays,
        minimum = t.minimum.orEmpty(),
        minimumLimitPerWeek = t.minimumLimitPerWeek,
        proof = ProofSpec(t.proofKind, t.proofPhoto, t.proofTextMinChars, t.proofAudioMinSeconds, t.proofWindowMinutes),
        postponeLimit = t.postponeLimit,
        ignoreDnd = t.ignoreDnd,
        snoozeMinutes = t.snoozeMinutes.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(10) },
    )
}

/** "Neuer Reminder": the templates, most recently used first, and an empty form. */
@Composable
fun TemplateChooser(templates: List<TemplateEntity>, onPick: (TemplateEntity?) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = Avl.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text("Neuer Reminder", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Aus Vorlage", Modifier.padding(vertical = 8.dp), color = c.muted, fontSize = 14.sp)
            templates.forEach { t ->
                EntryCard(title = t.name, detail = templateSummary(t), onClick = { onPick(t) }, modifier = Modifier.testTag("template${t.id}"))
            }
            OutlinedButton(onClick = { onPick(null) }, Modifier.fillMaxWidth().padding(top = 12.dp).testTag("templateEmpty")) { Text("Leer") }
        }
    }
}

fun templateSummary(t: TemplateEntity): String = listOf(
    when (t.scheduleType) {
        ScheduleType.ONCE -> "einmalig"
        ScheduleType.DAILY -> "täglich"
        ScheduleType.WEEKDAYS -> Format.days(t.weekdays.toDays())
        ScheduleType.AFTER_DONE -> "alle ${t.intervalDays} Tage"
        ScheduleType.ON_ARRIVAL -> "wenn ich zuhause bin"
        ScheduleType.DEADLINE -> "Frist"
    } + (t.timeMinutes?.takeIf { t.scheduleType != ScheduleType.ON_ARRIVAL }?.let { " ${Format.time(it.toTime())}" } ?: ""),
    Format.strictness(t.strictness),
).joinToString(" · ")

/** Asks for the name of a new template, or a new name. */
@Composable
fun TemplateNameDialog(initial: String, title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp) },
        text = { OutlinedTextField(name, { name = it }, Modifier.testTag("templateName"), singleLine = true, placeholder = { Text("z. B. Training") }) },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()); onDismiss() }, Modifier.testTag("templateSave"), enabled = name.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Rename and delete in the settings. */
@Composable
fun TemplateList(templates: List<TemplateEntity>, onRename: (Long, String) -> Unit, onDelete: (Long) -> Unit) {
    val c = Avl.colors
    var renaming by remember { mutableStateOf<TemplateEntity?>(null) }
    if (templates.isEmpty()) {
        Text("Noch keine – im Formular „Als Vorlage speichern“.", Modifier.padding(16.dp), color = c.muted, fontSize = 14.sp)
    }
    templates.forEach { t ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, color = c.text, fontSize = 15.sp)
                Text(templateSummary(t), color = c.muted, fontSize = 13.sp)
            }
            TextButton(onClick = { renaming = t }) { Text("Umbenennen", color = c.accent) }
            IconButton(onClick = { onDelete(t.id) }, Modifier.testTag("deleteTemplate${t.id}")) { Icon(AvlIcons.Delete, "Löschen", tint = c.strict) }
        }
    }
    renaming?.let { t -> TemplateNameDialog(t.name, "Vorlage umbenennen", onDismiss = { renaming = null }) { onRename(t.id, it) } }
}
