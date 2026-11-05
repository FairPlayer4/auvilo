package com.avlreminder.claude

import com.avlreminder.core.model.Character
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.rules.Stats
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.Overview
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.core.usecases.SaveResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The interface for Claude over adb (Anf. 7.26 AG11): read everything, create and edit
 * reminders through the same use cases as the screens. Nothing that completes, skips or
 * silences (AG5), no deleting; a reminder that rings or is overdue only changes in the app. Answers `{"ok": true, …}` or `{"ok": false, "error": …}`.
 */
class ClaudeApi(private val access: Access) {

    /** What the interface needs from the app; the app graph in production. */
    interface Access {
        /** Switched on in the settings. */
        suspend fun enabled(): Boolean
        suspend fun <T> withEngine(block: suspend (ReminderEngine) -> T): T
        suspend fun reminders(): List<Reminder>
        suspend fun openOccurrences(): List<Occurrence>
        suspend fun resolvedOccurrences(): List<Occurrence>
        fun now(): Instant
        val zone: ZoneId
    }

    class Failure(message: String) : Exception(message)

    suspend fun call(method: String, arg: String?): String = try {
        if (method == "status") {
            ok().put("enabled", access.enabled())
        } else {
            if (!access.enabled()) throw Failure("nicht freigegeben – in den Einstellungen unter „Schnittstelle für Claude“ einschalten")
            val o = arg?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
            when (method) {
                "methods" -> ok().put("methods", JSONArray(METHODS))
                "reminders" -> reminders(o)
                "reminder" -> reminder(o.getLong("id"))
                "today" -> today()
                "history" -> history(o.optInt("days", 14))
                "save" -> save(o)
                "setActive" -> setActive(o.getLong("id"), o.getBoolean("active"))
                "archive" -> archive(o.getLong("id"), o.optBoolean("archived", true))
                "addBlocker" -> addBlocker(o.getLong("reminderId"), o.getString("text"))
                else -> throw Failure("unbekannte Methode $method; bekannt: ${METHODS.joinToString()}")
            }
        }.toString()
    } catch (e: Failure) {
        error(e.message)
    } catch (e: Exception) {
        // Bad JSON, a missing key, an invalid value: say what, the caller fixes the request.
        error("${e.javaClass.simpleName}: ${e.message}")
    }

    private val json get() = ClaudeJson(access.zone)

    private fun ok() = JSONObject().put("ok", true).put("version", VERSION)
    private fun error(message: String?) = JSONObject().put("ok", false).put("version", VERSION).put("error", message ?: "Fehler").toString()

    private suspend fun all(): List<Reminder> = access.reminders()

    /** Archived ones only with {"archived": true}. */
    private suspend fun reminders(o: JSONObject): JSONObject {
        val withArchived = o.optBoolean("archived")
        val open = access.openOccurrences().associateBy { it.reminderId }
        val list = all().filter { withArchived || it.archivedAt == null }.map { r ->
            json.reminder(r).put("next", open[r.id]?.let(json::occurrence) ?: JSONObject.NULL)
        }
        return ok().put("reminders", JSONArray(list))
    }

    private suspend fun reminder(id: Long): JSONObject {
        val r = all().firstOrNull { it.id == id } ?: throw Failure("Reminder $id nicht gefunden")
        val resolved = access.resolvedOccurrences().filter { it.reminderId == id }
        val stats = Stats.of(r, resolved, access.now(), access.zone)
        val (blockers, prep) = access.withEngine { it.blockers(id) to it.prep(id) }
        return ok().put("reminder", json.reminder(r))
            .put("next", access.openOccurrences().firstOrNull { it.reminderId == id }?.let(json::occurrence) ?: JSONObject.NULL)
            .put("recent", JSONArray(resolved.sortedByDescending { it.dueAt }.take(RECENT).map(json::occurrence)))
            .put("stats", JSONObject().put("week", json.counts(stats.week)).put("month", json.counts(stats.month)).put("total", json.counts(stats.total)))
            .put("streak", json.streak(stats.streak))
            .put("blockers", JSONArray(blockers.map(json::blocker)))
            .put("prep", json.prep(prep))
    }

    private suspend fun today(): JSONObject {
        val today = Overview.today(all(), access.openOccurrences() + access.resolvedOccurrences(), access.now(), access.zone)
        fun list(entries: List<Entry>) = JSONArray(entries.map { e -> json.occurrence(e.occurrence).put("title", e.reminder.title) })
        return ok()
            .put("dueNow", list(today.dueNow))
            .put("laterToday", list(today.laterToday))
            .put("resolved", list(today.resolved))
            .put("overdue", list(today.overdue))
            .put("waiting", list(today.waiting))
            .put("upcoming", list(today.upcoming))
    }

    private suspend fun history(days: Int): JSONObject {
        val from = access.now().minus(Duration.ofDays(days.toLong()))
        val titles = all().associate { it.id to it.title }
        val list = access.resolvedOccurrences().filter { (it.resolvedAt ?: it.dueAt) >= from }.sortedByDescending { it.dueAt }
            .map { json.occurrence(it).put("title", titles[it.reminderId] ?: JSONObject.NULL) }
        return ok().put("days", days).put("occurrences", JSONArray(list))
    }

    /**
     * {"reminder": {…}, "prep": […]}: without an id a new reminder ("title" and "schedule"
     * needed), with an id only the given keys change.
     */
    private suspend fun save(o: JSONObject): JSONObject {
        val data = o.getJSONObject("reminder")
        val id = data.optLong("id", 0)
        if (id == 0L && (!data.has("title") || !data.has("schedule"))) throw Failure("neuer Reminder braucht title und schedule")
        // Read and save in one go, so a change in the app in between is not overwritten.
        val result = access.withEngine { engine ->
            val base = if (id == 0L) {
                // Like the form: "festhalten" unless said otherwise; "Überwindung" needs the four answers in "prep".
                Reminder(title = "", character = Character.DOCUMENT, schedule = Schedule.Daily(LocalTime.NOON), createdAt = access.now())
            } else {
                notRunning(id)
                all().firstOrNull { it.id == id } ?: throw Failure("Reminder $id nicht gefunden")
            }
            val reminder = json.reminder(data, base, isNew = id == 0L)
            val prep = o.optJSONArray("prep")?.let { json.prep(it, id, access.now()) }.orEmpty()
            engine.save(reminder, prep)
        }
        return when (result) {
            is SaveResult.Invalid -> throw Failure(result.problems.joinToString("; "))
            is SaveResult.Saved -> {
                ok().put("id", result.id).put("reminder", json.reminder(all().first { it.id == result.id }))
            }
        }
    }

    private suspend fun setActive(id: Long, active: Boolean): JSONObject {
        known(id)
        access.withEngine { notRunning(id); it.setActive(id, active) }
        return ok().put("id", id).put("active", active)
    }

    private suspend fun archive(id: Long, archived: Boolean): JSONObject {
        known(id)
        access.withEngine { notRunning(id); it.setArchived(id, archived) }
        return ok().put("id", id).put("archived", archived)
    }

    private suspend fun addBlocker(reminderId: Long, text: String): JSONObject {
        known(reminderId)
        if (text.isBlank()) throw Failure("Blocker ohne Text")
        return ok().put("id", access.withEngine { it.addBlocker(reminderId, text) })
    }

    /**
     * Pausing, archiving or editing would end a ringing alarm without a miss (AG5): new due
     * times, a weaker proof, no postpone limit. That stays with Kevin in the app.
     */
    private suspend fun notRunning(id: Long) {
        val open = access.openOccurrences().firstOrNull { it.reminderId == id } ?: return
        // Ringing, overdue or waiting for the arrival after it rang (AG5); snoozed keeps its day on save.
        if (open.state != OccurrenceState.PLANNED || open.currentAt <= access.now() || open.awaitsArrival) throw Failure("läuft gerade – nur in der App")
    }

    private suspend fun known(id: Long) {
        if (all().none { it.id == id }) throw Failure("Reminder $id nicht gefunden")
    }

    companion object {
        /** Raised when requests or answers change in a way a caller has to know. */
        const val VERSION = 1
        private const val RECENT = 10
        val METHODS = listOf("status", "methods", "reminders", "reminder", "today", "history", "save", "setActive", "archive", "addBlocker")

        /** For the diagnosis log: method and id, never contents. */
        fun logLine(method: String, arg: String?, answer: String): String {
            val asked = runCatching { JSONObject(arg ?: "{}").let { o -> o.optJSONObject("reminder")?.optLong("id") ?: o.optLong("id", o.optLong("reminderId")) } }.getOrNull()
            // A new reminder has its id only in the answer.
            val id = asked?.takeIf { it != 0L } ?: runCatching { JSONObject(answer).optLong("id") }.getOrNull()
            val ok = runCatching { JSONObject(answer).getBoolean("ok") }.getOrDefault(false)
            return "claude $method" + (id?.takeIf { it != 0L }?.let { " id=$it" } ?: "") + if (ok) " ok" else " error"
        }
    }
}
