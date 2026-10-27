package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Strictness.NORMAL
import com.avlreminder.core.model.Strictness.RELAXED
import com.avlreminder.core.model.Strictness.STRICT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

class EscalationTest {
    private val s = AlertSettings()
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `do not disturb makes everything silent`() {
        for (strictness in listOf(RELAXED, NORMAL, STRICT)) {
            assertEquals(AlarmLevel.SILENT, Escalation.level(strictness, t(12), s, doNotDisturb = true))
            assertEquals(AlarmLevel.SILENT, Escalation.level(strictness, t(19), s, doNotDisturb = true))
        }
    }

    @Test
    fun `without do not disturb the night is no longer silent`() {
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(NORMAL, t(2), s))
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(NORMAL, t(22), s))
    }

    @Test
    fun `reminders that ignore do not disturb ring as usual`() {
        assertEquals(AlarmLevel.BANNER, Escalation.level(RELAXED, t(12), s, doNotDisturb = true, ignoreDnd = true))
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(NORMAL, t(6), s, doNotDisturb = true, ignoreDnd = true))
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, Escalation.level(STRICT, t(19), s, doNotDisturb = true, ignoreDnd = true))
    }

    @Test
    fun `relaxed shows a banner with vibration`() {
        assertEquals(AlarmLevel.BANNER, Escalation.level(RELAXED, t(12), s))
    }

    @Test
    fun `normal is full screen but never loud`() {
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(NORMAL, t(7), s))
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(NORMAL, t(19), s))
    }

    @Test
    fun `strict is loud from 18`() {
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(STRICT, t(17, 59), s))
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, Escalation.level(STRICT, t(18), s))
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, Escalation.level(STRICT, t(23, 30), s))
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(STRICT, t(2), s))
    }

    @Test
    fun `settings must be consistent`() {
        assertThrows<IllegalArgumentException> { AlertSettings(loudFrom = t(23), dayEnd = t(22)) }
    }

    @Test
    fun `next round always comes ten minutes later`() {
        assertEquals(at(26, 12, 10), Escalation.nextRound(at(26, 12)))
        assertEquals(at(26, 2, 10), Escalation.nextRound(at(26, 2)))
    }

    @Test
    fun `missed at the end of the due day`() {
        assertEquals(at(26, 23), Escalation.missedAt(at(26, 18), ZONE, s))
        assertEquals(at(26, 23), Escalation.missedAt(at(26, 2), ZONE, s))
    }

    @Test
    fun `due after the day end means missed at the next day end`() {
        assertEquals(at(27, 23), Escalation.missedAt(at(26, 23, 30), ZONE, s))
        assertEquals(at(27, 22), Escalation.missedAt(at(26, 22), ZONE, AlertSettings(dayEnd = t(22))))
    }

    @Test
    fun `snooze buttons past the day end are hidden`() {
        val missed = at(26, 23)
        assertEquals(listOf(30, 60), Escalation.snoozeOptions(listOf(30, 60), at(26, 21), missed))
        assertEquals(listOf(30), Escalation.snoozeOptions(listOf(30, 60), at(26, 22), missed))
        assertEquals(emptyList<Int>(), Escalation.snoozeOptions(listOf(30, 60), at(26, 22, 30), missed))
    }
}
