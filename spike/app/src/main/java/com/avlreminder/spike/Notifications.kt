package com.avlreminder.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifications {
    const val ALARM_ID = 1
    private const val SILENT_ID = 2

    private const val CHANNEL_ALARM = "alarm"
    private const val CHANNEL_SILENT = "silent"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            // Sound and vibration come from AlarmService, so the channel itself stays quiet.
            NotificationChannel(CHANNEL_ALARM, "Alarm (Test)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, "Stille Erinnerung (Test)", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun alarm(context: Context, label: String, mode: AlarmMode): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, 0,
            AlarmActivity.intent(context, label, mode),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AVL Spike: $label")
            .setContentText(mode.label)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(serviceAction(context, AlarmService.ACTION_ACK, "Bestätigen", 10))
            .addAction(serviceAction(context, AlarmService.ACTION_SNOOZE, "+5 min", 11))
            .build()
    }

    /** Used when the foreground service could not be started. */
    fun showAlarmWithoutService(context: Context, label: String, mode: AlarmMode) {
        context.getSystemService(NotificationManager::class.java).notify(ALARM_ID, alarm(context, label, mode))
    }

    fun showSilent(context: Context, label: String) {
        val notification = Notification.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AVL Spike: $label")
            .setContentText("Stille Erinnerung (außerhalb 7–21 Uhr)")
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(SILENT_ID, notification)
    }

    private fun serviceAction(context: Context, action: String, title: String, requestCode: Int): Notification.Action {
        val intent = Intent(context, AlarmService::class.java).setAction(action)
            .putExtra(AlarmService.EXTRA_SOURCE, "Benachrichtigung")
        val pending = PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, title, pending).build()
    }
}
