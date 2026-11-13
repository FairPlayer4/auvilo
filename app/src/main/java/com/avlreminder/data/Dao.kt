package com.avlreminder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Queries; the app reads through [RoomReminderRepository], only templates and suggestions directly.

private const val OPEN = "('PLANNED','DUE','ESCALATING','RUNNING')"

@Dao
interface AvlDao {
    @Query("SELECT * FROM reminder ORDER BY title COLLATE NOCASE")
    suspend fun reminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminder ORDER BY title COLLATE NOCASE")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun reminder(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    /** All reminders, also paused and archived ones (brake of the cloud backup, R-2). */
    @Query("SELECT COUNT(*) FROM reminder")
    suspend fun reminderCount(): Int

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun occurrence(id: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE state IN $OPEN")
    suspend fun openOccurrences(): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE state IN $OPEN")
    fun observeOpenOccurrences(): Flow<List<OccurrenceEntity>>

    // The oldest, like the fake; there should only ever be one (Review-Lauf 4).
    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state IN $OPEN ORDER BY id LIMIT 1")
    suspend fun openOccurrence(reminderId: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state IN ('DONE','CLOSED') ORDER BY resolvedAt DESC, id DESC LIMIT 1")
    suspend fun lastResolved(reminderId: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state = 'DONE' ORDER BY resolvedAt DESC, id DESC LIMIT 1")
    suspend fun lastDone(reminderId: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state IN ('DONE','CLOSED') ORDER BY resolvedAt DESC, id DESC LIMIT :limit")
    suspend fun recentResolved(reminderId: Long, limit: Int): List<OccurrenceEntity>

    @Query(
        "SELECT COUNT(*) FROM occurrence WHERE reminderId = :reminderId AND resolution = :resolution " +
            // A session counts at its start (7.11 D7): Occurrence.countedAt.
            "AND resolvedAt IS NOT NULL AND COALESCE(startedAt, resolvedAt) >= :from AND COALESCE(startedAt, resolvedAt) < :until",
    )
    suspend fun countResolved(reminderId: Long, resolution: String, from: Instant, until: Instant): Int

    @Query("SELECT * FROM occurrence WHERE state IN ('DONE','CLOSED') ORDER BY resolvedAt DESC")
    fun observeResolved(): Flow<List<OccurrenceEntity>>

    @Insert
    suspend fun insert(occurrence: OccurrenceEntity): Long

    @Update
    suspend fun update(occurrence: OccurrenceEntity)

    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM event WHERE occurrenceId = :occurrenceId ORDER BY at")
    suspend fun events(occurrenceId: Long): List<EventEntity>

    @Insert
    suspend fun insert(proof: ProofEntity)

    @Query("SELECT * FROM proof")
    fun observeProofs(): Flow<List<ProofEntity>>

    @Query("SELECT filePath FROM proof JOIN occurrence ON proof.occurrenceId = occurrence.id WHERE occurrence.reminderId = :reminderId AND filePath IS NOT NULL")
    suspend fun proofFiles(reminderId: Long): List<String>

    @Query(
        "SELECT proof.text FROM proof JOIN occurrence ON proof.occurrenceId = occurrence.id " +
            "WHERE occurrence.reminderId = :reminderId AND proof.type = 'TEXT' ORDER BY proof.takenAt DESC, proof.id DESC LIMIT 1",
    )
    suspend fun lastProofText(reminderId: Long): String?

    @Query("SELECT * FROM prep_answer WHERE reminderId = :reminderId ORDER BY at, id")
    suspend fun prepAnswers(reminderId: Long): List<PrepAnswerEntity>

    @Query("SELECT * FROM prep_answer ORDER BY at, id")
    fun observePrepAnswers(): Flow<List<PrepAnswerEntity>>

    @Insert
    suspend fun insert(answer: PrepAnswerEntity)

    @Query("SELECT * FROM place ORDER BY id")
    suspend fun places(): List<PlaceEntity>

    @Query("SELECT * FROM place ORDER BY id")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("SELECT * FROM place_wifi ORDER BY placeId, id")
    suspend fun placeWifis(): List<PlaceWifiEntity>

    @Query("SELECT * FROM place_wifi ORDER BY placeId, id")
    fun observePlaceWifis(): Flow<List<PlaceWifiEntity>>

    @Query("DELETE FROM place_wifi WHERE placeId = :placeId")
    suspend fun deletePlaceWifis(placeId: Long)

    @Insert
    suspend fun insert(wifis: List<PlaceWifiEntity>)

    @Query("SELECT * FROM checklist_item WHERE reminderId = :reminderId ORDER BY position")
    suspend fun items(reminderId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_item ORDER BY reminderId, position")
    suspend fun allItems(): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_item ORDER BY reminderId, position")
    fun observeItems(): Flow<List<ChecklistItemEntity>>

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_item WHERE reminderId = :reminderId AND id NOT IN (:keep)")
    suspend fun deleteItemsExcept(reminderId: Long, keep: List<Long>)

    @Query("SELECT * FROM item_done WHERE occurrenceId = :occurrenceId ORDER BY doneAt, id")
    suspend fun itemsDone(occurrenceId: Long): List<ItemDoneEntity>

    @Query("DELETE FROM item_done WHERE occurrenceId = :occurrenceId AND doneAt >= :from")
    suspend fun deleteItemsDone(occurrenceId: Long, from: Instant)

    /** Events, proofs and checklist progress go with the foreign-key cascade. */
    @Query("DELETE FROM occurrence WHERE id = :id")
    suspend fun deleteOccurrence(id: Long)

    @Query(
        "SELECT item_done.* FROM item_done JOIN occurrence ON occurrence.id = item_done.occurrenceId " +
            "WHERE occurrence.reminderId = :reminderId ORDER BY item_done.doneAt, item_done.id",
    )
    suspend fun itemsDoneOfReminder(reminderId: Long): List<ItemDoneEntity>

    @Query("SELECT * FROM item_done ORDER BY doneAt, id")
    fun observeItemsDone(): Flow<List<ItemDoneEntity>>

    @Insert
    suspend fun insert(done: ItemDoneEntity)

    @Query("SELECT * FROM blocker WHERE reminderId = :reminderId ORDER BY createdAt, id")
    suspend fun blockers(reminderId: Long): List<BlockerEntity>

    @Query("SELECT * FROM blocker ORDER BY createdAt, id")
    fun observeBlockers(): Flow<List<BlockerEntity>>

    @Query("SELECT * FROM blocker WHERE id = :id")
    suspend fun blocker(id: Long): BlockerEntity?

    @Insert
    suspend fun insert(blocker: BlockerEntity): Long

    @Update
    suspend fun update(blocker: BlockerEntity)

    @Query("SELECT * FROM template ORDER BY usedAt DESC, id DESC")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun template(id: Long): TemplateEntity?

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("UPDATE template SET usedAt = :at WHERE id = :id")
    suspend fun templateUsed(id: Long, at: Instant)

    @Query("UPDATE template SET name = :name WHERE id = :id")
    suspend fun renameTemplate(id: Long, name: String)

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)
}
