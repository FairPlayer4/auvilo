package com.avlreminder.alarm

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.AvlApp
import com.avlreminder.graph
import android.net.ConnectivityManager
import com.avlreminder.location.AndroidPlaceAlarms
import com.avlreminder.location.AndroidWifi
import com.avlreminder.location.GeofenceReceiver
import com.avlreminder.location.WifiReceiver
import org.robolectric.shadows.ShadowNetwork
import com.avlreminder.location.PlaceCheckReceiver
import com.avlreminder.system.SystemReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * The way from Android into the engine and back, with the real database: a broadcast arrives
 * (alarm, notification button, arrival, boot), the engine decides, and the result shows up as
 * notification, alarm service or planned alarm. The engine itself is tested in core; here only
 * the wiring counts, so the checks do not depend on how loud something rings.
 */
@RunWith(AndroidJUnit4::class)
class ReceiverGlueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = context.graph
    private val repo = graph.repository
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        // The receivers run on the real clock; near the end of the day an occurrence due a few
        // minutes ago would already count as missed (23:00) and the checks would not apply.
        val now = LocalTime.now()
        assumeTrue("not shortly before or after midnight", now > LocalTime.of(0, 30) && now < LocalTime.of(22, 30))
        // The app syncs on start (on Dispatchers.Default); let that finish so it does not race
        // with the test. Idling the main looper alone did not wait for it.
        runBlocking { (context as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    private fun reminder(title: String = "Katzenbrunnen", checklist: List<ChecklistItem> = emptyList()) = Reminder(
        title = title, schedule = Schedule.Daily(LocalTime.of(18, 0)), checklist = checklist, createdAt = Instant.now(),
        character = Character.DOCUMENT,
    )

    private suspend fun occurrence(reminderId: Long, state: OccurrenceState, dueAgo: Duration): Long =
        repo.saveOccurrence(Occurrence(reminderId = reminderId, dueAt = Instant.now().minus(dueAgo), state = state))

    private fun broadcast(intent: Intent) {
        context.sendBroadcast(intent)
        ShadowLooper.idleMainLooper()
    }

    private fun alarmFor(occurrenceId: Long) = Intent(context, AlarmReceiver::class.java)
        .putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId)

    /** The receivers work asynchronously: waits until [done] holds, at most two seconds. */
    private fun waitFor(what: String, done: suspend () -> Boolean) = runBlocking {
        repeat(100) {
            if (done()) return@runBlocking
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
        throw AssertionError("timed out waiting for: $what")
    }

    /** Rang in any form: a notification, or the alarm service with its full screen. */
    private fun rang(occurrenceId: Long): Boolean {
        if (shadowOf(notifications).getNotification(Notifications.id(occurrenceId)) != null) return true
        val app = shadowOf(context as Application)
        while (true) {
            val started = app.nextStartedService ?: return false
            if (started.getLongExtra("id", -1) == occurrenceId) return true
        }
    }

    // --- Alarm ------------------------------------------------------------------------------

    @Test
    fun aFiringAlarmMakesTheOccurrenceDueAndRings() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.PLANNED, Duration.ofSeconds(30))

        broadcast(alarmFor(o))
        // It rings after the command is stored, so wait for the ring itself.
        waitFor("rang") { rang(o) }
        assertEquals(OccurrenceState.DUE, repo.occurrence(o)?.state)
        // The next round is planned.
        assertNotNull(alarmManager.nextAlarmClock)
    }

    @Test
    fun anAlarmOfAClosedOccurrenceDoesNothing() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.CLOSED, Duration.ofMinutes(1))
        val before = repo.occurrence(o)

        broadcast(alarmFor(o))
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()

        assertEquals(before, repo.occurrence(o))
        assertEquals(false, rang(o))
    }

    // --- Buttons in the notification ------------------------------------------------------------

    @Test
    fun snoozeFromTheNotificationPlansTheAlarmForLater() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.DUE, Duration.ofMinutes(1))
        Notifications.show(context, AlarmInfo(o, "Katzenbrunnen", null, quickDone = true), quiet = false)

        broadcast(Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_SNOOZE).putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, o).putExtra("minutes", 30))
        waitFor("snoozed") { repo.occurrence(o)?.state == OccurrenceState.PLANNED }

        val until = repo.occurrence(o)!!.currentAt
        val expected = Instant.now().plus(Duration.ofMinutes(30))
        assertTrue("paused until $until", Duration.between(until, expected).abs() < Duration.ofMinutes(1))
        assertEquals(until.toEpochMilli(), alarmManager.nextAlarmClock?.triggerTime)
        waitFor("notification removed") { shadowOf(notifications).getNotification(Notifications.id(o)) == null }
    }

    @Test
    fun checkingAnItemFromTheNotificationOffersTheNextOneQuietly() = runBlocking {
        val id = repo.saveReminder(reminder("Brunnen", listOf(ChecklistItem(title = "Wohnzimmer"), ChecklistItem(title = "Küche"))))
        val items = repo.reminder(id)!!.checklist
        val o = occurrence(id, OccurrenceState.DUE, Duration.ofMinutes(1))

        ActionReceiver.check(context, o, items[0].id).send()
        ShadowLooper.idleMainLooper()
        waitFor("item done") { repo.itemsDone(o).map { it.itemId } == listOf(items[0].id) }
        waitFor("next item offered") { shadowOf(notifications).getNotification(Notifications.id(o)) != null }

        val n = shadowOf(notifications).getNotification(Notifications.id(o))
        assertEquals("silent", n.channelId)
        assertEquals(OccurrenceState.DUE, repo.occurrence(o)?.state)
    }

    @Test
    fun doneFromTheNotificationOffersUndoForAMinuteAndUndoOpensItAgain() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.DUE, Duration.ofMinutes(1))
        Notifications.show(context, AlarmInfo(o, "Katzenbrunnen", null, quickDone = true), quiet = false)

        broadcast(Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DONE).putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, o))
        waitFor("undo offered") { shadowOf(notifications).getNotification(Undo.id(o)) != null }
        assertEquals(OccurrenceState.DONE, repo.occurrence(o)?.state)
        val offer = shadowOf(notifications).getNotification(Undo.id(o))
        assertEquals("„Katzenbrunnen“ erledigt", offer.extras.getString("android.title"))
        assertEquals("Rückgängig", offer.actions.single().title)
        assertEquals(Undo.TIMEOUT_MS, offer.timeoutAfter)

        broadcast(Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_REOPEN).putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, o))
        waitFor("reopened") { repo.occurrence(o)?.state == OccurrenceState.PLANNED }
        waitFor("told") { shadowOf(notifications).getNotification(Undo.id(o))?.extras?.getString("android.title") == "„Katzenbrunnen“ wieder offen" }
        assertNull(shadowOf(notifications).getNotification(Undo.id(o)).actions)
        assertEquals(o, repo.openOccurrence(id)?.id)
    }

    // --- Arrival ------------------------------------------------------------------------------

    /** Waits for the arrival only, so the test does not depend on the time of day. */
    private suspend fun training(home: Long): Long =
        when (val saved = graph.withEngine { it.save(reminder("Training").copy(schedule = Schedule.OnArrival, arrival = Arrival(home, WhenAway.DROP))) }) {
            is SaveResult.Saved -> saved.id
            is SaveResult.Invalid -> throw AssertionError(saved.problems.toString())
        }

    @Test
    fun anArrivalRingsItsRemindersAndTheLogSaysWhich() = runBlocking {
        val home = graph.withEngine { it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5)) }
        val id = training(home)
        val o = repo.openOccurrence(id)!!
        assertEquals(OccurrenceState.PLANNED, o.state)

        GeofenceReceiver.arrived(graph, listOf(home))

        assertEquals(OccurrenceState.DUE, repo.occurrence(o.id)?.state)
        assertTrue(rang(o.id))
        val log = File(context.filesDir, "diag/diag.log").readText()
        assertTrue(log, log.contains("arrival place=$home → 1 ring(s) \"Training\" due="))
    }

    @Test
    fun anArrivalAtAnotherPlaceRingsNothing() = runBlocking {
        val home = graph.withEngine { it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5)) }
        val id = training(home)

        GeofenceReceiver.arrived(graph, listOf(home + 1))

        assertEquals(OccurrenceState.PLANNED, repo.openOccurrence(id)?.state)
    }

    /**
     * In the arrival window the engine sets the check alarm of the place (7.29 Z.3); when it
     * fires, the check runs and the log has its result without WLAN names or BSSIDs.
     */
    @Test
    fun theCheckAlarmOfAPlaceRunsTheCheckAndLogsItWithoutNames() = runBlocking {
        val bssid = "aa:bb:cc:00:00:01"
        val home = graph.withEngine {
            it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5, wifis = listOf(PlaceWifi(bssid, "Heimnetz"))))
        }
        // Due in an hour: the window (90 min before) is running, the next check comes within 20 min.
        val latest = LocalTime.now().plusHours(1).withSecond(0).withNano(0)
        val saved = graph.withEngine {
            it.save(reminder("Training").copy(schedule = Schedule.Daily(latest), arrival = Arrival(home, WhenAway.DROP)))
        }
        assertTrue(saved.toString(), saved is SaveResult.Saved)
        val check = shadowOf(alarmManager).scheduledAlarms.single { it.operation?.let { op -> shadowOf(op).savedIntent }?.component?.className == PlaceCheckReceiver::class.java.name }
        assertTrue(Instant.ofEpochMilli(check.triggerAtTime) <= Instant.now().plus(Duration.ofMinutes(20)))
        // A check inside the window may come a little late; only the one at its start is exact.
        assertTrue("inexact", !check.isAllowWhileIdle)
        assertEquals(check.triggerAtTime, shadowOf(check.operation).savedIntent.getLongExtra(AndroidPlaceAlarms.EXTRA_AT, -1))

        broadcast(shadowOf(check.operation).savedIntent)

        val log = File(context.filesDir, "diag/diag.log")
        waitFor("check logged") { log.readText().contains("place check place=$home UNKNOWN") }
        val text = log.readText()
        assertTrue(text, !text.contains(bssid) && !text.contains("Heimnetz"))
    }

    /**
     * F2 (Homeoffice) hangs on the check at the start of the window: it is exact also in Doze and
     * carries its planned time, so the engine knows it was the first one however late it comes.
     */
    @Test
    fun theCheckAtTheStartOfTheWindowIsExactAndCarriesItsPlannedTime() = runBlocking {
        val home = graph.withEngine {
            it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5, wifis = listOf(PlaceWifi("aa:bb:cc:00:00:01"))))
        }
        // Due in three hours: the window (90 min before) starts in 90 min.
        val latest = LocalTime.now().plusHours(3).withSecond(0).withNano(0)
        graph.withEngine { it.save(reminder("Training").copy(schedule = Schedule.Daily(latest), arrival = Arrival(home))) }

        val check = shadowOf(alarmManager).scheduledAlarms.single { it.operation?.let { op -> shadowOf(op).savedIntent }?.component?.className == PlaceCheckReceiver::class.java.name }
        assertTrue("exact while idle", check.isAllowWhileIdle)
        val start = Instant.ofEpochMilli(check.triggerAtTime)
        assertEquals(0, start.atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).compareTo(latest.minusMinutes(90)))
        assertEquals(check.triggerAtTime, shadowOf(check.operation).savedIntent.getLongExtra(AndroidPlaceAlarms.EXTRA_AT, -1))
    }

    /**
     * Android reports a WLAN connection that already exists again whenever the app registers for
     * reports, so at every process start: the same connection is no arrival (Review-Lauf 4).
     */
    @Test
    fun theSameWlanConnectionReportedAgainIsNoArrival() {
        val log = File(context.filesDir, "diag/diag.log")
        fun report(netId: Int) = broadcast(
            Intent(AndroidWifi.ACTION_CONNECT).setClass(context, WifiReceiver::class.java)
                .putExtra(ConnectivityManager.EXTRA_NETWORK, ShadowNetwork.newInstance(netId)),
        )
        fun repeats() = if (log.exists()) log.readText().split("same connection as before").size - 1 else 0

        report(7)
        waitFor("first report handled") { log.exists() && log.readText().contains("wifi connected: no bssid") }
        assertEquals(0, repeats())
        report(7)
        assertEquals(1, repeats())
        report(8)
        assertEquals(1, repeats())
    }

    // --- Boot and time change -------------------------------------------------------------------

    @Test
    fun afterABootAMissedAlarmRingsAndTheAlarmIsSetAgain() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.PLANNED, Duration.ofMinutes(10))

        broadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(context, SystemReceiver::class.java))
        waitFor("rang after boot") { rang(o) }
        assertEquals(OccurrenceState.DUE, repo.occurrence(o)?.state)
        assertNotNull(alarmManager.nextAlarmClock)
    }

    @Test
    fun aForeignBroadcastIsIgnored() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.PLANNED, Duration.ofMinutes(10))

        broadcast(Intent("com.example.FAKE").setClass(context, SystemReceiver::class.java))
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()

        assertEquals(OccurrenceState.PLANNED, repo.occurrence(o)?.state)
        assertNull(alarmManager.nextAlarmClock)
    }
}
