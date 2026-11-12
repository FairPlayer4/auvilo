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
import androidx.annotation.VisibleForTesting
import com.avlreminder.core.model.Place
import com.avlreminder.core.ports.GeofencePort
import com.avlreminder.AppGraph
import com.avlreminder.graph
import com.avlreminder.system.Diag
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
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
    @SuppressLint("MissingPermission") // checked right before
    override fun watch(places: List<Place>) {
        val wanted = wanted(places, dwellMinutes())
        if (wanted == registered) return
        val client = LocationServices.getGeofencingClient(context)
        client.removeGeofences(pending(context))
        registered = null
        if (places.isEmpty()) return
        if (!context.hasBackgroundLocation()) {
            Log.w(TAG, "Geofences nicht registriert: Standort „Immer erlauben“ fehlt")
            Diag.log("geofences not registered: background location missing")
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
            .addOnFailureListener {
                Log.w(TAG, "Geofence-Registrierung fehlgeschlagen", it)
                Diag.log("geofence registration failed: ${it.message}")
            }
    }

    companion object {
        /**
         * What is registered in this process; geofences are gone after a reboot, and so is this.
         * Process-wide, so the receiver can [forget] it.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        @Volatile internal var registered: Pair<List<Place>, Int>? = null

        /** What [watch] registers: only the circles count; a new WLAN of a place needs no new geofence. */
        internal fun wanted(places: List<Place>, dwellMinutes: Int): Pair<List<Place>, Int> =
            places.map { it.copy(wifis = emptyList()) }.sortedBy { it.id } to dwellMinutes

        /**
         * Android dropped the geofences (GEOFENCE_NOT_AVAILABLE, e.g. location switched off):
         * the next sync registers them again (design.md 6).
         */
        fun forget() {
            registered = null
        }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        // Play services fills in the event, so the intent must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

/**
 * "Am I there right now?" from one fresh coarse position, the fallback at the latest time when the
 * WLAN is unclear (7.29 OZ2). Clearly inside → true, clearly outside → false; no permission, no
 * fix in time or a fix too inaccurate to tell → null (unknown).
 */
class AndroidLocation(private val context: Context) {
    suspend fun isAt(place: Place): Boolean? {
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
        val error = event.errorCode.takeIf { event.hasError() }
        if (error != null) {
            Log.w(TAG, "Geofence-Fehler $error")
            Diag.log("geofence error $error")
        } else {
            Diag.log("geofence transition=${event.geofenceTransition}")
        }
        val meaning = meaning(error, event.geofenceTransition, event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() })
        if (meaning == GeofenceMeaning.Ignore) return
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                when (meaning) {
                    GeofenceMeaning.Lost -> lost(graph)
                    is GeofenceMeaning.Arrived -> arrived(graph, meaning.placeIds)
                    GeofenceMeaning.Ignore -> Unit
                }
            } finally {
                result.finish()
            }
        }
    }

    /** What a geofence event asks for. */
    sealed interface GeofenceMeaning {
        data class Arrived(val placeIds: List<Long>) : GeofenceMeaning
        /** Android dropped the geofences, e.g. location switched off: register them again. */
        data object Lost : GeofenceMeaning
        data object Ignore : GeofenceMeaning
    }

    companion object {
        /** The decision on an event, apart from Android so it is tested (Review-Lauf 4, A6). */
        fun meaning(error: Int?, transition: Int, placeIds: List<Long>): GeofenceMeaning = when {
            error == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> GeofenceMeaning.Lost
            error != null -> GeofenceMeaning.Ignore
            transition == Geofence.GEOFENCE_TRANSITION_DWELL && placeIds.isNotEmpty() -> GeofenceMeaning.Arrived(placeIds)
            else -> GeofenceMeaning.Ignore
        }

        /**
         * The geofences are gone: forget what was registered, so the sync registers them again
         * right away; that fails as long as the location stays off, then the next sync tries.
         */
        suspend fun lost(graph: AppGraph) {
            AndroidGeofences.forget()
            graph.syncAndRing()
        }

        /**
         * Stayed long enough at [placeIds]: their reminders ring now. The log says which ones and
         * for which time, so an arrival that rang too early can be traced back.
         */
        suspend fun arrived(graph: AppGraph, placeIds: List<Long>) {
            placeIds.forEach { id ->
                val rings = graph.withEngine { it.onArrival(id) }
                Diag.log("arrival place=$id → ${rings.size} ring(s)" + rings.joinToString("") { " \"${it.reminder.title}\" due=${it.occurrence.dueAt}" })
            }
        }
    }
}
