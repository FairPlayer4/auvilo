package com.avlreminder.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File

/**
 * Record button: tap to start, tap again to stop. The file goes to filesDir/audio/ (AAC),
 * never to shared storage. [onRecorded] gets the path and the length in seconds.
 */
@Composable
fun AudioRecorder(prefix: String, label: String, onRecorded: (path: String, seconds: Int) -> Unit) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    if (!allowed) {
        OutlinedButton(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth()) { Text("Mikrofon erlauben") }
        return
    }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) {
        val r = recording ?: return@LaunchedEffect
        while (true) {
            elapsed = r.seconds()
            delay(250)
        }
    }
    // Leaving the screen while recording keeps nothing half-written.
    DisposableEffect(Unit) { onDispose { recording?.cancel() } }

    val current = recording
    if (current == null) {
        OutlinedButton(
            onClick = {
                elapsed = 0
                recording = Recording.start(context, prefix)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(label) }
    } else {
        Button(
            onClick = {
                recording = null
                current.stop()?.let { onRecorded(it.path, current.seconds()) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("● Aufnahme läuft · $elapsed s – stoppen") }
    }
}

private class Recording(private val recorder: MediaRecorder, private val file: File) {
    private val started = SystemClock.elapsedRealtime()
    private var stoppedAt: Long? = null

    fun seconds(): Int = (((stoppedAt ?: SystemClock.elapsedRealtime()) - started) / 1000).toInt()

    /** The file, or null if nothing usable was recorded. */
    fun stop(): File? {
        stoppedAt = SystemClock.elapsedRealtime()
        val ok = runCatching { recorder.stop() }.isSuccess
        recorder.release()
        if (!ok) file.delete()
        return file.takeIf { ok }
    }

    fun cancel() {
        runCatching { recorder.stop() }
        recorder.release()
        file.delete()
    }

    companion object {
        fun start(context: Context, prefix: String): Recording? {
            val file = File(context.filesDir, "audio/$prefix-${System.currentTimeMillis()}.m4a")
            file.parentFile?.mkdirs()
            val recorder = MediaRecorder(context)
            return try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(64_000)
                recorder.setAudioSamplingRate(44_100)
                recorder.setOutputFile(file.path)
                recorder.prepare()
                recorder.start()
                Recording(recorder, file)
            } catch (e: Exception) {
                Log.w("AudioRecorder", "recording failed", e)
                recorder.release()
                file.delete()
                Toast.makeText(context, "Aufnahme nicht möglich: ${e.message}", Toast.LENGTH_LONG).show()
                null
            }
        }
    }
}

/** Plays one recording at a time (motivation audio, audio proof in the history). */
object AudioPlayer {
    private var player: MediaPlayer? = null

    fun play(context: Context, path: String) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.onFailure {
            Log.w("AudioPlayer", "playback failed", it)
            Toast.makeText(context, "Abspielen nicht möglich", Toast.LENGTH_SHORT).show()
        }.getOrNull()
    }

    fun stop() {
        player?.release()
        player = null
    }
}
