package com.avlreminder.core.rules

import com.avlreminder.core.model.Strictness
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Global alert settings. Quiet times come from the phone's "do not disturb";
 * [dayEnd] is when an unanswered occurrence counts as missed.
 */
data class AlertSettings(
    val loudFrom: LocalTime = LocalTime.of(18, 0),
    val dayEnd: LocalTime = LocalTime.of(23, 0),
    /** After this many stuck occurrences in a row the alarm asks what keeps one from it (S8). */
    val askBlockerAfter: Int = 2,
) {
    init {
        require(loudFrom < dayEnd) { "loud must start before the end of the day" }
        require(askBlockerAfter in 1..5) { "ask after 1..5 stuck occurrences" }
    }

    fun isLoud(time: LocalTime) = time >= loudFrom
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
     * An occurrence without reaction counts as missed at the end of the day it is due.
     * Due at 02:00 → 23:00 the same day; due at 23:30 → 23:00 the next day.
     */
    fun missedAt(currentAt: Instant, zone: ZoneId, settings: AlertSettings): Instant {
        val local = currentAt.atZone(zone)
        val date = if (local.toLocalTime() < settings.dayEnd) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return date.atTime(settings.dayEnd).atZone(zone).toInstant()
    }

    /** The snooze buttons that stay before the end of the day; a later one would dodge "missed". */
    fun snoozeOptions(minutes: List<Int>, now: Instant, missedAt: Instant): List<Int> =
        minutes.filter { now.plus(Duration.ofMinutes(it.toLong())) < missedAt }
}
