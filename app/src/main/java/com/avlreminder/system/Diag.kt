package com.avlreminder.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import com.avlreminder.alarm.Notifications
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostic log for device tests: what fired when, how loud, and what the user did. Goes to
 * logcat (tag "AvlDiag") and to files/diag/diag.log in the app's own storage, which survives
 * logcat's short buffer. Times, ids, titles and states only; no proof contents, no positions.
 */
object Diag {
    private const val TAG = "AvlDiag"
    /** Two files of this size at most. */
    private const val MAX_BYTES = 512 * 1024
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @Volatile private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "diag").apply { mkdirs() }
    }

    @Synchronized
    fun log(message: String) {
        Log.i(TAG, message)
        val d = dir ?: return
        runCatching {
            val file = File(d, "diag.log")
            if (file.length() > MAX_BYTES) {
                val old = File(d, "diag.1.log")
                old.delete()
                file.renameTo(old)
            }
            file.appendText("${stamp.format(LocalDateTime.now())} $message\n")
        }
    }

    /** What decides whether an alarm can come through; at start and after boot. */
    fun state(context: Context, reason: String) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        log(
            "state $reason: notifications=${notifications.areNotificationsEnabled()} " +
                "exact=${context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()} " +
                "fullscreen=${notifications.canUseFullScreenIntent()} " +
                "batteryExempt=${power.isIgnoringBatteryOptimizations(context.packageName)} " +
                "location=${granted(Manifest.permission.ACCESS_FINE_LOCATION)} " +
                "background=${granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)} " +
                // Same check as the settings screen: the alarm channels may bypass "Nicht stören".
                "dndBlocked=${Notifications.channelsBlockedByDnd(context).ifEmpty { "none" }} " +
                "interruptionFilter=${notifications.currentInterruptionFilter}",
        )
    }
}
