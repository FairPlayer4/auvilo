package com.avlreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.rules.Lifecycle
import com.avlreminder.core.model.PostponePeriod
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
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

    // Das Limit pro Woche oder Monat zählt Verschieben und ein zählendes „Rest auf morgen“, wann es geschah (Z2b).
    @Test
    fun countPostponedCountsMovesOfTheReminderInTheSpan() = runTest {
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        val other = repo.saveReminder(reminder(Schedule.Daily(LocalTime.NOON)))
        val o1 = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0))
        val o2 = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0.plusSeconds(86_400)))
        val x = repo.saveOccurrence(Occurrence(reminderId = other, dueAt = t0))
        val to = t0.plusSeconds(90_000).toString()
        repo.addEvent(Event(occurrenceId = o1, at = t0, type = EventType.POSTPONED, detail = to))
        repo.addEvent(Event(occurrenceId = o2, at = t0.plusSeconds(60), type = EventType.DEFERRED, detail = to + Lifecycle.COUNTED))
        // Not counted: a free "Rest auf morgen", a snooze, another reminder, outside the span.
        repo.addEvent(Event(occurrenceId = o2, at = t0.plusSeconds(120), type = EventType.DEFERRED, detail = to))
        repo.addEvent(Event(occurrenceId = o1, at = t0.plusSeconds(180), type = EventType.SNOOZED, detail = "10 min"))
        repo.addEvent(Event(occurrenceId = x, at = t0, type = EventType.POSTPONED, detail = to))
        repo.addEvent(Event(occurrenceId = o1, at = t0.plusSeconds(3600), type = EventType.POSTPONED, detail = to))
        assertEquals(2, repo.countPostponed(id, t0, t0.plusSeconds(3600)))
        assertEquals(1, repo.countPostponed(id, t0.plusSeconds(61), t0.plusSeconds(7200)))
        assertEquals(1, repo.countPostponed(other, t0, t0.plusSeconds(3600)))

        repo.saveReminder(repo.reminder(id)!!.copy(postponePeriod = PostponePeriod.WEEK))
        assertEquals(PostponePeriod.WEEK, repo.reminder(id)!!.postponePeriod)
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
    fun placesAndArrivalSurviveTheRoundTrip() = runTest {
        val home = repo.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5))
        assertEquals(home, repo.savePlace(Place(id = home, name = "Zuhause", lat = 48.2, lon = 11.6, radiusMeters = 200)))
        assertEquals(listOf(Place(home, "Zuhause", 48.2, 11.6, 200)), repo.places())

        for (r in listOf(
            reminder(Schedule.Daily(LocalTime.of(19, 0))).copy(arrival = Arrival(home, WhenAway.RING)),
            reminder(Schedule.Daily(LocalTime.of(19, 0))).copy(arrival = Arrival(home)),
            reminder(Schedule.OnArrival).copy(arrival = Arrival(home)),
        )) {
            val id = repo.saveReminder(r)
            assertEquals(r.copy(id = id), repo.reminder(id))
        }
    }

    @Test
    fun itemsDoneOfAReminderComeOverAllItsOccurrencesInOrder() = runTest {
        val items = listOf(ChecklistItem(title = "a"), ChecklistItem(title = "b"))
        val id = repo.saveReminder(reminder(Schedule.Daily(LocalTime.of(18, 0))).copy(checklist = items))
        val other = repo.saveReminder(reminder(Schedule.Daily(LocalTime.of(18, 0))).copy(checklist = items))
        val (a, b) = repo.reminder(id)!!.checklist
        val o1 = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0, state = OccurrenceState.DONE))
        val o2 = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0.plusSeconds(3600)))
        val o3 = repo.saveOccurrence(Occurrence(reminderId = other, dueAt = t0))
        repo.addItemDone(ItemDone(occurrenceId = o2, itemId = b.id, doneAt = t0.plusSeconds(3600)))
        repo.addItemDone(ItemDone(occurrenceId = o1, itemId = a.id, doneAt = t0))
        repo.addItemDone(ItemDone(occurrenceId = o3, itemId = repo.reminder(other)!!.checklist.first().id, doneAt = t0))
        assertEquals(listOf(o1 to a.id, o2 to b.id), repo.itemsDoneOfReminder(id).map { it.occurrenceId to it.itemId })
    }

    @Test
    fun checklistKeepsItsIdsAndOrderAndTakesRemovedItemsAlong() = runTest {
        val r = reminder(Schedule.Daily(LocalTime.of(18, 0))).copy(
            checklist = listOf(ChecklistItem(title = "Brunnen 1"), ChecklistItem(title = "Brunnen 2"), ChecklistItem(title = "Futter")),
        )
        val id = repo.saveReminder(r)
        val saved = repo.reminder(id)!!
        assertEquals(listOf("Brunnen 1", "Brunnen 2", "Futter"), saved.checklist.map { it.title })
        assertTrue(saved.checklist.all { it.id > 0 })
        assertEquals(saved, repo.reminders().single())

        val (b1, b2, futter) = saved.checklist
        val o = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = t0, state = OccurrenceState.DUE, firstProgressAt = t0))
        repo.addItemDone(ItemDone(occurrenceId = o, itemId = b2.id, doneAt = t0, withProof = false))
        repo.addProof(Proof(occurrenceId = o, type = ProofType.TEXT, filePath = null, takenAt = t0, text = "sauber", itemId = b2.id))
        repo.addItemDone(ItemDone(occurrenceId = o, itemId = futter.id, doneAt = t0.plusSeconds(60)))

        // Reorder, rename, drop one, add one: kept items keep their id and their progress.
        repo.saveReminder(saved.copy(checklist = listOf(b2.copy(title = "Brunnen Küche"), ChecklistItem(title = "Neu"), b1)))
        val edited = repo.reminder(id)!!.checklist
        assertEquals(listOf("Brunnen Küche", "Neu", "Brunnen 1"), edited.map { it.title })
        assertEquals(b2.id, edited[0].id)
        assertEquals(b1.id, edited[2].id)
        assertEquals(listOf(ItemDone(repo.itemsDone(o).single().id, o, b2.id, t0, withProof = false)), repo.itemsDone(o))
        assertEquals(t0, repo.occurrence(o)!!.firstProgressAt)
        // The proof stays even if its item goes.
        assertEquals(b2.id, db.dao().observeProofs().first().single().toModel().itemId)

        repo.saveReminder(repo.reminder(id)!!.copy(checklist = emptyList()))
        assertTrue(repo.reminder(id)!!.checklist.isEmpty())
        assertTrue(repo.itemsDone(o).isEmpty())
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
