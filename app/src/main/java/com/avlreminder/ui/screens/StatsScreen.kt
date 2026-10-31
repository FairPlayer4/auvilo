package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.Counts
import com.avlreminder.core.rules.ReminderStats
import com.avlreminder.core.rules.StreakUnit
import com.avlreminder.core.rules.WeekStatus
import com.avlreminder.core.usecases.Digests
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.theme.Avl

/** Success over time for one reminder (Anf. P1, design 12.3 point 7): first as a table. */
@Composable
fun StatsScreen(
    reminder: Reminder,
    stats: ReminderStats,
    week: WeekStatus?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Avl.colors
    Column(modifier) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text(reminder.title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            week?.let {
                SectionTitle("Diese Woche")
                Text(Digests.week(it), color = c.text, fontSize = 16.sp, modifier = Modifier.testTag("week"))
            }
            stats.streak?.let { s ->
                SectionTitle("Serie")
                val unit = when (s.unit) {
                    StreakUnit.DAYS -> "Tage"
                    StreakUnit.WEEKS -> "Wochen"
                    StreakUnit.TIMES -> "Mal"
                }
                Text("${s.current} $unit", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("streak"))
                Text("beste: ${s.best} $unit · ein verpasstes Mal pro ${if (s.unit == StreakUnit.DAYS) "Woche" else "Monat"} bricht sie nicht", color = c.muted, fontSize = 13.sp)
            }
            SectionTitle("Erledigungen")
            val periods = listOf("Woche" to stats.week, "Monat" to stats.month, "Jahr" to stats.year, "Gesamt" to stats.total)
            TableRow("", periods.map { it.first }, header = true)
            ROWS.forEach { (label, value) -> TableRow(label, periods.map { value(it.second).toString() }) }
            Text(
                "Ausgelassen und entfallen zählen neutral. „Vorab“ heißt: vor der Erinnerung erledigt.",
                Modifier.padding(top = 12.dp, bottom = 48.dp), color = c.muted, fontSize = 13.sp,
            )
        }
    }
}

private val ROWS: List<Pair<String, (Counts) -> Int>> = listOf(
    "Ziel" to { it.goal },
    "Minimum" to { it.minimum },
    "ohne Nachweis" to { it.noProof },
    "vorab" to { it.early },
    "ausgelassen" to { it.skipped },
    "entfallen" to { it.dropped },
    "verpasst" to { it.missed },
)

@Composable
private fun TableRow(label: String, cells: List<String>, header: Boolean = false) {
    val c = Avl.colors
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1.6f), color = c.text, fontSize = 15.sp)
        cells.forEach { Text(it, Modifier.weight(1f), color = if (header) c.muted else c.text, fontSize = 15.sp, fontWeight = weight, textAlign = TextAlign.End) }
    }
}
