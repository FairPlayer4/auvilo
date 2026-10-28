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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.graph
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmDevices
import com.avlreminder.ui.screens.AlarmUi
import com.avlreminder.ui.theme.AvlTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Full-screen alarm, also over the lock screen. Always dark. */
class AlarmActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ID = "occurrence"
        private const val EXTRA_EARLY = "early"

        /** [early]: complete before the reminder is due ("vorab erledigen"). */
        fun intent(context: Context, occurrenceId: Long, early: Boolean = false): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_ID, occurrenceId)
                .putExtra(EXTRA_EARLY, early)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private var occurrenceId by mutableLongStateOf(-1L)
    /** Bumped after a checklist item, so the screen shows the new state. */
    private var version by mutableIntStateOf(0)
    private var early = false
    /** The proof screen is open and the alarm paused. */
    private var proofRunning = false
    /** The proof was paused because the app went to the background; resumes on return. */
    private var proofLeft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        occurrenceId = intent.getLongExtra(EXTRA_ID, -1)
        early = intent.getBooleanExtra(EXTRA_EARLY, false)
        setContent {
            AvlTheme(dark = true) { AlarmHost(occurrenceId) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        occurrenceId = intent.getLongExtra(EXTRA_ID, -1)
        early = intent.getBooleanExtra(EXTRA_EARLY, false)
    }

    override fun onStart() {
        super.onStart()
        if (proofLeft) {
            proofLeft = false
            startProof(occurrenceId)
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app with the proof open must not keep the alarm quiet (requirement N0).
        if (proofRunning && !isChangingConfigurations) {
            proofLeft = true
            abortProof(occurrenceId)
        }
    }

    @Composable
    private fun AlarmHost(id: Long) {
        val graph = remember { graph }
        val zone = ZoneId.systemDefault()
        val ui by produceState<AlarmUi?>(null, id, version) {
            value = load(id)
        }
        // Close as soon as the occurrence is resolved or snoozed elsewhere (notification, other device action).
        LaunchedEffect(id) {
            graph.db.dao().observeOpenOccurrences().collect { open ->
                val o = open.firstOrNull { it.id == id }
                if (o == null || (o.state == OccurrenceState.PLANNED && !early)) finish()
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
            onComplete = { minimumOnly, evidence, note -> act(id, Action.Complete(minimumOnly, evidence, note)) },
            onSnooze = { act(id, Action.Snooze(Duration.ofMinutes(it.toLong()))) },
            onPostpone = { act(id, Action.Postpone(it)) },
            onSkip = { act(id, Action.Skip(it)) },
            onCheckItem = { itemId, evidence -> act(id, Action.CheckItem(itemId, evidence), stay = true) },
            onDeferRest = { act(id, Action.DeferRest(it)) },
            onStartProof = { startProof(id) },
            onAbortProof = { abortProof(id) },
            devices = AlarmDevices(
                camera = { onPhoto -> ProofCamera(id, onPhoto) },
                recorder = { onRecorded -> AudioRecorder("occ-$id", "● Aufnahme starten", onRecorded) },
                play = { AudioPlayer.play(this, it) },
            ),
        )
    }

    private suspend fun load(id: Long): AlarmUi? {
        val repo = graph.repository
        val occurrence = repo.occurrence(id) ?: return null
        val reminder = repo.reminder(occurrence.reminderId) ?: return null
        return graph.withEngine { engine ->
            val limits = engine.limits(reminder, occurrence)
            AlarmUi(
                reminder = reminder,
                occurrence = occurrence,
                minimumAllowed = Lifecycle.canUseMinimum(reminder, limits),
                minimumsLeft = Lifecycle.minimumsLeft(reminder, limits),
                canPostpone = Lifecycle.canPostpone(reminder, occurrence),
                postponesLeft = Lifecycle.postponesLeft(reminder, occurrence),
                snooze = engine.snoozeOptions(reminder, occurrence),
                prep = engine.prep(reminder.id),
                lastProofText = limits.lastProofText,
                early = early,
                done = limits.itemsDone.keys,
                // Doing an item today makes "Rest auf morgen" free; otherwise it is a postponement.
                canDeferRest = reminder.checklist.isNotEmpty() && (limits.itemDoneToday || Lifecycle.canPostpone(reminder, occurrence)),
            )
        }
    }

    private fun startProof(id: Long) {
        proofRunning = true
        lifecycleScope.launch {
            val result = graph.withEngine { it.startProof(id) }
            val o = (result as? TransitionResult.Ok)?.occurrence ?: return@launch
            val r = graph.repository.reminder(o.reminderId) ?: return@launch
            Ringer.pause(this@AlarmActivity, AlarmInfo(o.id, r.title, r.why ?: r.goal, quickDone = false), o.pausedUntil!!)
        }
    }

    private fun abortProof(id: Long) {
        proofRunning = false
        // The application scope survives the activity going to the background.
        graph.scope.launch { graph.withEngine { it.abortProof(id) } }
    }

    /** [stay]: a checklist item; the screen stays for the next one unless that was the last. */
    private fun act(id: Long, action: Action, stay: Boolean = false) {
        proofRunning = false
        lifecycleScope.launch {
            val result = graph.withEngine { it.act(id, action) }
            if (result !is TransitionResult.Ok) {
                Toast.makeText(this@AlarmActivity, "Nicht möglich: ${(result as TransitionResult.Rejected).reason}", Toast.LENGTH_LONG).show()
                return@launch
            }
            Ringer.dismiss(this@AlarmActivity, id)
            if (stay && result.occurrence.state != OccurrenceState.DONE) {
                version++
                return@launch
            }
            AudioPlayer.stop()
            finish()
        }
    }
}
