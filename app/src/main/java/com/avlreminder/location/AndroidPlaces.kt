package com.avlreminder.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import com.avlreminder.alarm.Ringer
import com.avlreminder.core.model.Place
import com.avlreminder.core.ports.GeofencePort
import com.avlreminder.core.ports.LocationPort
import com.avlreminder.graph
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "AvlPlaces"

fun Context.hasLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Geofences need "Immer erlauben"; without it arrivals are not noticed and the time alone counts. */
fun Context.hasBackgroundLocation() =
    hasLocation() && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * One DWELL geofence per watched place: driving past does not count, staying [dwellMinutes] does.
 * No initial trigger; being home already is covered by [AndroidLocation] where it matters.
 */
class AndroidGeofences(private val context: Context, private val dwellMinutes: () -> Int) : GeofencePort {
    /** What is registered in this process; geofences are gone after a reboot, and so is this. */
    @Volatile private var registered: Pair<List<Place>, Int>? = null

    @SuppressLint("MissingPermission") // checked right before
    override fun watch(places: List<Place>) {
        val wanted = places.sortedBy { it.id } to dwellMinutes()
        if (wanted == registered) return
        val client = LocationServices.getGeofencingClient(context)
        client.removeGeofences(pending(context))
        registered = null
        if (places.isEmpty()) return
        if (!context.hasBackgroundLocation()) {
            Log.w(TAG, "Geofences nicht registriert: Standort „Immer erlauben“ fehlt")
            return
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(
                places.map { p ->
                    Geofence.Builder()
                        .setRequestId(p.id.toString())
                        .setCircularRegion(p.lat, p.lon, p.radiusMeters.toFloat())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                        .setLoiteringDelay(wanted.second * 60_000)
                        .build()
                },
            )
            .build()
        client.addGeofences(request, pending(context))
            .addOnSuccessListener { registered = wanted }
            .addOnFailureListener { Log.w(TAG, "Geofence-Registrierung fehlgeschlagen", it) }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        // Play services fills in the event, so the intent must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

/**
 * "Am I there right now?" from one fresh position. Clearly inside → true, clearly outside → false;
 * no permission, no fix in time or a fix too inaccurate to tell → null (unknown).
 */
class AndroidLocation(private val context: Context) : LocationPort {
    override suspend fun isAt(place: Place): Boolean? {
        val here = current(Priority.PRIORITY_BALANCED_POWER_ACCURACY) ?: return null
        val distance = here.distanceTo(place.location())
        return when {
            distance <= place.radiusMeters -> true
            distance - here.accuracy > place.radiusMeters -> false
            else -> null
        }
    }

    /** A fresh position, or null without permission or after [TIMEOUT_MS]. */
    @SuppressLint("MissingPermission") // checked right before
    suspend fun current(priority: Int = Priority.PRIORITY_HIGH_ACCURACY): Location? {
        if (!context.hasLocation()) return null
        val cancel = CancellationTokenSource()
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { cancel.cancel() }
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(priority, cancel.token)
                    .addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
            }
        }
    }

    private fun Place.location() = Location("place").apply { latitude = lat; longitude = lon }

    companion object {
        /** Short enough for a broadcast receiver, which has about ten seconds. */
        const val TIMEOUT_MS = 8_000L
    }
}

/** Stayed at a watched place long enough: its reminders ring now. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence-Fehler ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                ids.forEach { id -> graph.withEngine { it.onArrival(id) }.forEach { Ringer.ring(context, it) } }
            } finally {
                result.finish()
            }
        }
    }
}
