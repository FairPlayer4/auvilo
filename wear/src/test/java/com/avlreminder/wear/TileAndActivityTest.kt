package com.avlreminder.wear

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.DeviceParametersBuilders
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchAction
import com.avlreminder.wear.Fixtures.app
import com.avlreminder.wear.Fixtures.receive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId

/** Die Kachel (G8) und der Weg von ihr in die App, durch die echte Activity (design.md 17). */
@Config(sdk = [34, 36])
@RunWith(AndroidJUnit4::class)
class TileAndActivityTest {
    private val device = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192).setScreenHeightDp(192).setScreenDensity(2f)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .build()

    @Before
    fun setUp() = app.link.reset()

    private fun tileText(state: com.avlreminder.core.usecases.WatchState?): String =
        SessionTileService.tile(app, state, app.clock.now, ZoneId.systemDefault(), device).tileTimeline!!.toString()

    @Test
    fun `Kachel – laufende Sitzung mit ihrer Zeit, die die Uhr selbst zaehlt`() {
        val tile = tileText(Fixtures.running)
        assertTrue(tile, "Training" in tile)
        assertTrue(tile, "18 min" in tile) // 17:20 since 17:02, the fallback without dynamic time
        assertTrue(tile, "platform" in tile.lowercase() || "duration" in tile.lowercase())
    }

    @Test
    fun `Kachel – Ich starte jetzt fuer den naechsten mit Sitzung`() {
        val tile = tileText(Fixtures.startable)
        assertTrue(tile, "Aufräumen" in tile)
        assertTrue(tile, "Ich starte jetzt" in tile)
        assertTrue(tile, WatchActivity::class.java.name in tile)
        assertTrue(tile, WatchActivity.EXTRA_START in tile)
    }

    @Test
    fun `Kachel – sonst nur der naechste Reminder, ohne Starten`() {
        val tile = tileText(Fixtures.nextOnly)
        assertTrue(tile, "Katzenbrunnen" in tile)
        assertFalse(tile, "Ich starte jetzt" in tile)
        assertFalse(tile, WatchActivity.EXTRA_START in tile)
        assertTrue(tileText(null), "Noch kein Stand vom Handy" in tileText(null))
    }

    private fun launch(start: Long?): ActivityScenario<WatchActivity> {
        val intent = Intent(app, WatchActivity::class.java)
        start?.let { intent.putExtra(WatchActivity.EXTRA_START, it) }
        return ActivityScenario.launch<WatchActivity>(intent)
    }

    private fun waitFor(what: String, done: () -> Boolean) {
        repeat(250) {
            if (done()) return
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test
    fun `von der Kachel gestartet – einmal, auch nach Neuaufbau`() {
        receive(Fixtures.startable)
        launch(start = 9).use { scenario ->
            waitFor("start sent") { app.link.commands().isNotEmpty() }
            assertEquals(WatchAction.START to 9L, app.link.commands().single().let { it.action to it.occurrenceId })
            scenario.recreate()
            ShadowLooper.idleMainLooper()
            Thread.sleep(100)
            ShadowLooper.idleMainLooper()
            assertEquals(1, app.link.commands().size)
        }
    }

    @Test
    fun `beim Oeffnen fragt die Uhr nach dem Stand und nach der Erlaubnis fuer Benachrichtigungen`() {
        launch(start = null).use { scenario ->
            waitFor("hello sent") { app.link.sent.any { it.path == Watch.PATH_HELLO } }
            scenario.onActivity { activity ->
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, shadowOf(activity).lastRequestedPermission?.requestedPermissions?.single())
            }
            // While the system asks: a rebuild does not ask a second time, nothing is started.
            scenario.recreate()
            scenario.onActivity { assertEquals(null, shadowOf(it).lastRequestedPermission) }
            assertTrue(app.link.commands().isEmpty())
        }
    }
}
