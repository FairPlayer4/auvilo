package com.avlreminder.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avlreminder.graph
import kotlinx.coroutines.launch

/** Boot, time or time-zone change, app update: alarms are gone or wrong, rebuild them. */
class SystemReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        // The receiver is exported for the system broadcasts; ignore anything else.
        if (intent.action !in actions) return
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.syncAndRing()
            } finally {
                result.finish()
            }
        }
    }
}
