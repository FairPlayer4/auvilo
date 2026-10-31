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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.rules.Prep
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.AvlSwitch
import com.avlreminder.ui.Fold
import com.avlreminder.ui.NumberDialog
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
    onSuggest: (() -> Unit)? = null,
    /** Archive (true) or restore (false); null for a new reminder. */
    onArchive: ((Boolean) -> Unit)? = null,
    archived: Boolean = false,
    /** Opens the statistics; null for a new reminder. */
    onStats: (() -> Unit)? = null,
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
            onSuggest?.let { SuggestButton(it) }
            Button(onClick = { onSave(form) }, Modifier.padding(end = 8.dp)) { Text("Speichern") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            problems.forEach { Text(it, color = c.strict, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
            onStats?.let { TextButton(onClick = it, Modifier.testTag("stats")) { Text("Statistik und Serie ›", color = c.accent) } }

            SectionTitle("Was?")
            OutlinedTextField(
                form.title, { form = form.copy(title = it) }, Modifier.fillMaxWidth().testTag("title"),
                placeholder = { Text("z. B. Katzenbrunnen reinigen") }, singleLine = true,
            )

            Text("Charakter", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
            Choice(listOf(Character.DOCUMENT to "Dokumentation", Character.OVERCOME to "Überwindung"), form.character) {
                form = form.copy(character = it)
            }
            Text(
                if (form.character == Character.OVERCOME) "für Unangenehmes: mit Startklar-Fragen vor dem Speichern" else "festhalten, was erledigt ist",
                Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp,
            )

            SectionTitle("Wann?")
            Choice(
                listOf(
                    ScheduleKind.ONCE to "Einmalig", ScheduleKind.DAILY to "Täglich", ScheduleKind.WEEKDAYS to "Wochentage",
                    ScheduleKind.AFTER_DONE to "Nach Erledigung", ScheduleKind.ON_ARRIVAL to "Wenn ich zuhause bin",
                ),
                form.kind,
            ) { form = form.copy(kind = it) }
            ScheduleDetail(form) { form = it }
            PlaceSection(form) { form = it }

            SectionTitle("Wie streng?")
            Choice(
                listOf(Strictness.RELAXED to "locker", Strictness.NORMAL to "normal", Strictness.STRICT to "streng"),
                form.strictness,
                color = { if (it == Strictness.STRICT) c.strict else c.accentFill },
            ) { form = form.withStrictness(it) }
            Text(presetText(form.strictness), Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)

            // Everything else folds away; the summary says what is set (Vorschlag 6).
            Spacer(Modifier.height(8.dp))
            if (form.weeklyPossible) {
                Fold("Wochenziel", weeklySummary(form), "foldWeek") { WeeklyGoalSection(form) { form = it } }
            }
            Fold("Ziel und Minimum", goalSummary(form), "foldGoal") {
                Text("Ziel", color = c.text, fontSize = 14.sp)
                OutlinedTextField(form.goal, { form = form.copy(goal = it) }, Modifier.fillMaxWidth(), placeholder = { Text("z. B. 45 min") }, singleLine = true)
                Text("Minimum", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
                OutlinedTextField(form.minimum, { form = form.copy(minimum = it) }, Modifier.fillMaxWidth(), placeholder = { Text("leer = kein Minimum") }, singleLine = true)
                if (form.minimum.isNotBlank()) {
                    Text("Minimum max. pro Woche", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
                    NumberChoice(listOf(1, 2, 3), form.minimumLimitPerWeek, 1..7, "Minimum max. pro Woche", unit = "×", unlimited = "unbegrenzt") {
                        form = form.copy(minimumLimitPerWeek = it)
                    }
                }
            }
            Fold("Checkliste", checklistSummary(form), "foldChecklist") { ChecklistSection(form.checklist) { form = form.copy(checklist = it) } }
            Fold("Nachweis", proofSummary(form), "foldProof") { ProofSection(form) { form = it } }
            Fold("Warum und Botschaft", whySummary(form), "foldWhy") {
                Text("Warum ist mir das wichtig?", color = c.text, fontSize = 14.sp)
                OutlinedTextField(form.why, { form = form.copy(why = it) }, Modifier.fillMaxWidth(), minLines = 2)
                MotivationAudio(form.motivationAudio, recorder, play) { form = form.copy(motivationAudio = it) }
            }
            // Opens by itself when "Überwindung" makes the questions required.
            val overcome = form.character == Character.OVERCOME
            key(overcome) {
                Fold(if (overcome) "Startklar (Pflicht)" else "Startklar (optional)", prepSummary(form), "foldPrep", initiallyOpen = overcome && prepAnswered(form) < Prep.QUESTIONS.count()) {
                    PrepSection(form) { form = it }
                }
            }
            Fold("Feineinstellungen", fineSummary(form), "foldFine") {
                SwitchLine(
                    "Auch bei „Nicht stören“",
                    "für besondere Ereignisse; braucht die Ausnahme in den Einstellungen",
                    form.ignoreDnd,
                ) { form = form.copy(ignoreDnd = it) }
                Text("Verschieben max. pro Fälligkeit", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
                NumberChoice(listOf(0, 1, 2, 3, 5), form.postponeLimit, 0..20, "Verschieben max. pro Fälligkeit", unit = "×", unlimited = "unbegrenzt") {
                    form = form.copy(postponeLimit = it)
                }
                Text("Später-Knöpfe im Alarm", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
                SnoozeChoice(form.snoozeMinutes) { form = form.copy(snoozeMinutes = it) }
            }

            if (onArchive != null) {
                OutlinedButton(onClick = { onArchive(!archived) }, Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text(if (archived) "Wiederherstellen (bleibt pausiert)" else "Archivieren", color = c.text)
                }
            }
            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth().padding(top = if (onArchive != null) 8.dp else 24.dp)) {
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
    // The whole line toggles, not only the small switch.
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 15.sp)
            Text(hint, color = c.muted, fontSize = 13.sp)
        }
        AvlSwitch(checked, null)
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
            NumberChoice(listOf(20, 50, 100), min, 1..2000, "Text: mindestens Zeichen", unit = "") {
                onChange(form.copy(proof = p.copy(textMinChars = it)))
            }
        }
        p.audioMinSeconds?.let { min ->
            Text("Audio: mindestens", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
            NumberChoice(listOf(5, 10, 30), min, 1..600, "Audio: mindestens", unit = "s") {
                onChange(form.copy(proof = p.copy(audioMinSeconds = it)))
            }
        }
        Text("Alarm pausiert während des Nachweises", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
        NumberChoice(listOf(5, 10, 15, 30), p.windowMinutes, 1..120, "Alarm pausiert während des Nachweises", unit = "min") {
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

/** Arrival at home as an earlier trigger; the time stays the fallback. */
@Composable
private fun PlaceSection(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    if (form.kind != ScheduleKind.ON_ARRIVAL) {
        Choice(listOf(false to "Nur Uhrzeit", true to "Bei Ankunft zuhause"), form.atHome) {
            onChange(form.copy(atHome = it))
        }
        if (form.atHome) {
            Text("Bist du vor der Uhrzeit zuhause, klingelt es dann. Um ${Format.time(form.time)} noch nicht zuhause:", Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)
            Choice(listOf(WhenAway.DROP to "entfällt", WhenAway.RING to "trotzdem klingeln"), form.whenAway) { onChange(form.copy(whenAway = it)) }
        }
    }
    if ((form.atHome || form.kind == ScheduleKind.ON_ARRIVAL) && form.homeId == null) {
        Text("Zuhause ist noch nicht festgelegt – in den Einstellungen.", Modifier.padding(top = 6.dp), color = c.strict, fontSize = 13.sp)
    }
}

/** "3–4× pro Woche, mindestens 1×" (Anf. Z1c): the offers count over the week (design 12.3). */
@Composable
private fun WeeklyGoalSection(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    SwitchLine("Wochenziel", "zählt über die Woche (Mo–So) statt jedes Angebot einzeln", form.weekly) { onChange(form.copy(weekly = it)) }
    if (!form.weekly) return
    val days = if (form.kind == ScheduleKind.DAILY) 7 else form.days.size.coerceAtLeast(1)
    val range = (1..days).toList()
    @Composable
    fun line(label: String, value: Int, tag: String, pick: (Int) -> Unit) {
        Text(label, Modifier.padding(top = 8.dp).testTag(tag), color = c.text, fontSize = 14.sp)
        Choice(range.map { it to "$it×" }, value.coerceIn(1, days), onPick = pick)
    }
    // Keep minimum ≤ target ≤ at most while picking.
    line("Ziel pro Woche", form.weekTarget, "weekTarget") { t ->
        onChange(form.copy(weekTarget = t, weekMax = maxOf(form.weekMax, t), weekMinimum = minOf(form.weekMinimum, t)))
    }
    line("Höchstens (danach kein Angebot mehr)", form.weekMax, "weekMax") { m ->
        onChange(form.copy(weekMax = m, weekTarget = minOf(form.weekTarget, m), weekMinimum = minOf(form.weekMinimum, m)))
    }
    line("Mindestens (sonst Rettung am Wochenende)", form.weekMinimum, "weekMinimum") { n ->
        onChange(form.copy(weekMinimum = n, weekTarget = maxOf(form.weekTarget, n), weekMax = maxOf(form.weekMax, n)))
    }
    Text(
        "Ab dem Ziel kommen weitere Angebote still. Reichen die restlichen Tage nur noch knapp, wird es eine Stufe strenger." +
            if (form.kind == ScheduleKind.WEEKDAYS && form.days.none { it.value >= 6 }) " Steht die Woche am Samstag noch unter dem Minimum, erinnert die App Samstag und Sonntag um 10 Uhr streng." else "",
        Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp,
    )
}

/** Up to 20 items; each is done on its own in the alarm, the last one completes the reminder. */
@Composable
private fun ChecklistSection(items: List<ChecklistItem>, onChange: (List<ChecklistItem>) -> Unit) {
    val c = Avl.colors
    if (items.isEmpty()) Text("Keine – der Reminder ist eine Aufgabe.", color = c.muted, fontSize = 13.sp)
    items.forEachIndexed { i, item ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                item.title, { t -> onChange(items.toMutableList().also { it[i] = item.copy(title = t) }) },
                Modifier.weight(1f).padding(vertical = 2.dp).testTag("item$i"), singleLine = true,
                placeholder = { Text("Punkt ${i + 1}") },
            )
            IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(i) }) }) { Text("✕", color = c.muted) }
        }
    }
    if (items.size < 20) {
        TextButton(onClick = { onChange(items + ChecklistItem(title = "")) }) { Text("+ Punkt", color = c.accent) }
    }
}

/** Offered values as chips plus "andere…" for a free one in [range] (Vorschlag 4). */
@Composable
private fun NumberChoice(values: List<Int>, selected: Int, range: IntRange, title: String, unit: String, onPick: (Int) -> Unit) {
    var other by remember { mutableStateOf(false) }
    val all = (values + selected).distinct().sorted()
    Choice(all.map { it to label(it, unit) } + (OTHER to "andere…"), selected) { if (it == OTHER) other = true else onPick(it) }
    if (other) NumberDialog(title, selected, range, unit, onDismiss = { other = false }, onPick = onPick)
}

/** Like the above with null as [unlimited]. */
@Composable
private fun NumberChoice(values: List<Int>, selected: Int?, range: IntRange, title: String, unit: String, unlimited: String, onPick: (Int?) -> Unit) {
    var other by remember { mutableStateOf(false) }
    val all = (values + listOfNotNull(selected)).distinct().sorted()
    Choice(all.map<Int, Pair<Int?, String>> { it to label(it, unit) } + (null to unlimited) + (OTHER to "andere…"), selected) {
        if (it == OTHER) other = true else onPick(it)
    }
    if (other) NumberDialog(title, selected, range, unit, onDismiss = { other = false }, onPick = onPick)
}

private const val OTHER = Int.MIN_VALUE

private fun label(value: Int, unit: String) = when (unit) {
    "" -> "$value"
    "×" -> "$value×"
    else -> "$value $unit"
}

/** Snooze button sets, or one or two own minutes. */
@Composable
private fun SnoozeChoice(selected: List<Int>, onPick: (List<Int>) -> Unit) {
    val c = Avl.colors
    var other by remember { mutableStateOf(false) }
    val sets = (SNOOZE_SETS + listOf(selected)).distinct()
    Choice(sets.map<List<Int>, Pair<List<Int>?, String>> { it to it.joinToString(" / ") { m -> "+$m" } } + (null to "andere…"), selected) {
        if (it == null) other = true else onPick(it)
    }
    if (other) {
        var text by remember { mutableStateOf(selected.joinToString(", ")) }
        val minutes = text.split(',', ' ', '/').mapNotNull { it.trim().toIntOrNull() }
        val valid = minutes.size in 1..2 && minutes.all { it in 1..240 } && minutes.toSet().size == minutes.size
        AlertDialog(
            onDismissRequest = { other = false },
            title = { Text("Später-Knöpfe", fontSize = 18.sp) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, Modifier.testTag("snoozeOwn"), singleLine = true, placeholder = { Text("z. B. 20, 45") })
                    Text("ein oder zwei Minutenwerte, 1 bis 240", Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { onPick(minutes.sorted()); other = false }, Modifier.testTag("snoozeOk"), enabled = valid) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { other = false }) { Text("Abbrechen") } },
        )
    }
}

private fun prepAnswered(form: ReminderForm) = Prep.QUESTIONS.count { form.prep[it]?.answered == true }

private fun weeklySummary(form: ReminderForm) = if (!form.weekly) "aus" else {
    val goal = if (form.weekTarget == form.weekMax) "${form.weekTarget}×" else "${form.weekTarget}–${form.weekMax}×"
    "$goal pro Woche, mindestens ${form.weekMinimum}×"
}

private fun goalSummary(form: ReminderForm) = listOfNotNull(
    form.goal.trim().takeIf { it.isNotEmpty() }?.let { "Ziel: $it" },
    form.minimum.trim().takeIf { it.isNotEmpty() }?.let { "Minimum: $it" },
).joinToString(" · ").ifEmpty { "nicht festgelegt" }

private fun checklistSummary(form: ReminderForm) = when (val n = form.checklist.count { it.title.isNotBlank() }) {
    0 -> "keine"
    1 -> "1 Punkt"
    else -> "$n Punkte"
}

private fun proofSummary(form: ReminderForm): String {
    val p = form.proof
    return when (p.kind) {
        ProofKind.NONE -> "keiner"
        ProofKind.CONFIRM -> "quittieren"
        ProofKind.EVIDENCE -> "Nachweis: " + listOfNotNull(
            "Foto".takeIf { p.photo }, p.textMinChars?.let { "Text" }, p.audioMinSeconds?.let { "Audio" },
        ).joinToString(", ")
    }
}

private fun whySummary(form: ReminderForm) = listOfNotNull(
    form.why.trim().takeIf { it.isNotEmpty() },
    "Botschaft aufgenommen".takeIf { form.motivationAudio != null },
).joinToString(" · ").ifEmpty { "nichts hinterlegt" }

private fun prepSummary(form: ReminderForm) = "${prepAnswered(form)} von ${Prep.QUESTIONS.count()} beantwortet"

private fun fineSummary(form: ReminderForm) = listOfNotNull(
    "Verschieben " + (form.postponeLimit?.let { "$it×" } ?: "unbegrenzt"),
    "Später " + form.snoozeMinutes.joinToString(" / ") { "+$it" },
    "auch bei Nicht stören".takeIf { form.ignoreDnd },
).joinToString(" · ")

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
            ScheduleKind.ON_ARRIVAL -> Text(
                "Klingelt, sobald du ein paar Minuten zuhause bist – einmalig. Bist du beim Speichern schon da, sofort.",
                color = c.muted, fontSize = 13.sp,
            )
        }
        if (form.kind != ScheduleKind.ON_ARRIVAL) {
            OutlinedButton(onClick = { pickTime = true }, Modifier.padding(top = 4.dp).testTag("time")) { Text("um ${Format.time(form.time)}") }
        }
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
