package com.avlreminder.spike

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen alarm shown over the lock screen. */
class AlarmActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, label: String, mode: AlarmMode): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_MODE, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "?"
        val mode = AlarmMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: AlarmMode.VISIBLE.name)

        if (savedInstanceState == null) {
            // Locked + screen was off at this moment means the full-screen intent opened it by itself.
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val screenOn = getSystemService(PowerManager::class.java).isInteractive
            SpikeLog.log(this, "Vollbild angezeigt: '$label' (gesperrt=$locked, Bildschirm an=$screenOn)")
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AlarmScreen(
                    label = label,
                    mode = mode,
                    onAck = { AlarmService.send(this, AlarmService.ACTION_ACK, "Vollbild"); finish() },
                    onSnooze = { AlarmService.send(this, AlarmService.ACTION_SNOOZE, "Vollbild"); finish() },
                    onAlarmEnded = { finish() },
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(
    label: String,
    mode: AlarmMode,
    onAck: () -> Unit,
    onSnooze: () -> Unit,
    onAlarmEnded: () -> Unit,
) {
    // Close when the alarm ends elsewhere (notification action, timeout). The activity can
    // open before the service reports active, so only react to an active -> inactive change.
    val active by AlarmService.active.collectAsState()
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) wasActive = true else if (wasActive) onAlarmEnded()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("AVL Spike", fontSize = 18.sp)
            Text(label, fontSize = 32.sp)
            Text(mode.label)
            Button(onClick = onAck, modifier = Modifier.fillMaxWidth()) { Text("Bestätigen") }
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text("+5 min") }
        }
    }
}
