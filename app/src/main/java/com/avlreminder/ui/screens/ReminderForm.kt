package com.avlreminder.ui.screens

import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.PreNotice
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

enum class ScheduleKind { ONCE, DAILY, WEEKDAYS, AFTER_DONE, ON_ARRIVAL, DEADLINE }

enum class PreNoticeKind { NONE, EVENING, BEFORE }

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
    /** DAILY/WEEKDAYS: the further times a day (VS1); empty = one time. */
    val moreTimes: List<LocalTime> = emptyList(),
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
    /** Ring on arrival at [placeId] in the window before the time instead of only at the time (Anf. 7.29). */
    val atHome: Boolean = false,
    val whenAway: WhenAway = WhenAway.DROP,
    /** The place of the arrival; for a new reminder "Zuhause" if it is set. */
    val placeId: Long? = null,
    /** Window before the latest time; 0 = any arrival before the time, as before 7.29. */
    val lead: Int = Arrival.DEFAULT_LEAD,
    val buffer: Int = Arrival.DEFAULT_BUFFER,
    val check: Int = Arrival.DEFAULT_CHECK,
    /** Items keep their id while editing; empty ones are dropped on save. */
    val checklist: List<ChecklistItem> = emptyList(),
    /** Weekly goal (only daily or on weekdays); the numbers are kept when it is switched off. */
    val weekly: Boolean = false,
    val weekTarget: Int = 3,
    val weekMax: Int = 4,
    val weekMinimum: Int = 1,
    /** DEADLINE: [date] and [time] are the deadline, [days] the window days. */
    val windowFrom: LocalTime = LocalTime.of(9, 0),
    val windowUntil: LocalTime = LocalTime.of(17, 0),
    val remindEvery: Int = 60,
    val preNotice: PreNoticeKind = PreNoticeKind.NONE,
    val preNoticeEvening: LocalTime = LocalTime.of(19, 0),
    val preNoticeBefore: Int = 30,
    val solvedBlockerIsMinimum: Boolean = false,
    /** Kept for a preparation task; not editable. */
    val prepFor: Long? = null,
    /** Session with timer (Anf. 7.11); the numbers are kept when it is switched off. */
    val session: Boolean = false,
    val sessionCheck: Int = SessionSpec.DEFAULT_CHECK,
    val sessionCheckStrictness: Strictness = Strictness.RELAXED,
    val sessionGoal: Int? = null,
    val sessionMinimum: Int? = null,
    val sessionMax: Int = SessionSpec.DEFAULT_MAX,
    val sessionFinal: Int = SessionSpec.DEFAULT_FINAL,
) {
    /** Several times a day are possible for these kinds only (VS1). */
    val timesPossible: Boolean get() = kind == ScheduleKind.DAILY || kind == ScheduleKind.WEEKDAYS

    /** All times of the day, ascending. */
    val times: List<LocalTime> get() = if (timesPossible) (listOf(time) + moreTimes).distinct().sorted() else listOf(time)

    val severalADay: Boolean get() = times.size > 1

    /** A weekly goal fits only schedules with offer days, and one time a day. */
    val weeklyPossible: Boolean get() = timesPossible && !severalADay

    /** Picking a strictness resets proof and limits to the preset; each can be changed afterwards. */
    fun withStrictness(s: Strictness): ReminderForm {
        val p = Presets.of(s)
        return copy(strictness = s, proof = p.proof, postponeLimit = p.postponeLimit, minimumLimitPerWeek = p.minimumLimitPerWeek)
    }

    val schedule: Schedule?
        get() = when (kind) {
            ScheduleKind.ONCE -> Schedule.Once(LocalDateTime.of(date, time))
            ScheduleKind.DAILY -> Schedule.Daily(times)
            ScheduleKind.WEEKDAYS -> if (days.isEmpty()) null else Schedule.Weekdays(times, days)
            ScheduleKind.AFTER_DONE -> if (intervalDays < 1) null else Schedule.AfterDone(intervalDays, time, firstOn = date)
            ScheduleKind.ON_ARRIVAL -> Schedule.OnArrival
            ScheduleKind.DEADLINE ->
                if (days.isEmpty()) null else Schedule.Deadline(LocalDateTime.of(date, time), days, windowFrom, windowUntil, remindEvery)
        }

    /** The arrival has a window with buffer and checks (not for "wenn ich ankomme"). */
    val hasWindow: Boolean get() = atHome && placePossible && kind != ScheduleKind.ON_ARRIVAL && lead > 0

    /** A deadline rings in its windows only, so it has no arrival; nor do several times a day. */
    val placePossible: Boolean get() = kind != ScheduleKind.DEADLINE && !severalADay

    /** Waiting for the arrival needs no time; the arrival option of the other kinds is then implied. */
    val arrival: Arrival?
        get() = when {
            kind == ScheduleKind.ON_ARRIVAL -> Arrival(placeId ?: 0, WhenAway.RING, lead, buffer, check)
            atHome && placePossible -> Arrival(placeId ?: 0, whenAway, lead, buffer, check)
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
            arrival = arrival?.takeIf { placeId != null },
            checklist = checklist.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() },
            weeklyGoal = if (weekly && weeklyPossible) WeeklyGoal(weekTarget, weekMax, weekMinimum) else null,
            preNotice = when (preNotice) {
                PreNoticeKind.NONE -> null
                PreNoticeKind.EVENING -> PreNotice.Evening(preNoticeEvening)
                PreNoticeKind.BEFORE -> PreNotice.Before(preNoticeBefore)
            },
            prepFor = prepFor,
            solvedBlockerIsMinimum = solvedBlockerIsMinimum,
            session = if (session) {
                SessionSpec(sessionCheck, sessionCheckStrictness, sessionGoal, sessionMinimum, sessionMax, sessionFinal)
            } else {
                null
            },
        )
    }

    /** Answered questions; the engine stores only what changed. */
    fun prepAnswers(now: Instant): List<PrepAnswer> = prep.filterValues { it.answered }.map { (q, d) ->
        PrepAnswer(reminderId = id, question = q, text = d.text.trim().ifEmpty { null }, unknown = d.unknown, at = now)
    }

    companion object {
        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        /** A new reminder: next full hour today. */
        fun new(now: Instant, zone: ZoneId, placeId: Long? = null): ReminderForm {
            val next = now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
            return ReminderForm(date = next.toLocalDate(), time = next.toLocalTime(), placeId = placeId)
        }

        fun of(r: Reminder, today: LocalDate, prep: Map<Int, PrepAnswer> = emptyMap(), placeId: Long? = null): ReminderForm {
            val base = ReminderForm(
                id = r.id, createdAt = r.createdAt, active = r.active, title = r.title, why = r.why.orEmpty(),
                character = r.character, strictness = r.strictness, date = today, time = LocalTime.of(18, 0),
                goal = r.goal.orEmpty(), minimum = r.minimum.orEmpty(), proof = r.proof,
                postponeLimit = r.postponeLimit, minimumLimitPerWeek = r.minimumLimitPerWeek,
                ignoreDnd = r.ignoreDnd, snoozeMinutes = r.snoozeMinutes, motivationAudio = r.motivationAudio,
                prep = prep.mapValues { (_, a) -> PrepDraft(a.text.orEmpty(), a.unknown) },
                atHome = r.arrival != null && r.schedule != Schedule.OnArrival,
                whenAway = r.arrival?.whenAway ?: WhenAway.DROP,
                placeId = r.arrival?.placeId ?: placeId,
                lead = r.arrival?.leadMinutes ?: Arrival.DEFAULT_LEAD,
                buffer = r.arrival?.bufferMinutes ?: Arrival.DEFAULT_BUFFER,
                check = r.arrival?.checkMinutes ?: Arrival.DEFAULT_CHECK,
                checklist = r.checklist,
                weekly = r.weeklyGoal != null,
                weekTarget = r.weeklyGoal?.target ?: 3,
                weekMax = r.weeklyGoal?.max ?: 4,
                weekMinimum = r.weeklyGoal?.minimum ?: 1,
                preNotice = when (r.preNotice) {
                    null -> PreNoticeKind.NONE
                    is PreNotice.Evening -> PreNoticeKind.EVENING
                    is PreNotice.Before -> PreNoticeKind.BEFORE
                },
                preNoticeEvening = (r.preNotice as? PreNotice.Evening)?.time ?: LocalTime.of(19, 0),
                preNoticeBefore = (r.preNotice as? PreNotice.Before)?.minutes ?: 30,
                solvedBlockerIsMinimum = r.solvedBlockerIsMinimum,
                prepFor = r.prepFor,
                session = r.session != null,
                sessionCheck = r.session?.checkMinutes ?: SessionSpec.DEFAULT_CHECK,
                sessionCheckStrictness = r.session?.checkStrictness ?: Strictness.RELAXED,
                sessionGoal = r.session?.goalMinutes,
                sessionMinimum = r.session?.minimumMinutes,
                sessionMax = r.session?.maxMinutes ?: SessionSpec.DEFAULT_MAX,
                sessionFinal = r.session?.finalMinutes ?: SessionSpec.DEFAULT_FINAL,
            )
            return when (val s = r.schedule) {
                is Schedule.Once -> base.copy(kind = ScheduleKind.ONCE, date = s.at.toLocalDate(), time = s.at.toLocalTime())
                is Schedule.Daily -> base.copy(kind = ScheduleKind.DAILY, time = s.time, moreTimes = s.times.drop(1))
                is Schedule.Weekdays -> base.copy(kind = ScheduleKind.WEEKDAYS, time = s.time, moreTimes = s.times.drop(1), days = s.days)
                is Schedule.AfterDone -> base.copy(kind = ScheduleKind.AFTER_DONE, time = s.time, intervalDays = s.intervalDays, date = s.firstOn ?: today)
                Schedule.OnArrival -> base.copy(kind = ScheduleKind.ON_ARRIVAL)
                is Schedule.Deadline -> base.copy(
                    kind = ScheduleKind.DEADLINE, date = s.due.toLocalDate(), time = s.due.toLocalTime(), days = s.days,
                    windowFrom = s.from, windowUntil = s.until, remindEvery = s.intervalMinutes,
                )
            }
        }
    }
}
