package com.avlreminder.ui

import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.ScheduleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderFormTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-24T16:20:00Z") // 18:20 in Berlin

    @Test
    fun newFormStartsAtTheNextFullHour() {
        val form = ReminderForm.new(now, zone)
        assertEquals(LocalDate.of(2026, 10, 24), form.date)
        assertEquals(LocalTime.of(19, 0), form.time)
        assertEquals(Strictness.NORMAL, form.strictness)
    }

    @Test
    fun strictnessAppliesThePreset() {
        val strict = ReminderForm.new(now, zone).withStrictness(Strictness.STRICT)
        assertEquals(ProofType.PHOTO, strict.proof)
        assertEquals(1, strict.postponeLimit)
        assertEquals(1, strict.minimumLimitPerWeek)
    }

    @Test
    fun weeklyLimitIsDroppedWithoutMinimum() {
        val r = ReminderForm.new(now, zone).withStrictness(Strictness.STRICT).copy(title = " Training ").toReminder(now)!!
        assertEquals("Training", r.title)
        assertNull(r.minimum)
        assertNull(r.minimumLimitPerWeek)
    }

    @Test
    fun weekdaysWithoutDaysIsIncomplete() {
        val form = ReminderForm.new(now, zone).copy(kind = ScheduleKind.WEEKDAYS, days = emptySet())
        assertNull(form.toReminder(now))
    }

    @Test
    fun everyScheduleSurvivesEditing() {
        val base = ReminderForm.new(now, zone).copy(title = "x", time = LocalTime.of(7, 30))
        val schedules = listOf(
            base.copy(kind = ScheduleKind.ONCE, date = LocalDate.of(2026, 10, 28)),
            base.copy(kind = ScheduleKind.DAILY),
            base.copy(kind = ScheduleKind.WEEKDAYS, days = setOf(DayOfWeek.TUESDAY)),
            base.copy(kind = ScheduleKind.AFTER_DONE, intervalDays = 3),
        ).map { it.toReminder(now)!! }
        assertEquals(Schedule.Once(LocalDateTime.of(2026, 10, 28, 7, 30)), schedules[0].schedule)
        for (r in schedules) {
            assertEquals(r, ReminderForm.of(r.copy(id = 5), LocalDate.of(2026, 10, 24)).toReminder(now)!!.copy(id = 0))
        }
    }
}
