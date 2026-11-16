package com.avlreminder.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.avlreminder.data.ThemeMode
import com.avlreminder.core.rules.SpokenReminder
import com.avlreminder.core.rules.VoiceReminders
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.graph
import com.avlreminder.ui.Format
import com.avlreminder.ui.theme.AvlTheme
import com.avlreminder.ui.theme.isDark
import kotlinx.coroutines.launch

/**
 * Quick reminder by voice (Anf. 7.35, simplest version): the widget opens this screen, it
 * listens right away and shows the pattern to say; "Fertig" evaluates, then a summary with
 * "Anlegen", "Nochmal" and "Abbrechen". Everything stays on the phone.
 */
// Same as MainActivity: lint reads an old transitive androidx.fragment; this is no FragmentActivity.
@SuppressLint("InvalidFragmentVersionForActivityResult")
class VoiceActivity : ComponentActivity() {
    companion object {
        fun intent(context: Context): Intent =
            Intent(context, VoiceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private var state by mutableStateOf<VoiceState>(VoiceState.Listening(""))
    private var needsPermission by mutableStateOf(false)
    private lateinit var recognizer: AndroidRecognizer
    private lateinit var session: VoiceSession

    private val askMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        needsPermission = !allowed
        if (allowed) listen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recognizer = AndroidRecognizer(this)
        session = VoiceSession(recognizer) { state = it }
        recognizer.session = session
        setContent {
            val theme by graph.settings.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            AvlTheme(dark = isDark(theme)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VoiceScreen(
                        state = state,
                        needsPermission = needsPermission,
                        onDevice = recognizer.onDevice,
                        onAllow = { askMicrophone.launch(Manifest.permission.RECORD_AUDIO) },
                        onFinish = session::finish,
                        onSave = ::save,
                        onRetry = ::listen,
                        onCancel = ::finish,
                    )
                }
            }
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onDestroy() {
        recognizer.destroy()
        super.onDestroy()
    }

    private fun listen() {
        if (!recognizer.available) {
            state = VoiceState.Failed("Auf diesem Handy gibt es keine Spracherkennung.")
            return
        }
        session.start()
    }

    private fun save(spoken: SpokenReminder) {
        lifecycleScope.launch {
            val g = graph
            val now = g.now()
            val reminder = VoiceReminders.reminder(spoken, now, g.zone)
            when (val result = g.withEngine { it.save(reminder) }) {
                is SaveResult.Saved -> {
                    val at = Format.time(now.plusSeconds(spoken.minutes * 60), g.zone)
                    Toast.makeText(this@VoiceActivity, "Erinnerung um $at: ${spoken.title}", Toast.LENGTH_LONG).show()
                    finish()
                }
                is SaveResult.Invalid -> state = VoiceState.Failed(result.problems.joinToString(", "))
            }
        }
    }
}

@Composable
internal fun VoiceScreen(
    state: VoiceState,
    needsPermission: Boolean,
    onDevice: Boolean,
    onAllow: () -> Unit,
    onFinish: () -> Unit,
    onSave: (SpokenReminder) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Schnellerinnerung", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (needsPermission) {
            Text("Für die Spracheingabe braucht die App das Mikrofon. Die Erkennung läuft über Android, die Aufnahme wird nicht gespeichert.")
            Button(onClick = onAllow, Modifier.fillMaxWidth()) { Text("Mikrofon erlauben") }
            TextButton(onClick = onCancel) { Text("Abbrechen") }
            return@Column
        }
        when (state) {
            is VoiceState.Listening -> {
                Text("So sprechen:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("„${VoiceReminders.PATTERN}“", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text("Zeit auch „in einer Viertelstunde“ oder „in 2 Stunden“.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Höre zu …", fontWeight = FontWeight.Bold)
                Text(state.text.ifEmpty { " " }, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Button(onClick = onFinish, Modifier.fillMaxWidth().height(64.dp)) { Text("Fertig") }
                TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("Abbrechen") }
            }
            is VoiceState.Heard -> {
                val spoken = VoiceReminders.parse(state.text)
                Text(spoken.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("in ${spoken.minutes} min", style = MaterialTheme.typography.titleLarge)
                spoken.hints.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Gehört: „${state.text}“", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (onDevice) "Erkennung auf dem Gerät" else "Erkennung über den Android-Dienst", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(spoken) }, Modifier.fillMaxWidth().height(64.dp)) { Text("Anlegen") }
                Choices(onRetry, onCancel)
            }
            is VoiceState.Failed -> {
                Text(state.message)
                Spacer(Modifier.weight(1f))
                Choices(onRetry, onCancel)
            }
        }
    }
}

@Composable
private fun Choices(onRetry: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRetry, Modifier.weight(1f)) { Text("Nochmal") }
        OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text("Abbrechen") }
    }
}
