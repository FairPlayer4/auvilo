package com.avlreminder.core.usecases

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.reminder
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.PauseLength
import com.avlreminder.core.rules.Presets
import com.avlreminder.core.usecases.PostponeTarget.Kind
import java.time.Instant
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Time rules that used to live in the app (Review-Lauf 7, E30; V1.3c14 U1): the test alarm, the
 * end of "Notaus" and the targets of "Verschieben".
 */
class TimeRulesFromAppTest {

    @Test
    fun `the test alarm rings one to two minutes ahead, counted from the start of the minute`() {
        val now = at(27, 14, 29).plusSeconds(40)
        assertEquals(LocalDateTime.of(2026, 10, 27, 14, 31), Presets.probeAt(now, ZONE))
        assertEquals(LocalDateTime.of(2026, 10, 27, 14, 31), Presets.probeAt(at(27, 14, 29), ZONE))
        val probe = Presets.probe("Probealarm", now, ZONE)
        assertEquals(Schedule.Once(LocalDateTime.of(2026, 10, 27, 14, 31)), probe.schedule)
        assertEquals(Character.DOCUMENT, probe.character)
        assertEquals(Strictness.NORMAL, probe.strictness)
        assertEquals(now, probe.createdAt)
    }

    @Test
    fun `the test alarm crosses midnight and the change of time`() {
        assertEquals(LocalDateTime.of(2026, 10, 28, 0, 1), Presets.probeAt(at(27, 23, 59).plusSeconds(30), ZONE))
        // 2026-10-25 03:00 summer time becomes 02:00: local time goes on from the wall clock.
        val beforeChange = Instant.parse("2026-10-25T00:59:30Z") // 02:59:30 summer time
        assertEquals(LocalDateTime.of(2026, 10, 25, 3, 1), Presets.probeAt(beforeChange, ZONE))
    }

    @Test
    fun `Notaus ends after an hour, at six in the morning or never`() {
        val afternoon = at(27, 14)
        assertEquals(at(27, 15), PauseLength.HOUR.until(afternoon, ZONE))
        assertEquals(at(28, 6), PauseLength.MORNING.until(afternoon, ZONE))
        assertEquals(at(28, 6), PauseLength.MORNING.until(at(28, 3), ZONE))
        assertEquals(at(29, 6), PauseLength.MORNING.until(at(28, 6), ZONE))
        assertEquals(AlertSettings.PAUSED_FOR_GOOD, PauseLength.FOR_GOOD.until(afternoon, ZONE))
        // The night of the change of time: six o'clock is wall-clock time.
        assertEquals(at(25, 6), PauseLength.MORNING.until(at(25, 1), ZONE))
    }

    private fun view(due: Instant, early: Boolean = false, missedAt: Instant? = null, deadline: Instant? = null, nextTime: Instant? = null) =
        AlarmView(
            reminder = reminder(),
            occurrence = Occurrence(id = 1, reminderId = 1, dueAt = due),
            minimumAllowed = true, minimumsLeft = null, canPostpone = true, postponesLeft = null,
            early = early, missedAt = missedAt, deadline = deadline, nextTime = nextTime,
        )

    private fun AlarmView.targets(now: Instant) = postponeTargets(now, ZONE).map { it.kind to it.at }

    @Test
    fun `ringing it moves by an hour, to the evening or to tomorrow at its time`() {
        val ringing = view(at(27, 18))
        assertEquals(
            listOf(Kind.IN_AN_HOUR to at(27, 19, 5), Kind.EVENING to at(27, 20, 30), Kind.TOMORROW to at(28, 18)),
            ringing.targets(at(27, 18, 5)),
        )
        // The evening less than half an hour ahead is no target.
        assertEquals(listOf(Kind.IN_AN_HOUR to at(27, 21, 10), Kind.TOMORROW to at(28, 18)), ringing.targets(at(27, 20, 10)))
        // Tomorrow is counted from now: after midnight it is the day after.
        assertEquals(Kind.TOMORROW to at(29, 18), ringing.targets(at(28, 0, 30)).last())
    }

    @Test
    fun `in advance it moves from the planned time and only before it counts as missed`() {
        val early = view(at(27, 18), early = true, missedAt = at(27, 19, 30))
        assertEquals(
            listOf(Kind.PLUS_30 to at(27, 18, 30), Kind.PLUS_60 to at(27, 19), Kind.TOMORROW to at(28, 18)),
            early.targets(at(27, 15)),
        )
        assertEquals(listOf(Kind.PLUS_30, Kind.PLUS_60, Kind.PLUS_120, Kind.TOMORROW), view(at(27, 18), early = true).targets(at(27, 15)).map { it.first })
    }

    @Test
    fun `no target after the deadline, the next time or before now`() {
        assertEquals(listOf(Kind.IN_AN_HOUR to at(27, 19, 5)), view(at(27, 18), nextTime = at(27, 20)).targets(at(27, 18, 5)))
        assertEquals(listOf(Kind.IN_AN_HOUR to at(27, 19, 5)), view(at(27, 18), deadline = at(27, 20, 30)).targets(at(27, 18, 5)))
        // Due at 06:00 and opened at 23:30: tomorrow 06:00 still lies ahead, the evening does not.
        assertEquals(listOf(Kind.IN_AN_HOUR, Kind.TOMORROW), view(at(27, 6)).targets(at(27, 23, 30)).map { it.first })
    }

    @Test
    fun `rest on tomorrow keeps the time of day it was due`() {
        assertEquals(at(28, 18), view(at(27, 18)).tomorrowAtDueTime(at(27, 22), ZONE))
    }
}
