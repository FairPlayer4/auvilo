package com.avlreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.rules.Action
import com.avlreminder.graph
import com.avlreminder.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** One AlarmManager alarm per open occurrence, told apart by the intent's data URI. */
class AndroidAlarmPort(private val context: Context) : AlarmPort {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(occurrenceId: Long, at: Instant, kind: AlarmKind) {
        val pending = pending(context, occurrenceId)
        when (kind) {
            AlarmKind.ALARM_CLOCK -> {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), pending)
            }
            AlarmKind.EXACT -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    override fun cancel(occurrenceId: Long) {
        alarmManager.cancel(pending(context, occurrenceId))
    }

    companion object {
        const val ACTION_ALARM = "com.avlreminder.ALARM"
        const val EXTRA_OCCURRENCE = "occurrence"

        fun pending(context: Context, occurrenceId: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_ALARM)
                .setData("avl://occurrence/$occurrenceId".toUri())
                .putExtra(EXTRA_OCCURRENCE, occurrenceId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** An alarm fired: let the engine decide what to show. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, -1)
        if (id < 0) return
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.withEngine { it.onAlarm(id) }?.let { Ringer.ring(context, it) }
            } finally {
                result.finish()
            }
        }
    }
}

/** Buttons in the notification: done (without photo) and snooze. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, -1)
        val action = when (intent.action) {
            ACTION_DONE -> Action.Complete(minimumOnly = false, proofProvided = false)
            ACTION_SNOOZE -> Action.Snooze(SNOOZE)
            else -> return
        }
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.withEngine { it.act(id, action) }
                Ringer.dismiss(context, id)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.avlreminder.DONE"
        const val ACTION_SNOOZE = "com.avlreminder.SNOOZE"
        val SNOOZE: Duration = Duration.ofMinutes(10)

        fun pending(context: Context, action: String, occurrenceId: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ActionReceiver::class.java)
                .setAction(action)
                .setData("avl://occurrence/$occurrenceId".toUri())
                .putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
