package com.avlreminder.core

import com.avlreminder.core.model.Event
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.ReminderRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

/** 2026-10-26 is a Monday. */
fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 10): Instant =
    LocalDateTime.of(2026, month, day, hour, minute).atZone(ZONE).toInstant()

class MutableClock(var now: Instant, private val zone: ZoneId = ZONE) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

fun reminder(
    schedule: Schedule = Schedule.Daily(LocalTime.of(18, 0)),
    created: Instant = at(26, 8),
) = Reminder(title = "Katzenbrunnen", schedule = schedule, createdAt = created)

class FakeAlarms : AlarmPort {
    val set = mutableMapOf<Long, Pair<Instant, AlarmKind>>()
    override fun set(occurrenceId: Long, at: Instant, kind: AlarmKind) {
        set[occurrenceId] = at to kind
    }
    override fun cancel(occurrenceId: Long) {
        set.remove(occurrenceId)
    }
}

class FakeRepository : ReminderRepository {
    val reminders = linkedMapOf<Long, Reminder>()
    val occurrences = linkedMapOf<Long, Occurrence>()
    val events = mutableListOf<Event>()
    val proofs = mutableListOf<Proof>()
    private var nextId = 1L

    override suspend fun reminders() = reminders.values.toList()
    override suspend fun reminder(id: Long) = reminders[id]
    override suspend fun saveReminder(reminder: Reminder): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
        reminders[id] = reminder.copy(id = id)
        return id
    }
    override suspend fun deleteReminder(id: Long) {
        reminders.remove(id)
        val ids = occurrences.values.filter { it.reminderId == id }.map { it.id }.toSet()
        ids.forEach { occurrences.remove(it) }
        events.removeAll { it.occurrenceId in ids }
        proofs.removeAll { it.occurrenceId in ids }
    }

    override suspend fun occurrence(id: Long) = occurrences[id]
    override suspend fun openOccurrences() = occurrences.values.filter { it.isOpen }
    override suspend fun openOccurrence(reminderId: Long) =
        occurrences.values.firstOrNull { it.reminderId == reminderId && it.isOpen }
    override suspend fun lastResolved(reminderId: Long) =
        occurrences.values.filter { it.reminderId == reminderId && !it.isOpen }.maxByOrNull { it.resolvedAt!! }
    override suspend fun lastDone(reminderId: Long) =
        occurrences.values.filter { it.reminderId == reminderId && it.state == OccurrenceState.DONE }.maxByOrNull { it.resolvedAt!! }
    override suspend fun countResolved(reminderId: Long, resolution: Resolution, from: Instant, until: Instant) =
        occurrences.values.count {
            it.reminderId == reminderId && it.resolution == resolution && it.resolvedAt!! >= from && it.resolvedAt < until
        }
    override suspend fun saveOccurrence(occurrence: Occurrence): Long {
        val id = if (occurrence.id == 0L) nextId++ else occurrence.id
        occurrences[id] = occurrence.copy(id = id)
        return id
    }

    override suspend fun addEvent(event: Event) {
        events += event
    }
    override suspend fun addProof(proof: Proof) {
        proofs += proof
    }
}
