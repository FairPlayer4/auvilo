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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.avlreminder.core.usecases.ReminderList
import com.avlreminder.core.usecases.ReminderSort
import com.avlreminder.ui.AvlSwitch
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

/** All reminders in groups (L4), sortable (L5); finished and archived ones folded (L1–L3). */
@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    openByReminder: Map<Long, Occurrence>,
    today: LocalDate,
    zone: ZoneId,
    onEdit: (Reminder) -> Unit,
    onSetActive: (Reminder, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sort: ReminderSort = ReminderSort.NEXT_DUE,
    onSort: (ReminderSort) -> Unit = {},
    onRestore: (Reminder) -> Unit = {},
) {
    val c = Avl.colors
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val groups = ReminderList.group(reminders, openByReminder, sort)
    val card: @Composable (Reminder) -> Unit = { r ->
        ReminderCard(r, openByReminder[r.id], today, zone, onEdit, onSetActive, onRestore)
    }
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item { ScreenTitle("Reminder") }
        if (reminders.isEmpty()) {
            item { Text("Noch keine Reminder.", Modifier.padding(top = 24.dp), color = c.muted) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(ReminderSort.NEXT_DUE to "Nächste zuerst", ReminderSort.NAME to "Name", ReminderSort.NEWEST to "Neueste")) { (s, label) ->
                        FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(label) })
                    }
                }
            }
        }
        items(groups.active, key = { it.id }) { card(it) }
        if (groups.paused.isNotEmpty()) {
            item { SectionTitle("Pausiert") }
            items(groups.paused, key = { it.id }) { card(it) }
        }
        if (groups.closed.isNotEmpty()) {
            item {
                TextButton(onClick = { showClosed = !showClosed }, Modifier.padding(top = 8.dp).testTag("closed")) {
                    Text((if (showClosed) "▾ " else "▸ ") + "Abgeschlossen (${groups.closed.size})", color = c.accent)
                }
            }
            if (showClosed) items(groups.closed, key = { it.id }) { card(it) }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun ReminderCard(
    r: Reminder,
    open: Occurrence?,
    today: LocalDate,
    zone: ZoneId,
    onEdit: (Reminder) -> Unit,
    onSetActive: (Reminder, Boolean) -> Unit,
    onRestore: (Reminder) -> Unit,
) {
    val c = Avl.colors
    val next = open?.currentAt
    val finished = ReminderList.finished(r, open)
    val detail = buildString {
        append(Format.plan(r))
        when {
            r.archivedAt != null -> append(" · archiviert")
            // A one-off that already happened rings again only with a new time.
            finished && r.schedule is Schedule.Once -> append(" · abgeschlossen – neue Zeit wählen, um ihn zu wiederholen")
            finished -> append(" · erledigt")
            !r.active -> append(" · pausiert")
            r.schedule == Schedule.OnArrival -> append(" · wartet auf Ankunft")
            next != null -> append(" · nächstes: ${Format.moment(next, zone, today)}")
        }
    }
    EntryCard(title = r.title, detail = detail, dimmed = !r.active || finished, onClick = { onEdit(r) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StrictnessTag(r.strictness)
        }
        when {
            r.archivedAt != null -> TextButton(onClick = { onRestore(r) }) { Text("Wiederherstellen", color = c.accent) }
            // Switching it on would plan nothing; the card opens the form for a new time.
            finished -> {}
            else -> AvlSwitch(r.active, { onSetActive(r, it) })
        }
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
