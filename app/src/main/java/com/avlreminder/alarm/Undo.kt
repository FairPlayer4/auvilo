package com.avlreminder.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.avlreminder.R
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.graph
import com.avlreminder.system.Diag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Zurücknehmen" after a misclick (Anf. 7.31): after "Erledigt" or "Auslassen" in the alarm, the
 * notification or on the watch, a quiet notification offers "Rückgängig" for [TIMEOUT_MS].
 * Later the same is on "Heute" under "Erledigt", until the next time or the end of the day.
 */
object Undo {
    const val TIMEOUT_MS = 60_000L

    /** Own id: the alarm's foreground notification goes asynchronously and would take it along. */
    fun id(occurrenceId: Long): Int = Notifications.id(occurrenceId) + 1_000_000_000

    /** Offers "Rückgängig" if [occurrence] was just done or skipped by hand. */
    suspend fun offer(context: Context, occurrence: Occurrence) {
        if (!Lifecycle.canReopen(occurrence)) return
        val title = context.graph.repository.reminder(occurrence.reminderId)?.title ?: return
        val what = if (occurrence.state == OccurrenceState.DONE) "erledigt" else "ausgelassen"
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
        is TransitionResult.Ok -> "Klingelt wieder um ${time(result.occurrence.currentAt, zone)}."
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
