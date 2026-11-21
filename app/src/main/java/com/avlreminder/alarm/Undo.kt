package com.avlreminder.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.avlreminder.R
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.usecases.Watch
import com.avlreminder.graph
import com.avlreminder.system.Diag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Zurücknehmen" after a misclick (Anf. 7.31): after "Erledigt" or "Auslassen" in the alarm, the
 * notification or on the watch, and after "Abbruch" of a session there, a quiet notification
 * offers "Rückgängig" for [TIMEOUT_MS].
 * Later the same is on "Heute" under "Erledigt", until the next time or the end of the day.
 */
object Undo {
    const val TIMEOUT_MS = Watch.UNDO_MS

    /** The engine's reason when a session is past its maximum ([Lifecycle.transition]). */
    private const val MAX_REACHED = "maximum duration reached"

    /** Own id: the alarm's foreground notification goes asynchronously and would take it along. */
    fun id(occurrenceId: Long): Int = Notifications.id(occurrenceId) + 1_000_000_000

    /** Offers "Rückgängig" if [occurrence] was just done or skipped by hand. */
    suspend fun offer(context: Context, occurrence: Occurrence) {
        val reminder = context.graph.repository.reminder(occurrence.reminderId) ?: return
        // An aborted session only before its maximum, as "Heute" and the engine (Review-Lauf 6).
        if (!Lifecycle.canReopen(reminder, occurrence, context.graph.now())) return
        val title = reminder.title
        val what = when {
            occurrence.state == OccurrenceState.DONE -> "erledigt"
            occurrence.resolution == Resolution.ABORTED -> "abgebrochen"
            else -> "ausgelassen"
        }
        notify(context, occurrence.id, "„$title“ $what", "Verklickt? Hier zurücknehmen.", undo = true)
    }

    /** "Rückgängig" in the notification. */
    suspend fun reopen(context: Context, occurrenceId: Long) {
        val result = context.graph.withEngine { it.reopen(occurrenceId) }
        Diag.log("undo occ=$occurrenceId → ${if (result is TransitionResult.Ok) "reopened ${result.occurrence.currentAt}" else result}")
        val title = (result as? TransitionResult.Ok)?.occurrence
            ?.let { context.graph.repository.reminder(it.reminderId)?.title }
        notify(context, occurrenceId, title?.let { "„$it“ wieder offen" } ?: "Nicht zurückgenommen", message(result), undo = false)
    }

    /** What happened, for the notification and "Heute". */
    fun message(result: TransitionResult, zone: ZoneId = ZoneId.systemDefault()): String = when (result) {
        is TransitionResult.Ok -> result.occurrence.startedAt?.takeIf { result.occurrence.isRunning }
            ?.let { "Die Sitzung läuft weiter, seit ${time(it, zone)}." }
            ?: "Klingelt wieder um ${time(result.occurrence.currentAt, zone)}."
        is TransitionResult.Rejected if result.reason == MAX_REACHED -> "Geht nicht mehr: Die Sitzung hat ihre maximale Dauer erreicht."
        else -> "Geht nicht mehr: Der nächste Termin läuft schon, oder es wäre nach dem Ende des Tages."
    }

    private fun time(at: Instant, zone: ZoneId) = DateTimeFormatter.ofPattern("HH:mm").format(at.atZone(zone))

    private fun notify(context: Context, occurrenceId: Long, title: String, text: String, undo: Boolean) {
        val builder = Notification.Builder(context, Notifications.CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
        if (undo) {
            builder.addAction(
                Notification.Action.Builder(null, "Rückgängig", ActionReceiver.pending(context, ActionReceiver.ACTION_REOPEN, occurrenceId)).build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).notify(id(occurrenceId), builder.build())
    }
}
