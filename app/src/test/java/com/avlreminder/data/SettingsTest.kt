package com.avlreminder.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.DayTimes
import com.avlreminder.core.usecases.ReminderSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

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

    /** The backup (B2b) brings the settings back, but not the pause or the interface. */
    @Test
    fun aBackupRestoresTheSettingsButNotThePauseOrTheInterface() = runTest {
        val alert = AlertSettings(LocalTime.of(17, 0), LocalTime.of(22, 30), 3, DayTimes(LocalTime.of(19, 0), LocalTime.of(23, 30)))
        val overview = OverviewTimes(LocalTime.of(7, 0), null, OverviewTimes(LocalTime.of(9, 0), LocalTime.of(21, 0)))
        settings.setAlert(alert)
        settings.setOverview(overview)
        settings.setTheme(ThemeMode.DARK)
        settings.setReminderSort(ReminderSort.NAME)
        settings.setDwellMinutes(10)
        val backup = settings.backup()

        settings.setAlert(AlertSettings())
        settings.setOverview(OverviewTimes())
        settings.setTheme(ThemeMode.LIGHT)
        settings.setReminderSort(ReminderSort.NEXT_DUE)
        settings.setDwellMinutes(1)
        settings.setClaudeAccess(true)
        settings.setAlarmsPaused(now.plus(Duration.ofHours(2)), now)
        settings.restore(backup)

        assertEquals(alert.copy(alarmsPausedUntil = now.plus(Duration.ofHours(2)), alarmsPausedFrom = now), settings.alert.first())
        assertEquals(overview, settings.overview.first())
        assertEquals(ThemeMode.DARK, settings.theme.first())
        assertEquals(ReminderSort.NAME, settings.reminderSort.first())
        assertEquals(10, settings.dwellMinutes.first())
        assertTrue(settings.claudeAccess.first())
        settings.setAlarmsPaused(null, now)
    }
}
