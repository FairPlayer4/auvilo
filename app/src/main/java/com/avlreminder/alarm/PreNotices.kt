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
import com.avlreminder.core.ports.PreNoticePort
import com.avlreminder.graph
import com.avlreminder.system.Diag
import com.avlreminder.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The silent notice before a reminder (T6, S6). Like the overview not exact: a few minutes late
 * do not matter. The text is built from the database when it is shown.
 */
object PreNotices {
    private const val CHANNEL = "prenotice"
    private const val ACTION = "com.avlreminder.PRENOTICE"
    private const val EXTRA_ID = "occurrence"
    private const val WINDOW_MS = 5 * 60 * 1000L

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Vorab-Reminder", NotificationManager.IMPORTANCE_LOW),
        )
    }

    class Port(private val context: Context) : PreNoticePort {
        override fun set(occurrenceId: Long, at: Instant) {
            context.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW_MS, pending(context, occurrenceId))
        }

        override fun cancel(occurrenceId: Long) {
            context.getSystemService(AlarmManager::class.java).cancel(pending(context, occurrenceId))
        }
    }

    /** A tap opens the reminder in the app. */
    fun show(context: Context, occurrenceId: Long, reminderId: Long, title: String, lines: List<String>) {
        val open = PendingIntent.getActivity(
            context, occurrenceId.toInt(), MainActivity.editReminder(context, reminderId), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(tag(occurrenceId), 0, notification)
    }

    internal fun occurrenceId(intent: Intent) = intent.getLongExtra(EXTRA_ID, -1)

    private fun tag(occurrenceId: Long) = "prenotice-$occurrenceId"

    private fun pending(context: Context, occurrenceId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, PreNoticeReceiver::class.java).setAction(ACTION)
            .setData("avl://prenotice/$occurrenceId".toUri()).putExtra(EXTRA_ID, occurrenceId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Shows the notice unless the occurrence was done in advance or is gone. */
class PreNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = PreNotices.occurrenceId(intent)
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                val notice = graph.withEngine { it.preNotice(id) }
                val reminderId = graph.repository.occurrence(id)?.reminderId
                if (notice != null && reminderId != null) {
                    PreNotices.show(context, id, reminderId, notice.title, notice.lines)
                }
                Diag.log("prenotice occ=$id ${if (notice == null) "skipped" else "shown (${notice.lines.size} lines)"}")
            } finally {
                result.finish()
            }
        }
    }
}
