package com.avlreminder.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.avlreminder.R
import com.avlreminder.core.usecases.Digest
import com.avlreminder.core.usecases.Digests
import com.avlreminder.data.OverviewTimes
import com.avlreminder.graph
import com.avlreminder.system.Diag
import com.avlreminder.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The silent overview in the morning and evening (Anf. O1). Not exact: a few minutes late do
 * not matter, so it uses an inexact alarm and is set again after each one.
 */
object OverviewNotifications {
    private const val CHANNEL = "overview"
    private const val MORNING = "morning"
    private const val EVENING = "evening"
    private const val ACTION = "com.avlreminder.OVERVIEW"
    private const val NOTIFICATION_ID = Int.MIN_VALUE + 1

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Übersicht morgens und abends", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Sets both alarms from the settings; a time of null cancels its alarm. */
    fun schedule(context: Context, times: OverviewTimes, now: Instant = Instant.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        listOf<Pair<String, (OverviewTimes) -> LocalTime?>>(MORNING to { it.morning }, EVENING to { it.evening }).forEach { (kind, time) ->
            val pending = pending(context, kind)
            val at = next(times, time, now, ZoneId.systemDefault())
            if (at == null) {
                alarms.cancel(pending)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW_MS, pending)
                Diag.log("overview $kind planned for $at")
            }
        }
    }

    /** The next overview after [now] with the times of its day (VS2); null if off on every day. */
    fun next(times: OverviewTimes, pick: (OverviewTimes) -> LocalTime?, now: Instant, zone: ZoneId): Instant? {
        val today = now.atZone(zone).toLocalDate()
        return (0L..7L).firstNotNullOfOrNull { plus ->
            val day = today.plusDays(plus)
            pick(times.on(day))?.let { day.atTime(it).atZone(zone).toInstant() }?.takeIf { it > now }
        }
    }

    /** The next [time] strictly after [now]. */
    fun next(time: LocalTime, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate().atTime(time).atZone(zone).toInstant()
        return if (today > now) today else now.atZone(zone).toLocalDate().plusDays(1).atTime(time).atZone(zone).toInstant()
    }

    fun show(context: Context, digest: Digest) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = digest.lines.joinToString("\n")
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(digest.title)
            .setContentText(digest.lines.firstOrNull())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun pending(context: Context, kind: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, OverviewReceiver::class.java).setAction(ACTION).setData("avl://overview/$kind".toUri()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun isMorning(intent: Intent) = intent.data?.lastPathSegment == MORNING

    private const val WINDOW_MS = 10 * 60 * 1000L
}

/** Builds the overview from the database at the moment it is shown. */
class OverviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val morning = OverviewNotifications.isMorning(intent)
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                val now = Instant.now()
                val zone = graph.zone
                val today = graph.today(now)
                val digest = if (morning) Digests.morning(today, zone)
                else Digests.evening(today, graph.withEngine { it.weekGoals() }, now, zone)
                OverviewNotifications.show(context, digest)
                Diag.log("overview ${if (morning) "morning" else "evening"} shown: ${digest.title} (${digest.lines.size} lines)")
                OverviewNotifications.schedule(context, graph.settings.overview.first())
            } finally {
                result.finish()
            }
        }
    }
}
