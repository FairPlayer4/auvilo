package com.avlreminder.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

/** Fired by AlarmManager. Measures the delay, continues the plan and starts the alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val plannedAt = intent.getLongExtra(AlarmScheduler.EXTRA_PLANNED_AT, 0)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: "?"
        val mode = intent.getStringExtra(AlarmScheduler.EXTRA_MODE)?.let(AlarmMode::valueOf)
            ?: AlarmMode.forTime(LocalTime.now())
        val delaySeconds = (System.currentTimeMillis() - plannedAt) / 1000

        SpikeLog.log(
            context,
            "ALARM '$label' geplant ${SpikeLog.formatTime(plannedAt)}, Verspätung ${delaySeconds}s, " +
                "Modus ${mode.name} | ${SpikeLog.deviceState(context)}",
        )

        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_FROM_PLAN, false)) {
            AlarmScheduler.scheduleNextPlanSlot(context)
        }

        if (mode == AlarmMode.SILENT) {
            Notifications.showSilent(context, label)
        } else {
            AlarmService.start(context, label, mode)
        }

        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_HOME_CHECK, false)) {
            HomeLocation.checkAtHome(context, goAsync())
        }
    }
}
