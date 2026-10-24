package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Strictness.NORMAL
import com.avlreminder.core.model.Strictness.RELAXED
import com.avlreminder.core.model.Strictness.STRICT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

class EscalationTest {
    private val w = EscalationWindows()
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `everything is silent outside the visible window`() {
        for (s in listOf(RELAXED, NORMAL, STRICT)) {
            assertEquals(AlarmLevel.SILENT, Escalation.level(s, t(6, 59), w))
            assertEquals(AlarmLevel.SILENT, Escalation.level(s, t(21, 0), w))
            assertEquals(AlarmLevel.SILENT, Escalation.level(s, t(2), w))
        }
    }

    @Test
    fun `relaxed stays silent inside the window`() {
        assertEquals(AlarmLevel.SILENT, Escalation.level(RELAXED, t(12), w))
    }

    @Test
    fun `normal shows a banner with vibration`() {
        assertEquals(AlarmLevel.BANNER, Escalation.level(NORMAL, t(7), w))
        assertEquals(AlarmLevel.BANNER, Escalation.level(NORMAL, t(19), w))
    }

    @Test
    fun `strict is full screen and loud only from 18 to 21`() {
        assertEquals(AlarmLevel.FULLSCREEN, Escalation.level(STRICT, t(17, 59), w))
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, Escalation.level(STRICT, t(18), w))
        assertEquals(AlarmLevel.FULLSCREEN_LOUD, Escalation.level(STRICT, t(20, 59), w))
    }

    @Test
    fun `windows must be consistent`() {
        assertThrows<IllegalArgumentException> { EscalationWindows(visibleFrom = t(22), visibleUntil = t(7)) }
        assertThrows<IllegalArgumentException> { EscalationWindows(loudFrom = t(22)) }
    }

    @Test
    fun `relaxed reminders do not repeat`() {
        assertNull(Escalation.nextRound(RELAXED, at(26, 12), ZONE, w))
    }

    @Test
    fun `next round comes ten minutes later inside the window`() {
        assertEquals(at(26, 12, 10), Escalation.nextRound(NORMAL, at(26, 12), ZONE, w))
    }

    @Test
    fun `next round waits for the window to open`() {
        assertEquals(at(27, 7), Escalation.nextRound(STRICT, at(26, 20, 55), ZONE, w))
        assertEquals(at(26, 7), Escalation.nextRound(STRICT, at(26, 2), ZONE, w))
    }

    @Test
    fun `missed at the end of the window of the due day`() {
        assertEquals(at(26, 21), Escalation.missedAt(at(26, 18), ZONE, w))
        assertEquals(at(26, 21), Escalation.missedAt(at(26, 2), ZONE, w))
    }

    @Test
    fun `due after the window means missed at the end of the next day's window`() {
        assertEquals(at(27, 21), Escalation.missedAt(at(26, 21, 30), ZONE, w))
    }
}
