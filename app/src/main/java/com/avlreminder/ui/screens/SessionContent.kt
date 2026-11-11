package com.avlreminder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.SessionView
import com.avlreminder.ui.Format
import com.avlreminder.ui.theme.Avl
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The running session (Anf. 7.11, design.md E.3): big timer, the four answers with the suggested
 * one first, goal, minimum and the average of the last sessions. At the last reminder "Noch
 * dran" is gone (D5).
 * [clock]: the current time each second; tests pass a fixed one.
 */
@Composable
fun SessionMain(
    ui: AlarmView,
    session: SessionView,
    zone: ZoneId,
    onStillOn: () -> Unit,
    onComplete: (minimumOnly: Boolean) -> Unit,
    onAbort: () -> Unit,
    clock: @Composable () -> Instant = { ticking() },
) {
    val c = Avl.colors
    val r = ui.reminder
    val spec = r.session
    val now = clock()
    val elapsed = Duration.between(session.startedAt, now).coerceAtLeast(Duration.ZERO)
    var aborting by remember { mutableStateOf(false) }
    if (aborting) {
        AlertDialog(
            onDismissRequest = { aborting = false },
            title = { Text("Sitzung abbrechen?") },
            text = { Text("Zählt nicht als erledigt und unterbricht die Serie wie „verpasst“.") },
            confirmButton = { TextButton(onClick = { aborting = false; onAbort() }, Modifier.testTag("abortConfirm")) { Text("Abbrechen") } },
            dismissButton = { TextButton(onClick = { aborting = false }) { Text("Weiter") } },
        )
    }
    Column(Modifier.fillMaxWidth()) {
        val head = if (session.final) "Maximale Dauer erreicht" else "Läuft seit ${Format.time(session.startedAt, zone)}"
        Text(head, color = if (session.final) c.strict else c.muted, fontSize = 14.sp)
        Text(Format.elapsed(elapsed), Modifier.testTag("timer"), color = c.text, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(r.title, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        r.why?.let { Text("„$it“", Modifier.padding(top = 8.dp), color = c.muted, fontSize = 16.sp, fontStyle = FontStyle.Italic) }
        Spacer(Modifier.height(16.dp))
        val durations = listOfNotNull(
            spec?.goalMinutes?.let { "Ziel ${Format.minutes(it)}" },
            spec?.minimumMinutes?.let { "Minimum ${Format.minutes(it)}" },
            "höchstens bis ${Format.time(session.maxAt, zone)}",
        )
        SessionInfo("Dauer", durations.joinToString(" · "))
        session.average?.let { SessionInfo("Schnitt der letzten Sitzungen", "${Format.duration(it)} · ${Format.pace(elapsed, it)}") }
        r.goal?.let { SessionInfo("Ziel", it) }
        r.minimum?.let { SessionInfo("Minimum", it) }
        Spacer(Modifier.height(24.dp))
        val suggested = session.suggestion
        val minimum = r.minimum != null || spec?.minimumMinutes != null
        // The suggested answer is the big button (D4); the others stay one tap away, nothing is locked.
        val order = buildList {
            add(suggested)
            if (!session.final) add(SessionAnswer.STILL_ON)
            add(SessionAnswer.DONE)
            if (minimum) add(SessionAnswer.MINIMUM)
        }.distinct().filter { it != SessionAnswer.STILL_ON || !session.final }
        order.forEachIndexed { i, answer ->
            val (text, tag, action) = when (answer) {
                SessionAnswer.STILL_ON -> Triple("Noch dran", "stillOn", onStillOn)
                SessionAnswer.DONE -> Triple("Fertig", "sessionDone") { onComplete(false) }
                SessionAnswer.MINIMUM -> Triple(
                    if (ui.minimumAllowed) "Teilschritt" else "Minimum diese Woche aufgebraucht", "sessionMinimum",
                ) { onComplete(true) }
                SessionAnswer.ABORT -> Triple("Abbruch", "abort") { aborting = true }
            }
            val enabled = answer != SessionAnswer.MINIMUM || ui.minimumAllowed
            if (i == 0) {
                Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag)) { Text(text, fontSize = 18.sp) }
            } else {
                OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(tag)) { Text(text) }
            }
        }
        TextButton(onClick = { aborting = true }, Modifier.padding(top = 16.dp).testTag("abort")) {
            Text("Abbruch", color = c.strict)
        }
    }
}

/** The current time, every second, for the timer. */
@Composable
private fun ticking(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(1_000)
        }
    }
    return now
}

@Composable
private fun SessionInfo(label: String, value: String) {
    val c = Avl.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = c.muted, fontSize = 13.sp)
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
