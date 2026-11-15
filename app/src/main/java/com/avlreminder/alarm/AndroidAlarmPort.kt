package com.avlreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.Evidence
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.graph
import com.avlreminder.system.Diag
import com.avlreminder.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** One AlarmManager alarm per open occurrence, told apart by the intent's data URI. */
class AndroidAlarmPort(private val context: Context) : AlarmPort {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(occurrenceId: Long, at: Instant, kind: AlarmKind) {
        Diag.log("alarm set occ=$occurrenceId at=$at kind=$kind")
        val pending = pending(context, occurrenceId, at)
        // Without "Wecker und Erinnerungen" both come inexact (Anf. 7.28 PS10).
        ExactAlarms.set(context, at, pending, "occ=$occurrenceId") {
            when (kind) {
                AlarmKind.ALARM_CLOCK -> {
                    val show = PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                    )
                    setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), pending)
                }
                AlarmKind.EXACT -> setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
            }
        }
    }

    override fun cancel(occurrenceId: Long) {
        alarmManager.cancel(pending(context, occurrenceId))
    }

    companion object {
        const val ACTION_ALARM = "com.avlreminder.ALARM"
        const val EXTRA_OCCURRENCE = "occurrence"
        /** The planned time, to log how late the alarm came. */
        const val EXTRA_AT = "at"

        fun pending(context: Context, occurrenceId: Long, at: Instant? = null): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_ALARM)
                .setData("avl://occurrence/$occurrenceId".toUri())
                .putExtra(EXTRA_OCCURRENCE, occurrenceId)
                .apply { at?.let { putExtra(EXTRA_AT, it.toEpochMilli()) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** An alarm fired: let the engine decide what to show. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, -1)
        if (id < 0) return
        val planned = intent.getLongExtra(AndroidAlarmPort.EXTRA_AT, -1)
        val late = if (planned < 0) "?" else "${(System.currentTimeMillis() - planned) / 1000}s"
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                val ring = graph.withEngine { it.onAlarm(id) }
                // Ended by this alarm (a session at its maximum, the end of the day): the engine ends its ringing (E17).
                Diag.log("alarm fired occ=$id late=$late → ${if (ring == null) "nothing (stale, planned again or day ended)" else "ring"}")
            } finally {
                result.finish()
            }
        }
    }
}

/** Buttons in the notification: done or the next checklist item (without photo, text or audio) and snooze. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, -1)
        if (intent.action == ACTION_REOPEN) {
            val result = goAsync()
            context.graph.scope.launch {
                try {
                    Undo.reopen(context, id)
                } finally {
                    result.finish()
                }
            }
            return
        }
        val action = when (intent.action) {
            ACTION_DONE -> Action.Complete(minimumOnly = false)
            ACTION_MINIMUM -> Action.Complete(minimumOnly = true)
            ACTION_START -> Action.Start
            ACTION_STILL_ON -> Action.StillOn
            ACTION_ABORT -> Action.Abort
            ACTION_SNOOZE -> Action.Snooze(Duration.ofMinutes(intent.getIntExtra(EXTRA_MINUTES, 10).toLong()))
            ACTION_CHECK -> Action.CheckItem(intent.getLongExtra(EXTRA_ITEM, -1), Evidence())
            else -> return
        }
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                val next = graph.withEngine { e ->
                    val acted = e.act(id, action)
                    Diag.log("notification action occ=$id ${action::class.simpleName} → ${acted::class.simpleName}")
                    val o = (acted as? TransitionResult.Ok)?.occurrence
                    // Still open after an item: offer the next one, quietly.
                    if (action !is Action.CheckItem || o == null || o.state != OccurrenceState.DUE) return@withEngine o
                    e.alarmView(o.id)?.let(AlarmInfo::of)
                }
                // The engine ended the ringing, or left it when the button was rejected (E17).
                when (next) {
                    is AlarmInfo -> Notifications.show(context, next, quiet = true)
                    // Done from the notification or the watch: "Rückgängig" for a minute (Anf. 7.31).
                    // A session started or going on shows its own notification (SessionService).
                    is Occurrence -> if (next.state != OccurrenceState.RUNNING) Undo.offer(context, next)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.avlreminder.DONE"
        const val ACTION_SNOOZE = "com.avlreminder.SNOOZE"
        const val ACTION_CHECK = "com.avlreminder.CHECK"
        const val ACTION_REOPEN = "com.avlreminder.REOPEN"
        /** The session (Anf. 7.11): "Ich starte jetzt", "Noch dran", "Teilschritt", "Abbruch"; "Fertig" is [ACTION_DONE]. */
        const val ACTION_START = "com.avlreminder.START"
        const val ACTION_STILL_ON = "com.avlreminder.STILL_ON"
        const val ACTION_MINIMUM = "com.avlreminder.MINIMUM"
        const val ACTION_ABORT = "com.avlreminder.ABORT"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_ITEM = "item"

        /** Ticks off one checklist item; the item is part of the data URI so each stays apart. */
        fun check(context: Context, occurrenceId: Long, itemId: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ActionReceiver::class.java)
                .setAction(ACTION_CHECK)
                .setData("avl://occurrence/$occurrenceId/item/$itemId".toUri())
                .putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId)
                .putExtra(EXTRA_ITEM, itemId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** One snooze button; the minutes are part of the data URI so two buttons stay apart. */
        fun snooze(context: Context, occurrenceId: Long, minutes: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ActionReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .setData("avl://occurrence/$occurrenceId/snooze/$minutes".toUri())
                .putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId)
                .putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

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
