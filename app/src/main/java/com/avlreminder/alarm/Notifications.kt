package com.avlreminder.alarm

import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.rules.Sessions
import android.os.Bundle
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.avlreminder.R
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.Ring
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a notification needs to know about a ringing occurrence. */
data class AlarmInfo(
    val occurrenceId: Long,
    val title: String,
    val text: String?,
    val quickDone: Boolean,
    /** Snooze buttons in minutes, already without those past the end of the day. */
    val snooze: List<Int> = emptyList(),
    /** Next open checklist item, to tick off from the notification; only without proof. */
    val nextItem: ChecklistItem? = null,
    /** "Ich starte jetzt" (Anf. 7.11 D1). */
    val canStart: Boolean = false,
    /** A running session: the alarm is a check or its last reminder (D3, D5). */
    val session: SessionCheck? = null,
) {
    /** KRITISCH: everything, for the intent of [AlarmService]; what is missing there is missing in its notifications. */
    fun toBundle(): Bundle = Bundle().apply {
        putLong("id", occurrenceId)
        putString("title", title)
        putString("text", text)
        putBoolean("quickDone", quickDone)
        putIntArray("snooze", snooze.toIntArray())
        nextItem?.let { putLong("itemId", it.id); putString("itemTitle", it.title) }
        putBoolean("canStart", canStart)
        session?.let {
            putLong("sessionStart", it.startedAt.toEpochMilli())
            putBoolean("sessionFinal", it.final)
            putBoolean("sessionMinimum", it.minimum)
            putString("sessionSuggestion", it.suggestion.name)
        }
    }

    companion object {
        fun of(ring: Ring) = of(ring.view)

        fun of(view: AlarmView, zone: ZoneId = ZoneId.systemDefault()): AlarmInfo {
            val reminder = view.reminder
            val quick = view.quickDone
            val session = view.session?.let { SessionCheck(it.startedAt, it.final, view.minimumAllowed, it.suggestion) }
            // In a session the checklist is not ticked off from the notification; the answers come first.
            val nextItem = view.nextItem?.takeIf { session == null }
            return AlarmInfo(
                occurrenceId = view.occurrence.id,
                title = reminder.title,
                text = session?.let { sessionText(it, zone) } ?: nextItem?.let { "Als Nächstes: ${it.title}" } ?: reminder.why ?: reminder.goal,
                quickDone = quick,
                snooze = if (session == null) view.snooze else emptyList(),
                nextItem = nextItem?.takeIf { quick },
                canStart = view.canStart,
                session = session,
            )
        }

        fun of(bundle: Bundle): AlarmInfo = AlarmInfo(
            occurrenceId = bundle.getLong("id", -1),
            title = bundle.getString("title") ?: "",
            text = bundle.getString("text"),
            quickDone = bundle.getBoolean("quickDone"),
            snooze = bundle.getIntArray("snooze")?.toList().orEmpty(),
            nextItem = bundle.getString("itemTitle")?.let { ChecklistItem(bundle.getLong("itemId", -1), it) },
            canStart = bundle.getBoolean("canStart"),
            session = if (bundle.containsKey("sessionStart")) {
                SessionCheck(
                    Instant.ofEpochMilli(bundle.getLong("sessionStart")),
                    final = bundle.getBoolean("sessionFinal"),
                    minimum = bundle.getBoolean("sessionMinimum"),
                    suggestion = SessionAnswer.valueOf(bundle.getString("sessionSuggestion") ?: SessionAnswer.STILL_ON.name),
                )
            } else {
                null
            },
        )

        /** "Noch dran? · seit 18:42" or "Maximale Dauer erreicht · seit 18:42". */
        fun sessionText(s: SessionCheck, zone: ZoneId): String {
            val since = DateTimeFormatter.ofPattern("HH:mm").format(s.startedAt.atZone(zone))
            val ask = when {
                s.final -> "Maximale Dauer erreicht"
                s.suggestion == SessionAnswer.DONE -> "Ziel erreicht – Fertig?"
                // Without the button it says why, as the running session's notification (Review-Lauf 6).
                s.suggestion == SessionAnswer.MINIMUM && !s.minimum -> "Minimum erreicht – Teilschritt diese Woche aufgebraucht"
                s.suggestion == SessionAnswer.MINIMUM -> "Minimum erreicht – Teilschritt?"
                else -> "Noch dran?"
            }
            return "$ask · seit $since"
        }
    }
}

/** The check of a running session in a notification (Anf. 7.11 D3–D5). */
data class SessionCheck(
    val startedAt: Instant,
    /** The last reminder: "Noch dran" is no longer offered. */
    val final: Boolean,
    /** "Teilschritt" is allowed. */
    val minimum: Boolean,
    val suggestion: SessionAnswer,
)

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    private const val CHANNEL_BANNER = "banner"
    const val CHANNEL_SILENT = "silent"
    private const val CHANNEL_WATCH = "watch"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            // Sound and vibration come from AlarmService, so the channel itself stays quiet.
            NotificationChannel(CHANNEL_ALARM, "Alarm (streng)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BANNER, "Erinnerung (normal)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, "Stille Erinnerung", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            // The ongoing alarm notification is not passed on to the watch; this copy is.
            NotificationChannel(CHANNEL_WATCH, "Alarm auf der Uhr", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            },
        )
    }

    /** Channels a reminder with "auch bei Nicht stören" needs as exception; empty if all may. */
    fun channelsBlockedByDnd(context: Context): List<String> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return listOf(CHANNEL_ALARM, CHANNEL_BANNER).filter { manager.getNotificationChannel(it)?.canBypassDnd() != true }
    }

    /** Notification id per occurrence, so several reminders can be open at once. */
    fun id(occurrenceId: Long): Int = (occurrenceId % Int.MAX_VALUE).toInt() + 1

    /** Id of the watch copy; negative ids never meet [id]. */
    private fun mirrorId(occurrenceId: Long): Int = -id(occurrenceId)

    /** Full-screen notification used by the foreground service. */
    fun fullScreen(context: Context, info: AlarmInfo): Notification {
        val open = openAlarm(context, info.occurrenceId)
        return builder(context, CHANNEL_ALARM, info)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setLocalOnly(true)
            .setFullScreenIntent(open, true)
            .build()
    }

    /** Normal copy of a full-screen alarm with the same buttons, so the watch shows it too. */
    fun showMirror(context: Context, info: AlarmInfo) {
        val notification = builder(context, CHANNEL_WATCH, info)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOnlyAlertOnce(false)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(mirrorId(info.occurrenceId), notification)
    }

    fun cancelMirror(context: Context, occurrenceId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(mirrorId(occurrenceId))
    }

    /** Quiet while the proof is being made; tapping it goes back to the proof. */
    fun showProofRunning(context: Context, info: AlarmInfo, until: Instant) {
        val time = DateTimeFormatter.ofPattern("HH:mm").format(until.atZone(ZoneId.systemDefault()))
        val notification = Notification.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(info.title)
            .setContentText("Nachweis läuft – Alarm pausiert bis $time")
            .setContentIntent(openAlarm(context, info.occurrenceId))
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id(info.occurrenceId), notification)
    }

    /** Banner or silent notification; [quiet] chooses the channel. */
    fun show(context: Context, info: AlarmInfo, quiet: Boolean) {
        val notification = builder(context, if (quiet) CHANNEL_SILENT else CHANNEL_BANNER, info)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id(info.occurrenceId), notification)
    }

    fun cancel(context: Context, occurrenceId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id(occurrenceId))
        cancelMirror(context, occurrenceId)
    }

    private fun builder(context: Context, channel: String, info: AlarmInfo): Notification.Builder {
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(info.title)
            .setContentIntent(openAlarm(context, info.occurrenceId))
        info.text?.let { builder.setContentText(it) }
        val buttons = buttons(info)
        // At most three fit on the phone; the watch shows all of them (design.md 3.1, G2).
        buttons.phone.forEach { builder.addAction(it.toAction(context, info.occurrenceId)) }
        if (buttons.watch != buttons.phone) {
            builder.extend(Notification.WearableExtender().addActions(buttons.watch.map { it.toAction(context, info.occurrenceId) }))
        }
        return builder
    }

    /** A button of an alarm notification; [Buttons] decides which and in what order. */
    sealed interface Button {
        data object Start : Button
        data object Done : Button
        data class Item(val item: ChecklistItem) : Button
        data class Snooze(val minutes: Int) : Button
        data object StillOn : Button
        data object SessionDone : Button
        data object Minimum : Button
        data object Abort : Button
    }

    data class Buttons(val phone: List<Button>, val watch: List<Button>)

    /**
     * The buttons of [info]. A session check: *Noch dran* (not at the last reminder), the
     * suggested way to end it, *Abbruch* – on the watch all four. An alarm with a session:
     * *Ich starte jetzt* first.
     */
    fun buttons(info: AlarmInfo): Buttons {
        val session = info.session
        if (session != null) {
            // The answers and their order come from core (E18); without the screen none with a proof.
            val answers = Sessions.quickAnswers(session.final, session.suggestion, session.minimum, info.quickDone)
            val stillOn = Button.StillOn.takeIf { SessionAnswer.STILL_ON in answers }
            val ends = answers.mapNotNull {
                when (it) {
                    SessionAnswer.DONE -> Button.SessionDone
                    SessionAnswer.MINIMUM -> Button.Minimum
                    else -> null
                }
            }
            return Buttons(
                phone = listOfNotNull(stillOn, ends.firstOrNull(), Button.Abort),
                watch = listOfNotNull(stillOn) + ends + Button.Abort,
            )
        }
        // A checklist is ticked off item by item; "Alles erledigt" is in the alarm screen.
        val done = info.nextItem?.let { Button.Item(it) } ?: Button.Done.takeIf { info.quickDone }
        val all = listOfNotNull(Button.Start.takeIf { info.canStart }, done) + info.snooze.take(2).map { Button.Snooze(it) }
        return Buttons(phone = all.take(3), watch = all)
    }

    private fun Button.toAction(context: Context, occurrenceId: Long): Notification.Action = when (this) {
        Button.Start -> action(context, ActionReceiver.ACTION_START, "Ich starte jetzt", occurrenceId)
        Button.Done -> action(context, ActionReceiver.ACTION_DONE, "Erledigt", occurrenceId)
        is Button.Item -> Notification.Action.Builder(null, "✓ ${item.title}", ActionReceiver.check(context, occurrenceId, item.id)).build()
        is Button.Snooze -> Notification.Action.Builder(null, "+$minutes min", ActionReceiver.snooze(context, occurrenceId, minutes)).build()
        Button.StillOn -> action(context, ActionReceiver.ACTION_STILL_ON, "Noch dran", occurrenceId)
        Button.SessionDone -> action(context, ActionReceiver.ACTION_DONE, "Fertig", occurrenceId)
        Button.Minimum -> action(context, ActionReceiver.ACTION_MINIMUM, "Teilschritt", occurrenceId)
        Button.Abort -> action(context, ActionReceiver.ACTION_ABORT, "Abbruch", occurrenceId)
    }

    fun openAlarm(context: Context, occurrenceId: Long): PendingIntent = PendingIntent.getActivity(
        context, 0, AlarmActivity.intent(context, occurrenceId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun action(context: Context, action: String, title: String, occurrenceId: Long) =
        Notification.Action.Builder(null, title, ActionReceiver.pending(context, action, occurrenceId)).build()
}
