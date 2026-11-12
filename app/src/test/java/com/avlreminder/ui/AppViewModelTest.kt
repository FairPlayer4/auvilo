package com.avlreminder.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.TestClock
import com.avlreminder.alarm.AlarmInfo
import com.avlreminder.alarm.Notifications
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.graph
import com.avlreminder.settle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.time.LocalTime

/**
 * What the view model does around the engine: says why something was rejected (Review-Lauf 1) and
 * takes the alarm away when a change from the app ends it (Review-Lauf 5, E22: the wiring itself,
 * not only [com.avlreminder.AppGraph.withoutAlarm]).
 */
@RunWith(AndroidJUnit4::class)
class AppViewModelTest {
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

    /** Waits for the view model's coroutine, then for what it started in the graph. */
    private fun waitFor(what: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!what() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertTrue(what())
        graph.settle()
    }

    private fun latestToast(): String? {
        waitFor { ShadowToast.getTextOfLatestToast() != null }
        return ShadowToast.getTextOfLatestToast()
    }

    /** A reminder whose occurrence rings, with its notification. */
    private fun ringing(): Pair<Long, Long> {
        val (id, o) = runBlocking {
            val id = repo.saveReminder(Reminder(title = "Katzenbrunnen", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = TestClock.now()))
            id to repo.saveOccurrence(Occurrence(reminderId = id, dueAt = TestClock.now().minusSeconds(60), state = OccurrenceState.DUE))
        }
        Notifications.show(app, AlarmInfo(o, "Katzenbrunnen", null, quickDone = true), quiet = false)
        assertNotNull(shown(o))
        return id to o
    }

    private fun shown(o: Long): Notification? = shadowOf(notifications).getNotification(Notifications.id(o))

    private fun reminder(id: Long) = runBlocking { repo.reminder(id) }

    @Test
    fun `eine abgelehnte Antwort auf die Hinweis-Karte sagt warum`() {
        val vm = AppViewModel(app)
        // The reminder is gone meanwhile (e.g. deleted on the other screen): the engine rejects.
        vm.answerSnoozeHint(999, SnoozeHintChoice.DISMISS)
        assertEquals("Nicht möglich: Reminder nicht gefunden", latestToast())
    }

    @Test
    fun `Löschen nimmt die Benachrichtigung des klingelnden Termins weg`() {
        val (id, o) = ringing()
        AppViewModel(app).delete(id)
        waitFor { reminder(id) == null && shown(o) == null }
    }

    @Test
    fun `Pausieren nimmt die Benachrichtigung des klingelnden Termins weg`() {
        val (id, o) = ringing()
        AppViewModel(app).setActive(id, false)
        waitFor { reminder(id)?.active == false && shown(o) == null }
    }

    @Test
    fun `Archivieren nimmt die Benachrichtigung des klingelnden Termins weg`() {
        val (id, o) = ringing()
        AppViewModel(app).setArchived(id, true)
        waitFor { reminder(id)?.archivedAt != null && shown(o) == null }
    }

    @Test
    fun `Verwerfen nimmt die Benachrichtigung des Termins weg`() {
        val (_, o) = ringing()
        AppViewModel(app).discard(o)
        waitFor { runBlocking { repo.occurrence(o) }?.resolvedAt != null && shown(o) == null }
    }

    @Test
    fun `eine abgelehnte Aufgabe zum Blocker sagt warum`() {
        AppViewModel(app).planTask(999, java.time.LocalDateTime.of(2030, 1, 1, 18, 0))
        assertEquals("Nicht möglich: Blocker nicht gefunden", latestToast())
    }

    @Test
    fun `Pausieren eines unbekannten Reminders sagt nicht, dass er läuft`() {
        AppViewModel(app).setActive(999, false)
        assertEquals("Nicht möglich: Reminder nicht gefunden", latestToast())
    }
}
