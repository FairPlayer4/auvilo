package com.avlreminder.ui.screens

import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Place
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.Validation
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.AvlSwitch
import com.avlreminder.ui.GrowingField
import com.avlreminder.ui.Fold
import com.avlreminder.ui.NumberDialog
import com.avlreminder.ui.Format
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.TextKeyboard
import com.avlreminder.ui.TimeDialog
import com.avlreminder.ui.theme.Avl
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** What the blocker list in the form can do; only for a saved reminder (S5). */
class BlockerActions(
    val add: (String) -> Unit,
    val solve: (Long) -> Unit,
    val plan: (Long, LocalDateTime) -> Unit,
    /** The evening before the reminder is due next. */
    val suggestedTime: LocalDateTime,
    /** When a planned task is due; null when it is done or gone. */
    val taskAt: (Long) -> Instant?,
)

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
    /** The places to choose from for the arrival; the first is "Zuhause". */
    places: List<Place> = emptyList(),
    /** Opens the statistics; null for a new reminder. */
    onStats: (() -> Unit)? = null,
    /** "Als Vorlage speichern" with the name. */
    onSaveTemplate: ((String, ReminderForm) -> Unit)? = null,
    blockers: List<Blocker> = emptyList(),
    blockerActions: BlockerActions? = null,
    /** Checks items of the open occurrence any time ("Vorab erledigen"); null without a checklist. */
    onCheckItems: (() -> Unit)? = null,
) {
    val c = Avl.colors
    // The draft survives dark mode, font size and process death (Review-Lauf 5, 6a).
    var form by rememberSaveable(initial) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    var namingTemplate by remember { mutableStateOf(false) }
    var templateSaved by remember { mutableStateOf(false) }

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
        // Under the fixed header: in sight also when the form is scrolled down (Review-Lauf 5).
        if (problems.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
                problems.forEach { Text(it, color = c.strict, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            onStats?.let { TextButton(onClick = it, Modifier.testTag("stats")) { Text("Statistik und Serie ›", color = c.accent) } }
            onCheckItems?.let { TextButton(onClick = it, Modifier.testTag("checkItems")) { Text("Punkte abhaken ›", color = c.accent) } }

            SectionTitle("Was?")
            GrowingField(form.title, { form = form.copy(title = it) }, Modifier.fillMaxWidth().testTag("title"), "z. B. Katzenbrunnen reinigen")

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
                    ScheduleKind.AFTER_DONE to "Nach Erledigung", ScheduleKind.ON_ARRIVAL to "Wenn ich ankomme",
                    ScheduleKind.DEADLINE to "Frist",
                ),
                form.kind,
            ) { form = form.copy(kind = it) }
            ScheduleDetail(form) { form = it }
            PlaceSection(form, places) { form = it }

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
                GrowingField(form.goal, { form = form.copy(goal = it) }, Modifier.fillMaxWidth(), "z. B. 45 min")
                Text("Minimum", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
                GrowingField(form.minimum, { form = form.copy(minimum = it) }, Modifier.fillMaxWidth(), "leer = kein Minimum")
                if (form.hasMinimum) {
                    Text("Minimum max. pro Woche", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
                    NumberChoice(listOf(1, 2, 3), form.minimumLimitPerWeek, 1..7, "Minimum max. pro Woche", unit = "×", unlimited = "unbegrenzt") {
                        form = form.copy(minimumLimitPerWeek = it)
                    }
                }
            }
            if (form.kind != ScheduleKind.ON_ARRIVAL) {
                Fold("Vorab-Reminder", preNoticeSummary(form), "foldPreNotice") { PreNoticeSection(form) { form = it } }
            }
            Fold("Blocker", blockerSummary(form, blockers), "foldBlocker") {
                BlockerSection(form, blockers, blockerActions) { form = it }
            }
            Fold("Sitzung mit Timer", sessionSummary(form), "foldSession") { SessionSection(form) { form = it } }
            Fold("Checkliste", checklistSummary(form), "foldChecklist") { ChecklistSection(form.checklist) { form = form.copy(checklist = it) } }
            Fold("Nachweis", proofSummary(form), "foldProof") { ProofSection(form) { form = it } }
            Fold("Warum und Botschaft", whySummary(form), "foldWhy") {
                Text("Warum ist mir das wichtig?", color = c.text, fontSize = 14.sp)
                OutlinedTextField(form.why, { form = form.copy(why = it) }, Modifier.fillMaxWidth(), minLines = 2, keyboardOptions = TextKeyboard)
                MotivationAudio(form.motivationAudio, recorder, play) { form = form.copy(motivationAudio = it) }
            }
            // Opens by itself when "Überwindung" makes the questions required.
            val overcome = form.character == Character.OVERCOME
            key(overcome) {
                Fold(if (overcome) "Startklar (Pflicht)" else "Startklar (optional)", prepSummary(form), "foldPrep", initiallyOpen = overcome && prepAnswered(form) < Prep.QUESTIONS.count()) {
                    PrepSection(form, initial.prep) { form = it }
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
                ArrivalFine(form) { form = it }
            }

            if (onSaveTemplate != null && form.prepFor == null) {
                OutlinedButton(onClick = { namingTemplate = true }, Modifier.fillMaxWidth().padding(top = 24.dp).testTag("saveTemplate")) {
                    Text(if (templateSaved) "✓ Als Vorlage gespeichert" else "Als Vorlage speichern", color = c.text)
                }
            }
            if (onArchive != null) {
                OutlinedButton(onClick = { onArchive(!archived) }, Modifier.fillMaxWidth().padding(top = if (onSaveTemplate != null) 8.dp else 24.dp)) {
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

    if (namingTemplate && onSaveTemplate != null) {
        TemplateNameDialog(form.title.trim(), "Als Vorlage speichern", onDismiss = { namingTemplate = false }) {
            onSaveTemplate(it, form)
            templateSaved = true
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
            NumberChoice(listOf(20, 50, 100), min, 1..ProofSpec.MAX_TEXT_CHARS, "Text: mindestens Zeichen", unit = "") {
                onChange(form.copy(proof = p.copy(textMinChars = it)))
            }
        }
        p.audioMinSeconds?.let { min ->
            Text("Audio: mindestens", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
            NumberChoice(listOf(5, 10, 30), min, 1..ProofSpec.MAX_AUDIO_SECONDS, "Audio: mindestens", unit = "s") {
                onChange(form.copy(proof = p.copy(audioMinSeconds = it)))
            }
        }
        Text("Alarm pausiert während des Nachweises", Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
        NumberChoice(listOf(5, 10, 15, 30), p.windowMinutes, 1..ProofSpec.MAX_WINDOW_MINUTES, "Alarm pausiert während des Nachweises", unit = "min") {
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
private fun PrepSection(form: ReminderForm, stored: Map<Int, PrepDraft>, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    Prep.QUESTIONS.forEach { q ->
        val draft = form.prep[q] ?: PrepDraft()
        fun set(d: PrepDraft) = onChange(form.copy(prep = form.prep + (q to d)))
        Text(PrepTexts.QUESTIONS.getValue(q), Modifier.padding(top = 10.dp), color = c.text, fontSize = 14.sp)
        OutlinedTextField(
            draft.text, { set(draft.copy(text = it, unknown = false)) },
            Modifier.fillMaxWidth().padding(top = 4.dp).testTag("prep$q"),
            enabled = !draft.unknown, keyboardOptions = TextKeyboard,
            // An answer can be overwritten, not emptied: an empty field keeps it (Review-Lauf 5, 6c).
            supportingText = if (!draft.answered && stored[q]?.answered == true) {
                { Text("Leer bleibt die bisherige Antwort", Modifier.testTag("prepKept$q")) }
            } else {
                null
            },
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

/**
 * Arrival at a place in a window before the time (Anf. 7.29): the time is the latest one, the
 * lead says from when an arrival counts. Buffer and check interval are under *Feineinstellungen*.
 */
@Composable
private fun PlaceSection(form: ReminderForm, places: List<Place>, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    if (!form.placePossible) return
    val arriving = form.atHome || form.kind == ScheduleKind.ON_ARRIVAL
    if (form.kind != ScheduleKind.ON_ARRIVAL) {
        Choice(listOf(false to "Nur Uhrzeit", true to "Bei Ankunft"), form.atHome) {
            onChange(form.copy(atHome = it))
        }
    }
    if (arriving && places.size > 1) {
        Choice(places.map { it.id to it.name }, form.placeId) { onChange(form.copy(placeId = it)) }
    }
    if (form.atHome && form.kind != ScheduleKind.ON_ARRIVAL) {
        val latest = Format.time(form.time)
        Text("Spätestens um $latest. Ankunft zählt ab", Modifier.padding(top = 6.dp), color = c.text, fontSize = 14.sp)
        NumberChoice(listOf(30, 60, 90, 120), form.lead, 0..Arrival.MAX_LEAD, "Ankunft zählt ab … vorher", "min vorher") {
            onChange(form.copy(lead = it))
        }
        val how = if (form.lead == 0) {
            "Ohne Zeitraum: Kommst du vor $latest an, klingelt es sofort."
        } else {
            "Ab ${Format.time(form.time.minusMinutes(form.lead.toLong()))}: Kommst du an, klingelt es ${form.buffer} min später. " +
                "Bist du da schon da, um $latest."
        }
        Text("$how Um $latest nicht da:", Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)
        Choice(listOf(WhenAway.DROP to "entfällt", WhenAway.RING to "trotzdem klingeln"), form.whenAway) { onChange(form.copy(whenAway = it)) }
    }
    if (arriving && form.placeId == null) {
        Text("Ort ist noch nicht festgelegt – in den Einstellungen unter „Orte“.", Modifier.padding(top = 6.dp), color = c.strict, fontSize = 13.sp)
    }
}

/** Buffer and check interval of the arrival window, under *Feineinstellungen*. */
@Composable
private fun ArrivalFine(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    if (!form.hasWindow) return
    Text("Puffer nach der Ankunft", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(0, 5, 10, 15), form.buffer, 0..Arrival.MAX_BUFFER, "Puffer nach der Ankunft", "min") { onChange(form.copy(buffer = it)) }
    Text("Prüfen im Zeitraum alle", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(15, 20, 30), form.check, Arrival.MIN_CHECK..Arrival.MAX_CHECK, "Prüfen im Zeitraum alle", "min") { onChange(form.copy(check = it)) }
}

/** The further times of a day (VS1): change, remove, add up to [Validation.MAX_TIMES] in all. */
@Composable
private fun MoreTimes(form: ReminderForm, onChange: (ReminderForm) -> Unit, onPick: (Int) -> Unit) {
    val c = Avl.colors
    form.moreTimes.forEachIndexed { i, t ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = { onPick(i) }, Modifier.testTag("moreTime$i")) { Text("und ${Format.time(t)}") }
            TextButton(onClick = { onChange(form.copy(moreTimes = form.moreTimes - t)) }, Modifier.testTag("removeTime$i")) { Text("entfernen") }
        }
    }
    when {
        form.weekly || form.atHome ->
            Text("Mit Wochenziel oder Ankunft nur eine Uhrzeit am Tag.", Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp)
        form.times.size < Validation.MAX_TIMES ->
            TextButton(onClick = { onPick(-1) }, Modifier.testTag("addTime")) { Text("+ weitere Uhrzeit") }
    }
    if (form.severalADay) {
        Text("Jede Uhrzeit gilt als verpasst, sobald die nächste beginnt.", Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp)
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
            GrowingField(
                item.title, { t -> onChange(items.toMutableList().also { it[i] = item.copy(title = t) }) },
                Modifier.weight(1f).padding(vertical = 2.dp).testTag("item$i"), "Punkt ${i + 1}",
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
        val valid = minutes.size in 1..2 && minutes.all { it in 1..Reminder.MAX_SNOOZE_MINUTES } && minutes.toSet().size == minutes.size
        AlertDialog(
            onDismissRequest = { other = false },
            title = { Text("Später-Knöpfe", fontSize = 18.sp) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, Modifier.testTag("snoozeOwn"), singleLine = true, placeholder = { Text("z. B. 20, 45") })
                    Text("ein oder zwei Minutenwerte, 1 bis ${Reminder.MAX_SNOOZE_MINUTES}", Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp)
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

private fun sessionSummary(form: ReminderForm): String {
    if (!form.session) return "aus"
    return listOfNotNull(
        "Nachfrage alle ${form.sessionCheck} min",
        form.sessionGoal?.let { "Ziel ${Format.minutes(it)}" },
        form.sessionMinimum?.let { "Minimum ${Format.minutes(it)}" },
        "max. ${Format.minutes(form.sessionMax)}",
    ).joinToString(" · ")
}

/** "Ich starte jetzt", timer and checks (Anf. 7.11, design.md 3.1). */
@Composable
private fun SessionSection(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    SwitchLine(
        "Sitzung mit Timer",
        "„Ich starte jetzt“ im Alarm, dann läuft ein Timer und fragt nach, ob du noch dran bist",
        form.session,
    ) { onChange(form.copy(session = it)) }
    if (!form.session) return
    Text("Nachfrage alle", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(5, 10, 15, 30), form.sessionCheck, SessionSpec.MIN_CHECK..SessionSpec.MAX_CHECK, "Nachfrage alle", unit = "min") {
        onChange(form.copy(sessionCheck = it))
    }
    Text("Nachfragen", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    Choice(
        listOf(Strictness.RELAXED to "locker", Strictness.NORMAL to "normal", Strictness.STRICT to "streng"),
        form.sessionCheckStrictness,
    ) { onChange(form.copy(sessionCheckStrictness = it)) }
    Text("Ziel-Dauer", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(15, 30, 45, 60), form.sessionGoal, 1..SessionSpec.MAX_MAX, "Ziel-Dauer", unit = "min", unlimited = "keine") {
        onChange(form.copy(sessionGoal = it))
    }
    Text("Minimum-Dauer", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(5, 10, 15), form.sessionMinimum, 1..SessionSpec.MAX_MAX, "Minimum-Dauer", unit = "min", unlimited = "keine") {
        onChange(form.copy(sessionMinimum = it))
    }
    Text("Höchstens", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(60, 90, 120), form.sessionMax, 1..SessionSpec.MAX_MAX, "Höchstens", unit = "min") {
        onChange(form.copy(sessionMax = it))
    }
    Text("Reaktionszeit am Ende", Modifier.padding(top = 12.dp), color = c.text, fontSize = 14.sp)
    NumberChoice(listOf(3, 5, 10), form.sessionFinal, 1..SessionSpec.MAX_FINAL, "Reaktionszeit am Ende", unit = "min") {
        onChange(form.copy(sessionFinal = it))
    }
    Text(
        "Mit Ziel-Dauer schlägt die Nachfrage „Fertig“ vor. Nach der Höchstdauer kommt ein letzter Reminder; " +
            "ohne Antwort zählt es als verpasst (mit erledigten Punkten als Minimum).",
        Modifier.padding(top = 8.dp), color = c.muted, fontSize = 13.sp,
    )
}

private fun fineSummary(form: ReminderForm) = listOfNotNull(
    "Verschieben " + (form.postponeLimit?.let { "$it×" } ?: "unbegrenzt"),
    "Später " + form.snoozeMinutes.joinToString(" / ") { "+$it" },
    "auch bei Nicht stören".takeIf { form.ignoreDnd },
    "Ankunft +${form.buffer} min, prüfen alle ${form.check} min".takeIf { form.hasWindow },
).joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDetail(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    var pickTime by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }
    // Index into moreTimes, or -1 for a new time.
    var pickMore by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.padding(top = 8.dp)) {
        when (form.kind) {
            ScheduleKind.ONCE -> OutlinedButton(onClick = { pickDate = true }) { Text("am ${Format.date(form.date)}") }
            ScheduleKind.WEEKDAYS -> DayChips(form, onChange)
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
            ScheduleKind.DEADLINE -> Column {
                OutlinedButton(onClick = { pickDate = true }, Modifier.testTag("deadlineDate")) { Text("Frist ${Format.date(form.date)}") }
                Text("Ausführungsfenster", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
                DayChips(form) { onChange(it) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickFrom = true }, Modifier.testTag("windowFrom")) { Text("von ${Format.time(form.windowFrom)}") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { pickUntil = true }, Modifier.testTag("windowUntil")) { Text("bis ${Format.time(form.windowUntil)}") }
                }
                Text("Erinnert im Fenster alle", Modifier.padding(top = 8.dp), color = c.text, fontSize = 14.sp)
                NumberChoice(listOf(30, 60, 120), form.remindEvery, Schedule.Deadline.MIN_INTERVAL..Schedule.Deadline.MAX_INTERVAL, "Erinnert im Fenster alle", unit = "min") {
                    onChange(form.copy(remindEvery = it))
                }
                Text(
                    "Klingelt nur in den Fenstern bis zur Frist; außerhalb ist Ruhe. Am letzten Tag eine Stufe strenger. " +
                        "Danach steht es unter „Überfällig“, bis du es erledigst oder verwirfst.",
                    Modifier.padding(top = 4.dp), color = c.muted, fontSize = 13.sp,
                )
            }
            ScheduleKind.DAILY -> {}
            ScheduleKind.ON_ARRIVAL -> Text(
                "Klingelt, sobald du ein paar Minuten zuhause bist – einmalig. Bist du beim Speichern schon da, sofort.",
                color = c.muted, fontSize = 13.sp,
            )
        }
        if (form.kind != ScheduleKind.ON_ARRIVAL) {
            val label = if (form.kind == ScheduleKind.DEADLINE) "Frist um" else "um"
            OutlinedButton(onClick = { pickTime = true }, Modifier.padding(top = 4.dp).testTag("time")) { Text("$label ${Format.time(form.time)}") }
        }
        if (form.timesPossible) MoreTimes(form, onChange) { pickMore = it }
    }
    pickMore?.let { i ->
        val others = form.times - (form.moreTimes.getOrNull(i) ?: LocalTime.MIDNIGHT.minusNanos(1))
        TimeDialog(
            form.moreTimes.getOrNull(i) ?: form.times.last().plusHours(1),
            onDismiss = { pickMore = null },
            error = { if (it in others) "Diese Uhrzeit gibt es schon" else null },
        ) { t ->
            val more = if (i < 0) form.moreTimes + t else form.moreTimes.mapIndexed { j, old -> if (j == i) t else old }
            onChange(form.copy(moreTimes = more.sorted()))
        }
    }
    if (pickFrom) TimeDialog(form.windowFrom, onDismiss = { pickFrom = false }) { onChange(form.copy(windowFrom = it)) }
    if (pickUntil) TimeDialog(form.windowUntil, onDismiss = { pickUntil = false }) { onChange(form.copy(windowUntil = it)) }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in form.days,
                onClick = { onChange(form.copy(days = if (d in form.days) form.days - d else form.days + d)) },
                label = { Text(Format.weekday(d)) },
            )
        }
    }
}

/** A silent notice the evening before or some minutes before (T6, S6). */
@Composable
private fun PreNoticeSection(form: ReminderForm, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    var pickEvening by remember { mutableStateOf(false) }
    Choice(
        listOf(PreNoticeKind.NONE to "aus", PreNoticeKind.EVENING to "am Vorabend", PreNoticeKind.BEFORE to "kurz vorher"),
        form.preNotice,
    ) { onChange(form.copy(preNotice = it)) }
    when (form.preNotice) {
        PreNoticeKind.NONE -> {}
        PreNoticeKind.EVENING ->
            OutlinedButton(onClick = { pickEvening = true }, Modifier.padding(top = 8.dp).testTag("preNoticeEvening")) {
                Text("um ${Format.time(form.preNoticeEvening)}")
            }
        PreNoticeKind.BEFORE ->
            NumberChoice(listOf(15, 30, 60, 120), form.preNoticeBefore, PreNotice.Before.MIN_MINUTES..PreNotice.Before.MAX_MINUTES, "Vorab-Reminder", unit = "min") {
                onChange(form.copy(preNoticeBefore = it))
            }
    }
    Text(
        "Eine stille Benachrichtigung mit dem ersten Schritt und offenen Blockern. Ist es schon erledigt, kommt sie nicht.",
        Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp,
    )
    if (pickEvening) TimeDialog(form.preNoticeEvening, onDismiss = { pickEvening = false }) { onChange(form.copy(preNoticeEvening = it)) }
}

/** Open blockers as a list with "Als Aufgabe planen" and "gelöst" (S5, S7). */
@Composable
private fun BlockerSection(form: ReminderForm, blockers: List<Blocker>, actions: BlockerActions?, onChange: (ReminderForm) -> Unit) {
    val c = Avl.colors
    var planning by remember { mutableStateOf<Blocker?>(null) }
    var text by remember { mutableStateOf("") }
    SwitchLine(
        "Gelöster Blocker zählt als Minimum",
        "hast du seit dem letzten Mal einen Blocker gelöst, darfst du damit abschließen",
        form.solvedBlockerIsMinimum,
    ) { onChange(form.copy(solvedBlockerIsMinimum = it)) }
    if (actions == null) {
        Text("Blocker kommen aus „Startklar“ oder lassen sich nach dem Speichern eintragen.", Modifier.padding(top = 8.dp), color = c.muted, fontSize = 13.sp)
        return
    }
    val open = blockers.filter { it.isOpen }
    if (open.isEmpty()) Text("Keine offenen Blocker.", Modifier.padding(top = 8.dp), color = c.muted, fontSize = 13.sp)
    open.forEach { b ->
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("blocker${b.id}")) {
            Text(b.text, color = c.text, fontSize = 15.sp)
            b.taskReminderId?.let { id ->
                val at = actions.taskAt(id)
                Text(
                    if (at != null) "Aufgabe geplant: ${Format.shortDay(at.atZone(ZoneId.systemDefault()).toLocalDate())} ${Format.time(at, ZoneId.systemDefault())}" else "Aufgabe geplant",
                    color = c.muted, fontSize = 13.sp,
                )
            }
            Row {
                TextButton(onClick = { planning = b }, Modifier.testTag("planTask${b.id}")) {
                    Text(if (b.taskReminderId == null) "Als Aufgabe planen" else "Neu planen", color = c.accent)
                }
                TextButton(onClick = { actions.solve(b.id) }, Modifier.testTag("solve${b.id}")) { Text("gelöst", color = c.accent) }
            }
        }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        GrowingField(text, { text = it }, Modifier.weight(1f).padding(top = 8.dp).testTag("newBlocker"), "Was fehlt noch?")
        TextButton(onClick = { actions.add(text); text = "" }, enabled = text.isNotBlank()) { Text("+", fontSize = 20.sp, color = c.accent) }
    }
    planning?.let { b ->
        DateTimeDialog("Aufgabe: ${b.text}", actions.suggestedTime, onDismiss = { planning = null }) { at ->
            actions.plan(b.id, at)
            planning = null
        }
    }
}

/** Date and time, prefilled; for planning a task. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(title: String, initial: LocalDateTime, onDismiss: () -> Unit, onPick: (LocalDateTime) -> Unit) {
    var at by remember { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedButton(onClick = { pickDate = true }) { Text("am ${Format.date(at.toLocalDate())}") }
                OutlinedButton(onClick = { pickTime = true }, Modifier.padding(top = 4.dp)) { Text("um ${Format.time(at.toLocalTime())}") }
                Text("Locker, einmalig; erledigt löst sie den Blocker.", Modifier.padding(top = 6.dp), color = Avl.colors.muted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(at) }, Modifier.testTag("planOk")) { Text("Planen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (pickTime) TimeDialog(at.toLocalTime(), onDismiss = { pickTime = false }) { at = at.with(it) }
    if (pickDate) {
        val state = rememberDatePickerState(at.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { at = at.with(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}

private fun preNoticeSummary(form: ReminderForm) = when (form.preNotice) {
    PreNoticeKind.NONE -> "aus"
    PreNoticeKind.EVENING -> "am Vorabend um ${Format.time(form.preNoticeEvening)}"
    PreNoticeKind.BEFORE -> "${form.preNoticeBefore} min vorher"
}

private fun blockerSummary(form: ReminderForm, blockers: List<Blocker>) = listOfNotNull(
    blockers.count { it.isOpen }.let { if (it == 0) "keine offen" else "$it offen" },
    "gelöst zählt als Minimum".takeIf { form.solvedBlockerIsMinimum },
).joinToString(" · ")
