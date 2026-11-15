package com.avlreminder.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import com.avlreminder.R
import com.avlreminder.system.Diag
import java.time.Instant

/**
 * Exact alarms are the app's core (Anf. 7.28 PS10). The app holds SCHEDULE_EXACT_ALARM: the user
 * grants it in "Wecker und Erinnerungen" and can take it back. Without it alarms still come, but
 * up to an hour late and without the lasting alarm ([AlarmService] and [SessionService] need it for
 * their type `systemExempted`). The app says so on "Heute" and, when it plans an alarm without
 * the permission, in a notification.
 */
object ExactAlarms {
    private const val CHANNEL = "permission"
    private const val NOTIFICATION_ID = Int.MIN_VALUE + 3

    const val LABEL = "Wecker und Erinnerungen"
    const val WARNING = "Ohne die Erlaubnis „$LABEL“ funktioniert die App nicht richtig: Alarme kommen bis zu " +
        "einer Stunde zu spät und klingeln nicht, bis du reagierst."

    fun allowed(context: Context): Boolean = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Android's page for this app's switch. */
    fun settings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Fehlende Erlaubnis", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /**
     * Plans [pending] with [exact] if allowed. Otherwise, or if the permission was taken back a
     * moment ago, as close as Android allows without it (also in Doze, up to an hour late), and
     * the notification says why.
     */
    fun set(context: Context, at: Instant, pending: PendingIntent, what: String, exact: AlarmManager.() -> Unit) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.exact()
                return
            } catch (_: SecurityException) {
                // Taken back between the check and the call: fall through.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        Diag.log("exact alarms not allowed, $what inexact at=$at")
        showMissing(context)
    }

    /** Takes the notification away once the permission is there again. */
    fun refresh(context: Context) {
        if (allowed(context)) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun showMissing(context: Context) {
        val open = PendingIntent.getActivity(context, 0, settings(context), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Erlaubnis „$LABEL“ fehlt")
            .setContentText("Alarme kommen zu spät. Tippen zum Erlauben.")
            .setStyle(Notification.BigTextStyle().bigText("$WARNING Tippen zum Erlauben."))
            .setContentIntent(open)
            .setAutoCancel(true)
            // Planned again with every change: alert once, then stay quietly until granted.
            .setOnlyAlertOnce(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}
