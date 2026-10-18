package com.avlreminder.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager alarms and geofences do not survive a reboot – restore both. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SpikeLog.log(context, "NEUSTART erkannt | ${SpikeLog.deviceState(context)}")
        AlarmScheduler.checkMissed(context, "nach Neustart")
        HomeLocation.registerGeofence(context)
    }
}
