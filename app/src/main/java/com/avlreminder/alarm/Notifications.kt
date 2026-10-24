package com.avlreminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.usecases.Ring

/** What a notification needs to know about a ringing occurrence. */
data class AlarmInfo(val occurrenceId: Long, val title: String, val text: String?, val quickDone: Boolean) {
    companion object {
        fun of(ring: Ring) = AlarmInfo(
            occurrenceId = ring.occurrence.id,
            title = ring.reminder.title,
            text = ring.reminder.why ?: ring.reminder.goal,
            // A photo cannot be taken from the notification, so "done" there would always count as without proof.
            quickDone = ring.reminder.proof != ProofType.PHOTO,
        )
    }
}

object Notifications {
    private const val CHANNEL_ALARM = "alarm"
    private const val CHANNEL_BANNER = "banner"
    private const val CHANNEL_SILENT = "silent"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            // Sound and vibration come from AlarmService, so the channel itself stays quiet.
            NotificationChannel(CHANNEL_ALARM, "Alarm (streng)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BANNER, "Erinnerung (normal)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, "Stille Erinnerung", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Notification id per occurrence, so several reminders can be open at once. */
    fun id(occurrenceId: Long): Int = (occurrenceId % Int.MAX_VALUE).toInt() + 1

    /** Full-screen notification used by the foreground service. */
    fun fullScreen(context: Context, info: AlarmInfo): Notification {
        val open = openAlarm(context, info.occurrenceId)
        return builder(context, CHANNEL_ALARM, info)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .build()
    }

    /** Banner or silent notification; [quiet] chooses the channel. */
    fun show(context: Context, info: AlarmInfo, quiet: Boolean) {
        val notification = builder(context, if (quiet) CHANNEL_SILENT else CHANNEL_BANNER, info)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id(info.occurrenceId), notification)
    }

    fun cancel(context: Context, occurrenceId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id(occurrenceId))
    }

    private fun builder(context: Context, channel: String, info: AlarmInfo): Notification.Builder {
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(info.title)
            .setContentIntent(openAlarm(context, info.occurrenceId))
        info.text?.let { builder.setContentText(it) }
        if (info.quickDone) {
            builder.addAction(action(context, ActionReceiver.ACTION_DONE, "Erledigt", info.occurrenceId))
        }
        builder.addAction(action(context, ActionReceiver.ACTION_SNOOZE, "+10 min", info.occurrenceId))
        return builder
    }

    private fun openAlarm(context: Context, occurrenceId: Long): PendingIntent = PendingIntent.getActivity(
        context, 0, AlarmActivity.intent(context, occurrenceId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, title: String, occurrenceId: Long) =
        Notification.Action.Builder(null, title, ActionReceiver.pending(context, action, occurrenceId)).build()
}
