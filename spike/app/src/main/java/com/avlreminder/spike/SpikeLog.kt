package com.avlreminder.spike

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Append-only protocol of everything the spike observes.
 * Stored in the app's private files (files/spike-log.txt) and mirrored to logcat (tag "AvlSpike").
 * Read from the PC with: adb shell run-as com.avlreminder.spike cat files/spike-log.txt
 */
object SpikeLog {
    private const val TAG = "AvlSpike"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun file(context: Context) = File(context.filesDir, "spike-log.txt")

    @Synchronized
    fun log(context: Context, message: String) {
        Log.i(TAG, message)
        file(context).appendText("${LocalDateTime.now().format(formatter)}  $message\n")
    }

    fun newestFirst(context: Context, max: Int = 300): List<String> {
        val f = file(context)
        return if (f.exists()) f.readLines().takeLast(max).reversed() else emptyList()
    }

    fun formatTime(epochMillis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(formatter)

    /** Power-management state that decides whether alarms arrive on time. */
    fun deviceState(context: Context): String {
        val power = context.getSystemService(PowerManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val bucket = when (val b = usage.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
            else -> "bucket=$b"
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        return "idle=${power.isDeviceIdleMode} powerSave=${power.isPowerSaveMode} " +
            "akkuOptAus=${power.isIgnoringBatteryOptimizations(context.packageName)} " +
            "standby=$bucket exakt=${alarms.canScheduleExactAlarms()} " +
            "vollbild=${notifications.canUseFullScreenIntent()} dnd=${notifications.currentInterruptionFilter}"
    }
}
