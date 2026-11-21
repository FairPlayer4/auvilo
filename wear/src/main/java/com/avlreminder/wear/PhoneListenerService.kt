package com.avlreminder.wear

import android.content.Context
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchCodec
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Messages of the phone (design.md 17): the state, a check that rings and its end, the answer to a
 * button. Play services start the service only for them; the watch app has no service of its own
 * and no alarm (battery).
 */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) = PhoneMessages.received(applicationContext, event.path, event.data)

    /** The phone came near or went away: the buttons are free or locked. */
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != Watch.CAPABILITY_PHONE) return
        applicationContext.wear.connected(PlayPhoneLink.nearby(info.nodes) != null)
    }
}

/** What [PhoneListenerService] does with a message. */
object PhoneMessages {
    fun received(context: Context, path: String, data: ByteArray) {
        val graph = context.wear
        when (path) {
            Watch.PATH_STATE -> graph.received(data)?.let { shown ->
                WearNotifications.session(context, shown.state)
                SessionTileService.update(context)
            }
            Watch.PATH_CHECK -> WatchCodec.decodeCheck(data)?.let { WearNotifications.check(context, it) }
            Watch.PATH_CHECK_END -> WatchCodec.decodeId(data)?.let { WearNotifications.endCheck(context, it) }
            Watch.PATH_RESULT -> graph.result(data)
        }
    }
}
