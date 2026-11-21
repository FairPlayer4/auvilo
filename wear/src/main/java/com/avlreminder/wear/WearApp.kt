package com.avlreminder.wear

import android.app.Application
import android.content.Context
import java.time.Clock

/** The watch app (V1.4a, design.md 17); the tests replace the way to the phone and the clock. */
open class WearApp : Application() {
    lateinit var graph: WearGraph
        private set

    protected open fun phoneLink(): PhoneLink = PlayPhoneLink(this)

    protected open fun clock(): Clock = Clock.systemDefaultZone()

    override fun onCreate() {
        super.onCreate()
        graph = WearGraph(this, phoneLink(), clock())
        WearNotifications.channels(this)
    }
}

val Context.wear: WearGraph get() = (applicationContext as WearApp).graph
