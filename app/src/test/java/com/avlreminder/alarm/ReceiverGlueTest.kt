package com.avlreminder.alarm

import com.avlreminder.TestClock
import com.avlreminder.settle
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
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.graph
import android.net.ConnectivityManager
import com.avlreminder.location.AndroidPlaceAlarms
import com.avlreminder.location.AndroidWifi
import com.avlreminder.location.AndroidGeofences
import com.avlreminder.location.GeofenceReceiver
import com.avlreminder.location.GeofenceReceiver.GeofenceMeaning
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import kotlinx.coroutines.flow.first
import com.avlreminder.location.WifiReceiver
import org.robolectric.shadows.ShadowNetwork
import com.avlreminder.location.PlaceCheckReceiver
import com.avlreminder.system.SystemReceiver
import com.avlreminder.data.OverviewTimes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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
        TestClock.at()
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() = TestClock.reset()

    private fun reminder(title: String = "Katzenbrunnen", checklist: List<ChecklistItem> = emptyList()) = Reminder(
        title = title, schedule = Schedule.Daily(LocalTime.of(18, 0)), checklist = checklist, createdAt = TestClock.now(),
        character = Character.DOCUMENT,
    )

    private suspend fun occurrence(reminderId: Long, state: OccurrenceState, dueAgo: Duration): Long =
        repo.saveOccurrence(Occurrence(reminderId = reminderId, dueAt = TestClock.now().minus(dueAgo), state = state))

    private fun broadcast(intent: Intent) {
        context.sendBroadcast(intent)
        ShadowLooper.idleMainLooper()
    }

    private fun alarmFor(occurrenceId: Long) = Intent(context, AlarmReceiver::class.java)
        .putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId)

    /** The receivers work asynchronously: waits until [done] holds, at most ten seconds (the CI runner is slower). */
    private fun waitFor(what: String, done: suspend () -> Boolean) = runBlocking {
        repeat(500) {
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
        graph.settle()

        assertEquals(before, repo.occurrence(o))
        assertEquals(false, rang(o))
    }

    // --- Buttons in the notification ------------------------------------------------------------

    @Test
    fun snoozeFromTheNotificationPlansTheAlarmForLater() = runBlocking {
        val id = repo.saveReminder(reminder())
        val o = occurrence(id, OccurrenceState.DUE, Duration.ofMinutes(1))
        Notifications.show(context, AlarmInfo(o, "Katzenbrunnen", null, quickDone = true, snooze = listOf(10, 30)), quiet = false)

        // The button Kevin presses, not an intent built by the test (Review-Lauf 4, A2).
        val shown = shadowOf(notifications).getNotification(Notifications.id(o))
        assertEquals(listOf("Erledigt", "+10 min", "+30 min"), shown.actions.map { it.title.toString() })
        shown.actions.single { it.title == "+30 min" }.actionIntent.send()
        ShadowLooper.idleMainLooper()
        waitFor("snoozed") { repo.occurrence(o)?.state == OccurrenceState.PLANNED }

        val until = repo.occurrence(o)!!.currentAt
        val expected = TestClock.now().plus(Duration.ofMinutes(30))
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

    // --- Notice before and pause ----------------------------------------------------------------

    private suspend fun withPreNotice(): Long {
        val saved = graph.withEngine { it.save(reminder("Training").copy(preNotice = PreNotice.Before(60))) }
        return repo.openOccurrence((saved as SaveResult.Saved).id)!!.id
    }

    private fun preNoticeAlarm() = shadowOf(alarmManager).scheduledAlarms.single {
        it.operation?.let { op -> shadowOf(op).savedIntent }?.component?.className == PreNoticeReceiver::class.java.name
    }

    @Test
    fun theNoticeBeforeComesAnHourAheadAndOffersToMoveIt() = runBlocking {
        val o = withPreNotice()
        val alarm = preNoticeAlarm()
        assertEquals(repo.occurrence(o)!!.plannedAt.minus(Duration.ofHours(1)).toEpochMilli(), alarm.triggerAtTime)

        alarm.operation!!.send()
        ShadowLooper.idleMainLooper()
        graph.settle()

        val shown = notifications.activeNotifications.single { it.tag == "prenotice-$o" }.notification
        assertEquals("Heute 18:00: Training", shown.extras.getString("android.title"))
        val move = shown.actions.single()
        assertEquals("Verschieben …", move.title)
        val opens = shadowOf(move.actionIntent).savedIntent
        assertEquals(AlarmActivity::class.java.name, opens.component?.className)
        assertEquals(o, opens.getLongExtra("occurrence", -1))
        assertTrue(opens.getBooleanExtra("early", false))
    }

    @Test
    fun noNoticeBeforeForAnOccurrenceDoneInAdvance() = runBlocking {
        val o = withPreNotice()
        val alarm = preNoticeAlarm()
        repo.saveOccurrence(repo.occurrence(o)!!.copy(state = OccurrenceState.DONE, resolution = Resolution.GOAL, resolvedAt = TestClock.now()))

        alarm.operation!!.send()
        ShadowLooper.idleMainLooper()
        graph.settle()

        assertTrue(notifications.activeNotifications.none { it.tag == "prenotice-$o" })
    }

    @Test
    fun wiederEinschaltenInThePauseNotificationEndsThePause() = runBlocking {
        graph.setAlarmsPaused(TestClock.now().plus(Duration.ofHours(2)))
        graph.settle()
        val pause = notifications.activeNotifications.single { it.notification.channelId == "pause" }.notification
        assertTrue(graph.settings.alert.first().alarmsPaused(TestClock.now()))

        pause.actions.single { it.title == "Wieder einschalten" }.actionIntent.send()
        ShadowLooper.idleMainLooper()
        graph.settle()

        assertFalse(graph.settings.alert.first().alarmsPaused(TestClock.now()))
        assertTrue(notifications.activeNotifications.none { it.notification.channelId == "pause" })
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

    @Test
    fun aGeofenceEventMeansArrivalOnlyAfterStayingAndLossOnlyWhenAndroidDroppedThem() {
        val dwell = Geofence.GEOFENCE_TRANSITION_DWELL
        assertEquals(GeofenceMeaning.Arrived(listOf(3L)), GeofenceReceiver.meaning(null, dwell, listOf(3L)))
        // Driving past (enter/exit) does not count, nor a stay without a known place.
        assertEquals(GeofenceMeaning.Ignore, GeofenceReceiver.meaning(null, Geofence.GEOFENCE_TRANSITION_ENTER, listOf(3L)))
        assertEquals(GeofenceMeaning.Ignore, GeofenceReceiver.meaning(null, dwell, emptyList()))
        assertEquals(GeofenceMeaning.Lost, GeofenceReceiver.meaning(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE, -1, emptyList()))
        assertEquals(GeofenceMeaning.Ignore, GeofenceReceiver.meaning(GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES, -1, emptyList()))
    }

    /**
     * Android dropped the geofences (location switched off): the app must not keep believing they
     * are registered, or no arrival rings until the next app update (Review-Lauf 4, A6).
     */
    @Test
    fun lostGeofencesAreRegisteredAgainRightAway() = runBlocking {
        val home = graph.withEngine { it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5)) }
        training(home)
        // As if play services had confirmed the registration (Robolectric has none that would).
        AndroidGeofences.registered = AndroidGeofences.wanted(repo.places(), graph.settings.dwellMinutes.first())
        val log = File(context.filesDir, "diag/diag.log")
        fun attempts() = log.readText().split("geofences not registered").size - 1
        val before = attempts()

        GeofenceReceiver.lost(graph)

        assertNull(AndroidGeofences.registered)
        // Registering again was tried; here it fails for the missing background location.
        assertEquals(before + 1, attempts())
    }

    /**
     * In the arrival window the engine sets the check alarm of the place (design.md 6); when it
     * fires, the check runs and the log has its result without WLAN names or BSSIDs.
     */
    @Test
    fun theCheckAlarmOfAPlaceRunsTheCheckAndLogsItWithoutNames() = runBlocking {
        val bssid = "aa:bb:cc:00:00:01"
        val home = graph.withEngine {
            it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5, wifis = listOf(PlaceWifi(bssid, "Heimnetz"))))
        }
        // Due in an hour: the window (90 min before) is running, the next check comes within 20 min.
        val latest = TestClock.time().plusHours(1).withSecond(0).withNano(0)
        val saved = graph.withEngine {
            it.save(reminder("Training").copy(schedule = Schedule.Daily(latest), arrival = Arrival(home, WhenAway.DROP)))
        }
        assertTrue(saved.toString(), saved is SaveResult.Saved)
        val check = shadowOf(alarmManager).scheduledAlarms.single { it.operation?.let { op -> shadowOf(op).savedIntent }?.component?.className == PlaceCheckReceiver::class.java.name }
        assertTrue(Instant.ofEpochMilli(check.triggerAtTime) <= TestClock.now().plus(Duration.ofMinutes(20)))
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
        val latest = TestClock.time().plusHours(3).withSecond(0).withNano(0)
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
        graph.settle()

        assertEquals(OccurrenceState.PLANNED, repo.occurrence(o)?.state)
        assertNull(alarmManager.nextAlarmClock)
    }

    /**
     * Android delivers these only to receivers declared in the manifest; a missing filter is not
     * noticed until the time zone changes on a journey (Review-Lauf 4, A5).
     */
    // Opening the app inside the delivery window of today's overview must not move it to
    // tomorrow; only start, system events and new times plan it (Review-Lauf 6).
    @Test
    fun theAppInFrontLeavesTodaysOverviewPlanned() = runBlocking {
        fun overview() = shadowOf(alarmManager).scheduledAlarms.single {
            it.operation?.let { op -> shadowOf(op).savedIntent }?.component?.className == OverviewReceiver::class.java.name
        }
        TestClock.at(LocalTime.of(6, 55))
        graph.settings.setOverview(OverviewTimes(morning = LocalTime.of(7, 0), evening = null))
        graph.rebuild("overview times", overview = true)
        val today = overview().triggerAtTime
        assertEquals(TestClock.now().plus(Duration.ofMinutes(5)).toEpochMilli() / 60_000, today / 60_000)
        TestClock.advance(Duration.ofMinutes(8))
        graph.rebuild("app in front")
        assertEquals(today, overview().triggerAtTime)
    }

    @Test
    fun theManifestDeliversBootTimeAndTimeZoneChangesToTheSystemReceiver() {
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            val receivers = context.packageManager.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            // The widget library has its own receiver for app updates.
            assertTrue(action, SystemReceiver::class.java.name in receivers.map { it.activityInfo.name })
        }
    }
}
