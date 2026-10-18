package com.avlreminder.spike

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the alarm going until the user reacts: repeating vibration pattern, alarm sound
 * in LOUD mode, and a full-screen notification. Gives up after [TIMEOUT_MILLIS].
 */
class AlarmService : Service() {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_ACK = "ack"
        const val ACTION_SNOOZE = "snooze"
        const val EXTRA_SOURCE = "source"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_MODE = "mode"

        private const val TIMEOUT_MILLIS = 3 * 60 * 1000L

        /** Long, unusual pattern (ms): off/on pairs, easy to tell apart from normal notifications. */
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 200, 800, 200, 100, 100, 100, 100, 100, 1200)

        private val _active = MutableStateFlow(false)
        /** True while an alarm is running; AlarmActivity closes itself when this turns false. */
        val active: StateFlow<Boolean> = _active

        fun start(context: Context, label: String, mode: AlarmMode) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_MODE, mode.name)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                SpikeLog.log(context, "FEHLER: Foreground-Service nicht startbar: $e – nur Benachrichtigung")
                Notifications.showAlarmWithoutService(context, label, mode)
            }
        }

        fun send(context: Context, action: String, source: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action).putExtra(EXTRA_SOURCE, source))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var label = ""
    private var mode = AlarmMode.VISIBLE
    private var startedAt = 0L

    private val timeout = Runnable {
        SpikeLog.log(this, "KEINE REAKTION auf '$label' nach ${TIMEOUT_MILLIS / 60000} min – Alarm beendet")
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm(
                intent.getStringExtra(EXTRA_LABEL) ?: "?",
                AlarmMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: AlarmMode.VISIBLE.name),
            )
            ACTION_ACK -> {
                SpikeLog.log(this, "Bestätigt: '$label' nach ${secondsSinceStart()}s (via ${intent.getStringExtra(EXTRA_SOURCE)})")
                stopAlarm()
            }
            ACTION_SNOOZE -> {
                SpikeLog.log(this, "Snooze: '$label' nach ${secondsSinceStart()}s (via ${intent.getStringExtra(EXTRA_SOURCE)})")
                AlarmScheduler.scheduleTest(this, 5 * 60, mode, "$label (Snooze)")
                stopAlarm()
            }
            else -> stopAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(newLabel: String, newMode: AlarmMode) {
        if (_active.value) {
            SpikeLog.log(this, "Neuer Alarm '$newLabel' ersetzt laufenden Alarm '$label'")
            stopSound()
        }
        label = newLabel
        mode = newMode
        startedAt = System.currentTimeMillis()

        try {
            startForeground(
                Notifications.ALARM_ID,
                Notifications.alarm(this, label, mode),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } catch (e: Exception) {
            SpikeLog.log(this, "FEHLER: startForeground fehlgeschlagen: $e – nur Benachrichtigung")
            Notifications.showAlarmWithoutService(this, label, mode)
            stopSelf()
            return
        }
        _active.value = true

        vibrate()
        if (mode == AlarmMode.LOUD) playSound()

        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TIMEOUT_MILLIS)
        SpikeLog.log(this, "Alarm läuft: '$label' (${mode.label})")
    }

    private fun vibrate() {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(
            VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun playSound() {
        val audio = getSystemService(AudioManager::class.java)
        SpikeLog.log(
            this,
            "Alarmton: Lautstärke ${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
                "${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}, Klingelmodus ${audio.ringerMode}",
        )
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopSound() {
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        player?.release()
        player = null
    }

    private fun stopAlarm() {
        handler.removeCallbacks(timeout)
        stopSound()
        _active.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Also removes the fallback notification posted without the service.
        getSystemService(NotificationManager::class.java).cancel(Notifications.ALARM_ID)
        stopSelf()
    }

    private fun secondsSinceStart() = (System.currentTimeMillis() - startedAt) / 1000

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopSound()
        _active.value = false
        super.onDestroy()
    }
}
