package com.avlreminder.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.TestClock
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.graph
import com.avlreminder.settle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.time.LocalTime

/**
 * Was nach einer Änderung aus der App von einem klingelnden Termin übrig bleibt (Review-Lauf 1):
 * Löschen, Pausieren, Archivieren und Verwerfen nehmen Klingeln und Benachrichtigung weg, Notaus
 * lässt eine stille Benachrichtigung stehen.
 */
@RunWith(AndroidJUnit4::class)
class AlarmCleanupTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val graph = app.graph
    private val repo = graph.repository
    private val notifications = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        TestClock.at()
        runBlocking { (app as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
        TestClock.reset()
    }

    private fun due(): Pair<Long, Long> = runBlocking {
        val id = repo.saveReminder(Reminder(title = "Katzenbrunnen", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = TestClock.now()))
        id to repo.saveOccurrence(Occurrence(reminderId = id, dueAt = TestClock.now().minusSeconds(60), state = OccurrenceState.DUE))
    }

    private fun shown(o: Long): Notification? = shadowOf(notifications).getNotification(Notifications.id(o))

    @Test
    fun `Pausieren nimmt die Benachrichtigung des klingelnden Termins weg`() {
        val (id, o) = due()
        Notifications.show(app, AlarmInfo(o, "Katzenbrunnen", null, quickDone = true), quiet = false)
        assertNotNull(shown(o))

        assertEquals(true, runBlocking { graph.withoutAlarm(o) { it.setActive(id, false) } })
        graph.settle()

        assertNull(shown(o))
    }

    @Test
    fun `eine abgelehnte Änderung lässt die Benachrichtigung stehen`() {
        val (_, o) = due()
        Notifications.show(app, AlarmInfo(o, "Katzenbrunnen", null, quickDone = true), quiet = false)

        // So meldet die Engine eine abgelehnte Pause (laufende Sitzung).
        runBlocking { graph.withoutAlarm(o) { false } }
        graph.settle()

        assertNotNull(shown(o))
    }

    @Test
    fun `Notaus stoppt das Klingeln und lässt eine stille Benachrichtigung`() {
        val info = AlarmInfo(41, "Katzenbrunnen", null, quickDone = true)
        AlarmService.start(app, info, loud = false)
        val service = Robolectric.buildService(AlarmService::class.java, shadowOf(app).nextStartedService).create().startCommand(0, 1).get()
        assertEquals(41L, AlarmService.ringing.value)

        runBlocking { graph.setAlarmsPaused(TestClock.now().plus(Duration.ofHours(1))) }
        val quiet = generateSequence { shadowOf(app).nextStartedService }.first { it.action == "quiet" }
        service.onStartCommand(quiet, 0, 2)

        assertNull(AlarmService.ringing.value)
        assertEquals("Katzenbrunnen", shown(41)?.extras?.getString(Notification.EXTRA_TITLE))
        runBlocking { graph.settings.setAlarmsPaused(null, TestClock.now()) }
    }
}
