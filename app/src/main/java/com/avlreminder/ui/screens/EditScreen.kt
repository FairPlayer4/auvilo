package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.Prep
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.Format
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.theme.Avl
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * "Reminder anlegen / bearbeiten". Holds the form state itself; [onSave] gets the finished form,
 * [problems] shows what the engine rejected. [recorder] and [play] handle the motivation audio.
 */
@Composable
fun EditScreen(
    initial: ReminderForm,
    problems: List<String>,
    onSave: (ReminderForm) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    recorder: @Composable (onRecorded: (String, Int) -> Unit) -> Unit = {},
    play: (String) -> Unit = {},
) {
    val c = Avl.colors
    var form by remember(initial) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text(
                if (initial.id == 0L) "Neuer Reminder" else "Reminder bearbeiten",
                Modifier.weight(1f), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            Button(onClick = { onSave(form) }, Modifier.padding(end = 8.dp)) { Text("Speichern") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            problems.forEach { Text(it, color = c.strict, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }

            SectionTitle("Was?")
            OutlinedTextField(
                form.title, { form = form.copy(title = it) }, Modifier.fillMaxWidth().testTag("title"),
                placeholder = { Text("z. B. Katzenbrunnen reinigen") }, singleLine = true,
            )

            SectionTitle("Wann?")
            Choice(
                listOf(ScheduleKind.ONCE to "Einmalig", ScheduleKind.DAILY to "Täglich", ScheduleKind.WEEKDAYS to "Wochentage", ScheduleKind.AFTER_DONE to "Nach Erledigung"),
                form.kind,
            ) { form = form.copy(kind = it) }
            ScheduleDetail(form) { form = it }

            SectionTitle("Wie streng?")
            Choice(
                listOf(Strictness.RELAXED to "locker", Strictness.NORMAL to "normal", Strictness.STRICT to "streng"),
                form.strictness,
                color = { if (it == Strictness.STRICT) c.strict else c.accentFill },
            ) { form = form.withStrictness(it) }
            Text(presetText(form.strictness), Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)
            SwitchLine(
                "Auch bei „Nicht stören“",
                "für besondere Ereignisse; braucht die Ausnahme in den Einstellungen",
                form.ignoreDnd,
            ) { form = form.copy(ignoreDnd = it) }

            SectionTitle("Ziel")
            OutlinedTextField(form.goal, { form = form.copy(goal = it) }, Modifier.fillMaxWidth(), placeholder = { Text("z. B. 45 min") }, singleLine = true)
            SectionTitle("Minimum")
            OutlinedTextField(form.minimum, { form = form.copy(minimum = it) }, Modifier.fillMaxWidth(), placeholder = { Text("leer = kein Minimum") }, singleLine = true)

            SectionTitle("Nachweis")
            ProofSection(form) { form = it }

            SectionTitle("Warum ist mir das wichtig?")
            OutlinedTextField(form.why, { form = form.copy(why = it) }, Modifier.fillMaxWidth(), minLines = 2)
            MotivationAudio(form.motivationAudio, recorder, play) { form = form.copy(motivationAudio = it) }

            SectionTitle(if (form.character == Character.OVERCOME) "Startklar (Pflicht bei Überwindung)" else "Startklar (optional)")
            PrepSection(form) { form = it }

            SectionTitle("Feineinstellungen")
            Text("Verschieben max. pro Fälligkeit", color = c.text, fontSize = 14.sp)
            LimitChoice(listOf(0, 1, 2, 3, 5), form.postponeLimit) { form = form.copy(postponeLimit = it) }
            Text("Später-Knöpfe im Alarm", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
            val snoozeSets = (SNOOZE_SETS + listOf(form.snoozeMinutes)).distinct()
            Choice(snoozeSets.map { it to it.joinToString(" / ") { m -> "+$m" } }, form.snoozeMinutes) { form = form.copy(snoozeMinutes = it) }
            if (form.minimum.isNotBlank()) {
                Text("Minimum max. pro Woche", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
                LimitChoice(listOf(1, 2, 3), form.minimumLimitPerWeek) { form = form.copy(minimumLimitPerWeek = it) }
            }
            Text("Charakter", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
            Choice(listOf(Character.OVERCOME to "Überwindung", Character.DOCUMENT to "Dokumentation"), form.character) {
                form = form.copy(character = it)
            }

            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Icon(AvlIcons.Delete, null, tint = c.strict)
                    Text("Löschen", color = c.strict)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Reminder löschen?") },
            text = { Text("Der Verlauf und die Fotos dieses Reminders werden ebenfalls gelöscht.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen", color = c.strict) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

private fun presetText(s: Strictness) = when (s) {
    Strictness.RELAXED -> "Alarm: Banner + Vibration, alle 10 min · Verschieben unbegrenzt"
    Strictness.NORMAL -> "Alarm: Vollbild + Vibration wie ein Wecker, ohne Ton · Verschieben 3×"
    Strictness.STRICT -> "Alarm: Vollbild + Vibration, ab 18 Uhr mit Alarmton · Verschieben 1× · Foto · Minimum 1×/Woche"
}

/** At most two buttons: a notification has room for "done" plus two. */
private val SNOOZE_SETS = listOf(listOf(10), listOf(15), listOf(30), listOf(10, 30), listOf(30, 60))

@Composable
private fun SwitchLine(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Avl.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 15.sp)
            Text(hint, color = c.muted, fontSize = 13.sp)
        }
        Switch(checked, onChange)
    }
}

/** Kind of proof; for "Nachweis" which parts (combinable), their minimum and the quiet window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProofSection(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    val p = form.proof
    Choice(listOf(ProofKind.NONE to "Keiner", ProofKind.CONFIRM to "Quittieren", ProofKind.EVIDENCE to "Nachweis"), p.kind) { kind ->
        // A fresh "Nachweis" starts with a photo, the most common case.
        val photo = if (kind == ProofKind.EVIDENCE && !p.photo && p.textMinChars == null && p.audioMinSeconds == null) true else p.photo
        onChange(form.copy(proof = p.copy(kind = kind, photo = photo)))
    }
    if (p.needsEvidence) {
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(p.photo, { onChange(form.copy(proof = p.copy(photo = !p.photo))) }, label = { Text("Foto") })
            FilterChip(
                p.textMinChars != null,
                { onChange(form.copy(proof = p.copy(textMinChars = if (p.textMinChars == null) 50 else null))) },
                label = { Text("Text") },
            )
            FilterChip(
                p.audioMinSeconds != null,
                { onChange(form.copy(proof = p.copy(audioMinSeconds = if (p.audioMinSeconds == null) 10 else null))) },
                label = { Text("Audio") },
            )
        }
        p.textMinChars?.let { min ->
            Text("Text: mindestens Zeichen (ohne Leerzeichen)", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
            Choice((listOf(20, 50, 100) + min).distinct().sorted().map { it to "$it" }, min) {
                onChange(form.copy(proof = p.copy(textMinChars = it)))
            }
        }
        p.audioMinSeconds?.let { min ->
            Text("Audio: mindestens", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
            Choice((listOf(5, 10, 30) + min).distinct().sorted().map { it to "$it s" }, min) {
                onChange(form.copy(proof = p.copy(audioMinSeconds = it)))
            }
        }
        Text("Alarm pausiert während des Nachweises", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
        Choice((listOf(5, 10, 15, 30) + p.windowMinutes).distinct().sorted().map { it to "$it min" }, p.windowMinutes) {
            onChange(form.copy(proof = p.copy(windowMinutes = it)))
        }
    }
    Text("Ohne verlangten Nachweis zählt eine Erledigung als nicht erfolgreich.", Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)
}

/** Message to oneself, played in the alarm on tap only. */
@Composable
private fun MotivationAudio(
    path: String?,
    recorder: @Composable (onRecorded: (String, Int) -> Unit) -> Unit,
    play: (String) -> Unit,
    onChange: (String?) -> Unit,
) {
    val c = Avl.colors
    Text("Botschaft an mich (Audio, optional)", Modifier.padding(top = 12.dp, bottom = 6.dp), color = c.text, fontSize = 14.sp)
    if (path != null) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("✓ aufgenommen", Modifier.weight(1f), color = c.text)
            TextButton(onClick = { play(path) }) { Text("▶ Abspielen") }
            TextButton(onClick = { onChange(null) }) { Text("Entfernen", color = c.strict) }
        }
    }
    recorder { newPath, _ -> onChange(newPath) }
}

/** The four "Startklar" questions; "weiß ich noch nicht" is a valid answer. */
@Composable
private fun PrepSection(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    Prep.QUESTIONS.forEach { q ->
        val draft = form.prep[q] ?: PrepDraft()
        fun set(d: PrepDraft) = onChange(form.copy(prep = form.prep + (q to d)))
        Text(PrepTexts.QUESTIONS.getValue(q), Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
        OutlinedTextField(
            draft.text, { set(draft.copy(text = it, unknown = false)) },
            Modifier.fillMaxWidth().padding(top = 4.dp).testTag("prep$q"),
            enabled = !draft.unknown,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q == Prep.READY) {
                FilterChip(draft.text == Prep.READY_YES, { set(PrepDraft(Prep.READY_YES)) }, label = { Text(Prep.READY_YES) })
            }
            FilterChip(draft.unknown, { set(draft.copy(unknown = !draft.unknown)) }, label = { Text("weiß ich noch nicht") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, color: @Composable (T) -> Color = { Avl.colors.accentFill }, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onPick(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color(value), selectedLabelColor = Color.White),
            )
        }
    }
}

/** Limit as chips; null means unlimited. */
@Composable
private fun LimitChoice(values: List<Int>, selected: Int?, onPick: (Int?) -> Unit) {
    val all = (values + listOfNotNull(selected)).distinct().sorted()
    Choice(all.map<Int, Pair<Int?, String>> { it to "$it×" } + (null to "unbegrenzt"), selected, onPick = onPick)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDetail(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    var pickTime by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 8.dp)) {
        when (form.kind) {
            ScheduleKind.ONCE -> OutlinedButton(onClick = { pickDate = true }) { Text("am ${Format.date(form.date)}") }
            ScheduleKind.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = d in form.days,
                        onClick = { onChange(form.copy(days = if (d in form.days) form.days - d else form.days + d)) },
                        label = { Text(Format.weekday(d)) },
                    )
                }
            }
            ScheduleKind.AFTER_DONE -> Column {
                // Number between the buttons only, the long explanation below: fits narrow screens.
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onChange(form.copy(intervalDays = (form.intervalDays - 1).coerceAtLeast(1))) }) { Text("−") }
                    Text(
                        "${form.intervalDays} Tage", Modifier.padding(horizontal = 16.dp).testTag("interval"),
                        color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(onClick = { onChange(form.copy(intervalDays = form.intervalDays + 1)) }) { Text("+") }
                }
                Text("nach letzter Erledigung", Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp)
                OutlinedButton(onClick = { pickDate = true }, Modifier.padding(top = 8.dp)) { Text("erstmals am ${Format.date(form.date)}") }
            }
            ScheduleKind.DAILY -> {}
        }
        OutlinedButton(onClick = { pickTime = true }, Modifier.padding(top = 4.dp).testTag("time")) { Text("um ${Format.time(form.time)}") }
    }

    if (pickTime) {
        val state = rememberTimePickerState(form.time.hour, form.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { onChange(form.copy(time = LocalTime.of(state.hour, state.minute))); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Abbrechen") } },
            text = { TimeInput(state) },
        )
    }
    if (pickDate) {
        val state = rememberDatePickerState(form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(form.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}
