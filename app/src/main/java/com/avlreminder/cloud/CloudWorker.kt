package com.avlreminder.cloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.avlreminder.R
import com.avlreminder.graph
import com.avlreminder.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * The hourly backup (backup-entwurf.md, step 4, A-9). All decisions are in
 * [CloudBackup.backUpAutomatically]; the worker only calls it.
 *
 * It always ends as success: a failed backup is tried again at the next hour, not right away
 * with WorkManager's backoff (A-13, "Kosten vor Verfügbarkeit").
 */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.graph.cloud.backUpAutomatically()
        return Result.success()
    }
}

/**
 * [CloudSchedule] with WorkManager: every hour, only with network.
 *
 * Errors of WorkManager are only logged: the backup may fail, the app may not (backend.md).
 */
class WorkManagerSchedule(private val context: Context, private val log: (String) -> Unit = {}) : CloudSchedule {
    override fun start() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // KEEP: started again at every app start, the rhythm stays.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }.onFailure { log("cloud: schedule not started (${it::class.simpleName})") }
    }

    override fun stop() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
            .onFailure { log("cloud: schedule not stopped (${it::class.simpleName})") }
    }

    companion object {
        const val NAME = "cloud-backup"
        const val PERIOD_HOURS = 1L
    }
}

/** [CloudNotices] as notifications in their own channel; a tap opens the settings. */
class AndroidCloudNotices(private val context: Context) : CloudNotices {
    override fun otherDevice(created: Instant) = show(
        ID_OTHER_DEVICE,
        "Cloud-Backup pausiert",
        "Ein anderes Gerät hat zuletzt gesichert (${time(created)}). In den Einstellungen „Jetzt sichern“, " +
            "um von diesem Gerät weiterzusichern.",
    )

    override fun stale(lastBackup: Instant) = show(
        ID_STALE,
        "Cloud-Backup seit 3 Tagen nicht aktuell",
        "Zuletzt gesichert: ${time(lastBackup)}. Der Grund steht in den Einstellungen unter Cloud-Backup.",
    )

    override fun clear() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(TAG, ID_OTHER_DEVICE)
        manager.cancel(TAG, ID_STALE)
    }

    private fun show(id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context,
            id,
            MainActivity.openSettings(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Without the permission Android drops it; the status in the settings still shows.
        runCatching { context.getSystemService(NotificationManager::class.java).notify(TAG, id, notification) }
    }

    private fun time(at: Instant) = FORMAT.format(at.atZone(ZoneId.systemDefault()))

    companion object {
        const val CHANNEL = "cloud"

        // With their own tag, the ids cannot meet those of reminders and the overview.
        const val TAG = "cloud"
        const val ID_OTHER_DEVICE = 1
        const val ID_STALE = 2
        private val FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Cloud-Backup", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
