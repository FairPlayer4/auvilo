package com.avlreminder.core.rules

import com.avlreminder.core.model.Strictness
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Global time windows (settings). Outside [visibleFrom]..[visibleUntil] everything is silent. */
data class EscalationWindows(
    val visibleFrom: LocalTime = LocalTime.of(7, 0),
    val visibleUntil: LocalTime = LocalTime.of(21, 0),
    val loudFrom: LocalTime = LocalTime.of(18, 0),
) {
    init {
        require(visibleFrom < visibleUntil) { "visible window must not cross midnight" }
        require(loudFrom in visibleFrom..visibleUntil) { "loud window must lie inside the visible window" }
    }

    fun isVisible(time: LocalTime) = time >= visibleFrom && time < visibleUntil
    fun isLoud(time: LocalTime) = time >= loudFrom && time < visibleUntil
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
    /** Pause between two rounds. */
    val REPEAT: Duration = Duration.ofMinutes(10)

    fun level(strictness: Strictness, time: LocalTime, windows: EscalationWindows): AlarmLevel {
        if (!windows.isVisible(time)) return AlarmLevel.SILENT
        return when (strictness) {
            Strictness.RELAXED -> AlarmLevel.SILENT
            Strictness.NORMAL -> AlarmLevel.BANNER
            Strictness.STRICT -> if (windows.isLoud(time)) AlarmLevel.FULLSCREEN_LOUD else AlarmLevel.FULLSCREEN
        }
    }

    /**
     * When to ring again for an open, already fired occurrence. Relaxed reminders do not repeat.
     * Outside the visible window the next round waits for the window to open.
     */
    fun nextRound(strictness: Strictness, now: Instant, zone: ZoneId, windows: EscalationWindows): Instant? {
        if (strictness == Strictness.RELAXED) return null
        val candidate = now.plus(REPEAT)
        val local = candidate.atZone(zone)
        if (windows.isVisible(local.toLocalTime())) return candidate
        val date = if (local.toLocalTime() < windows.visibleFrom) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return date.atTime(windows.visibleFrom).atZone(zone).toInstant()
    }

    /**
     * An occurrence without reaction counts as missed at the end of the first visible window
     * that ends after it became due. Due at 02:00 → 21:00 the same day; due at 21:30 → 21:00 next day.
     */
    fun missedAt(currentAt: Instant, zone: ZoneId, windows: EscalationWindows): Instant {
        val local = currentAt.atZone(zone)
        val date = if (local.toLocalTime() < windows.visibleUntil) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return date.atTime(windows.visibleUntil).atZone(zone).toInstant()
    }
}
