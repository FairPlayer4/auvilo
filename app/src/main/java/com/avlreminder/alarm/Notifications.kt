package com.avlreminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.avlreminder.R
import com.avlreminder.core.usecases.Ring
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a notification needs to know about a ringing occurrence. */
data class AlarmInfo(
    val occurrenceId: Long,
    val title: String,
    val text: String?,
    val quickDone: Boolean,
    /** Snooze buttons in minutes, already without those past the end of the day. */
    val snooze: List<Int> = emptyList(),
) {
    companion object {
        fun of(ring: Ring) = AlarmInfo(
            occurrenceId = ring.occurrence.id,
            title = ring.reminder.title,
            text = ring.reminder.why ?: ring.reminder.goal,
            // Photo, text or audio cannot be given from the notification, so "done" there would always count as without proof.
            quickDone = !ring.reminder.proof.needsEvidence,
            snooze = ring.snooze,
        )
    }
}

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    private const val CHANNEL_BANNER = "banner"
    private const val CHANNEL_SILENT = "silent"
    private const val CHANNEL_WATCH = "watch"

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
        manager.createNotificationChannel(
            // The ongoing alarm notification is not passed on to the watch; this copy is.
            NotificationChannel(CHANNEL_WATCH, "Alarm auf der Uhr", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            },
        )
    }

    /** Channels a reminder with "auch bei Nicht stören" needs as exception; empty if all may. */
    fun channelsBlockedByDnd(context: Context): List<String> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return listOf(CHANNEL_ALARM, CHANNEL_BANNER).filter { manager.getNotificationChannel(it)?.canBypassDnd() != true }
    }

    /** Notification id per occurrence, so several reminders can be open at once. */
    fun id(occurrenceId: Long): Int = (occurrenceId % Int.MAX_VALUE).toInt() + 1

    /** Id of the watch copy; negative ids never meet [id]. */
    private fun mirrorId(occurrenceId: Long): Int = -id(occurrenceId)

    /** Full-screen notification used by the foreground service. */
    fun fullScreen(context: Context, info: AlarmInfo): Notification {
        val open = openAlarm(context, info.occurrenceId)
        return builder(context, CHANNEL_ALARM, info)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setLocalOnly(true)
            .setFullScreenIntent(open, true)
            .build()
    }

    /** Normal copy of a full-screen alarm with the same buttons, so the watch shows it too. */
    fun showMirror(context: Context, info: AlarmInfo) {
        val notification = builder(context, CHANNEL_WATCH, info)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOnlyAlertOnce(false)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(mirrorId(info.occurrenceId), notification)
    }

    fun cancelMirror(context: Context, occurrenceId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(mirrorId(occurrenceId))
    }

    /** Quiet while the proof is being made; tapping it goes back to the proof. */
    fun showProofRunning(context: Context, info: AlarmInfo, until: Instant) {
        val time = DateTimeFormatter.ofPattern("HH:mm").format(until.atZone(ZoneId.systemDefault()))
        val notification = Notification.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(info.title)
            .setContentText("Nachweis läuft – Alarm pausiert bis $time")
            .setContentIntent(openAlarm(context, info.occurrenceId))
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id(info.occurrenceId), notification)
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
        cancelMirror(context, occurrenceId)
    }

    private fun builder(context: Context, channel: String, info: AlarmInfo): Notification.Builder {
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(info.title)
            .setContentIntent(openAlarm(context, info.occurrenceId))
        info.text?.let { builder.setContentText(it) }
        if (info.quickDone) {
            builder.addAction(action(context, ActionReceiver.ACTION_DONE, "Erledigt", info.occurrenceId))
        }
        // At most three actions fit; "done" plus two snooze buttons.
        info.snooze.take(2).forEach { minutes ->
            builder.addAction(
                Notification.Action.Builder(null, "+$minutes min", ActionReceiver.snooze(context, info.occurrenceId, minutes)).build(),
            )
        }
        return builder
    }

    private fun openAlarm(context: Context, occurrenceId: Long): PendingIntent = PendingIntent.getActivity(
        context, 0, AlarmActivity.intent(context, occurrenceId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, title: String, occurrenceId: Long) =
        Notification.Action.Builder(null, title, ActionReceiver.pending(context, action, occurrenceId)).build()
}
