package com.avlreminder.core.rules

import com.avlreminder.core.model.Strictness
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** "Laut ab" and the end of the day for Saturday and Sunday (VS2). */
data class DayTimes(val loudFrom: LocalTime, val dayEnd: LocalTime) {
    init { require(loudFrom < dayEnd) { "loud must start before the end of the day" } }
}

/**
 * Global alert settings. Quiet times come from the phone's "do not disturb";
 * [dayEnd] is when an unanswered occurrence counts as missed.
 */
data class AlertSettings(
    val loudFrom: LocalTime = LocalTime.of(18, 0),
    val dayEnd: LocalTime = LocalTime.of(23, 0),
    /** After this many stuck occurrences in a row the alarm asks what keeps one from it (S8). */
    val askBlockerAfter: Int = 2,
    /** Other times on Saturday and Sunday; null = the same as on weekdays (VS2). */
    val weekend: DayTimes? = null,
    /** All alarms paused by hand until then (PS7); null = not paused. */
    val alarmsPausedUntil: Instant? = null,
) {
    init {
        require(loudFrom < dayEnd) { "loud must start before the end of the day" }
        require(askBlockerAfter in 1..5) { "ask after 1..5 stuck occurrences" }
    }

    fun isLoud(time: LocalTime) = time >= loudFrom

    fun alarmsPaused(now: Instant): Boolean = alarmsPausedUntil?.let { now < it } == true

    /** The settings that apply on [date]: on a weekend day its own times, if set. */
    fun on(date: LocalDate): AlertSettings =
        if (weekend != null && date.dayOfWeek in WEEKEND) copy(loudFrom = weekend.loudFrom, dayEnd = weekend.dayEnd) else this

    companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        /** "Bis ich es wieder einschalte". */
        val PAUSED_FOR_GOOD: Instant = Instant.parse("9999-01-01T00:00:00Z")
    }
}

enum class AlarmLevel {
    /** Notification without sound or vibration. */
    SILENT,
    /** Heads-up notification with vibration. */
    BANNER,
    /** Full screen with looping vibration. */
    FULLSCREEN,
    /** Full screen, vibration and alarm tone. */
    FULLSCREEN_LOUD,
}

object Escalation {
    /** How long one ringing round lasts without reaction. */
    val ROUND: Duration = Duration.ofMinutes(3)
    /** Pause between two rounds; also while "do not disturb" keeps them silent. */
    val REPEAT: Duration = Duration.ofMinutes(10)
    /** After leaving the proof the escalation goes on this much later. */
    val PROOF_GRACE: Duration = Duration.ofMinutes(1)

    /**
     * [doNotDisturb]: the phone is on "do not disturb"; [ignoreDnd]: the reminder rings anyway.
     */
    fun level(
        strictness: Strictness,
        time: LocalTime,
        settings: AlertSettings,
        doNotDisturb: Boolean = false,
        ignoreDnd: Boolean = false,
    ): AlarmLevel {
        if (doNotDisturb && !ignoreDnd) return AlarmLevel.SILENT
        return when (strictness) {
            Strictness.RELAXED -> AlarmLevel.BANNER
            Strictness.NORMAL -> AlarmLevel.FULLSCREEN
            Strictness.STRICT -> if (settings.isLoud(time)) AlarmLevel.FULLSCREEN_LOUD else AlarmLevel.FULLSCREEN
        }
    }

    /** When to ring again for an open, already fired occurrence. */
    fun nextRound(now: Instant): Instant = now.plus(REPEAT)

    /**
     * An occurrence without reaction counts as missed at the end of the day it is due, with the
     * end of that day (weekend or weekday, VS2). Due at 02:00 → 23:00 the same day; due at 23:30 →
     * 23:00 the next day.
     */
    fun missedAt(currentAt: Instant, zone: ZoneId, settings: AlertSettings): Instant {
        val local = currentAt.atZone(zone)
        val day = local.toLocalDate()
        val date = if (local.toLocalTime() < settings.on(day).dayEnd) day else day.plusDays(1)
        return date.atTime(settings.on(date).dayEnd).atZone(zone).toInstant()
    }

    /** The snooze buttons that stay before the end of the day; a later one would dodge "missed". */
    fun snoozeOptions(minutes: List<Int>, now: Instant, missedAt: Instant): List<Int> =
        minutes.filter { now.plus(Duration.ofMinutes(it.toLong())) < missedAt }
}
