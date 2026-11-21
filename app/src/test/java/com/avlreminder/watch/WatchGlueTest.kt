package com.avlreminder.watch

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.StartedAvlApp
import com.avlreminder.TestClock
import com.avlreminder.alarm.AlarmInfo
import com.avlreminder.alarm.Notifications
import com.avlreminder.alarm.SessionCheck
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchAction
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.core.usecases.WatchCommand
import com.avlreminder.core.usecases.WatchResult
import com.avlreminder.core.usecases.WatchScreen
import com.avlreminder.graph
import com.avlreminder.settle
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.time.LocalTime

/**
 * Die Uhr-App auf der Seite des Handys (V1.4a, design.md 17): Befehle durch den echten Dienst,
 * der Zustand nur bei Änderung und nur an Uhren in der Nähe, keine Spiegelung der Nachfrage.
 */
@Config(sdk = [34, 37])
@RunWith(AndroidJUnit4::class)
class WatchGlueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = context.graph
    private val repo = graph.repository
    private val link = (context as StartedAvlApp).watchLink
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val service = Robolectric.buildService(WatchCommandService::class.java).create().get()

    @Before
    fun setUp() {
        TestClock.at()
        ShadowLooper.idleMainLooper()
        link.watches = listOf(WATCH)
        link.fails = false
        runBlocking { graph.watch.push(force = true) }
        link.sent.clear()
    }

    @After
    fun tearDown() {
        link.watches = emptyList()
        runBlocking { graph.watch.push() }
        TestClock.reset()
    }

    private suspend fun running(minutesAgo: Long = 20, minimum: Boolean = true): Long {
        val now = TestClock.now()
        val id = repo.saveReminder(
            Reminder(
                title = "Training", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = now, character = Character.DOCUMENT,
                session = SessionSpec(goalMinutes = 45, minimumMinutes = if (minimum) 10 else null),
            ),
        )
        return repo.saveOccurrence(
            Occurrence(
                reminderId = id, dueAt = now.minus(Duration.ofMinutes(minutesAgo)), state = OccurrenceState.RUNNING,
                startedAt = now.minus(Duration.ofMinutes(minutesAgo)), nextCheckAt = now.plus(Duration.ofMinutes(10)),
            ),
        )
    }

    private fun message(path: String, data: ByteArray, from: String = WATCH) = object : MessageEvent {
        override fun getRequestId() = 1
        override fun getPath() = path
        override fun getData() = data
        override fun getSourceNodeId() = from
    }

    /** A button of the watch, through the real service; returns its answer. */
    private fun press(occurrenceId: Long, action: WatchAction, from: String = WATCH): WatchResult? {
        link.sent.clear()
        service.onMessageReceived(message(Watch.PATH_ACT, WatchCodec.encode(WatchCommand(77, occurrenceId, action)), from))
        graph.settle()
        return link.sentTo(Watch.PATH_RESULT).singleOrNull()?.let { WatchCodec.decodeResult(it.data) }
    }

    private fun lastState() = link.sentTo(Watch.PATH_STATE).lastOrNull()?.let { WatchCodec.decodeState(it.data) }

    // --- Befehle ---------------------------------------------------------------------------------

    @Test
    fun `der Dienst ist fuer Nachrichten und Faehigkeiten der Uhr angemeldet`() {
        val pm = context.packageManager
        listOf(
            "com.google.android.gms.wearable.MESSAGE_RECEIVED" to "wear://$WATCH${Watch.PATH_ACT}",
            "com.google.android.gms.wearable.CAPABILITY_CHANGED" to "wear://$WATCH/${Watch.CAPABILITY_WATCH}",
        ).forEach { (action, uri) ->
            val found = pm.queryIntentServices(Intent(action, Uri.parse(uri)).setPackage(context.packageName), 0)
            assertEquals(action, WatchCommandService::class.java.name, found.single().serviceInfo.name)
        }
    }

    @Test
    fun `Fertig von der Uhr beendet die Sitzung, die Uhr bekommt Antwort und neuen Zustand`() = runBlocking {
        val o = running(minutesAgo = 50)
        assertEquals(WatchResult(77, true), press(o, WatchAction.DONE))
        assertEquals(OccurrenceState.DONE, repo.occurrence(o)!!.state)
        assertEquals(Resolution.GOAL, repo.occurrence(o)!!.resolution)
        assertTrue(lastState()!!.sessions.isEmpty())
    }

    @Test
    fun `jeder Knopf landet in der Engine`() = runBlocking {
        data class Fall(val action: WatchAction, val state: OccurrenceState, val resolution: Resolution?)
        listOf(
            Fall(WatchAction.STILL_ON, OccurrenceState.RUNNING, null),
            Fall(WatchAction.MINIMUM, OccurrenceState.DONE, Resolution.MINIMUM),
            Fall(WatchAction.ABORT, OccurrenceState.CLOSED, Resolution.ABORTED),
        ).forEach { f ->
            val o = running()
            assertEquals(f.action.name, true, press(o, f.action)!!.ok)
            assertEquals(f.action.name, f.state, repo.occurrence(o)!!.state)
            assertEquals(f.action.name, f.resolution, repo.occurrence(o)!!.resolution)
        }
    }

    @Test
    fun `Ich starte jetzt von der Uhr`() = runBlocking {
        val id = repo.saveReminder(
            Reminder(title = "Lernen", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = TestClock.now(), session = SessionSpec()),
        )
        val o = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = TestClock.now().minusSeconds(60), state = OccurrenceState.DUE))
        assertTrue(press(o, WatchAction.START)!!.ok)
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        assertEquals(listOf(o), lastState()!!.sessions.map { it.occurrenceId })
    }

    @Test
    fun `Abbruch von der Uhr laesst sich dort zuruecknehmen`() = runBlocking {
        val o = running()
        val started = repo.occurrence(o)!!.startedAt
        assertTrue(press(o, WatchAction.ABORT)!!.ok)
        val undo = press(o, WatchAction.REOPEN)!!
        assertTrue(undo.ok)
        assertNotNull(undo.message)
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        assertEquals(started, repo.occurrence(o)!!.startedAt)
    }

    @Test
    fun `ein Knopf, den die Engine ablehnt, sagt warum`() = runBlocking {
        val o = running(minimum = false)
        // Without a minimum there is no Teilschritt; the session runs on.
        assertEquals(WatchResult(77, false, WatchScreen.NOT_POSSIBLE), press(o, WatchAction.MINIMUM))
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        // Ended on the phone meanwhile, or never there.
        assertTrue(press(o, WatchAction.ABORT)!!.ok)
        assertEquals(WatchResult(77, false, WatchScreen.NOT_POSSIBLE), press(o, WatchAction.STILL_ON))
        assertFalse(press(999_999, WatchAction.DONE)!!.ok)
    }

    @Test
    fun `von einer Uhr, die nicht in der Naehe ist, wird nichts genommen`() = runBlocking {
        val o = running()
        assertNull(press(o, WatchAction.ABORT, from = "far"))
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        assertTrue(link.sent.isEmpty())
    }

    @Test
    fun `Unlesbares und Unbekanntes wird nicht ausgefuehrt`() = runBlocking {
        val o = running()
        service.onMessageReceived(message(Watch.PATH_ACT, byteArrayOf(9, 9, 9)))
        service.onMessageReceived(message("/avl/snooze", WatchCodec.encode(WatchCommand(1, o, WatchAction.ABORT))))
        graph.settle()
        assertEquals(OccurrenceState.RUNNING, repo.occurrence(o)!!.state)
        assertTrue(link.sent.isEmpty())
    }

    // --- Zustand -------------------------------------------------------------------------------

    @Test
    fun `hallo bekommt den Zustand auch unveraendert, sonst nur bei Aenderung`() = runBlocking {
        val o = running()
        graph.watch.push()
        assertEquals(listOf(o), lastState()!!.sessions.map { it.occurrenceId })
        link.sent.clear()
        graph.watch.push()
        assertTrue("unchanged: nothing sent", link.sent.isEmpty())
        service.onMessageReceived(message(Watch.PATH_HELLO, ByteArray(0)))
        assertEquals(listOf(o), lastState()!!.sessions.map { it.occurrenceId })
    }

    @Test
    fun `kommt die Uhr in die Naehe, bekommt sie den Zustand`() = runBlocking {
        running()
        link.sent.clear()
        service.onCapabilityChanged(capability("anything_else"))
        assertTrue(link.sent.isEmpty())
        service.onCapabilityChanged(capability(Watch.CAPABILITY_WATCH))
        assertEquals(WATCH, link.sentTo(Watch.PATH_STATE).single().nodeId)
    }

    @Test
    fun `nur Uhren, die direkt verbunden sind, zaehlen als in der Naehe`() {
        fun node(id: String, nearby: Boolean) = object : Node {
            override fun getId() = id
            override fun getDisplayName() = id
            override fun isNearby() = nearby
        }
        assertEquals(listOf("bt"), PlayWatchLink.nearby(listOf(node("bt", true), node("cloud", false))))
        assertTrue(PlayWatchLink.nearby(emptyList()).isEmpty())
    }

    @Test
    fun `ohne Uhr in der Naehe geht nichts raus, nach einem Fehler wird neu gesendet`() = runBlocking {
        running()
        link.watches = emptyList()
        graph.watch.push(force = true)
        assertTrue(link.sent.isEmpty())
        assertFalse(graph.watch.nearby)

        link.watches = listOf(WATCH)
        link.fails = true
        graph.watch.push()
        link.fails = false
        graph.watch.push()
        assertEquals(1, link.sentTo(Watch.PATH_STATE).size)
    }

    @Test
    fun `eine Nachfrage geht an die Uhr, ihr Ende auch`() = runBlocking {
        val o = running()
        TestClock.advance(Duration.ofMinutes(11))
        graph.withEngine { it.onAlarm(o) }
        graph.settle()
        val check = WatchCodec.decodeCheck(link.sentTo(Watch.PATH_CHECK).single().data)!!
        assertEquals(o, check.occurrenceId)
        assertEquals("Training", check.title)

        assertTrue(press(o, WatchAction.DONE)!!.ok)
        assertEquals(o, WatchCodec.decodeId(link.sentTo(Watch.PATH_CHECK_END).single().data))
    }

    // --- Kein Doppel ---------------------------------------------------------------------------

    private fun check(id: Long) = AlarmInfo(id, "Training", null, quickDone = true, session = SessionCheck(TestClock.now(), false, true, SessionAnswer.STILL_ON))

    private fun isLocalOnly(n: Notification) = n.flags and Notification.FLAG_LOCAL_ONLY != 0

    @Test
    fun `mit der Uhr-App in der Naehe wird die Nachfrage nicht gespiegelt`() = runBlocking {
        Notifications.show(context, check(41), quiet = false)
        assertTrue(isLocalOnly(shadowOf(notifications).getNotification(Notifications.id(41))))
        Notifications.showMirror(context, check(41))
        assertEquals(1, shadowOf(notifications).allNotifications.count { it.extras.getString(Notification.EXTRA_TITLE) == "Training" })

        // A reminder without session still reaches the watch over the bridge.
        Notifications.show(context, AlarmInfo(42, "Katzenbrunnen", null, quickDone = true), quiet = false)
        assertFalse(isLocalOnly(shadowOf(notifications).getNotification(Notifications.id(42))))
    }

    @Test
    fun `ohne Uhr-App in der Naehe wird die Nachfrage gespiegelt wie bisher`() = runBlocking {
        link.watches = emptyList()
        graph.watch.push()
        Notifications.show(context, check(43), quiet = false)
        assertFalse(isLocalOnly(shadowOf(notifications).getNotification(Notifications.id(43))))
        Notifications.showMirror(context, check(43))
        assertEquals(2, shadowOf(notifications).allNotifications.count { it.extras.getString(Notification.EXTRA_TITLE) == "Training" })
    }

    private fun capability(name: String) = object : CapabilityInfo {
        override fun getName() = name
        override fun getNodes(): Set<Node> = emptySet()
    }

    private companion object {
        const val WATCH = "watch-1"
    }
}
