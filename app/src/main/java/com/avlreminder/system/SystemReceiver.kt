package com.avlreminder.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avlreminder.alarm.ExactAlarms
import com.avlreminder.graph
import kotlinx.coroutines.launch

/**
 * Boot, time or time-zone change, app update: alarms are gone or wrong, rebuild them. Also when
 * "Wecker und Erinnerungen" was granted: the alarms planned without it were inexact (Anf. 7.28
 * PS10). Taking it back sends nothing; Android stops the app and drops its exact alarms, the next
 * start plans them inexact.
 */
class SystemReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        // The receiver is exported for the system broadcasts; ignore anything else.
        if (intent.action !in actions) return
        Diag.state(context, intent.action.orEmpty().substringAfterLast('.'))
        ExactAlarms.refresh(context)
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.rebuild(intent.action.orEmpty().substringAfterLast('.'), checkPlaces = true, overview = true)
            } finally {
                result.finish()
            }
        }
    }
}
