package com.avlreminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.ui.theme.Avl
import java.time.LocalTime

/** Switch with a visible off state: with the theme's line color alone it looked disabled. */
@Composable
fun AvlSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Avl.colors
    Switch(
        checked, onChange, modifier,
        colors = SwitchDefaults.colors(uncheckedThumbColor = c.muted, uncheckedTrackColor = c.surface, uncheckedBorderColor = c.muted),
    )
}

/** Free time entry; [error] keeps OK off while the time does not fit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, error: (LocalTime) -> String? = { null }, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    val picked = LocalTime.of(state.hour, state.minute)
    val problem = error(picked)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(picked); onDismiss() }, Modifier.testTag("timeOk"), enabled = problem == null) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            Column {
                TimeInput(state)
                problem?.let { Text(it, color = Avl.colors.strict, fontSize = 13.sp) }
            }
        },
    )
}

/** Free number entry within [range], for values beyond the offered chips. */
@Composable
fun NumberDialog(title: String, initial: Int?, range: IntRange, unit: String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    val value = text.toIntOrNull()?.takeIf { it in range }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    text, { t -> text = t.filter(Char::isDigit).take(4) }, Modifier.testTag("number"), singleLine = true,
                    suffix = if (unit.isEmpty()) null else ({ Text(unit) }),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("${range.first} bis ${range.last}", Modifier.padding(top = 4.dp), color = Avl.colors.muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onPick); onDismiss() }, Modifier.testTag("numberOk"), enabled = value != null) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** A section that opens on tap; while closed, [summary] says what is set. */
@Composable
fun Fold(title: String, summary: String, tag: String, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    val c = Avl.colors
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.testTag(tag).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                if (!open) Text(summary, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (open) "▴" else "▾", color = c.muted, fontSize = 18.sp)
        }
        if (open) content()
        HorizontalDivider(Modifier.padding(top = 8.dp), color = c.line)
    }
}
