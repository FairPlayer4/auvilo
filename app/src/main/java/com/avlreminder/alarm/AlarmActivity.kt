package com.avlreminder.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.data.toModel
import com.avlreminder.graph
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmUi
import com.avlreminder.ui.theme.AvlTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Full-screen alarm, also over the lock screen. Always dark. */
class AlarmActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ID = "occurrence"

        fun intent(context: Context, occurrenceId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_ID, occurrenceId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private var occurrenceId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        occurrenceId = intent.getLongExtra(EXTRA_ID, -1)
        setContent {
            AvlTheme(dark = true) { AlarmHost(occurrenceId) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        occurrenceId = intent.getLongExtra(EXTRA_ID, -1)
    }

    @Composable
    private fun AlarmHost(id: Long) {
        val graph = remember { graph }
        val zone = ZoneId.systemDefault()
        val ui by produceState<AlarmUi?>(null, id) {
            value = load(id)
        }
        // Close as soon as the occurrence is resolved or snoozed elsewhere (notification, other device action).
        LaunchedEffect(id) {
            graph.db.dao().observeOpenOccurrences().collect { open ->
                val o = open.firstOrNull { it.id == id }
                if (o == null || o.state == OccurrenceState.PLANNED) finish()
            }
        }
        val now by produceState(Instant.now()) {
            while (true) {
                delay(15_000)
                value = Instant.now()
            }
        }
        val current = ui ?: return
        AlarmContent(
            ui = current,
            now = now,
            zone = zone,
            onComplete = { minimumOnly, photo, note ->
                act(id, Action.Complete(minimumOnly, proofProvided = photo != null, note = note), photo)
            },
            onSnooze = { act(id, Action.Snooze(ActionReceiver.SNOOZE)) },
            onPostpone = { act(id, Action.Postpone(it)) },
            onSkip = { act(id, Action.Skip(it)) },
            camera = { onPhoto -> ProofCamera(id, onPhoto) },
        )
    }

    private suspend fun load(id: Long): AlarmUi? {
        val repo = graph.repository
        val occurrence = repo.occurrence(id) ?: return null
        val reminder = repo.reminder(occurrence.reminderId) ?: return null
        val limits = graph.withEngine { it.limits(reminder) }
        return AlarmUi(
            reminder = reminder,
            occurrence = occurrence,
            minimumAllowed = Lifecycle.canUseMinimum(reminder, limits),
            minimumsLeft = Lifecycle.minimumsLeft(reminder, limits),
            canPostpone = Lifecycle.canPostpone(reminder, occurrence),
            postponesLeft = Lifecycle.postponesLeft(reminder, occurrence),
        )
    }

    private fun act(id: Long, action: Action, photo: String? = null) {
        lifecycleScope.launch {
            val result = graph.withEngine { it.act(id, action, photo) }
            if (result is TransitionResult.Rejected) {
                Toast.makeText(this@AlarmActivity, "Nicht möglich: ${result.reason}", Toast.LENGTH_LONG).show()
                return@launch
            }
            Ringer.dismiss(this@AlarmActivity, id)
            finish()
        }
    }
}
