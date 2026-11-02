package com.avlreminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.WeekStatus
import com.avlreminder.core.usecases.Digests
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.ProofCheck
import com.avlreminder.ui.Format
import com.avlreminder.ui.theme.Avl
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Everything the alarm screen shows; limits already evaluated by the engine. */
data class AlarmUi(
    val reminder: Reminder,
    val occurrence: Occurrence,
    val minimumAllowed: Boolean,
    /** null = unlimited. */
    val minimumsLeft: Int?,
    val canPostpone: Boolean,
    /** null = unlimited. */
    val postponesLeft: Int?,
    /** Snooze buttons that stay before the end of the day. */
    val snooze: List<Int> = emptyList(),
    /** Current "Startklar" answers. */
    val prep: Map<Int, PrepAnswer> = emptyMap(),
    val lastProofText: String? = null,
    /** Opened before the reminder is due ("vorab erledigen"): only done and minimum. */
    val early: Boolean = false,
    /** Checklist items already done in this occurrence. */
    val done: Set<Long> = emptySet(),
    val canDeferRest: Boolean = false,
    /** The week so far, for a reminder with a weekly goal. */
    val week: WeekStatus? = null,
    /** "Blocker gelöst" may close it as the minimum (S7). */
    val blockerSolved: Boolean = false,
    /** It got stuck several times in a row: ask "Was hält dich gerade ab?" (S8). */
    val askBlocker: Boolean = false,
    /** A deadline's next window from tomorrow on ("Auf morgen", not a postponement). */
    val tomorrow: Instant? = null,
    /** A deadline not yet passed: postponing must stay before it. */
    val deadline: Instant? = null,
    /** Open blockers of the reminder. */
    val blockers: List<Blocker> = emptyList(),
)

private enum class View { MAIN, PROOF, POSTPONE, SKIP }

/** Slots for what needs the device, so tests run without camera, microphone or speaker. */
class AlarmDevices(
    val camera: @Composable (onPhoto: (String) -> Unit) -> Unit,
    val recorder: @Composable (onRecorded: (String, Int) -> Unit) -> Unit,
    val play: (String) -> Unit,
)

/**
 * Alarm (always dark), with its sub-steps proof, postpone and skip. Opening the proof pauses
 * the alarm ([onStartProof]); going back resumes it ([onAbortProof]).
 */
@Composable
fun AlarmContent(
    ui: AlarmUi,
    now: Instant,
    zone: ZoneId,
    onComplete: (minimumOnly: Boolean, evidence: Evidence, note: String?) -> Unit,
    onSnooze: (minutes: Int) -> Unit,
    onPostpone: (Instant) -> Unit,
    onSkip: (String) -> Unit,
    onStartProof: () -> Unit,
    onAbortProof: () -> Unit,
    devices: AlarmDevices,
    onCheckItem: (itemId: Long, evidence: Evidence) -> Unit = { _, _ -> },
    onDeferRest: (Instant) -> Unit = {},
    /** Notes an improvement; the alarm keeps going meanwhile. */
    onSuggest: (text: String, important: Boolean) -> Unit = { _, _ -> },
    /** "Blocker gelöst – zählt als Minimum". */
    onBlockerSolved: () -> Unit = {},
    /** The answer to "Was hält dich gerade ab?"; null = skipped. [plan]: also plan it as a task. */
    onAnswerAsk: (text: String?, plan: Boolean) -> Unit = { _, _ -> },
    /** "Auf morgen" for a deadline. */
    onTomorrow: (Instant) -> Unit = {},
) {
    val c = Avl.colors
    var view by remember { mutableStateOf(View.MAIN) }
    var suggesting by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }
    var minimumOnly by remember { mutableStateOf(false) }
    /** The checklist item the proof is for; null = the whole reminder. */
    var proofItem by remember { mutableStateOf<ChecklistItem?>(null) }
    val r = ui.reminder
    val checklist = r.checklist.isNotEmpty()

    fun complete(minimum: Boolean) {
        minimumOnly = minimum
        proofItem = null
        if (r.proof.needsEvidence) {
            if (!ui.early) onStartProof()
            view = View.PROOF
        } else {
            onComplete(minimum, Evidence(), null)
        }
    }

    fun check(item: ChecklistItem) {
        if (r.proof.needsEvidence) {
            proofItem = item
            if (!ui.early) onStartProof()
            view = View.PROOF
        } else {
            onCheckItem(item.id, Evidence())
        }
    }

    if (ui.askBlocker && !asked && !ui.early) {
        AskDialog { text, plan ->
            asked = true
            onAnswerAsk(text, plan)
        }
    }
    if (suggesting) {
        SuggestionDialog(onSave = { text, important -> onSuggest(text, important); suggesting = false }, onDismiss = { suggesting = false })
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
            when (view) {
                View.MAIN -> {
                    val head = if (ui.early) "Vorab erledigen · fällig um ${Format.time(ui.occurrence.currentAt, zone)}"
                    else "${Format.strictness(r.strictness).replaceFirstChar { it.uppercase() }} · fällig seit ${Format.time(ui.occurrence.currentAt, zone)}"
                    Text(head, color = c.muted, fontSize = 14.sp)
                    Text(Format.time(now, zone), color = c.text, fontSize = 56.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(16.dp))
                    Text(r.title, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    r.why?.let { Text("„$it“", Modifier.padding(top = 8.dp), color = c.muted, fontSize = 16.sp, fontStyle = FontStyle.Italic) }
                    r.motivationAudio?.let { path ->
                        TextButton(onClick = { devices.play(path) }) { Text("▶ Botschaft an mich abspielen", color = c.accent) }
                    }
                    Spacer(Modifier.height(20.dp))
                    PrepCards(ui.prep)
                    if (ui.blockers.isNotEmpty()) InfoCard("Noch offen", ui.blockers.joinToString("\n") { it.text })
                    ui.week?.let { InfoCard("Diese Woche", Digests.week(it)) }
                    r.goal?.let { InfoCard("Ziel", it) }
                    r.minimum?.let { min ->
                        val left = ui.minimumsLeft?.let { " · noch ${it}× diese Woche" } ?: ""
                        InfoCard("Minimum$left", min)
                    }
                    if (checklist) Checklist(r.checklist, ui.done, ::check)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { complete(false) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (checklist) "Alles erledigt" else "Erledigt", fontSize = 18.sp)
                    }
                    if (r.minimum != null) {
                        OutlinedButton(
                            onClick = { complete(true) },
                            enabled = ui.minimumAllowed,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(if (ui.minimumAllowed) "Nur Minimum geschafft" else "Minimum diese Woche aufgebraucht") }
                    }
                    if (ui.blockerSolved) {
                        OutlinedButton(onClick = onBlockerSolved, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("blockerSolved")) {
                            Text("Blocker gelöst – zählt als Minimum")
                        }
                    }
                    if (!ui.early) {
                        if (checklist) {
                            val time = ui.occurrence.dueAt.atZone(zone).toLocalTime()
                            val tomorrow = now.atZone(zone).toLocalDate().plusDays(1).atTime(time).atZone(zone).toInstant()
                            OutlinedButton(
                                onClick = { onDeferRest(tomorrow) },
                                enabled = ui.canDeferRest,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text("Rest auf morgen ${Format.time(tomorrow, zone)}") }
                        }
                        ui.tomorrow?.let { at ->
                            OutlinedButton(onClick = { onTomorrow(at) }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("tomorrow")) {
                                Text("Auf morgen ${Format.moment(at, zone, now.atZone(zone).toLocalDate())}")
                            }
                        }
                        if (ui.snooze.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ui.snooze.forEach { minutes ->
                                    OutlinedButton(onClick = { onSnooze(minutes) }, modifier = Modifier.weight(1f)) { Text("Später\n+$minutes min") }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { view = View.POSTPONE }, enabled = ui.canPostpone, modifier = Modifier.weight(1f)) {
                                Text("Verschieben\n" + (ui.postponesLeft?.let { "noch ${it}×" } ?: "beliebig"))
                            }
                            OutlinedButton(onClick = { view = View.SKIP }, modifier = Modifier.weight(1f)) { Text("Auslassen\nmit Grund") }
                        }
                    }
                    // Unobtrusive at the very end, so it does not distract from reacting (Anf. 7.24, VV1).
                    TextButton(onClick = { suggesting = true }, Modifier.padding(top = 24.dp).testTag("suggest")) {
                        Text("Verbesserung vorschlagen", color = c.muted, fontSize = 13.sp)
                    }
                }
                View.PROOF -> ProofStep(
                    ui,
                    subtitle = proofItem?.title ?: if (minimumOnly) "Minimum" else "Ziel",
                    onDone = { evidence, note ->
                        val item = proofItem
                        if (item == null) {
                            onComplete(minimumOnly, evidence, note)
                        } else {
                            proofItem = null
                            view = View.MAIN
                            onCheckItem(item.id, evidence)
                        }
                    },
                    onBack = {
                        if (!ui.early) onAbortProof()
                        view = View.MAIN
                    },
                    devices = devices,
                )
                View.POSTPONE -> PostponeStep(ui, now, zone, onPostpone, onBack = { view = View.MAIN })
                View.SKIP -> SkipStep(onSkip, onBack = { view = View.MAIN })
            }
        }
    }
}

/** Done items ticked, open ones as buttons; each can be done on its own. */
@Composable
private fun Checklist(items: List<ChecklistItem>, done: Set<Long>, onCheck: (ChecklistItem) -> Unit) {
    val c = Avl.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(14.dp)) {
        Text("Checkliste · ${items.count { it.id in done }} von ${items.size}", color = c.muted, fontSize = 13.sp)
        items.forEach { item ->
            if (item.id in done) {
                Text("✓ ${item.title}", Modifier.padding(vertical = 10.dp), color = c.muted, fontSize = 17.sp)
            } else {
                OutlinedButton(onClick = { onCheck(item) }, Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("item${item.id}")) {
                    Text("○  ${item.title}", Modifier.weight(1f), color = c.text, fontSize = 17.sp)
                }
            }
        }
    }
}

/** Goal (question 1), first step (question 4) and open points from the "Startklar" answers. */
@Composable
private fun PrepCards(prep: Map<Int, PrepAnswer>) {
    prep[Prep.GOAL]?.takeIf { !it.unknown && !it.text.isNullOrBlank() }?.let { InfoCard("Davon hast du", it.text!!) }
    prep[Prep.FIRST_STEP]?.takeIf { !it.unknown && !it.text.isNullOrBlank() }?.let { InfoCard("Erster Schritt", it.text!!) }
    val open = Prep.open(prep)
    if (open.isNotEmpty()) {
        InfoCard("Offen", open.joinToString("\n") { if (it.unknown) "${PrepTexts.short(it.question)}: weiß ich noch nicht" else it.text!! })
    }
}

/** The four questions (requirements 7.21), shared by the form and the alarm. */
object PrepTexts {
    val QUESTIONS = mapOf(
        1 to "Was hast du davon, wenn es erledigt ist?",
        2 to "Was könnte dich abhalten?",
        3 to "Kannst du sofort starten? Wenn nein: was fehlt?",
        4 to "Was ist der allererste Schritt?",
    )

    fun short(question: Int) = when (question) {
        1 -> "Ziel"
        2 -> "Hindernis"
        3 -> "Startbereit"
        else -> "Erster Schritt"
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    val c = Avl.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(14.dp)) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val c = Avl.colors
    Text(title, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, Modifier.padding(top = 4.dp, bottom = 16.dp), color = c.muted, fontSize = 14.sp)
}

/** Photo, text and audio as the reminder asks; parts can be given in any order. */
@Composable
private fun ProofStep(
    ui: AlarmUi,
    subtitle: String,
    onDone: (Evidence, String?) -> Unit,
    onBack: () -> Unit,
    devices: AlarmDevices,
) {
    val c = Avl.colors
    val spec = ui.reminder.proof
    var photo by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var audio by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var note by remember { mutableStateOf("") }
    val evidence = Evidence(photo, text.ifBlank { null }, audio?.first, audio?.second)
    val problems = ProofCheck.problems(spec, evidence, ui.lastProofText)

    val pause = if (ui.early) "" else " · Alarm pausiert ${spec.windowMinutes} min"
    StepHeader("Nachweis", "${ui.reminder.title} · $subtitle$pause")

    if (spec.photo) {
        Text("Foto", color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        if (photo == null) {
            devices.camera { photo = it }
            Text("Foto bleibt in der App, nicht in der Galerie.", Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)
        } else {
            Row {
                Text("✓ Foto aufgenommen", Modifier.weight(1f).padding(top = 12.dp), color = c.text)
                TextButton(onClick = { photo = null }) { Text("Neu", color = c.accent) }
            }
        }
    }
    spec.textMinChars?.let { min ->
        Text("Was hast du getan?", color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth().padding(top = 6.dp).testTag("proofText"), minLines = 3,
            supportingText = { Text("${ProofCheck.chars(text)} / $min Zeichen · Diktieren über die Tastatur geht auch") },
        )
    }
    spec.audioMinSeconds?.let { min ->
        Text("Sprachaufnahme (mind. $min s)", color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        audio?.let { (path, seconds) ->
            Row {
                Text("✓ Aufnahme · $seconds s", Modifier.weight(1f).padding(top = 12.dp), color = c.text)
                TextButton(onClick = { devices.play(path) }) { Text("▶", color = c.accent) }
            }
        }
        devices.recorder { path, seconds -> audio = path to seconds }
    }
    if (spec.textMinChars == null) {
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Notiz (optional)") })
    }

    problems.forEach { Text(it, Modifier.padding(top = 8.dp), color = c.muted, fontSize = 14.sp) }
    Button(
        onClick = { onDone(evidence, note.ifBlank { null }) },
        enabled = problems.isEmpty(),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
    ) { Text("Abschließen", fontSize = 18.sp) }
    TextButton(onClick = { onDone(evidence, note.ifBlank { null }) }, Modifier.padding(top = 12.dp), enabled = problems.isNotEmpty()) {
        Text("Ohne vollständigen Nachweis abschließen – zählt als nicht erfolgreich", color = if (problems.isNotEmpty()) c.strict else c.muted)
    }
    TextButton(onClick = onBack) { Text("Zurück", color = c.muted) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostponeStep(ui: AlarmUi, now: Instant, zone: ZoneId, onPostpone: (Instant) -> Unit, onBack: () -> Unit) {
    val c = Avl.colors
    var pickDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val today = now.atZone(zone).toLocalDate()
    val left = ui.postponesLeft
    StepHeader(
        "Verschieben",
        ui.reminder.title + (left?.let { if (it == 1) " · letzte Verschiebung" else " · noch ${it}×" } ?: ""),
    )
    val evening = today.atTime(20, 30).atZone(zone).toInstant()
    val tomorrow = today.plusDays(1).atTime(ui.occurrence.dueAt.atZone(zone).toLocalTime()).atZone(zone).toInstant()
    val options = buildList {
        add("In 1 Stunde" to now.plusSeconds(3600))
        if (evening > now.plusSeconds(1800)) add("Heute Abend" to evening)
        add("Morgen" to tomorrow)
    }.filter { (_, at) -> ui.deadline == null || at < ui.deadline }
    ui.deadline?.let {
        Text("Nur bis zur Frist ${Format.moment(it, zone, today)}.", Modifier.padding(bottom = 8.dp), color = c.muted, fontSize = 13.sp)
    }
    options.forEach { (label, at) ->
        OutlinedButton(onClick = { onPostpone(at) }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(label, Modifier.weight(1f))
            Text(Format.moment(at, zone, today), color = c.muted)
        }
    }
    OutlinedButton(onClick = { pickDate = true }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("Datum und Uhrzeit wählen …") }
    if (left == 1) {
        Text("Danach bleiben nur noch Erledigt, Minimum oder Auslassen.", Modifier.padding(top = 12.dp), color = c.muted, fontSize = 13.sp)
    }
    TextButton(onClick = onBack, Modifier.padding(top = 8.dp)) { Text("Abbrechen", color = c.muted) }

    if (pickDate) {
        val state = rememberDatePickerState(today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Weiter") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
    pickedDate?.let { date ->
        val time = ui.occurrence.dueAt.atZone(zone).toLocalTime()
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickedDate = null },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = null
                    onPostpone(date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant())
                }) { Text("Verschieben") }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text("Abbrechen") } },
            text = { TimeInput(state) },
        )
    }
}

@Composable
private fun SkipStep(onSkip: (String) -> Unit, onBack: () -> Unit) {
    val c = Avl.colors
    var reason by remember { mutableStateOf("") }
    StepHeader("Auslassen", "Zählt neutral, nicht als verpasst.")
    OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("Grund") })
    Button(onClick = { onSkip(reason.trim()) }, enabled = reason.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Auslassen")
    }
    TextButton(onClick = onBack, Modifier.padding(top = 8.dp)) { Text("Abbrechen", color = c.muted) }
}

/** "Was hält dich gerade ab?" (S8): the answer becomes a blocker, optionally a task right away. */
@Composable
private fun AskDialog(onAnswer: (text: String?, plan: Boolean) -> Unit) {
    val c = Avl.colors
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Was hält dich gerade ab?", fontSize = 20.sp) },
        text = {
            Column {
                Text("Das hängt jetzt mehrmals. Was fehlt oder stört?", color = c.muted, fontSize = 14.sp)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("askText"), minLines = 2)
                TextButton(onClick = { onAnswer(text, true) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("askPlan")) {
                    Text("Speichern und als Aufgabe planen")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(text, false) }, Modifier.testTag("askSave"), enabled = text.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = { onAnswer(null, false) }, Modifier.testTag("askSkip")) { Text("Überspringen", color = c.muted) } },
    )
}
