package com.avlreminder.data

import androidx.room.withTransaction
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.AtTimes
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PreNotice
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WeeklyGoal
import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.ports.ReminderRepository
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class RoomReminderRepository(private val db: AvlDatabase) : ReminderRepository {
    private val dao = db.dao()

    override suspend fun reminders(): List<Reminder> {
        val items = dao.allItems().groupBy { it.reminderId }
        return dao.reminders().map { it.toModel(items[it.id].orEmpty()) }
    }

    override suspend fun reminder(id: Long) = dao.reminder(id)?.toModel(dao.items(id))

    override suspend fun saveReminder(reminder: Reminder): Long {
        var oldAudio: String? = null
        val id = db.withTransaction {
            val entity = reminder.toEntity()
            val id = if (reminder.id == 0L) {
                dao.insert(entity)
            } else {
                oldAudio = dao.reminder(reminder.id)?.motivationAudio
                dao.update(entity)
                reminder.id
            }
            saveItems(id, reminder.checklist)
            id
        }
        // A replaced or removed motivation audio is no longer referenced anywhere.
        oldAudio?.takeIf { it != reminder.motivationAudio }?.let { File(it).delete() }
        return id
    }

    /** Kept items keep their id (and their progress); removed ones go with their progress. */
    private suspend fun saveItems(reminderId: Long, items: List<ChecklistItem>) {
        val own = dao.items(reminderId).map { it.id }.toSet()
        val kept = items.filter { it.id in own }
        dao.deleteItemsExcept(reminderId, kept.map { it.id })
        items.forEachIndexed { position, item ->
            val entity = ChecklistItemEntity(id = if (item.id in own) item.id else 0, reminderId = reminderId, position = position, title = item.title)
            if (entity.id == 0L) dao.insert(entity) else dao.update(entity)
        }
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
    override suspend fun recentResolved(reminderId: Long, limit: Int) = dao.recentResolved(reminderId, limit).map { it.toModel() }
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

    override suspend fun events(occurrenceId: Long) =
        dao.events(occurrenceId).map { Event(occurrenceId = it.occurrenceId, at = it.at, type = it.type, detail = it.detail) }

    override suspend fun addProof(proof: Proof) = dao.insert(
        ProofEntity(
            occurrenceId = proof.occurrenceId, type = proof.type, filePath = proof.filePath, takenAt = proof.takenAt,
            text = proof.text, itemId = proof.itemId,
        ),
    )

    override suspend fun lastProofText(reminderId: Long) = dao.lastProofText(reminderId)

    override suspend fun prepAnswers(reminderId: Long) = dao.prepAnswers(reminderId).map { it.toModel() }

    override suspend fun addPrepAnswer(answer: PrepAnswer) = dao.insert(
        PrepAnswerEntity(reminderId = answer.reminderId, question = answer.question, text = answer.text, unknown = answer.unknown, at = answer.at),
    )

    override suspend fun places() = dao.places().map { it.toModel() }

    override suspend fun savePlace(place: Place): Long {
        val entity = PlaceEntity(place.id, place.name, place.lat, place.lon, place.radiusMeters)
        if (place.id == 0L) return dao.insert(entity)
        dao.update(entity)
        return place.id
    }

    override suspend fun itemsDone(occurrenceId: Long) = dao.itemsDone(occurrenceId).map { it.toModel() }

    override suspend fun addItemDone(done: ItemDone) =
        dao.insert(ItemDoneEntity(occurrenceId = done.occurrenceId, itemId = done.itemId, doneAt = done.doneAt, withProof = done.withProof))

    override suspend fun blockers(reminderId: Long) = dao.blockers(reminderId).map { it.toModel() }
    override suspend fun blocker(id: Long) = dao.blocker(id)?.toModel()

    override suspend fun saveBlocker(blocker: Blocker): Long {
        val entity = blocker.toEntity()
        if (blocker.id == 0L) return dao.insert(entity)
        dao.update(entity)
        return blocker.id
    }
}

// --- Mapping ----------------------------------------------------------------------------------

internal fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
internal fun LocalTime.toMinutes(): Int = hour * 60 + minute

internal fun Set<DayOfWeek>.toBits(): Int = fold(0) { bits, day -> bits or (1 shl (day.value - 1)) }
internal fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

fun ReminderEntity.toModel(items: List<ChecklistItemEntity> = emptyList()): Reminder = Reminder(
    id = id,
    title = title,
    why = why,
    character = character,
    strictness = strictness,
    schedule = when (scheduleType) {
        ScheduleType.ONCE -> Schedule.Once(requireNotNull(onceAt))
        ScheduleType.DAILY -> Schedule.Daily(times(timeMinutes, moreTimes))
        ScheduleType.WEEKDAYS -> Schedule.Weekdays(times(timeMinutes, moreTimes), weekdays.toDays())
        // For AFTER_DONE, onceAt holds the first due day (no schema change needed).
        ScheduleType.AFTER_DONE -> Schedule.AfterDone(requireNotNull(intervalDays), requireNotNull(timeMinutes).toTime(), onceAt?.toLocalDate())
        ScheduleType.ON_ARRIVAL -> Schedule.OnArrival
        ScheduleType.DEADLINE -> Schedule.Deadline(
            requireNotNull(onceAt), weekdays.toDays(), requireNotNull(timeMinutes).toTime(),
            requireNotNull(windowUntilMinutes).toTime(), remindEveryMinutes ?: 60,
        )
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
    arrival = arrivalPlaceId?.let { Arrival(it, arrivalWhenAway ?: WhenAway.DROP) },
    checklist = items.sortedBy { it.position }.map { ChecklistItem(it.id, it.title) },
    archivedAt = archivedAt,
    weeklyGoal = weekTarget?.let { WeeklyGoal(it, weekMax ?: it, weekMinimum ?: 1) },
    preNotice = preNoticeEveningMinutes?.let { PreNotice.Evening(it.toTime()) } ?: preNoticeBeforeMinutes?.let { PreNotice.Before(it) },
    prepFor = prepFor,
    solvedBlockerIsMinimum = solvedBlockerIsMinimum,
    hintQuietUntil = hintQuietUntil,
)

/** The first time plus the further ones of several a day (VS1). */
internal fun times(first: Int?, more: String?): List<LocalTime> =
    listOf(requireNotNull(first).toTime()) + more.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.toTime() }

/** The times after the first, or null for one time a day. */
internal fun Schedule.moreTimes(): String? =
    (this as? AtTimes)?.times?.drop(1)?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toMinutes().toString() }

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
            Schedule.OnArrival -> ScheduleType.ON_ARRIVAL
            is Schedule.Deadline -> ScheduleType.DEADLINE
        },
        timeMinutes = when (s) {
            is Schedule.Once, Schedule.OnArrival -> null
            is Schedule.Daily -> s.time.toMinutes()
            is Schedule.Weekdays -> s.time.toMinutes()
            is Schedule.AfterDone -> s.time.toMinutes()
            is Schedule.Deadline -> s.from.toMinutes()
        },
        onceAt = when (s) {
            is Schedule.Once -> s.at
            is Schedule.AfterDone -> s.firstOn?.atTime(s.time)
            is Schedule.Deadline -> s.due
            else -> null
        },
        weekdays = when (s) {
            is Schedule.Weekdays -> s.days.toBits()
            is Schedule.Deadline -> s.days.toBits()
            else -> 0
        },
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
        arrivalPlaceId = arrival?.placeId,
        arrivalWhenAway = arrival?.whenAway,
        archivedAt = archivedAt,
        weekTarget = weeklyGoal?.target,
        weekMax = weeklyGoal?.max,
        weekMinimum = weeklyGoal?.minimum,
        windowUntilMinutes = (s as? Schedule.Deadline)?.until?.toMinutes(),
        remindEveryMinutes = (s as? Schedule.Deadline)?.intervalMinutes,
        preNoticeEveningMinutes = (preNotice as? PreNotice.Evening)?.time?.toMinutes(),
        preNoticeBeforeMinutes = (preNotice as? PreNotice.Before)?.minutes,
        prepFor = prepFor,
        solvedBlockerIsMinimum = solvedBlockerIsMinimum,
        moreTimes = s.moreTimes(),
        hintQuietUntil = hintQuietUntil,
    )
}

fun BlockerEntity.toModel() = Blocker(
    id = id, reminderId = reminderId, text = text, createdAt = createdAt, solvedAt = solvedAt, origin = origin, taskReminderId = taskReminderId,
)

fun Blocker.toEntity() = BlockerEntity(
    id = id, reminderId = reminderId, text = text, createdAt = createdAt, solvedAt = solvedAt, origin = origin, taskReminderId = taskReminderId,
)

fun OccurrenceEntity.toModel() = Occurrence(
    id = id, reminderId = reminderId, dueAt = dueAt, currentAt = currentAt, state = state,
    postponeCount = postponeCount, resolvedAt = resolvedAt, resolution = resolution, note = note,
    pausedUntil = pausedUntil, firstProgressAt = firstProgressAt,
)

fun Occurrence.toEntity() = OccurrenceEntity(
    id = id, reminderId = reminderId, dueAt = dueAt, currentAt = currentAt, state = state,
    postponeCount = postponeCount, resolvedAt = resolvedAt, resolution = resolution, note = note,
    pausedUntil = pausedUntil, firstProgressAt = firstProgressAt,
)

fun PrepAnswerEntity.toModel() = PrepAnswer(id = id, reminderId = reminderId, question = question, text = text, unknown = unknown, at = at)

fun ProofEntity.toModel() = Proof(id = id, occurrenceId = occurrenceId, type = type, filePath = filePath, takenAt = takenAt, text = text, itemId = itemId)

fun PlaceEntity.toModel() = Place(id = id, name = name, lat = lat, lon = lon, radiusMeters = radiusMeters)

fun ItemDoneEntity.toModel() = ItemDone(id = id, occurrenceId = occurrenceId, itemId = itemId, doneAt = doneAt, withProof = withProof)
