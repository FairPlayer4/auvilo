package com.avlreminder.wear

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchCheck
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.wear.Fixtures.app
import com.avlreminder.wear.Fixtures.running
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Nachrichten vom Handy durch den echten Dienst der Uhr (design.md 17). */
@Config(sdk = [34, 36])
@RunWith(AndroidJUnit4::class)
class PhoneListenerTest {
    private val service = Robolectric.buildService(PhoneListenerService::class.java).create().get()
    private val notifications = app.getSystemService(NotificationManager::class.java)

    private fun message(path: String, data: ByteArray) = object : MessageEvent {
        override fun getRequestId() = 1
        override fun getPath() = path
        override fun getData() = data
        override fun getSourceNodeId() = FakePhoneLink.PHONE
    }

    private fun node(nearby: Boolean) = object : Node {
        override fun getId() = FakePhoneLink.PHONE
        override fun getDisplayName() = "Handy"
        override fun isNearby() = nearby
    }

    private fun capability(name: String, vararg nodes: Node) = object : CapabilityInfo {
        override fun getName() = name
        override fun getNodes(): Set<Node> = nodes.toSet()
    }

    @Test
    fun `der Dienst ist fuer Nachrichten und Faehigkeit des Handys angemeldet`() {
        listOf(
            "com.google.android.gms.wearable.MESSAGE_RECEIVED" to "wear://phone${Watch.PATH_STATE}",
            "com.google.android.gms.wearable.CAPABILITY_CHANGED" to "wear://phone/${Watch.CAPABILITY_PHONE}",
        ).forEach { (action, uri) ->
            val found = app.packageManager.queryIntentServices(Intent(action, Uri.parse(uri)).setPackage(app.packageName), 0)
            assertEquals(action, PhoneListenerService::class.java.name, found.single().serviceInfo.name)
        }
    }

    @Test
    fun `der Zustand wird gezeigt, behalten und uebersteht einen Neustart`() {
        service.onMessageReceived(message(Watch.PATH_STATE, WatchCodec.encode(running)))
        val shown = app.graph.shown.value!!
        assertEquals(running, shown.state)
        assertEquals(app.clock.now, shown.receivedAt)
        assertTrue(app.graph.connected.value)

        // Unreadable: the last state stays.
        service.onMessageReceived(message(Watch.PATH_STATE, byteArrayOf(42)))
        assertEquals(running, app.graph.shown.value!!.state)

        // The app process restarts: the state is read back, the connection is looked at anew.
        val again = WearGraph(app, app.link, app.clock)
        assertEquals(shown, again.shown.value)
        assertFalse(again.connected.value)
    }

    @Test
    fun `laufende Sitzung als laufende Aktivitaet, ohne Sitzung weg`() {
        service.onMessageReceived(message(Watch.PATH_STATE, WatchCodec.encode(running)))
        val ongoing = shadowOf(notifications).getNotification(WearNotifications.SESSION_ID)
        assertNotNull(ongoing)
        assertEquals("Training", ongoing.extras.getString("android.title"))
        assertEquals(WearNotifications.CHANNEL_SESSION, ongoing.channelId)
        assertTrue(ongoing.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)

        service.onMessageReceived(message(Watch.PATH_STATE, WatchCodec.encode(Fixtures.nextOnly)))
        assertNull(shadowOf(notifications).getNotification(WearNotifications.SESSION_ID))
    }

    @Test
    fun `eine Nachfrage vibriert, still bleibt still, ihr Ende nimmt sie weg`() {
        service.onMessageReceived(message(Watch.PATH_CHECK, WatchCodec.encode(WatchCheck(7, "Training", audible = true, final = false))))
        val loud = shadowOf(notifications).getNotification(WearNotifications.checkId(7))
        assertEquals(WearNotifications.CHANNEL_CHECK, loud.channelId)
        assertEquals("Noch dran?", loud.extras.getString("android.text"))
        val channel = notifications.getNotificationChannel(WearNotifications.CHANNEL_CHECK)
        assertTrue(channel.shouldVibrate())
        assertTrue(channel.importance >= NotificationManager.IMPORTANCE_HIGH)

        service.onMessageReceived(message(Watch.PATH_CHECK, WatchCodec.encode(WatchCheck(8, "Lernen", audible = false, final = true))))
        val quiet = shadowOf(notifications).getNotification(WearNotifications.checkId(8))
        assertEquals(WearNotifications.CHANNEL_CHECK_QUIET, quiet.channelId)
        assertFalse(notifications.getNotificationChannel(WearNotifications.CHANNEL_CHECK_QUIET).shouldVibrate())

        service.onMessageReceived(message(Watch.PATH_CHECK_END, WatchCodec.encodeId(7)))
        assertNull(shadowOf(notifications).getNotification(WearNotifications.checkId(7)))
        assertNotNull(shadowOf(notifications).getNotification(WearNotifications.checkId(8)))
    }

    @Test
    fun `das Handy kommt in die Naehe oder geht weg`() {
        service.onCapabilityChanged(capability(Watch.CAPABILITY_PHONE, node(nearby = true)))
        assertTrue(app.graph.connected.value)
        // Only over the internet counts as not connected (Anf. 7.14 G9b).
        service.onCapabilityChanged(capability(Watch.CAPABILITY_PHONE, node(nearby = false)))
        assertFalse(app.graph.connected.value)
        service.onCapabilityChanged(capability(Watch.CAPABILITY_PHONE, node(nearby = true)))
        service.onCapabilityChanged(capability(Watch.CAPABILITY_PHONE))
        assertFalse(app.graph.connected.value)
        service.onCapabilityChanged(capability("other", node(nearby = true)))
        assertFalse(app.graph.connected.value)
        assertEquals(FakePhoneLink.PHONE, PlayPhoneLink.nearby(listOf(node(nearby = false), node(nearby = true))))
        assertNull(PlayPhoneLink.nearby(listOf(node(nearby = false))))
    }
}
