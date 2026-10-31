package com.avlreminder.ui.screens

import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.rules.Presets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ScheduleKind { ONCE, DAILY, WEEKDAYS, AFTER_DONE, ON_ARRIVAL }

/** One "Startklar" answer while editing. */
data class PrepDraft(val text: String = "", val unknown: Boolean = false) {
    val answered: Boolean get() = unknown || text.isNotBlank()
}

/** Editable state of the "Reminder anlegen" screen. Keeps all schedule fields so switching kinds loses nothing. */
data class ReminderForm(
    val id: Long = 0,
    val createdAt: Instant? = null,
    val active: Boolean = true,
    val title: String = "",
    val why: String = "",
    val character: Character = Character.DOCUMENT,
    val strictness: Strictness = Strictness.NORMAL,
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val date: LocalDate,
    val time: LocalTime,
    val days: Set<DayOfWeek> = WORKDAYS,
    val intervalDays: Int = 14,
    val goal: String = "",
    val minimum: String = "",
    val proof: ProofSpec = Presets.of(Strictness.NORMAL).proof,
    val postponeLimit: Int? = Presets.of(Strictness.NORMAL).postponeLimit,
    val minimumLimitPerWeek: Int? = Presets.of(Strictness.NORMAL).minimumLimitPerWeek,
    val ignoreDnd: Boolean = false,
    val snoozeMinutes: List<Int> = listOf(10),
    val motivationAudio: String? = null,
    /** Question 1..4 → answer; only answered ones are saved. */
    val prep: Map<Int, PrepDraft> = emptyMap(),
    /** Ring on arrival at home instead of only at the time. */
    val atHome: Boolean = false,
    val whenAway: WhenAway = WhenAway.DROP,
    /** Id of the "Zuhause" place, if it is set. */
    val homeId: Long? = null,
    /** Items keep their id while editing; empty ones are dropped on save. */
    val checklist: List<ChecklistItem> = emptyList(),
    /** Weekly goal (only daily or on weekdays); the numbers are kept when it is switched off. */
    val weekly: Boolean = false,
    val weekTarget: Int = 3,
    val weekMax: Int = 4,
    val weekMinimum: Int = 1,
) {
    /** A weekly goal fits only schedules with offer days. */
    val weeklyPossible: Boolean get() = kind == ScheduleKind.DAILY || kind == ScheduleKind.WEEKDAYS

    /** Picking a strictness resets proof and limits to the preset; each can be changed afterwards. */
    fun withStrictness(s: Strictness): ReminderForm {
        val p = Presets.of(s)
        return copy(strictness = s, proof = p.proof, postponeLimit = p.postponeLimit, minimumLimitPerWeek = p.minimumLimitPerWeek)
    }

    val schedule: Schedule?
        get() = when (kind) {
            ScheduleKind.ONCE -> Schedule.Once(LocalDateTime.of(date, time))
            ScheduleKind.DAILY -> Schedule.Daily(time)
            ScheduleKind.WEEKDAYS -> if (days.isEmpty()) null else Schedule.Weekdays(time, days)
            ScheduleKind.AFTER_DONE -> if (intervalDays < 1) null else Schedule.AfterDone(intervalDays, time, firstOn = date)
            ScheduleKind.ON_ARRIVAL -> Schedule.OnArrival
        }

    /** Waiting for the arrival needs no time; the arrival option of the other kinds is then implied. */
    val arrival: Arrival?
        get() = when {
            kind == ScheduleKind.ON_ARRIVAL -> Arrival(homeId ?: 0, WhenAway.RING)
            atHome -> Arrival(homeId ?: 0, whenAway)
            else -> null
        }

    /** The reminder to save, or null if the schedule is incomplete. */
    fun toReminder(now: Instant): Reminder? {
        val schedule = schedule ?: return null
        val minimum = minimum.trim().ifEmpty { null }
        return Reminder(
            id = id,
            title = title.trim(),
            why = why.trim().ifEmpty { null },
            character = character,
            strictness = strictness,
            schedule = schedule,
            goal = goal.trim().ifEmpty { null },
            minimum = minimum,
            // Without a minimum a weekly limit means nothing.
            minimumLimitPerWeek = if (minimum == null) null else minimumLimitPerWeek,
            proof = proof,
            postponeLimit = postponeLimit,
            active = active,
            createdAt = createdAt ?: now,
            ignoreDnd = ignoreDnd,
            snoozeMinutes = snoozeMinutes,
            motivationAudio = motivationAudio,
            // Without a place the validation says what is missing.
            arrival = arrival?.takeIf { homeId != null },
            checklist = checklist.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() },
            weeklyGoal = if (weekly && weeklyPossible) WeeklyGoal(weekTarget, weekMax, weekMinimum) else null,
        )
    }

    /** Answered questions; the engine stores only what changed. */
    fun prepAnswers(now: Instant): List<PrepAnswer> = prep.filterValues { it.answered }.map { (q, d) ->
        PrepAnswer(reminderId = id, question = q, text = d.text.trim().ifEmpty { null }, unknown = d.unknown, at = now)
    }

    companion object {
        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        /** A new reminder: next full hour today. */
        fun new(now: Instant, zone: ZoneId, homeId: Long? = null): ReminderForm {
            val next = now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
            return ReminderForm(date = next.toLocalDate(), time = next.toLocalTime(), homeId = homeId)
        }

        fun of(r: Reminder, today: LocalDate, prep: Map<Int, PrepAnswer> = emptyMap(), homeId: Long? = null): ReminderForm {
            val base = ReminderForm(
                id = r.id, createdAt = r.createdAt, active = r.active, title = r.title, why = r.why.orEmpty(),
                character = r.character, strictness = r.strictness, date = today, time = LocalTime.of(18, 0),
                goal = r.goal.orEmpty(), minimum = r.minimum.orEmpty(), proof = r.proof,
                postponeLimit = r.postponeLimit, minimumLimitPerWeek = r.minimumLimitPerWeek,
                ignoreDnd = r.ignoreDnd, snoozeMinutes = r.snoozeMinutes, motivationAudio = r.motivationAudio,
                prep = prep.mapValues { (_, a) -> PrepDraft(a.text.orEmpty(), a.unknown) },
                atHome = r.arrival != null && r.schedule != Schedule.OnArrival,
                whenAway = r.arrival?.whenAway ?: WhenAway.DROP,
                homeId = r.arrival?.placeId ?: homeId,
                checklist = r.checklist,
                weekly = r.weeklyGoal != null,
                weekTarget = r.weeklyGoal?.target ?: 3,
                weekMax = r.weeklyGoal?.max ?: 4,
                weekMinimum = r.weeklyGoal?.minimum ?: 1,
            )
            return when (val s = r.schedule) {
                is Schedule.Once -> base.copy(kind = ScheduleKind.ONCE, date = s.at.toLocalDate(), time = s.at.toLocalTime())
                is Schedule.Daily -> base.copy(kind = ScheduleKind.DAILY, time = s.time)
                is Schedule.Weekdays -> base.copy(kind = ScheduleKind.WEEKDAYS, time = s.time, days = s.days)
                is Schedule.AfterDone -> base.copy(kind = ScheduleKind.AFTER_DONE, time = s.time, intervalDays = s.intervalDays, date = s.firstOn ?: today)
                Schedule.OnArrival -> base.copy(kind = ScheduleKind.ON_ARRIVAL)
            }
        }
    }
}
