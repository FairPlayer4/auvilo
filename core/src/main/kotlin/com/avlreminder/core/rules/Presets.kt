package com.avlreminder.core.rules

import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Start values of a strictness preset; every value can be changed per reminder. */
data class Preset(val proof: ProofSpec, val postponeLimit: Int?, val minimumLimitPerWeek: Int?)

object Presets {
    fun of(strictness: Strictness): Preset = when (strictness) {
        Strictness.RELAXED -> Preset(ProofSpec.CONFIRM, postponeLimit = null, minimumLimitPerWeek = null)
        Strictness.NORMAL -> Preset(ProofSpec.CONFIRM, postponeLimit = 3, minimumLimitPerWeek = null)
        Strictness.STRICT -> Preset(ProofSpec.PHOTO, postponeLimit = 1, minimumLimitPerWeek = 1)
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
        val proof = reminder.proof
        if (reminder.strictness == Strictness.STRICT && proof.kind == ProofKind.NONE) {
            add("Streng verlangt einen Nachweis")
        }
        if (proof.needsEvidence && !proof.photo && proof.textMinChars == null && proof.audioMinSeconds == null) {
            add("Nachweis: Foto, Text oder Audio wählen")
        }
        proof.textMinChars?.let { if (it !in 1..2000) add("Mindestzeichen 1 bis 2000") }
        proof.audioMinSeconds?.let { if (it !in 1..600) add("Mindestdauer 1 bis 600 s") }
        if (proof.windowMinutes !in 1..60) add("Frist für den Nachweis 1 bis 60 min")
        if (reminder.snoozeMinutes.size !in 1..2) add("Ein oder zwei Verschiebe-Knöpfe")
        if (reminder.snoozeMinutes.any { it !in 1..240 }) add("Verschiebe-Knöpfe 1 bis 240 min")
        if (reminder.snoozeMinutes.toSet().size != reminder.snoozeMinutes.size) add("Verschiebe-Knöpfe doppelt")
        reminder.postponeLimit?.let { if (it < 0) add("Verschiebe-Limit darf nicht negativ sein") }
        reminder.minimumLimitPerWeek?.let { if (it < 0) add("Minimum-Limit darf nicht negativ sein") }
        if (reminder.minimumLimitPerWeek != null && reminder.minimum.isNullOrBlank()) add("Limit ohne Minimum")
        if (reminder.schedule == Schedule.OnArrival && reminder.arrival == null) add("„Wenn ich zuhause bin“ braucht einen Ort")
        if (reminder.checklist.any { it.title.isBlank() }) add("Checklisten-Punkt ohne Text")
        if (reminder.checklist.size > MAX_ITEMS) add("Höchstens $MAX_ITEMS Checklisten-Punkte")
        reminder.weeklyGoal?.let { g ->
            val days = WeeklyGoals.offerDays(reminder.schedule)
            when {
                days == null -> add("Wochenziel nur bei täglich oder an Wochentagen")
                g.minimum < 1 || g.minimum > g.target || g.target > g.max -> add("Wochenziel: mindestens ≤ Ziel ≤ höchstens")
                g.max > days -> add("Wochenziel: höchstens $days× bei diesen Tagen")
            }
        }
    }

    const val MAX_ITEMS = 20
}

/** Weeks run Monday to Sunday. */
object Weeks {
    fun start(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** [from, to) of the week containing [now]. */
    fun range(now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val monday = start(now.atZone(zone).toLocalDate())
        return range(monday, zone)
    }

    /** [from, to) of the week containing [date]. */
    fun range(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
        val monday = start(date)
        return monday.atStartOfDay(zone).toInstant() to monday.plusWeeks(1).atStartOfDay(zone).toInstant()
    }
}

/** One-tap reminders from the widget. */
object QuickReminders {
    fun meeting(minutes: Long, now: Instant, zone: ZoneId): Reminder = Reminder(
        title = "Meeting",
        strictness = Strictness.NORMAL,
        schedule = Schedule.Once(now.plusSeconds(minutes * 60).atZone(zone).toLocalDateTime().withNano(0)),
        character = Character.DOCUMENT,
        proof = ProofSpec.NONE,
        postponeLimit = 3,
        createdAt = now,
    )
}
