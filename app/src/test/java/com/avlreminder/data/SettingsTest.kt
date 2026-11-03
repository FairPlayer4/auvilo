package com.avlreminder.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SettingsTest {
    private val settings = Settings(ApplicationProvider.getApplicationContext<Context>())
    private val now = Instant.parse("2026-11-03T20:00:00Z")

    /** The start of a pause decides whether it excuses an open occurrence (PS7). */
    @Test
    fun aPauseKeepsItsStartWhenExtendedAndALaterOneStartsAnew() = runTest {
        settings.setAlarmsPaused(now.plus(Duration.ofHours(1)), now)
        settings.setAlarmsPaused(now.plus(Duration.ofHours(3)), now.plus(Duration.ofMinutes(30)))
        var alert = settings.alert.first()
        assertEquals(now, alert.alarmsPausedFrom)
        assertEquals(now.plus(Duration.ofHours(3)), alert.alarmsPausedUntil)

        // The first pause is over: a new one starts now.
        val later = now.plus(Duration.ofHours(4))
        settings.setAlarmsPaused(later.plus(Duration.ofHours(1)), later)
        assertEquals(later, settings.alert.first().alarmsPausedFrom)

        settings.setAlarmsPaused(null, later)
        alert = settings.alert.first()
        assertNull(alert.alarmsPausedUntil)
        assertNull(alert.alarmsPausedFrom)
    }
}
