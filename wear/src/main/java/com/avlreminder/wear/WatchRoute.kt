package com.avlreminder.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchAction
import com.avlreminder.core.usecases.WatchScreen
import com.avlreminder.core.usecases.WatchSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps the buttons locked while a command waits for the phone's answer (as `rememberBusy` on the
 * phone, V1.3c14 U3): a double tap sends it once. [run] does nothing while busy.
 */
@Stable
class Busy internal constructor(private val state: MutableState<Boolean>) {
    val busy: Boolean get() = state.value

    fun run(start: (done: () -> Unit) -> Unit) {
        if (state.value) return
        state.value = true
        start { state.value = false }
    }
}

@Composable
fun rememberBusy(): Busy = remember { Busy(mutableStateOf(false)) }

/** "Rückgängig" for [occurrenceId] until [until], after *Abbruch* on the watch. */
private data class Undo(val occurrenceId: Long, val title: String, val until: Instant)

/**
 * The watch app's screen. [start] (from the tile) starts that session at once, then [onStarted].
 * [tick] lets the time run while the screen is on; [timeoutMs] is how long a button waits.
 */
@Composable
fun WatchRoute(
    graph: WearGraph,
    start: Long?,
    onStarted: () -> Unit = {},
    tick: Boolean = true,
    timeoutMs: Long = Watch.ANSWER_TIMEOUT_MS,
) {
    val shown by graph.shown.collectAsState()
    val connected by graph.connected.collectAsState()
    val now by produceState(graph.clock.instant(), tick) {
        // Only while the screen is shown; no timer runs in the background (battery).
        while (tick) {
            delay(1_000)
            value = graph.clock.instant()
        }
    }
    val zone = ZoneId.systemDefault()
    val busy = rememberBusy()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<Undo?>(null) }

    fun press(occurrenceId: Long, title: String, action: WatchAction) = busy.run { done ->
        scope.launch {
            val outcome = graph.act(occurrenceId, action, timeoutMs)
            message = when (outcome) {
                is Outcome.Done -> outcome.message
                is Outcome.Rejected -> outcome.message ?: WatchScreen.NOT_POSSIBLE
                Outcome.NoAnswer -> WatchScreen.NO_ANSWER
                Outcome.NoConnection -> WatchScreen.connection(false, graph.shown.value?.receivedAt, zone)
            }
            undo = when {
                action == WatchAction.ABORT && outcome is Outcome.Done -> Undo(occurrenceId, title, WatchScreen.undoUntil(graph.clock.instant()))
                action == WatchAction.REOPEN -> null
                else -> undo
            }
            done()
        }
    }

    LaunchedEffect(start) {
        if (start == null) return@LaunchedEffect
        val title = shown?.state?.startable?.firstOrNull { it.occurrenceId == start }?.title.orEmpty()
        press(start, title, WatchAction.START)
        onStarted()
    }

    val state = shown?.state
    val enabled = WatchScreen.enabled(connected, busy.busy)
    val list = rememberTransformingLazyColumnState()
    MaterialTheme {
        AppScaffold {
            ScreenScaffold(scrollState = list) { padding ->
                TransformingLazyColumn(state = list, contentPadding = padding) {
                    WatchScreen.connection(connected, shown?.receivedAt, zone)?.let { hint -> item { Hint(hint) } }
                    state?.let { WatchScreen.paused(it, zone) }?.let { paused -> item { Hint(paused) } }
                    message?.let { m -> item { Hint(m) } }
                    undo?.takeIf { now.isBefore(it.until) }?.let { u ->
                        item { Hint("„${u.title}“ abgebrochen") }
                        item { Answer(WatchAction.REOPEN, enabled) { press(u.occurrenceId, u.title, WatchAction.REOPEN) } }
                    }
                    when {
                        state == null -> item { Hint("Noch kein Stand vom Handy") }
                        state.sessions.isNotEmpty() -> state.sessions.forEach { s ->
                            item { ListHeader { Text(s.title) } }
                            item { SessionInfo(s, now, zone) }
                            WatchScreen.buttons(s).forEach { action ->
                                item { Answer(action, enabled) { press(s.occurrenceId, s.title, action) } }
                            }
                            if (s.proofOnPhone) item { Hint(WatchScreen.DONE_ON_PHONE) }
                        }
                        state.startable.isNotEmpty() -> state.startable.forEach { s ->
                            item { ListHeader { Text(s.title) } }
                            item { Hint(WatchScreen.startDetail(s, zone)) }
                            item { Start(enabled) { press(s.occurrenceId, s.title, WatchAction.START) } }
                        }
                        else -> item { Hint(WatchScreen.next(state, zone)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionInfo(session: WatchSession, now: Instant, zone: ZoneId) {
    Text(
        listOfNotNull(WatchScreen.timer(session, now), WatchScreen.detail(session, now, zone), WatchScreen.marks(session)).joinToString("\n"),
        Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
    )
}

@Composable
private fun Answer(action: WatchAction, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        colors = if (action == WatchAction.ABORT) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
    ) { Text(WatchScreen.label(action)) }
}

/** *Ich starte jetzt*; the title is in the header above. */
@Composable
private fun Start(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Text(WatchScreen.label(WatchAction.START), maxLines = 1)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
}
