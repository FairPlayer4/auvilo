package com.avlreminder.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Anf. 7.28 PS12, playstore.md P4: battery optimization is recommended, without Android's direct dialog. */
@RunWith(AndroidJUnit4::class)
class BatteryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Play allows the direct dialog only for a few kinds of apps; an alarm app is not one of them.
    @Test
    fun theAppDoesNotAskForTheDirectDialog() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue("manifest read", "android.permission.SCHEDULE_EXACT_ALARM" in requested)
        assertFalse("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in requested)
    }

    @Test
    fun theButtonOpensTheAppInfoOfThisApp() {
        val intent = Battery.settings(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun exemptFollowsAndroid() {
        val power = shadowOf(context.getSystemService(PowerManager::class.java))
        power.setIgnoringBatteryOptimizations(context.packageName, false)
        assertFalse(Battery.exempt(context))
        power.setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(Battery.exempt(context))
    }
}
