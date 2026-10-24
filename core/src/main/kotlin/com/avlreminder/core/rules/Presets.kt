package com.avlreminder.core.rules

import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Start values of a strictness preset; every value can be changed per reminder. */
data class Preset(val proof: ProofType, val postponeLimit: Int?, val minimumLimitPerWeek: Int?)

object Presets {
    fun of(strictness: Strictness): Preset = when (strictness) {
        Strictness.RELAXED -> Preset(ProofType.CONFIRM, postponeLimit = null, minimumLimitPerWeek = null)
        Strictness.NORMAL -> Preset(ProofType.CONFIRM, postponeLimit = 3, minimumLimitPerWeek = null)
        Strictness.STRICT -> Preset(ProofType.PHOTO, postponeLimit = 1, minimumLimitPerWeek = 1)
    }

    /** Applies the preset's start values to [reminder]. */
    fun apply(reminder: Reminder, strictness: Strictness): Reminder {
        val p = of(strictness)
        return reminder.copy(
            strictness = strictness,
            proof = p.proof,
            postponeLimit = p.postponeLimit,
            minimumLimitPerWeek = p.minimumLimitPerWeek,
        )
    }
}

object Validation {
    /** Problems that prevent saving; empty if the reminder is valid. */
    fun problems(reminder: Reminder): List<String> = buildList {
        if (reminder.title.isBlank()) add("Titel fehlt")
        if (reminder.strictness == Strictness.STRICT && reminder.proof == ProofType.NONE) {
            add("Streng verlangt einen Nachweis")
        }
        reminder.postponeLimit?.let { if (it < 0) add("Verschiebe-Limit darf nicht negativ sein") }
        reminder.minimumLimitPerWeek?.let { if (it < 0) add("Minimum-Limit darf nicht negativ sein") }
        if (reminder.minimumLimitPerWeek != null && reminder.minimum.isNullOrBlank()) add("Limit ohne Minimum")
    }
}

/** Weeks run Monday to Sunday. */
object Weeks {
    fun start(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** [from, to) of the week containing [now]. */
    fun range(now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val monday = start(now.atZone(zone).toLocalDate())
        return monday.atStartOfDay(zone).toInstant() to monday.plusWeeks(1).atStartOfDay(zone).toInstant()
    }
}

/** One-tap reminders from the widget. */
object QuickReminders {
    fun meeting(minutes: Long, now: Instant, zone: ZoneId): Reminder = Reminder(
        title = "Meeting",
        strictness = Strictness.NORMAL,
        schedule = Schedule.Once(now.plusSeconds(minutes * 60).atZone(zone).toLocalDateTime().withNano(0)),
        proof = ProofType.NONE,
        postponeLimit = 3,
        createdAt = now,
    )
}
