package com.avlreminder.data

import androidx.room.withTransaction
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.ports.ReminderRepository
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class RoomReminderRepository(private val db: AvlDatabase) : ReminderRepository {
    private val dao = db.dao()

    override suspend fun reminders() = dao.reminders().map { it.toModel() }
    override suspend fun reminder(id: Long) = dao.reminder(id)?.toModel()

    override suspend fun saveReminder(reminder: Reminder): Long {
        val entity = reminder.toEntity()
        if (reminder.id == 0L) return dao.insert(entity)
        val oldAudio = dao.reminder(reminder.id)?.motivationAudio
        dao.update(entity)
        // A replaced or removed motivation audio is no longer referenced anywhere.
        if (oldAudio != null && oldAudio != reminder.motivationAudio) File(oldAudio).delete()
        return reminder.id
    }

    /** Also deletes the proof files and the motivation audio; rows go with the foreign-key cascade. */
    override suspend fun deleteReminder(id: Long) {
        val files = db.withTransaction {
            val files = dao.proofFiles(id) + listOfNotNull(dao.reminder(id)?.motivationAudio)
            dao.deleteReminder(id)
            files
        }
        files.forEach { File(it).delete() }
    }

    override suspend fun occurrence(id: Long) = dao.occurrence(id)?.toModel()
    override suspend fun openOccurrences() = dao.openOccurrences().map { it.toModel() }
    override suspend fun openOccurrence(reminderId: Long) = dao.openOccurrence(reminderId)?.toModel()
    override suspend fun lastResolved(reminderId: Long) = dao.lastResolved(reminderId)?.toModel()
    override suspend fun lastDone(reminderId: Long) = dao.lastDone(reminderId)?.toModel()
    override suspend fun countResolved(reminderId: Long, resolution: Resolution, from: Instant, until: Instant) =
        dao.countResolved(reminderId, resolution.name, from, until)

    override suspend fun saveOccurrence(occurrence: Occurrence): Long {
        val entity = occurrence.toEntity()
        if (occurrence.id == 0L) return dao.insert(entity)
        dao.update(entity)
        return occurrence.id
    }

    override suspend fun addEvent(event: Event) =
        dao.insert(EventEntity(occurrenceId = event.occurrenceId, at = event.at, type = event.type, detail = event.detail))

    override suspend fun addProof(proof: Proof) = dao.insert(
        ProofEntity(occurrenceId = proof.occurrenceId, type = proof.type, filePath = proof.filePath, takenAt = proof.takenAt, text = proof.text),
    )

    override suspend fun lastProofText(reminderId: Long) = dao.lastProofText(reminderId)

    override suspend fun prepAnswers(reminderId: Long) = dao.prepAnswers(reminderId).map { it.toModel() }

    override suspend fun addPrepAnswer(answer: PrepAnswer) = dao.insert(
        PrepAnswerEntity(reminderId = answer.reminderId, question = answer.question, text = answer.text, unknown = answer.unknown, at = answer.at),
    )
}

// --- Mapping ----------------------------------------------------------------------------------

private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
private fun LocalTime.toMinutes(): Int = hour * 60 + minute

internal fun Set<DayOfWeek>.toBits(): Int = fold(0) { bits, day -> bits or (1 shl (day.value - 1)) }
internal fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

fun ReminderEntity.toModel(): Reminder = Reminder(
    id = id,
    title = title,
    why = why,
    character = character,
    strictness = strictness,
    schedule = when (scheduleType) {
        ScheduleType.ONCE -> Schedule.Once(requireNotNull(onceAt))
        ScheduleType.DAILY -> Schedule.Daily(requireNotNull(timeMinutes).toTime())
        ScheduleType.WEEKDAYS -> Schedule.Weekdays(requireNotNull(timeMinutes).toTime(), weekdays.toDays())
        // For AFTER_DONE, onceAt holds the first due day (no schema change needed).
        ScheduleType.AFTER_DONE -> Schedule.AfterDone(requireNotNull(intervalDays), requireNotNull(timeMinutes).toTime(), onceAt?.toLocalDate())
    },
    goal = goal,
    minimum = minimum,
    minimumLimitPerWeek = minimumLimitPerWeek,
    proof = ProofSpec(proofKind, proofPhoto, proofTextMinChars, proofAudioMinSeconds, proofWindowMinutes),
    postponeLimit = postponeLimit,
    active = active,
    createdAt = createdAt,
    ignoreDnd = ignoreDnd,
    snoozeMinutes = snoozeMinutes.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(10) },
    motivationAudio = motivationAudio,
)

fun Reminder.toEntity(): ReminderEntity {
    val s = schedule
    return ReminderEntity(
        id = id,
        title = title,
        why = why,
        character = character,
        strictness = strictness,
        scheduleType = when (s) {
            is Schedule.Once -> ScheduleType.ONCE
            is Schedule.Daily -> ScheduleType.DAILY
            is Schedule.Weekdays -> ScheduleType.WEEKDAYS
            is Schedule.AfterDone -> ScheduleType.AFTER_DONE
        },
        timeMinutes = when (s) {
            is Schedule.Once -> null
            is Schedule.Daily -> s.time.toMinutes()
            is Schedule.Weekdays -> s.time.toMinutes()
            is Schedule.AfterDone -> s.time.toMinutes()
        },
        onceAt = when (s) {
            is Schedule.Once -> s.at
            is Schedule.AfterDone -> s.firstOn?.atTime(s.time)
            else -> null
        },
        weekdays = (s as? Schedule.Weekdays)?.days?.toBits() ?: 0,
        intervalDays = (s as? Schedule.AfterDone)?.intervalDays,
        goal = goal,
        minimum = minimum,
        minimumLimitPerWeek = minimumLimitPerWeek,
        proofKind = proof.kind,
        postponeLimit = postponeLimit,
        active = active,
        createdAt = createdAt,
        proofPhoto = proof.photo,
        proofTextMinChars = proof.textMinChars,
        proofAudioMinSeconds = proof.audioMinSeconds,
        proofWindowMinutes = proof.windowMinutes,
        ignoreDnd = ignoreDnd,
        snoozeMinutes = snoozeMinutes.joinToString(","),
        motivationAudio = motivationAudio,
    )
}

fun OccurrenceEntity.toModel() = Occurrence(
    id = id, reminderId = reminderId, dueAt = dueAt, currentAt = currentAt, state = state,
    postponeCount = postponeCount, resolvedAt = resolvedAt, resolution = resolution, note = note,
    pausedUntil = pausedUntil,
)

fun Occurrence.toEntity() = OccurrenceEntity(
    id = id, reminderId = reminderId, dueAt = dueAt, currentAt = currentAt, state = state,
    postponeCount = postponeCount, resolvedAt = resolvedAt, resolution = resolution, note = note,
    pausedUntil = pausedUntil,
)

fun PrepAnswerEntity.toModel() = PrepAnswer(id = id, reminderId = reminderId, question = question, text = text, unknown = unknown, at = at)

fun ProofEntity.toModel() = Proof(id = id, occurrenceId = occurrenceId, type = type, filePath = filePath, takenAt = takenAt, text = text)
