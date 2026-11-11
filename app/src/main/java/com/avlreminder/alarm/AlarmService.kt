package com.avlreminder.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.avlreminder.system.Diag
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.rules.Escalation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps one strict alarm going for one round ([Escalation.ROUND]): vibration pattern in a loop,
 * alarm tone if loud, full-screen notification. After the round the notification stays, but quiet;
 * the engine has already planned the next round.
 */
class AlarmService : Service() {
    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_QUICK_DONE = "quickDone"
        private const val EXTRA_SNOOZE = "snooze"
        private const val EXTRA_LOUD = "loud"
        private const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_ITEM_TITLE = "itemTitle"

        /** Long, unusual pattern (ms): off/on pairs, easy to tell apart from normal notifications. */
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 200, 800, 200, 100, 100, 100, 100, 100, 1200)

        private val _ringing = MutableStateFlow<Long?>(null)
        /** Occurrence that is ringing right now, or null. */
        val ringing: StateFlow<Long?> = _ringing

        fun start(context: Context, info: AlarmInfo, loud: Boolean) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, info.occurrenceId)
                .putExtra(EXTRA_TITLE, info.title)
                .putExtra(EXTRA_TEXT, info.text)
                .putExtra(EXTRA_QUICK_DONE, info.quickDone)
                .putExtra(EXTRA_SNOOZE, info.snooze.toIntArray())
                .putExtra(EXTRA_LOUD, loud)
            // KRITISCH: Alles aus AlarmInfo muss mit, sonst fehlt es in den Benachrichtigungen
            // des Dienstes; ohne nextItem schlösse "Erledigt" eine Checkliste ganz ab.
            info.nextItem?.let { intent.putExtra(EXTRA_ITEM_ID, it.id).putExtra(EXTRA_ITEM_TITLE, it.title) }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Spike finding: should not happen with USE_EXACT_ALARM, but never lose a reminder over it.
                Log.w(TAG, "foreground service not startable, falling back to banner", e)
                Diag.log("service not startable, banner instead: ${e.message}")
                Notifications.show(context, info, quiet = false)
            }
        }

        /** Stops ringing if [occurrenceId] is the one ringing. */
        fun stop(context: Context, occurrenceId: Long) {
            if (_ringing.value != occurrenceId) return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, occurrenceId))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var info: AlarmInfo? = null

    private val roundOver = Runnable {
        val current = info
        stopAlarm()
        // Keep it visible until the user reacts or the next round starts.
        current?.let { Notifications.show(this, it, quiet = true) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm(
                AlarmInfo(
                    occurrenceId = intent.getLongExtra(EXTRA_ID, -1),
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "",
                    text = intent.getStringExtra(EXTRA_TEXT),
                    quickDone = intent.getBooleanExtra(EXTRA_QUICK_DONE, false),
                    snooze = intent.getIntArrayExtra(EXTRA_SNOOZE)?.toList().orEmpty(),
                    nextItem = intent.getStringExtra(EXTRA_ITEM_TITLE)?.let { ChecklistItem(intent.getLongExtra(EXTRA_ITEM_ID, -1), it) },
                ),
                intent.getBooleanExtra(EXTRA_LOUD, false),
            )
            ACTION_STOP -> if (intent.getLongExtra(EXTRA_ID, -1) == info?.occurrenceId) stopAlarm()
            else -> stopAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(newInfo: AlarmInfo, loud: Boolean) {
        // A second strict alarm replaces the first; the first keeps a quiet notification.
        info?.takeIf { it.occurrenceId != newInfo.occurrenceId }?.let { Notifications.show(this, it, quiet = true) }
        stopSound()
        Notifications.cancel(this, newInfo.occurrenceId)
        info = newInfo
        try {
            startForeground(
                Notifications.id(newInfo.occurrenceId),
                Notifications.fullScreen(this, newInfo),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed, falling back to banner", e)
            Diag.log("startForeground failed, banner instead: ${e.message}")
            Notifications.show(this, newInfo, quiet = false)
            info = null
            stopSelf()
            return
        }
        _ringing.value = newInfo.occurrenceId
        vibrate()
        if (loud) playSound()
        // After the looping vibration has started, so the copy's own short vibration does not replace it.
        Notifications.showMirror(this, newInfo)
        handler.removeCallbacks(roundOver)
        handler.postDelayed(roundOver, Escalation.ROUND.toMillis())
    }

    private fun vibrate() {
        getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
            VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun playSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
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
        }.onFailure {
            Log.w(TAG, "alarm tone failed, vibration only", it)
            Diag.log("alarm tone failed: ${it.message}")
        }.getOrNull()
    }

    private fun stopSound() {
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        player?.release()
        player = null
    }

    private fun stopAlarm() {
        handler.removeCallbacks(roundOver)
        stopSound()
        info?.let { Notifications.cancelMirror(this, it.occurrenceId) }
        info = null
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(roundOver)
        stopSound()
        _ringing.value = null
        super.onDestroy()
    }
}
