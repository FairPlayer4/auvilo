package com.avlreminder.spike

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** "Home" as a geofence (arrival/leaving) plus an on-demand "am I home right now?" check. */
object HomeLocation {
    private const val RADIUS_METERS = 150f
    private const val GEOFENCE_ID = "home"

    private const val PREFS = "home"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_STATE = "state"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun home(context: Context): Location? {
        val p = prefs(context)
        if (!p.contains(KEY_LAT)) return null
        return Location("home").apply {
            latitude = p.getFloat(KEY_LAT, 0f).toDouble()
            longitude = p.getFloat(KEY_LNG, 0f).toDouble()
        }
    }

    fun lastGeofenceState(context: Context): String = prefs(context).getString(KEY_STATE, "unbekannt")!!

    fun setLastGeofenceState(context: Context, state: String) {
        prefs(context).edit().putString(KEY_STATE, state).apply()
    }

    fun hasBackgroundLocation(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by the UI before calling
    fun setHomeToCurrentLocation(context: Context, onDone: () -> Unit) {
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    SpikeLog.log(context, "Zuhause setzen: kein Standort erhalten")
                } else {
                    prefs(context).edit()
                        .putFloat(KEY_LAT, location.latitude.toFloat())
                        .putFloat(KEY_LNG, location.longitude.toFloat())
                        .apply()
                    SpikeLog.log(context, "Zuhause gesetzt (Genauigkeit ${location.accuracy.toInt()} m)")
                    registerGeofence(context)
                }
                onDone()
            }
            .addOnFailureListener { SpikeLog.log(context, "Zuhause setzen fehlgeschlagen: $it"); onDone() }
    }

    /** Geofences are dropped on reboot and must be registered again. */
    @SuppressLint("MissingPermission")
    fun registerGeofence(context: Context) {
        val home = home(context) ?: return
        if (!hasBackgroundLocation(context)) {
            SpikeLog.log(context, "Geofence nicht registriert: Standort 'Immer erlauben' fehlt")
            return
        }
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(home.latitude, home.longitude, RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        LocationServices.getGeofencingClient(context)
            .addGeofences(request, geofencePendingIntent(context))
            .addOnSuccessListener { SpikeLog.log(context, "Geofence 'zuhause' registriert (${RADIUS_METERS.toInt()} m)") }
            .addOnFailureListener { SpikeLog.log(context, "Geofence-Registrierung fehlgeschlagen: $it") }
    }

    /** The 19:00 rule: at home now → reminder, otherwise the day's training is skipped. */
    @SuppressLint("MissingPermission")
    fun checkAtHome(context: Context, pending: BroadcastReceiver.PendingResult) {
        val home = home(context)
        if (home == null || !hasBackgroundLocation(context)) {
            SpikeLog.log(context, "Zuhause-Prüfung übersprungen (Zuhause gesetzt=${home != null}, Hintergrund-Standort=${hasBackgroundLocation(context)})")
            pending.finish()
            return
        }
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnCompleteListener { task ->
                val location = if (task.isSuccessful) task.result else null
                if (location == null) {
                    SpikeLog.log(context, "Zuhause-Prüfung: kein Standort (${task.exception})")
                } else {
                    val distance = location.distanceTo(home).toInt()
                    val atHome = distance <= RADIUS_METERS
                    SpikeLog.log(
                        context,
                        "Zuhause-Prüfung: ${if (atHome) "ZUHAUSE" else "NICHT zuhause → Training entfällt"} " +
                            "(Distanz $distance m, Genauigkeit ${location.accuracy.toInt()} m, " +
                            "Geofence-Status ${lastGeofenceState(context)})",
                    )
                }
                pending.finish()
            }
    }

    private fun geofencePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, GeofenceReceiver::class.java),
            // Play services fills in the event, so the intent must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
}
