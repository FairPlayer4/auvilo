package com.avlreminder.spike

import android.app.Application

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A process start without user action means the system had killed the app before.
        SpikeLog.log(this, "Prozess gestartet | ${SpikeLog.deviceState(this)}")
        Notifications.createChannels(this)
    }
}
