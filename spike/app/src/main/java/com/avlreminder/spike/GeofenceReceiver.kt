package com.avlreminder.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Logs arrival/leaving and how old the triggering location fix was. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            SpikeLog.log(context, "Geofence-Fehler: Code ${event.errorCode}")
            return
        }
        val state = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "angekommen"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "verlassen"
            else -> "Übergang ${event.geofenceTransition}"
        }
        HomeLocation.setLastGeofenceState(context, state)
        val fix = event.triggeringLocation
        val fixAgeSeconds = fix?.let { (System.currentTimeMillis() - it.time) / 1000 }
        SpikeLog.log(
            context,
            "GEOFENCE zuhause: $state (Standort-Fix ${fixAgeSeconds}s alt, Genauigkeit ${fix?.accuracy?.toInt()} m) " +
                "| ${SpikeLog.deviceState(context)}",
        )
    }
}
