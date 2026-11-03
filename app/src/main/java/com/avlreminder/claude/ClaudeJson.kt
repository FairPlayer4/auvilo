package com.avlreminder.claude

import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.rules.Counts
import com.avlreminder.core.rules.Presets
import com.avlreminder.core.rules.Streak
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The JSON of the interface for Claude (Anf. 7.26 AG11). Times are local ("19:15",
 * "2026-11-03T19:15"), weekdays as in [DayOfWeek] ("MONDAY"), enums by name.
 */
internal class ClaudeJson(private val zone: ZoneId) {

    fun instant(at: Instant?): Any = at?.let { LocalDateTime.ofInstant(it, zone).toString() } ?: JSONObject.NULL

    // --- Out -------------------------------------------------------------------------------

    fun reminder(r: Reminder): JSONObject = JSONObject()
        .put("id", r.id)
        .put("title", r.title)
        .put("why", r.why ?: JSONObject.NULL)
        .put("character", r.character.name)
        .put("strictness", r.strictness.name)
        .put("schedule", schedule(r.schedule))
        .put("goal", r.goal ?: JSONObject.NULL)
        .put("minimum", r.minimum ?: JSONObject.NULL)
        .put("minimumLimitPerWeek", r.minimumLimitPerWeek ?: JSONObject.NULL)
        .put("proof", proof(r.proof))
        .put("postponeLimit", r.postponeLimit ?: JSONObject.NULL)
        .put("active", r.active)
        .put("archived", r.archivedAt != null)
        .put("createdAt", instant(r.createdAt))
        .put("ignoreDnd", r.ignoreDnd)
        .put("snoozeMinutes", JSONArray(r.snoozeMinutes))
        .put("motivationAudio", r.motivationAudio != null)
        .put("arrival", r.arrival?.let { JSONObject().put("placeId", it.placeId).put("whenAway", it.whenAway.name) } ?: JSONObject.NULL)
        .put("checklist", JSONArray(r.checklist.map { JSONObject().put("id", it.id).put("title", it.title) }))
        .put("weeklyGoal", r.weeklyGoal?.let { JSONObject().put("target", it.target).put("max", it.max).put("minimum", it.minimum) } ?: JSONObject.NULL)
        .put("preNotice", preNotice(r.preNotice))
        .put("prepFor", r.prepFor ?: JSONObject.NULL)
        .put("solvedBlockerIsMinimum", r.solvedBlockerIsMinimum)

    fun schedule(s: Schedule): JSONObject = when (s) {
        is Schedule.Once -> JSONObject().put("type", "once").put("at", s.at.toString())
        is Schedule.Daily -> JSONObject().put("type", "daily").put("times", times(s.times))
        is Schedule.Weekdays -> JSONObject().put("type", "weekdays").put("times", times(s.times)).put("days", days(s.days))
        is Schedule.AfterDone -> JSONObject().put("type", "afterDone").put("intervalDays", s.intervalDays).put("time", s.time.toString())
            .put("firstOn", s.firstOn?.toString() ?: JSONObject.NULL)
        Schedule.OnArrival -> JSONObject().put("type", "onArrival")
        is Schedule.Deadline -> JSONObject().put("type", "deadline").put("due", s.due.toString()).put("days", days(s.days))
            .put("from", s.from.toString()).put("until", s.until.toString()).put("intervalMinutes", s.intervalMinutes)
    }

    private fun times(times: List<LocalTime>) = JSONArray(times.map { it.toString() })

    private fun days(days: Set<DayOfWeek>) = JSONArray(days.sorted().map { it.name })

    private fun proof(p: ProofSpec): JSONObject = JSONObject()
        .put("kind", p.kind.name)
        .put("photo", p.photo)
        .put("textMinChars", p.textMinChars ?: JSONObject.NULL)
        .put("audioMinSeconds", p.audioMinSeconds ?: JSONObject.NULL)
        .put("windowMinutes", p.windowMinutes)

    private fun preNotice(p: PreNotice?): Any = when (p) {
        null -> JSONObject.NULL
        is PreNotice.Evening -> JSONObject().put("evening", p.time.toString())
        is PreNotice.Before -> JSONObject().put("beforeMinutes", p.minutes)
    }

    fun occurrence(o: Occurrence): JSONObject = JSONObject()
        .put("id", o.id)
        .put("reminderId", o.reminderId)
        .put("dueAt", instant(o.dueAt))
        .put("currentAt", instant(o.currentAt))
        .put("state", o.state.name)
        .put("postponeCount", o.postponeCount)
        .put("resolution", o.resolution?.name ?: JSONObject.NULL)
        .put("resolvedAt", instant(o.resolvedAt))
        .put("note", o.note ?: JSONObject.NULL)

    fun blocker(b: Blocker): JSONObject = JSONObject()
        .put("id", b.id)
        .put("text", b.text)
        .put("origin", b.origin.name)
        .put("createdAt", instant(b.createdAt))
        .put("solvedAt", instant(b.solvedAt))
        .put("taskReminderId", b.taskReminderId ?: JSONObject.NULL)

    fun prep(answers: Map<Int, PrepAnswer>): JSONArray = JSONArray(
        answers.values.sortedBy { it.question }.map { JSONObject().put("question", it.question).put("text", it.text ?: JSONObject.NULL).put("unknown", it.unknown) },
    )

    fun counts(c: Counts): JSONObject = JSONObject()
        .put("goal", c.goal).put("minimum", c.minimum).put("noProof", c.noProof).put("skipped", c.skipped)
        .put("missed", c.missed).put("dropped", c.dropped).put("early", c.early)

    fun streak(s: Streak?): Any = s?.let { JSONObject().put("unit", it.unit.name).put("current", it.current).put("best", it.best) } ?: JSONObject.NULL

    // --- In --------------------------------------------------------------------------------

    /**
     * [o] applied to [base]: only the keys present change something, so an edit sends just
     * what changes. A new reminder takes the preset of its strictness unless proof or limits
     * are given.
     */
    fun reminder(o: JSONObject, base: Reminder, isNew: Boolean): Reminder {
        var r = base
        if (o.has("title")) r = r.copy(title = o.getString("title").trim())
        if (o.has("why")) r = r.copy(why = o.optStringOrNull("why"))
        if (o.has("character")) r = r.copy(character = Character.valueOf(o.getString("character")))
        if (o.has("strictness")) r = r.copy(strictness = Strictness.valueOf(o.getString("strictness")))
        // Like the form: a new reminder starts with the preset, also without a strictness given.
        if (isNew) Presets.of(r.strictness).let { p -> r = r.copy(proof = p.proof, postponeLimit = p.postponeLimit, minimumLimitPerWeek = p.minimumLimitPerWeek) }
        if (o.has("schedule")) r = r.copy(schedule = schedule(o.getJSONObject("schedule")))
        if (o.has("goal")) r = r.copy(goal = o.optStringOrNull("goal"))
        if (o.has("minimum")) r = r.copy(minimum = o.optStringOrNull("minimum"))
        if (o.has("minimumLimitPerWeek")) r = r.copy(minimumLimitPerWeek = o.optIntOrNull("minimumLimitPerWeek"))
        if (o.has("proof")) r = r.copy(proof = proof(o.getJSONObject("proof"), r.proof))
        if (o.has("postponeLimit")) r = r.copy(postponeLimit = o.optIntOrNull("postponeLimit"))
        if (o.has("ignoreDnd")) r = r.copy(ignoreDnd = o.getBoolean("ignoreDnd"))
        if (o.has("snoozeMinutes")) r = r.copy(snoozeMinutes = o.getJSONArray("snoozeMinutes").ints())
        if (o.has("arrival")) {
            r = r.copy(
                arrival = o.optJSONObject("arrival")?.let {
                    Arrival(it.getLong("placeId"), WhenAway.valueOf(it.optString("whenAway", WhenAway.DROP.name)))
                },
            )
        }
        if (o.has("checklist")) r = r.copy(checklist = checklist(o.getJSONArray("checklist")))
        if (o.has("weeklyGoal")) {
            r = r.copy(
                weeklyGoal = o.optJSONObject("weeklyGoal")?.let {
                    val target = it.getInt("target")
                    WeeklyGoal(target, it.optInt("max", target), it.optInt("minimum", 1))
                },
            )
        }
        if (o.has("preNotice")) {
            r = r.copy(
                preNotice = o.optJSONObject("preNotice")?.let {
                    if (it.has("evening")) PreNotice.Evening(LocalTime.parse(it.getString("evening"))) else PreNotice.Before(it.getInt("beforeMinutes"))
                },
            )
        }
        if (o.has("solvedBlockerIsMinimum")) r = r.copy(solvedBlockerIsMinimum = o.getBoolean("solvedBlockerIsMinimum"))
        // Like the form: without a minimum a weekly limit means nothing.
        if (r.minimum.isNullOrBlank() && !o.has("minimumLimitPerWeek")) r = r.copy(minimumLimitPerWeek = null)
        return r
    }

    fun schedule(o: JSONObject): Schedule = when (val type = o.getString("type")) {
        "once" -> Schedule.Once(LocalDateTime.parse(o.getString("at")))
        "daily" -> Schedule.Daily(times(o))
        "weekdays" -> Schedule.Weekdays(times(o), days(o.getJSONArray("days")))
        "afterDone" -> Schedule.AfterDone(
            o.getInt("intervalDays"), LocalTime.parse(o.getString("time")), o.optStringOrNull("firstOn")?.let(LocalDate::parse),
        )
        "onArrival" -> Schedule.OnArrival
        "deadline" -> Schedule.Deadline(
            LocalDateTime.parse(o.getString("due")), days(o.getJSONArray("days")),
            LocalTime.parse(o.getString("from")), LocalTime.parse(o.getString("until")), o.optInt("intervalMinutes", 60),
        )
        else -> throw IllegalArgumentException("unknown schedule type $type")
    }

    /** "times" as a list, or a single "time"; sorted, as the schedule needs them. */
    private fun times(o: JSONObject): List<LocalTime> =
        (o.optJSONArray("times")?.strings() ?: listOf(o.getString("time"))).map(LocalTime::parse).sorted()

    private fun days(a: JSONArray): Set<DayOfWeek> = a.strings().map { DayOfWeek.valueOf(it.uppercase()) }.toSet()

    private fun proof(o: JSONObject, base: ProofSpec) = ProofSpec(
        kind = if (o.has("kind")) ProofKind.valueOf(o.getString("kind")) else base.kind,
        photo = if (o.has("photo")) o.getBoolean("photo") else base.photo,
        textMinChars = if (o.has("textMinChars")) o.optIntOrNull("textMinChars") else base.textMinChars,
        audioMinSeconds = if (o.has("audioMinSeconds")) o.optIntOrNull("audioMinSeconds") else base.audioMinSeconds,
        windowMinutes = if (o.has("windowMinutes")) o.getInt("windowMinutes") else base.windowMinutes,
    )

    /** Items as {"id", "title"} (keeps the id) or plain strings (new items). */
    private fun checklist(a: JSONArray): List<ChecklistItem> = (0 until a.length()).map { i ->
        when (val item = a.get(i)) {
            is JSONObject -> ChecklistItem(item.optLong("id", 0), item.getString("title"))
            else -> ChecklistItem(title = item.toString())
        }
    }

    fun prep(a: JSONArray, reminderId: Long, at: Instant): List<PrepAnswer> = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        PrepAnswer(reminderId = reminderId, question = o.getInt("question"), text = o.optStringOrNull("text"), unknown = o.optBoolean("unknown"), at = at)
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key).trim().ifEmpty { null }
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
}
