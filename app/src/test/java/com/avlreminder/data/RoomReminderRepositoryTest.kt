package com.avlreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RoomReminderRepositoryTest {
    private lateinit var db: AvlDatabase
    private lateinit var repo: RoomReminderRepository
    private val t0 = Instant.parse("2026-10-26T07:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AvlDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomReminderRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun reminder(schedule: Schedule) = Reminder(
        title = "Training", why = "fit bleiben", strictness = Strictness.STRICT, schedule = schedule,
        goal = "45 min", minimum = "10 min", minimumLimitPerWeek = 1, proof = ProofSpec.PHOTO,
        postponeLimit = 1, createdAt = t0,
    )

    @Test
    fun everyScheduleTypeSurvivesTheRoundTrip() = runTest {
        val schedules = listOf(
            Schedule.Once(LocalDateTime.of(2026, 10, 28, 9, 30)),
            Schedule.Daily(LocalTime.of(18, 0)),
            Schedule.Weekdays(LocalTime.of(6, 45), setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            Schedule.AfterDone(14, LocalTime.of(21, 0)),
            Schedule.AfterDone(14, LocalTime.of(21, 0), firstOn = LocalDate.of(2026, 10, 30)),
        )
        for (s in schedules) {
            val r = reminder(s)
            val id = repo.saveReminder(r)
            assertEquals(r.copy(id = id), repo.reminder(id))
        }
        assertEquals(5, repo.reminders().size)
    }

    @Test
    fun updateKeepsTheId() = runTest {
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        assertEquals(id, repo.saveReminder(repo.reminder(id)!!.copy(title = "Neu", active = false)))
        assertEquals("Neu", repo.reminder(id)!!.title)
        assertFalse(repo.reminder(id)!!.active)
    }

    @Test
    fun occurrenceQueries() = runTest {
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        val done1 = Occurrence(reminderId = id, dueAt = t0, state = OccurrenceState.DONE,
            resolution = Resolution.MINIMUM, resolvedAt = t0.plusSeconds(60))
        val skipped = Occurrence(reminderId = id, dueAt = t0.plusSeconds(86_400), state = OccurrenceState.CLOSED,
            resolution = Resolution.SKIPPED, resolvedAt = t0.plusSeconds(86_460))
        val open = Occurrence(reminderId = id, dueAt = t0.plusSeconds(172_800))
        val d1 = repo.saveOccurrence(done1)
        val s1 = repo.saveOccurrence(skipped)
        val o1 = repo.saveOccurrence(open)

        assertEquals(o1, repo.openOccurrence(id)!!.id)
        assertEquals(listOf(o1), repo.openOccurrences().map { it.id })
        assertEquals(s1, repo.lastResolved(id)!!.id)
        assertEquals(d1, repo.lastDone(id)!!.id)
        assertEquals(1, repo.countResolved(id, Resolution.MINIMUM, t0, t0.plusSeconds(3600)))
        assertEquals(0, repo.countResolved(id, Resolution.MINIMUM, t0.plusSeconds(3600), t0.plusSeconds(7200)))
        assertEquals(2, db.dao().observeResolved().first().size)

        repo.saveOccurrence(repo.occurrence(o1)!!.copy(state = OccurrenceState.DUE, postponeCount = 1))
        assertEquals(OccurrenceState.DUE, repo.occurrence(o1)!!.state)
        assertEquals(1, repo.occurrence(o1)!!.postponeCount)
    }

    @Test
    fun deletingRemovesEverythingIncludingPhotos() = runTest {
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        val o = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0))
        repo.addEvent(Event(occurrenceId = o, at = t0, type = EventType.CREATED))
        val photo = File.createTempFile("proof", ".jpg")
        repo.addProof(Proof(occurrenceId = o, type = ProofType.PHOTO, filePath = photo.path, takenAt = t0))
        assertEquals(1, db.dao().events(o).size)
        assertEquals(1, db.dao().observeProofs().first().size)

        repo.deleteReminder(id)
        assertNull(repo.reminder(id))
        assertNull(repo.occurrence(o))
        assertTrue(db.dao().events(o).isEmpty())
        assertTrue(db.dao().observeProofs().first().isEmpty())
        assertFalse(photo.exists())
    }

    @Test
    fun newReminderFieldsSurviveTheRoundTrip() = runTest {
        val r = reminder(Schedule.Daily(LocalTime.NOON)).copy(
            proof = ProofSpec(ProofKind.EVIDENCE, photo = false, textMinChars = 50, audioMinSeconds = 10, windowMinutes = 15),
            ignoreDnd = true, snoozeMinutes = listOf(10, 30), motivationAudio = "/audio/m.m4a",
        )
        val id = repo.saveReminder(r)
        assertEquals(r.copy(id = id), repo.reminder(id))

        val o = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0, state = OccurrenceState.DUE, pausedUntil = t0.plusSeconds(600)))
        assertEquals(t0.plusSeconds(600), repo.occurrence(o)!!.pausedUntil)
    }

    @Test
    fun lastProofTextIsTheNewestOfThisReminder() = runTest {
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        val other = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        assertNull(repo.lastProofText(id))
        val o1 = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0))
        val o2 = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0.plusSeconds(86_400)))
        val o3 = repo.saveOccurrence(Occurrence(reminderId = other, dueAt = t0.plusSeconds(90_000)))
        repo.addProof(Proof(occurrenceId = o1, type = ProofType.TEXT, filePath = null, takenAt = t0, text = "alt"))
        repo.addProof(Proof(occurrenceId = o2, type = ProofType.TEXT, filePath = null, takenAt = t0.plusSeconds(86_400), text = "neu"))
        repo.addProof(Proof(occurrenceId = o3, type = ProofType.TEXT, filePath = null, takenAt = t0.plusSeconds(90_000), text = "anderer"))
        assertEquals("neu", repo.lastProofText(id))
    }

    @Test
    fun prepAnswersAreKeptAsHistoryAndDeletedWithTheReminder() = runTest {
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        repo.addPrepAnswer(PrepAnswer(reminderId = id, question = 1, text = "Ruhe", at = t0))
        repo.addPrepAnswer(PrepAnswer(reminderId = id, question = 2, text = null, unknown = true, at = t0))
        repo.addPrepAnswer(PrepAnswer(reminderId = id, question = 1, text = "mehr Ruhe", at = t0.plusSeconds(60)))
        assertEquals(listOf("Ruhe", null, "mehr Ruhe"), repo.prepAnswers(id).map { it.text })
        assertTrue(repo.prepAnswers(id)[1].unknown)

        repo.deleteReminder(id)
        assertTrue(repo.prepAnswers(id).isEmpty())
    }

    @Test
    fun replacedMotivationAudioIsDeleted() = runTest {
        val first = File.createTempFile("motivation", ".m4a")
        val second = File.createTempFile("motivation", ".m4a")
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)).copy(motivationAudio = first.path))
        repo.saveReminder(repo.reminder(id)!!.copy(motivationAudio = second.path))
        assertFalse(first.exists())
        assertTrue(second.exists())

        repo.deleteReminder(id)
        assertFalse(second.exists())
    }

    @Test
    fun weekdayBitsCoverTheWholeWeek() {
        assertEquals(DayOfWeek.entries.toSet(), DayOfWeek.entries.toSet().toBits().toDays())
        assertEquals(0b1000001, setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).toBits())
    }
}
