package com.avlreminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avlreminder.R
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.graph
import com.avlreminder.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Alle Alarme pausieren" (PS7): while paused a silent, lasting notification says so and
 * switches the alarms on again with one tap. It removes itself when the pause ends.
 */
object AlarmPause {
    private const val CHANNEL = "pause"
    private const val ACTION_RESUME = "com.avlreminder.RESUME_ALARMS"
    private const val NOTIFICATION_ID = Int.MIN_VALUE + 2
    /** "Bis morgen früh". */
    val MORNING: LocalTime = LocalTime.of(6, 0)

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarme pausiert", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** The next [MORNING] after [now]: today before six, tomorrow otherwise. */
    fun untilMorning(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val day = if (local.toLocalTime() < MORNING) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return day.atTime(MORNING).atZone(zone).toInstant()
    }

    /** "bis 19:30", "bis morgen 06:00" or "bis du sie wieder einschaltest". */
    fun untilText(until: Instant, now: Instant, zone: ZoneId): String {
        if (until == AlertSettings.PAUSED_FOR_GOOD) return "bis du sie wieder einschaltest"
        val time = DateTimeFormatter.ofPattern("HH:mm").format(until.atZone(zone))
        return when (until.atZone(zone).toLocalDate()) {
            now.atZone(zone).toLocalDate() -> "bis $time"
            now.atZone(zone).toLocalDate().plusDays(1) -> "bis morgen $time"
            else -> "bis ${DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(until.atZone(zone))}"
        }
    }

    /** Shows the notification while [until] lies ahead, removes it otherwise. */
    fun update(context: Context, until: Instant?, now: Instant = Instant.now()) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (until == null || until <= now) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val resume = PendingIntent.getBroadcast(
            context, 0, Intent(context, PauseReceiver::class.java).setAction(ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Alle Alarme pausiert")
            .setContentText("${untilText(until, now, ZoneId.systemDefault())} – fällige Reminder kommen nur still")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Wieder einschalten", resume).build())
        if (until != AlertSettings.PAUSED_FOR_GOOD) builder.setTimeoutAfter(Duration.between(now, until).toMillis())
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    internal fun isResume(intent: Intent) = intent.action == ACTION_RESUME
}

/** "Wieder einschalten" in the notification. */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AlarmPause.isResume(intent)) return
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.setAlarmsPaused(null)
            } finally {
                result.finish()
            }
        }
    }
}
