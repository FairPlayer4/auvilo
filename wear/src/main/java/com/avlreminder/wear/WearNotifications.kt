package com.avlreminder.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.avlreminder.core.usecases.WatchCheck
import com.avlreminder.core.usecases.WatchScreen
import com.avlreminder.core.usecases.WatchState
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * The watch's own notifications (design.md 17): the running session as an ongoing activity on the
 * watch face, and a check that vibrates. Its own channels, so "Nicht stören" and the theatre mode
 * of the watch count by themselves.
 */
object WearNotifications {
    const val CHANNEL_SESSION = "session"
    const val CHANNEL_CHECK = "check"
    const val CHANNEL_CHECK_QUIET = "check_quiet"

    /** The ongoing activity of the running session. */
    const val SESSION_ID = 1

    /** Vibration of a check: twice long, clearly not a message (design.md 17, "Vibration"). */
    val PATTERN = longArrayOf(0, 600, 300, 600)

    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SESSION, "Laufende Sitzung", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECK, "Nachfrage", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = PATTERN
            },
        )
        // A check that is quiet on the phone (Notaus, "Nicht stören") is quiet on the watch too.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECK_QUIET, "Nachfrage still", NotificationManager.IMPORTANCE_LOW).apply { enableVibration(false) },
        )
    }

    /** Check id per occurrence; the session's [SESSION_ID] never meets it. */
    fun checkId(occurrenceId: Long): Int = (occurrenceId % (Int.MAX_VALUE - 1)).toInt() + 2

    /** The ongoing activity for the first running session of [state], or none. */
    fun session(context: Context, state: WatchState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val session = state.sessions.firstOrNull()
        if (session == null) {
            manager.cancel(SESSION_ID)
            return
        }
        val now = context.wear.clock.instant()
        val open = open(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(session.title)
            .setContentText(WatchScreen.detail(session, now, java.time.ZoneId.systemDefault()))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(session.startedAt.toEpochMilli())
        // The stopwatch of the watch face counts from the start, in the base of elapsedRealtime.
        val zero = SystemClock.elapsedRealtime() - WatchScreen.elapsedMillis(session, now)
        OngoingActivity.Builder(context, SESSION_ID, builder)
            .setStaticIcon(R.drawable.ic_bell)
            .setTouchIntent(open)
            .setTitle(session.title)
            .setStatus(Status.Builder().addTemplate("#time#").addPart("time", Status.StopwatchPart(zero)).build())
            .build()
            .apply(context)
        manager.notify(SESSION_ID, builder.build())
    }

    /** A check rings at the phone: the watch vibrates (unless quiet) and a tap opens the answers. */
    fun check(context: Context, check: WatchCheck) {
        val notification = NotificationCompat.Builder(context, if (check.audible) CHANNEL_CHECK else CHANNEL_CHECK_QUIET)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(check.title)
            .setContentText(WatchScreen.checkText(check))
            .setContentIntent(open(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (check.audible) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setVibrate(if (check.audible) PATTERN else null)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(checkId(check.occurrenceId), notification)
    }

    /** The check rings no more (answered on the phone or the watch, or it ended). */
    fun endCheck(context: Context, occurrenceId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(checkId(occurrenceId))
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, WatchActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
