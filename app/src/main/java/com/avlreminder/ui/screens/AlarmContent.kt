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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
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
)

private enum class View { MAIN, PROOF, POSTPONE, SKIP }

/**
 * Alarm (always dark), with its sub-steps proof, postpone and skip. [camera] takes the photo;
 * it is a slot so tests do not need a camera.
 */
@Composable
fun AlarmContent(
    ui: AlarmUi,
    now: Instant,
    zone: ZoneId,
    onComplete: (minimumOnly: Boolean, photoPath: String?, note: String?) -> Unit,
    onSnooze: () -> Unit,
    onPostpone: (Instant) -> Unit,
    onSkip: (String) -> Unit,
    camera: @Composable (onPhoto: (String) -> Unit) -> Unit,
) {
    val c = Avl.colors
    var view by remember { mutableStateOf(View.MAIN) }
    var minimumOnly by remember { mutableStateOf(false) }
    val r = ui.reminder

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            when (view) {
                View.MAIN -> {
                    Text(
                        "${Format.strictness(r.strictness).replaceFirstChar { it.uppercase() }} · fällig seit ${Format.time(ui.occurrence.currentAt, zone)}",
                        color = c.muted, fontSize = 14.sp,
                    )
                    Text(Format.time(now, zone), color = c.text, fontSize = 56.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(16.dp))
                    Text(r.title, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    r.why?.let { Text("„$it“", Modifier.padding(top = 8.dp), color = c.muted, fontSize = 16.sp, fontStyle = FontStyle.Italic) }
                    Spacer(Modifier.height(20.dp))
                    r.goal?.let { InfoCard("Ziel", it) }
                    r.minimum?.let { min ->
                        val left = ui.minimumsLeft?.let { " · noch ${it}× diese Woche" } ?: ""
                        InfoCard("Minimum$left", min)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { minimumOnly = false; if (r.proof == ProofType.PHOTO) view = View.PROOF else onComplete(false, null, null) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Erledigt", fontSize = 18.sp) }
                    if (r.minimum != null) {
                        OutlinedButton(
                            onClick = { minimumOnly = true; if (r.proof == ProofType.PHOTO) view = View.PROOF else onComplete(true, null, null) },
                            enabled = ui.minimumAllowed,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(if (ui.minimumAllowed) "Nur Minimum geschafft" else "Minimum diese Woche aufgebraucht") }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) { Text("Später\n+10 min") }
                        OutlinedButton(onClick = { view = View.POSTPONE }, enabled = ui.canPostpone, modifier = Modifier.weight(1f)) {
                            Text("Verschieben\n" + (ui.postponesLeft?.let { "noch ${it}×" } ?: "beliebig"))
                        }
                        OutlinedButton(onClick = { view = View.SKIP }, modifier = Modifier.weight(1f)) { Text("Auslassen\nmit Grund") }
                    }
                }
                View.PROOF -> ProofStep(ui, minimumOnly, onComplete, onBack = { view = View.MAIN }, camera = camera)
                View.POSTPONE -> PostponeStep(ui, now, zone, onPostpone, onBack = { view = View.MAIN })
                View.SKIP -> SkipStep(onSkip, onBack = { view = View.MAIN })
            }
        }
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

@Composable
private fun ProofStep(
    ui: AlarmUi,
    minimumOnly: Boolean,
    onComplete: (Boolean, String?, String?) -> Unit,
    onBack: () -> Unit,
    camera: @Composable (onPhoto: (String) -> Unit) -> Unit,
) {
    val c = Avl.colors
    var note by remember { mutableStateOf("") }
    StepHeader("Foto-Nachweis", "${ui.reminder.title} · ${if (minimumOnly) "Minimum" else "Ziel"}")
    camera { path -> onComplete(minimumOnly, path, note.ifBlank { null }) }
    Text("Foto bleibt in der App, nicht in der Galerie.", Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp)
    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Notiz (optional)") })
    TextButton(onClick = { onComplete(minimumOnly, null, note.ifBlank { null }) }, Modifier.padding(top = 12.dp)) {
        Text("Ohne Foto abschließen – zählt als nicht erfolgreich", color = c.strict)
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
