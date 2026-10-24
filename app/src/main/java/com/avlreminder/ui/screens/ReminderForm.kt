package com.avlreminder.ui.screens

import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.Presets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ScheduleKind { ONCE, DAILY, WEEKDAYS, AFTER_DONE }

/** Editable state of the "Reminder anlegen" screen. Keeps all schedule fields so switching kinds loses nothing. */
data class ReminderForm(
    val id: Long = 0,
    val createdAt: Instant? = null,
    val active: Boolean = true,
    val title: String = "",
    val why: String = "",
    val character: Character = Character.OVERCOME,
    val strictness: Strictness = Strictness.NORMAL,
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val date: LocalDate,
    val time: LocalTime,
    val days: Set<DayOfWeek> = WORKDAYS,
    val intervalDays: Int = 14,
    val goal: String = "",
    val minimum: String = "",
    val proof: ProofType = Presets.of(Strictness.NORMAL).proof,
    val postponeLimit: Int? = Presets.of(Strictness.NORMAL).postponeLimit,
    val minimumLimitPerWeek: Int? = Presets.of(Strictness.NORMAL).minimumLimitPerWeek,
) {
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
        )
    }

    companion object {
        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        /** A new reminder: next full hour today. */
        fun new(now: Instant, zone: ZoneId): ReminderForm {
            val next = now.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
            return ReminderForm(date = next.toLocalDate(), time = next.toLocalTime())
        }

        fun of(r: Reminder, today: LocalDate): ReminderForm {
            val base = ReminderForm(
                id = r.id, createdAt = r.createdAt, active = r.active, title = r.title, why = r.why.orEmpty(),
                character = r.character, strictness = r.strictness, date = today, time = LocalTime.of(18, 0),
                goal = r.goal.orEmpty(), minimum = r.minimum.orEmpty(), proof = r.proof,
                postponeLimit = r.postponeLimit, minimumLimitPerWeek = r.minimumLimitPerWeek,
            )
            return when (val s = r.schedule) {
                is Schedule.Once -> base.copy(kind = ScheduleKind.ONCE, date = s.at.toLocalDate(), time = s.at.toLocalTime())
                is Schedule.Daily -> base.copy(kind = ScheduleKind.DAILY, time = s.time)
                is Schedule.Weekdays -> base.copy(kind = ScheduleKind.WEEKDAYS, time = s.time, days = s.days)
                is Schedule.AfterDone -> base.copy(kind = ScheduleKind.AFTER_DONE, time = s.time, intervalDays = s.intervalDays, date = s.firstOn ?: today)
            }
        }
    }
}
