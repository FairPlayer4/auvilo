package com.avlreminder.core.rules

import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.model.AtTimes
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.PostponePeriod
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
    /** What a new reminder starts with: "festhalten", normal (form, interface for Claude, widget). */
    val DEFAULT_CHARACTER = Character.DOCUMENT
    val DEFAULT_STRICTNESS = Strictness.NORMAL

    /**
     * A new reminder with the start values everyone uses (Review-Lauf 5, E21): the form, the
     * interface for Claude and the one-tap reminders of the widget.
     */
    fun newReminder(title: String, schedule: Schedule, createdAt: Instant, strictness: Strictness = DEFAULT_STRICTNESS): Reminder =
        apply(Reminder(title = title, character = DEFAULT_CHARACTER, schedule = schedule, createdAt = createdAt), strictness)

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
        proof.textMinChars?.let { if (it !in 1..ProofSpec.MAX_TEXT_CHARS) add("Mindestzeichen 1 bis ${ProofSpec.MAX_TEXT_CHARS}") }
        proof.audioMinSeconds?.let { if (it !in 1..ProofSpec.MAX_AUDIO_SECONDS) add("Mindestdauer 1 bis ${ProofSpec.MAX_AUDIO_SECONDS} s") }
        if (proof.windowMinutes !in 1..ProofSpec.MAX_WINDOW_MINUTES) add("Frist für den Nachweis 1 bis ${ProofSpec.MAX_WINDOW_MINUTES} min")
        if (reminder.snoozeMinutes.size !in 1..2) add("Ein oder zwei Verschiebe-Knöpfe")
        if (reminder.snoozeMinutes.any { it !in 1..Reminder.MAX_SNOOZE_MINUTES }) add("Verschiebe-Knöpfe 1 bis ${Reminder.MAX_SNOOZE_MINUTES} min")
        if (reminder.snoozeMinutes.toSet().size != reminder.snoozeMinutes.size) add("Verschiebe-Knöpfe doppelt")
        reminder.postponeLimit?.let { if (it < 0) add("Verschiebe-Limit darf nicht negativ sein") }
        reminder.minimumLimitPerWeek?.let { if (it < 0) add("Minimum-Limit darf nicht negativ sein") }
        if (reminder.minimumLimitPerWeek != null && reminder.minimum.isNullOrBlank() && reminder.session?.minimumMinutes == null) add("Limit ohne Minimum")
        reminder.session?.let { s ->
            if (s.checkMinutes !in SessionSpec.MIN_CHECK..SessionSpec.MAX_CHECK) add("Nachfrage alle ${SessionSpec.MIN_CHECK} bis ${SessionSpec.MAX_CHECK} min")
            if (s.maxMinutes !in 1..SessionSpec.MAX_MAX) add("Maximale Dauer 1 bis ${SessionSpec.MAX_MAX} min")
            if (s.finalMinutes !in 1..SessionSpec.MAX_FINAL) add("Reaktionszeit 1 bis ${SessionSpec.MAX_FINAL} min")
            if (s.goalMinutes != null && s.goalMinutes !in 1..s.maxMinutes) add("Ziel-Dauer bis zur maximalen Dauer")
            if (s.minimumMinutes != null && s.minimumMinutes !in 1..(s.goalMinutes ?: s.maxMinutes)) add("Minimum-Dauer nicht über der Ziel-Dauer")
        }
        if (reminder.schedule == Schedule.OnArrival && reminder.arrival == null) add("„Wenn ich zuhause bin“ braucht einen Ort")
        (reminder.schedule as? Schedule.Deadline)?.let { d ->
            if (d.until <= d.from) add("Fenster: Ende nach dem Beginn")
            if (d.intervalMinutes !in Schedule.Deadline.MIN_INTERVAL..Schedule.Deadline.MAX_INTERVAL) {
                add("Abstand ${Schedule.Deadline.MIN_INTERVAL} bis ${Schedule.Deadline.MAX_INTERVAL} min")
            }
            if (reminder.arrival != null) add("Frist ohne Ankunft")
        }
        reminder.preNotice?.let { n ->
            if (reminder.schedule == Schedule.OnArrival) add("Vorab-Reminder braucht eine Uhrzeit")
            if (n is PreNotice.Before && n.minutes !in PreNotice.Before.MIN_MINUTES..PreNotice.Before.MAX_MINUTES) {
                add("Vorab ${PreNotice.Before.MIN_MINUTES} bis ${PreNotice.Before.MAX_MINUTES} min vorher")
            }
        }
        (reminder.schedule as? AtTimes)?.let { s ->
            if (s.times.size > MAX_TIMES) add("Höchstens $MAX_TIMES Uhrzeiten am Tag")
            if (s.severalADay && reminder.weeklyGoal != null) add("Wochenziel nur mit einer Uhrzeit am Tag")
            if (s.severalADay && reminder.arrival != null) add("Ankunft nur mit einer Uhrzeit am Tag")
        }
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
    const val MAX_TIMES = 6
}

/** Weeks run Monday to Sunday. */
/** The span a postpone limit per week or month counts in (Z2b). */
object PostponePeriods {
    /** [from, to) of [period] containing [now]; null per occurrence. */
    fun range(period: PostponePeriod, now: Instant, zone: ZoneId): Pair<Instant, Instant>? = when (period) {
        PostponePeriod.OCCURRENCE -> null
        PostponePeriod.WEEK -> Weeks.range(now, zone)
        PostponePeriod.MONTH -> now.atZone(zone).toLocalDate().withDayOfMonth(1)
            .let { it.atStartOfDay(zone).toInstant() to it.plusMonths(1).atStartOfDay(zone).toInstant() }
    }
}

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
    /** Normal, but without even a confirmation. */
    fun meeting(minutes: Long, now: Instant, zone: ZoneId): Reminder = inMinutes("Meeting", minutes, now, zone)

    /** One-off reminder [minutes] from [now], normal, no proof (meeting buttons, voice, Anf. 7.35). */
    fun inMinutes(title: String, minutes: Long, now: Instant, zone: ZoneId): Reminder = Presets.newReminder(
        title = title,
        schedule = Schedule.Once(now.plusSeconds(minutes * 60).atZone(zone).toLocalDateTime().withNano(0)),
        createdAt = now,
    ).copy(proof = ProofSpec.NONE)
}
