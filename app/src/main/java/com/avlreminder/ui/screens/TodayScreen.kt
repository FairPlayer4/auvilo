package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Reminder
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
    doNotDisturb: Boolean = false,
    itemsDone: Map<Long, List<ItemDone>> = emptyMap(),
) {
    fun progress(e: Entry): String {
        val items = e.reminder.checklist
        return if (items.isEmpty()) "" else " · ${itemsDone[e.occurrence.id].orEmpty().size} von ${items.size}"
    }

    val c = Avl.colors
    val waiting = if (doNotDisturb) today.dueNow.count { !it.reminder.ignoreDnd } else 0
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text(Format.longDay(date), color = c.muted, fontSize = 14.sp)
                Text("Heute", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (waiting > 0) {
            item {
                Text(
                    "„Nicht stören“ aktiv – $waiting ${if (waiting == 1) "Reminder wartet" else "Reminder warten"} still bis zum Ende",
                    Modifier.padding(top = 12.dp), color = c.muted, fontSize = 14.sp,
                )
            }
        }
        if (today.dueNow.isEmpty() && today.laterToday.isEmpty() && today.resolved.isEmpty() && today.waiting.isEmpty()) {
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
        if (today.laterToday.isNotEmpty()) {
            item { SectionTitle("Später heute") }
            items(today.laterToday, key = { "later" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = (goalLine(e.reminder) ?: Format.plan(e.reminder)) + progress(e),
                    time = Format.time(e.occurrence.currentAt, zone),
                    onClick = { onEdit(e.reminder) },
                ) {
                    StrictnessTag(e.reminder.strictness)
                    TextButton(onClick = { onCompleteEarly(e) }) { Text("Erledigen", color = c.accent) }
                }
            }
        }
        if (today.waiting.isNotEmpty()) {
            item { SectionTitle("Wenn du zuhause bist") }
            items(today.waiting, key = { "wait" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = (goalLine(e.reminder) ?: "klingelt bei Ankunft") + progress(e),
                    onClick = { onEdit(e.reminder) },
                ) {
                    StrictnessTag(e.reminder.strictness)
                    TextButton(onClick = { onCompleteEarly(e) }) { Text("Erledigen", color = c.accent) }
                }
            }
        }
        if (today.resolved.isNotEmpty()) {
            item { SectionTitle("Erledigt") }
            items(today.resolved, key = { "done" + it.occurrence.id }) { e ->
                EntryCard(
                    title = e.reminder.title,
                    detail = e.occurrence.resolvedAt?.let { Format.time(it, zone) },
                    dimmed = true,
                ) { e.occurrence.resolution?.let { ResultBadge(it) } }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
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
