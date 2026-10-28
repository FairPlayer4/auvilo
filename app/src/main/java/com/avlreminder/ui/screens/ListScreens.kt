package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.ui.EntryCard
import com.avlreminder.ui.Format
import com.avlreminder.ui.ResultBadge
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.StrictnessTag
import com.avlreminder.ui.theme.Avl
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ScreenTitle(text: String) {
    Text(text, Modifier.padding(top = 8.dp, bottom = 4.dp), color = Avl.colors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
}

/** All reminders with their schedule, next time and an on/off switch. */
@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    openByReminder: Map<Long, Occurrence>,
    today: LocalDate,
    zone: ZoneId,
    onEdit: (Reminder) -> Unit,
    onSetActive: (Reminder, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Avl.colors
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item { ScreenTitle("Reminder") }
        if (reminders.isEmpty()) {
            item { Text("Noch keine Reminder.", Modifier.padding(top = 24.dp), color = c.muted) }
        }
        items(reminders, key = { it.id }) { r ->
            val next = openByReminder[r.id]?.currentAt
            val detail = buildString {
                append(Format.plan(r))
                if (!r.active) append(" · pausiert")
                else if (r.schedule == Schedule.OnArrival) append(if (next != null) " · wartet auf Ankunft" else " · erledigt")
                else if (next != null) append(" · nächstes: ${Format.moment(next, zone, today)}")
            }
            EntryCard(title = r.title, detail = detail, dimmed = !r.active, onClick = { onEdit(r) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrictnessTag(r.strictness)
                }
                Switch(checked = r.active, onCheckedChange = { onSetActive(r, it) })
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** Resolved occurrences by day, filterable by reminder. */
@Composable
fun HistoryScreen(
    days: List<HistoryDay>,
    proofs: Map<Long, List<Proof>>,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onPlay: (String) -> Unit = {},
    itemsDone: Map<Long, List<ItemDone>> = emptyMap(),
) {
    val c = Avl.colors
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    val titles = days.flatMap { d -> d.entries.map { it.reminder.id to it.reminder.title } }.distinct()
    val shown = days
        .map { d -> d.copy(entries = d.entries.filter { filter == null || it.reminder.id == filter }) }
        .filter { it.entries.isNotEmpty() }
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item { ScreenTitle("Verlauf") }
        if (titles.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Alle") }) }
                    items(titles, key = { it.first }) { (id, title) ->
                        FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(title) })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item { Text("Noch nichts erledigt.", Modifier.padding(top = 24.dp), color = c.muted) }
        }
        shown.forEach { day ->
            item(key = "day" + day.date) { SectionTitle(Format.day(day.date, today)) }
            items(day.entries, key = { "h" + it.occurrence.id }) { e ->
                val o = e.occurrence
                val time = Format.time(o.resolvedAt!!, zone)
                val own = proofs[o.id].orEmpty()
                val audio = own.firstOrNull { it.type == ProofType.AUDIO }?.filePath
                val detail = when (o.resolution) {
                    Resolution.MISSED -> "keine Reaktion bis $time"
                    Resolution.SKIPPED -> "$time · Grund: ${o.note ?: "–"}"
                    else -> listOfNotNull(
                        time,
                        "vor dem Reminder erledigt".takeIf { o.resolvedAt!! < o.dueAt },
                        proofLine(own),
                        own.firstOrNull { it.type == ProofType.TEXT }?.text?.let { "„$it“" },
                        o.note,
                        itemLine(e.reminder.checklist, itemsDone[o.id].orEmpty(), zone),
                    ).joinToString(" · ")
                }
                EntryCard(title = e.reminder.title, detail = detail, time = time) {
                    if (audio != null) TextButton(onClick = { onPlay(audio) }) { Text("▶ Audio", color = c.accent) }
                    o.resolution?.let { ResultBadge(it) }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** When each checklist item was done (C7), in the order of the list. */
private fun itemLine(items: List<ChecklistItem>, done: List<ItemDone>, zone: ZoneId): String? {
    if (done.isEmpty()) return null
    val at = done.associate { it.itemId to it.doneAt }
    return items.filter { it.id in at }.joinToString(", ") { "${it.title} ${Format.time(at.getValue(it.id), zone)}" }
}

private fun proofLine(proofs: List<Proof>): String? {
    val kinds = proofs.map { it.type }.distinct().sorted().map {
        when (it) {
            ProofType.PHOTO -> "Foto"
            ProofType.TEXT -> "Text"
            ProofType.AUDIO -> "Audio"
        }
    }
    return if (kinds.isEmpty()) null else "mit " + kinds.joinToString(" + ")
}
