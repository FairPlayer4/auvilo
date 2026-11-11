package com.avlreminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.avlreminder.R
import com.avlreminder.system.Diag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A running session as its notification shows it (Anf. 7.11). */
data class SessionNote(
    val occurrenceId: Long,
    val title: String,
    val startedAt: Instant,
    /** "Fertig" from the notification; not with a proof that needs photo, text or audio. */
    val quickDone: Boolean,
    /** "Teilschritt" is allowed. */
    val minimum: Boolean,
)

/**
 * Keeps the running sessions visible (design.md E.3): one ongoing notification each with a
 * chronometer and *Fertig*, *Teilschritt*, *Abbruch*. It does not time anything itself – the checks
 * and the last reminder come over the `AlarmPort` and survive a restart through `sync`.
 * Foreground service of type `systemExempted` like the alarm; sessions last too long for `shortService`.
 */
class SessionService : Service() {
    companion object {
        private const val CHANNEL = "session"
        private const val ACTION_UPDATE = "update"

        /** The sessions to show; set by [show], read by the service in the same process. */
        @Volatile private var notes: List<SessionNote> = emptyList()
        /** The ids last shown, to remove the notifications of sessions that ended. */
        @Volatile private var shown: Set<Long> = emptySet()
        /** The service exists; only then does stopping it need a start command. */
        @Volatile private var running = false

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Laufende Sitzung", NotificationManager.IMPORTANCE_LOW),
            )
        }

        /**
         * Shows exactly [sessions]; called after every use case. Without any the service stops.
         * Unchanged, nothing happens.
         */
        fun show(context: Context, sessions: List<SessionNote>) {
            if (sessions == notes && sessions.map { it.occurrenceId }.toSet() == shown) return
            notes = sessions
            val manager = context.getSystemService(NotificationManager::class.java)
            (shown - sessions.map { it.occurrenceId }.toSet()).forEach { manager.cancel(id(it)) }
            shown = sessions.map { it.occurrenceId }.toSet()
            if (sessions.isEmpty()) {
                // KRITISCH: through onStartCommand, so a service started a moment ago calls startForeground before it stops.
                if (running) context.startService(Intent(context, SessionService::class.java).setAction(ACTION_UPDATE))
                return
            }
            try {
                context.startForegroundService(Intent(context, SessionService::class.java).setAction(ACTION_UPDATE))
            } catch (e: Exception) {
                // Never lose the session over it: the notifications without the service.
                Diag.log("session service not startable, plain notification: ${e.message}")
                sessions.forEach { manager.notify(id(it.occurrenceId), notification(context, it)) }
            }
        }

        /** Own id range: neither the alarm's ([Notifications.id]) nor its watch copy (negative ids near 0). */
        fun id(occurrenceId: Long): Int = Int.MIN_VALUE + 1_000 + (occurrenceId % 1_000_000).toInt()

        fun notification(context: Context, note: SessionNote): Notification {
            val since = DateTimeFormatter.ofPattern("HH:mm").format(note.startedAt.atZone(ZoneId.systemDefault()))
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(note.title)
                .setContentText("läuft seit $since")
                // The chronometer counts up from the start (D2).
                .setWhen(note.startedAt.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setContentIntent(Notifications.openAlarm(context, note.occurrenceId))
            if (note.quickDone) builder.addAction(Notifications.action(context, ActionReceiver.ACTION_DONE, "Fertig", note.occurrenceId))
            if (note.minimum) builder.addAction(Notifications.action(context, ActionReceiver.ACTION_MINIMUM, "Teilschritt", note.occurrenceId))
            builder.addAction(Notifications.action(context, ActionReceiver.ACTION_ABORT, "Abbruch", note.occurrenceId))
            return builder.build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = notes
        val manager = getSystemService(NotificationManager::class.java)
        if (current.isEmpty()) {
            // A start command without startForeground would crash the app, also right before stopping.
            runCatching { startForeground(Int.MIN_VALUE + 999, ended(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val first = current.first()
        try {
            startForeground(id(first.occurrenceId), notification(this, first), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: Exception) {
            Diag.log("session startForeground failed, plain notification: ${e.message}")
            manager.notify(id(first.occurrenceId), notification(this, first))
        }
        current.drop(1).forEach { manager.notify(id(it.occurrenceId), notification(this, it)) }
        // Restarted by the system the list would be empty; the next use case shows it again.
        return START_NOT_STICKY
    }

    private fun ended(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_bell)
        .setContentTitle("Sitzung beendet")
        .build()
}
