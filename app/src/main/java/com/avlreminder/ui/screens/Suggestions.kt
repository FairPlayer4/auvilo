package com.avlreminder.ui.screens

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.data.SuggestionExport
import com.avlreminder.data.SuggestionStatus
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.EntryCard
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.TextKeyboard
import com.avlreminder.ui.theme.Avl
import kotlinx.coroutines.launch
import java.time.ZoneId

/** The light bulb that opens [SuggestionDialog]; on every screen (Anf. 7.24, VV1). */
@Composable
fun SuggestButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier.testTag("suggest")) {
        Icon(AvlIcons.Idea, "Verbesserung vorschlagen", tint = Avl.colors.muted)
    }
}

/** Light bulb and settings, top right on the three tabs (Vorschlag 9). */
@Composable
fun TopActions(modifier: Modifier, onSuggest: () -> Unit, onSettings: () -> Unit) {
    Row(modifier.padding(8.dp)) {
        SuggestButton(onSuggest)
        IconButton(onClick = onSettings) { Icon(AvlIcons.Settings, "Einstellungen", tint = Avl.colors.text) }
    }
}

/** Quick note of an improvement (VV2): one text field, optionally important. */
@Composable
fun SuggestionDialog(onSave: (text: String, important: Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var important by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verbesserung vorschlagen") },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("suggestionText"),
                    placeholder = { Text("Was ist dir aufgefallen?") }, minLines = 3, keyboardOptions = TextKeyboard,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(important, { important = it }, Modifier.testTag("important"))
                    Text("wichtig", color = Avl.colors.text)
                }
                Text("Bildschirm, Zeit und App-Version werden mitgespeichert.", color = Avl.colors.muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, important) }, Modifier.testTag("suggestionSave"), enabled = text.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** All suggestions (VV4): open ones first, to copy or share as text (VV5). */
@Composable
fun SuggestionsScreen(
    suggestions: List<SuggestionEntity>,
    zone: ZoneId,
    onBack: () -> Unit,
    onUpdate: (SuggestionEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Avl.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showClosed by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SuggestionEntity?>(null) }
    var copied by remember { mutableStateOf(false) }
    val open = suggestions.filter { it.status == SuggestionStatus.OPEN }
    val closed = suggestions.filter { it.status != SuggestionStatus.OPEN }

    Column(modifier) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text("Verbesserungen", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            if (open.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val text = SuggestionExport.markdown(suggestions, zone)
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Verbesserungen", text))) }
                            copied = true
                        }) { Text("Alle offenen kopieren") }
                        OutlinedButton(onClick = { onShare(SuggestionExport.markdown(suggestions, zone)) }) { Text("Teilen") }
                    }
                    if (copied) Text("Kopiert", color = c.muted, fontSize = 13.sp)
                }
            }
            item { SectionTitle("Offen (${open.size})") }
            if (open.isEmpty()) {
                item {
                    Text("Nichts offen. Mit der Glühbirne oben rechts hältst du fest, was dir auffällt.", color = c.muted, fontSize = 15.sp)
                }
            }
            items(open, key = { it.id }) { s -> SuggestionCard(s, zone) { editing = s } }
            if (closed.isNotEmpty()) {
                item {
                    TextButton(onClick = { showClosed = !showClosed }) {
                        Text((if (showClosed) "▾ " else "▸ ") + "Umgesetzt und verworfen (${closed.size})", color = c.accent)
                    }
                }
                if (showClosed) items(closed, key = { it.id }) { s -> SuggestionCard(s, zone) { editing = s } }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    editing?.let { s ->
        SuggestionEditDialog(
            s,
            onUpdate = { onUpdate(it); editing = null },
            onDelete = { onDelete(s.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SuggestionCard(s: SuggestionEntity, zone: ZoneId, onClick: () -> Unit) {
    val status = when (s.status) {
        SuggestionStatus.OPEN -> null
        SuggestionStatus.DONE -> "umgesetzt"
        SuggestionStatus.DISCARDED -> "verworfen"
    }
    EntryCard(
        title = (if (s.important) "Wichtig: " else "") + s.text,
        detail = listOfNotNull(status, SuggestionExport.context(s, zone)).joinToString(" · "),
        dimmed = s.status != SuggestionStatus.OPEN,
        onClick = onClick,
    )
}

/** Text and "wichtig", plus done / discarded / open again and delete. */
@Composable
private fun SuggestionEditDialog(
    s: SuggestionEntity,
    onUpdate: (SuggestionEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(s.text) }
    var important by rememberSaveable { mutableStateOf(s.important) }
    fun with(status: SuggestionStatus) = s.copy(text = text, important = important, status = status)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vorschlag") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("suggestionEdit"), minLines = 3, keyboardOptions = TextKeyboard)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(important, { important = it })
                    Text("wichtig", color = Avl.colors.text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.status == SuggestionStatus.OPEN) {
                        FilterChip(false, { onUpdate(with(SuggestionStatus.DONE)) }, label = { Text("Umgesetzt") })
                        FilterChip(false, { onUpdate(with(SuggestionStatus.DISCARDED)) }, label = { Text("Verworfen") })
                    } else {
                        FilterChip(false, { onUpdate(with(SuggestionStatus.OPEN)) }, label = { Text("Wieder öffnen") })
                    }
                }
                TextButton(onClick = onDelete) { Text("Löschen", color = Avl.colors.strict) }
            }
        },
        confirmButton = { TextButton(onClick = { onUpdate(with(s.status)) }, enabled = text.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
