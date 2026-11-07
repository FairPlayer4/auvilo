package com.avlreminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.SnoozeHint
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.rules.WeekStatus
import com.avlreminder.core.usecases.Digests
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.Today
import com.avlreminder.ui.EntryCard
import com.avlreminder.ui.Format
import com.avlreminder.ui.ResultBadge
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.StrictnessTag
import com.avlreminder.ui.theme.Avl
import java.time.LocalDate
import java.time.ZoneId

/** "Heute": what is due now, what comes later today, what is already done. */
@Composable
fun TodayScreen(
    today: Today,
    date: LocalDate,
    zone: ZoneId,
    onOpenDue: (Entry) -> Unit,
    onEdit: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
    onCompleteEarly: (Entry) -> Unit = {},
    /** "Verwerfen" for an overdue deadline. */
    onDiscard: (Entry) -> Unit = {},
    doNotDisturb: Boolean = false,
    itemsDone: Map<Long, List<ItemDone>> = emptyMap(),
    /** Reminders with a weekly goal and their week so far. */
    weeks: List<Pair<Reminder, WeekStatus>> = emptyList(),
    /** "bis 19:30" while all alarms are paused (PS7), null otherwise. */
    pausedUntil: String? = null,
    onResume: () -> Unit = {},
    /** Reminders snoozed again and again (NT2). */
    hints: List<SnoozeHint> = emptyList(),
    onHint: (SnoozeHint, SnoozeHintChoice) -> Unit = { _, _ -> },
    /** "Zurücknehmen" after a misclick (Anf. 7.31). */
    onReopen: (Entry) -> Unit = {},
) {
    fun progress(e: Entry): String {
        val items = e.reminder.checklist
        return if (items.isEmpty()) "" else " · ${itemsDone[e.occurrence.id].orEmpty().size} von ${items.size}"
    }

    /** "verschoben von 18:00 · " for one moved before it rang (7.32 VB6). */
    fun moved(e: Entry): String = e.occurrence.takeIf { !it.hasFired && it.postponeCount > 0 }
        ?.let { "verschoben von ${Format.time(it.dueAt, zone)} · " } ?: ""

    val c = Avl.colors
    var discarding by remember { mutableStateOf<Entry?>(null) }
    var reopening by remember { mutableStateOf<Entry?>(null) }
    val waiting = if (doNotDisturb) today.dueNow.count { !it.reminder.ignoreDnd } else 0
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text(Format.longDay(date), color = c.muted, fontSize = 14.sp)
                Text("Heute", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (pausedUntil != null) item { PausedBanner(pausedUntil, onResume) }
        items(hints, key = { "hint${it.reminder.id}" }) { SnoozeHintCard(it, onHint) }
        if (waiting > 0) {
            item {
                Text(
                    "„Nicht stören“ aktiv – $waiting ${if (waiting == 1) "Reminder wartet" else "Reminder warten"} still bis zum Ende",
                    Modifier.padding(top = 12.dp), color = c.muted, fontSize = 14.sp,
                )
            }
        }
        if (today.dueNow.isEmpty() && today.laterToday.isEmpty() && today.resolved.isEmpty() && today.waiting.isEmpty() && today.overdue.isEmpty()) {
            item {
                Text(
                    "Heute steht nichts mehr an. Mit + legst du einen Reminder an.",
                    Modifier.padding(top = 32.dp), color = c.muted, fontSize = 15.sp,
                )
            }
        }
        if (today.dueNow.isNotEmpty()) {
            item { SectionTitle("Jetzt fällig") }
            items(today.dueNow, key = { "due" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = "seit ${Format.time(e.occurrence.currentAt, zone)}" + progress(e) + proofHint(e.reminder),
                    highlight = c.strict,
                    onClick = { onOpenDue(e) },
                ) { StrictnessTag(e.reminder.strictness) }
            }
        }
        if (today.overdue.isNotEmpty()) {
            // Passed deadlines stay until done or discarded (design 12.4 point 9).
            item { SectionTitle("Überfällig") }
            items(today.overdue, key = { "over" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = Format.plan(e.reminder) + progress(e),
                    highlight = c.strict,
                    onClick = { onEdit(e.reminder) },
                ) {
                    Column {
                        TextButton(onClick = { onCompleteEarly(e) }) { Text("Erledigen", color = c.accent) }
                        TextButton(onClick = { discarding = e }, Modifier.testTag("discard${e.occurrence.id}")) { Text("Verwerfen", color = c.muted) }
                    }
                }
            }
        }
        if (today.laterToday.isNotEmpty()) {
            item { SectionTitle("Später heute") }
            items(today.laterToday, key = { "later" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = moved(e) + (goalLine(e.reminder) ?: Format.plan(e.reminder)) + progress(e),
                    time = Format.time(e.occurrence.currentAt, zone),
                    onClick = { onEdit(e.reminder) },
                ) {
                    StrictnessTag(e.reminder.strictness)
                    TextButton(onClick = { onCompleteEarly(e) }, Modifier.testTag("early${e.occurrence.id}")) { Text("Vorab", color = c.accent) }
                }
            }
        }
        if (today.waiting.isNotEmpty()) {
            item { SectionTitle("Wenn du zuhause bist") }
            items(today.waiting, key = { "wait" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = if (e.occurrence.awaitsArrival) "verschoben – klingelt bei Ankunft, sonst heute verpasst" + progress(e)
                    else (goalLine(e.reminder) ?: "klingelt bei Ankunft") + progress(e),
                    onClick = { onEdit(e.reminder) },
                ) {
                    StrictnessTag(e.reminder.strictness)
                    TextButton(onClick = { onCompleteEarly(e) }) { Text("Vorab", color = c.accent) }
                }
            }
        }
        if (weeks.isNotEmpty()) {
            item { SectionTitle("Diese Woche") }
            items(weeks, key = { "week" + it.first.id }) { (r, w) ->
                EntryCard(title = r.title, detail = Digests.week(w), highlight = if (w.tight) c.strict else null, onClick = { onEdit(r) })
            }
        }
        if (today.resolved.isNotEmpty()) {
            item { SectionTitle("Erledigt") }
            items(today.resolved, key = { "done" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = e.occurrence.resolvedAt?.let { Format.time(it, zone) },
                    dimmed = true,
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        e.occurrence.resolution?.let { ResultBadge(it) }
                        if (Lifecycle.canReopen(e.occurrence)) {
                            TextButton(onClick = { reopening = e }, Modifier.testTag("reopen${e.occurrence.id}")) {
                                Text("Zurücknehmen", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
    reopening?.let { e ->
        AlertDialog(
            onDismissRequest = { reopening = null },
            title = { Text("„${e.reminder.title}“ zurücknehmen?") },
            text = { Text("Er ist wieder offen und klingelt in 10 Minuten. Ein Nachweis bleibt gespeichert und zählt beim nächsten Erledigt.") },
            confirmButton = {
                TextButton(onClick = { onReopen(e); reopening = null }, Modifier.testTag("reopenOk")) { Text("Zurücknehmen") }
            },
            dismissButton = { TextButton(onClick = { reopening = null }) { Text("Abbrechen") } },
        )
    }
    discarding?.let { e ->
        AlertDialog(
            onDismissRequest = { discarding = null },
            title = { Text("„${e.reminder.title}“ verwerfen?") },
            text = { Text("Die Frist ist vorbei. Verworfen erinnert die App nicht mehr daran; im Verlauf steht es als übersprungen.") },
            confirmButton = {
                TextButton(onClick = { onDiscard(e); discarding = null }, Modifier.testTag("discardOk")) { Text("Verwerfen", color = c.strict) }
            },
            dismissButton = { TextButton(onClick = { discarding = null }) { Text("Abbrechen") } },
        )
    }
}

internal fun goalLine(r: Reminder): String? = when {
    r.goal != null && r.minimum != null -> "Ziel ${r.goal} · Minimum ${r.minimum}"
    r.goal != null -> "Ziel ${r.goal}"
    else -> null
}

private fun proofHint(r: Reminder): String {
    val p = r.proof
    if (!p.needsEvidence) return ""
    val parts = listOfNotNull("Foto".takeIf { p.photo }, "Text".takeIf { p.textMinChars != null }, "Audio".takeIf { p.audioMinSeconds != null })
    return " · Nachweis: " + parts.joinToString(" + ")
}

/** All alarms paused by hand (PS7): impossible to overlook, one tap to switch them on again. */
@Composable
private fun PausedBanner(until: String, onResume: () -> Unit) {
    val c = Avl.colors
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.strict)
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("pausedBanner"),
    ) {
        Text("Alle Alarme pausiert $until", color = c.onStrict, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Fällige Reminder kommen nur still. Offen Gebliebenes zählt als ausgelassen (Notaus).", color = c.onStrict, fontSize = 13.sp)
        TextButton(onClick = onResume, Modifier.testTag("resumeAlarms")) { Text("Wieder einschalten", color = c.onStrict) }
    }
}

/** "Training schiebst du oft – lieber um 19:15?" (NT2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnoozeHintCard(hint: SnoozeHint, onHint: (SnoozeHint, SnoozeHintChoice) -> Unit) {
    val c = Avl.colors
    val question = hint.time?.let { "lieber um ${Format.time(it)}?" } ?: "lieber lockerer?"
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accentSoft)
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("snoozeHint${hint.reminder.id}"),
    ) {
        Text("„${hint.reminder.title}“ schiebst du oft – $question", color = c.onAccentSoft, fontSize = 15.sp)
        FlowRow {
            hint.time?.let { t ->
                TextButton(onClick = { onHint(hint, SnoozeHintChoice.TIME) }, Modifier.testTag("hintTime")) { Text("Auf ${Format.time(t)}") }
            }
            hint.relax?.let { s ->
                TextButton(onClick = { onHint(hint, SnoozeHintChoice.RELAX) }, Modifier.testTag("hintRelax")) {
                    Text("Strenge: ${Format.strictness(s)}")
                }
            }
            TextButton(onClick = { onHint(hint, SnoozeHintChoice.DISMISS) }, Modifier.testTag("hintDismiss")) { Text("Nein danke", color = c.muted) }
        }
    }
}
