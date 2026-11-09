package com.avlreminder.cloud

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.avlreminder.AvlApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.util.concurrent.TimeUnit

/** The Android side of the hourly backup (step 4): WorkManager, worker, notifications. */
@RunWith(AndroidJUnit4::class)
class CloudWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val work get() = WorkManager.getInstance(context)
    private fun infos(): List<WorkInfo> = work.getWorkInfosForUniqueWork(WorkManagerSchedule.NAME).get()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    // A-9: every hour, only with network.
    @Test
    fun theScheduleRunsHourlyAndOnlyWithNetwork() {
        WorkManagerSchedule(context).start()
        val info = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun startingAgainKeepsTheRhythm() {
        val schedule = WorkManagerSchedule(context)
        schedule.start()
        val first = infos().single().id
        schedule.start()
        assertEquals(first, infos().single().id)
    }

    @Test
    fun stoppingCancelsIt() {
        val schedule = WorkManagerSchedule(context)
        schedule.start()
        schedule.stop()
        assertEquals(WorkInfo.State.CANCELLED, infos().single().state)
    }

    // A-13: the worker ends as success, never as retry; the next try is the next hour, not
    // WorkManager's backoff. What it does is in CloudBackupTest.
    @Test
    fun theWorkerAlwaysEndsAsSuccess() {
        runBlocking { (context as AvlApp).started.join() }
        val worker = TestListenableWorkerBuilder<CloudBackupWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { worker.doWork() })
    }

    @Test
    fun theNoticesOpenTheSettingsAndGoAgain() {
        AndroidCloudNotices.createChannel(context)
        val notices = AndroidCloudNotices(context)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        notices.otherDevice(Instant.parse("2026-11-08T12:30:00Z"))
        notices.stale(Instant.parse("2026-11-05T12:00:00Z"))
        notices.brake(20, 5)
        val shown = manager.allNotifications
        assertEquals(3, shown.size)
        assertTrue(shown.all { it.channelId == AndroidCloudNotices.CHANNEL })
        val intent = shadowOf(shown.first().contentIntent).savedIntent
        assertTrue(intent.getBooleanExtra("settings", false))

        notices.clear()
        assertTrue(manager.allNotifications.isEmpty())
    }
}
