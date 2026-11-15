package com.avlreminder.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.model.WhenAway
import com.avlreminder.ui.screens.PrepDraft
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

    /** The same start values as the interface for Claude and the widget (Review-Lauf 5, E21). */
    @Test
    fun aNewFormGivesTheReminderEveryoneStartsWith() {
        val form = ReminderForm.new(now, zone).copy(title = "Lesen")
        val r = form.toReminder(now)!!
        assertEquals(com.avlreminder.core.rules.Presets.newReminder("Lesen", r.schedule, r.createdAt), r)
    }

    @Test
    fun strictnessAppliesThePreset() {
        val strict = ReminderForm.new(now, zone).withStrictness(Strictness.STRICT)
        assertEquals(ProofSpec.PHOTO, strict.proof)
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

    @Test
    fun onlyAnsweredPrepQuestionsAreSaved() {
        val form = ReminderForm.new(now, zone).copy(
            id = 4,
            prep = mapOf(1 to PrepDraft(" Ruhe "), 2 to PrepDraft(unknown = true), 3 to PrepDraft("  ")),
        )
        val answers = form.prepAnswers(now)
        assertEquals(listOf(1, 2), answers.map { it.question })
        assertEquals("Ruhe", answers[0].text)
        assertNull(answers[1].text)
        assertEquals(true, answers[1].unknown)
        assertEquals(4L, answers[0].reminderId)
    }

    @Test
    fun newFieldsGoIntoTheReminderAndBack() {
        val form = ReminderForm.new(now, zone).copy(
            title = "Steuer", ignoreDnd = true, snoozeMinutes = listOf(30, 60), motivationAudio = "/a.m4a",
        )
        val r = form.toReminder(now)!!
        assertEquals(true, r.ignoreDnd)
        assertEquals(listOf(30, 60), r.snoozeMinutes)
        assertEquals("/a.m4a", r.motivationAudio)
        val back = ReminderForm.of(r, form.date)
        assertEquals(form.snoozeMinutes, back.snoozeMinutes)
        assertEquals(form.motivationAudio, back.motivationAudio)
    }

    @Test
    fun arrivalNeedsTheHomeAndSurvivesEditing() {
        val base = ReminderForm.new(now, zone, placeId = 7).copy(title = "Training", time = LocalTime.of(19, 0))
        assertNull(base.toReminder(now)!!.arrival)

        val dropped = base.copy(atHome = true).toReminder(now)!!
        assertEquals(Arrival(7, WhenAway.DROP), dropped.arrival)
        val ringing = base.copy(atHome = true, whenAway = WhenAway.RING).toReminder(now)!!
        assertEquals(Arrival(7, WhenAway.RING), ringing.arrival)
        val waiting = base.copy(kind = ScheduleKind.ON_ARRIVAL).toReminder(now)!!
        assertEquals(Schedule.OnArrival, waiting.schedule)
        assertEquals(7L, waiting.arrival!!.placeId)

        for (r in listOf(dropped, ringing, waiting)) {
            assertEquals(r, ReminderForm.of(r.copy(id = 5), LocalDate.of(2026, 10, 24)).toReminder(now)!!.copy(id = 0))
        }
        // Without "Zuhause" the arrival stays, without a place, so the engine says what is missing
        // instead of saving without it (Review-Lauf 5).
        val noPlace = ReminderForm.new(now, zone).copy(title = "Training")
        assertEquals(0L, noPlace.copy(kind = ScheduleKind.ON_ARRIVAL).toReminder(now)!!.arrival!!.placeId)
        assertEquals(Arrival(0, WhenAway.DROP), noPlace.copy(atHome = true).toReminder(now)!!.arrival)
    }

    /** The weekly limit also applies to the minimum duration of a session (Review-Lauf 5, 6b). */
    @Test
    fun theWeeklyLimitKeepsForTheMinimumDurationOfASession() {
        val form = ReminderForm.new(now, zone).copy(title = "Training", minimum = "", minimumLimitPerWeek = 2, session = true, sessionMinimum = 10)
        assertTrue(form.hasMinimum)
        assertEquals(2, form.toReminder(now)!!.minimumLimitPerWeek)
        assertNull(form.copy(sessionMinimum = null).toReminder(now)!!.minimumLimitPerWeek)
        assertNull(form.copy(session = false).toReminder(now)!!.minimumLimitPerWeek)
    }

    // KRITISCH: Vorlauf, Puffer und Prüfabstand überstehen das Bearbeiten (Anf. 7.29); Feineinstellungen nur mit Zeitraum.
    @Test
    fun theArrivalWindowSurvivesEditingAndHasFineSettingsOnlyWithALead() {
        val base = ReminderForm.new(now, zone, placeId = 7).copy(title = "Training", time = LocalTime.of(19, 30), atHome = true)
        val r = base.copy(lead = 60, buffer = 10, check = 30).toReminder(now)!!
        assertEquals(Arrival(7, WhenAway.DROP, leadMinutes = 60, bufferMinutes = 10, checkMinutes = 30), r.arrival)
        assertEquals(r, ReminderForm.of(r.copy(id = 5), LocalDate.of(2026, 11, 11)).toReminder(now)!!.copy(id = 0))

        assertTrue("Standard: 90 min Zeitraum", base.hasWindow)
        assertFalse("Vorlauf 0 wie bisher", base.copy(lead = 0).hasWindow)
        assertFalse("nur Uhrzeit", base.copy(atHome = false).hasWindow)
        assertFalse("wenn ich ankomme", base.copy(kind = ScheduleKind.ON_ARRIVAL).hasWindow)
    }

    @Test
    fun emptyChecklistItemsAreDroppedAndIdsKept() {
        val form = ReminderForm.new(now, zone).copy(
            title = "Katzen",
            checklist = listOf(ChecklistItem(3, " Brunnen 1 "), ChecklistItem(title = "  "), ChecklistItem(title = "Futter")),
        )
        val r = form.toReminder(now)!!
        assertEquals(listOf(ChecklistItem(3, "Brunnen 1"), ChecklistItem(0, "Futter")), r.checklist)
        assertEquals(r.checklist, ReminderForm.of(r, LocalDate.of(2026, 10, 24)).checklist)
    }

    /**
     * Every field set away from its default survives opening and saving the form; only what the
     * form does not know (archive, quiet time of the card) is left to the engine (Review-Lauf 5, E22).
     */
    @Test
    fun aReminderWithEveryFieldSurvivesTheForm() {
        val full = Reminder(
            id = 7, title = "Training", why = "Rücken", character = Character.DOCUMENT, strictness = Strictness.STRICT,
            schedule = Schedule.Weekdays(LocalTime.of(7, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            goal = "45 min", minimum = "10 min", minimumLimitPerWeek = 2,
            proof = ProofSpec(ProofKind.EVIDENCE, photo = true, textMinChars = 20, audioMinSeconds = 5, windowMinutes = 15),
            postponeLimit = 2, active = false, createdAt = now.minusSeconds(86_400), ignoreDnd = true,
            snoozeMinutes = listOf(5, 15), motivationAudio = "audio/7.m4a",
            arrival = Arrival(3, WhenAway.RING, leadMinutes = 60, bufferMinutes = 10, checkMinutes = 15),
            checklist = listOf(ChecklistItem(11, "Matte"), ChecklistItem(12, "Wasser")),
            weeklyGoal = WeeklyGoal(2, 3, 1), preNotice = PreNotice.Before(45), prepFor = 4, solvedBlockerIsMinimum = true,
            session = SessionSpec(15, Strictness.STRICT, goalMinutes = 45, minimumMinutes = 10, maxMinutes = 60, finalMinutes = 3),
            archivedAt = now, hintQuietUntil = now,
        )
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(full.copy(archivedAt = null, hintQuietUntil = null), ReminderForm.of(full, today).toReminder(now))

        val deadline = full.copy(
            schedule = Schedule.Deadline(LocalDateTime.of(2026, 10, 30, 12, 0), setOf(DayOfWeek.FRIDAY), LocalTime.of(8, 0), LocalTime.of(10, 0), 30),
            arrival = null, weeklyGoal = null, preNotice = PreNotice.Evening(LocalTime.of(20, 30)),
        )
        assertEquals(deadline.copy(archivedAt = null, hintQuietUntil = null), ReminderForm.of(deadline, today).toReminder(now))
    }
}
