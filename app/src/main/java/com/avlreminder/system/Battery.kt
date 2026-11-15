package com.avlreminder.system

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Battery optimization (Anf. 7.28 PS12, playstore.md P4). Recommended, not required: alarms come
 * over `setAlarmClock` and wake the phone from Doze anyway, but without the exemption Android may
 * hold the app back, so there is no guarantee. The app does not ask Android's direct dialog
 * (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, not allowed in the Play Store for an alarm app); it opens
 * its app info, where "Akku" → "Nicht eingeschränkt" sets the same exemption.
 */
object Battery {
    const val LABEL = "Akku „Nicht eingeschränkt“"
    const val WHY = "empfohlen: sonst kann Android die App bremsen und Alarme kommen ohne Garantie. " +
        "In der App-Info unter „Akku“ wählen"

    fun exempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun settings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}
