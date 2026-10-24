package com.avlreminder.alarm

import android.content.Context
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.usecases.Ring

/** Turns the engine's [Ring] into something the user notices. */
object Ringer {
    fun ring(context: Context, ring: Ring) {
        val info = AlarmInfo.of(ring)
        when (ring.level) {
            AlarmLevel.SILENT -> Notifications.show(context, info, quiet = true)
            AlarmLevel.BANNER -> Notifications.show(context, info, quiet = false)
            AlarmLevel.FULLSCREEN -> AlarmService.start(context, info, loud = false)
            AlarmLevel.FULLSCREEN_LOUD -> AlarmService.start(context, info, loud = true)
        }
    }

    /** The user reacted: stop ringing and remove the notification. */
    fun dismiss(context: Context, occurrenceId: Long) {
        AlarmService.stop(context, occurrenceId)
        Notifications.cancel(context, occurrenceId)
    }
}
