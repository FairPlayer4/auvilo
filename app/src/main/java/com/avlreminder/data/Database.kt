package com.avlreminder.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Strictness
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDateTime

enum class ScheduleType { ONCE, DAILY, WEEKDAYS, AFTER_DONE }

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val why: String?,
    val character: Character,
    val strictness: Strictness,
    val scheduleType: ScheduleType,
    /** Minutes after midnight; null for ONCE. */
    val timeMinutes: Int?,
    val onceAt: LocalDateTime?,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val weekdays: Int,
    val intervalDays: Int?,
    val goal: String?,
    val minimum: String?,
    val minimumLimitPerWeek: Int?,
    val proof: ProofType,
    val postponeLimit: Int?,
    val active: Boolean,
    val createdAt: Instant,
)

@Entity(
    tableName = "occurrence",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId"), Index("state")],
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val dueAt: Instant,
    val currentAt: Instant,
    val state: OccurrenceState,
    val postponeCount: Int,
    val resolvedAt: Instant?,
    val resolution: Resolution?,
    val note: String?,
)

@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(OccurrenceEntity::class, ["id"], ["occurrenceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("occurrenceId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val at: Instant,
    val type: EventType,
    val detail: String?,
)

@Entity(
    tableName = "proof",
    foreignKeys = [ForeignKey(OccurrenceEntity::class, ["id"], ["occurrenceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("occurrenceId")],
)
data class ProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val type: ProofType,
    @ColumnInfo(name = "filePath") val filePath: String?,
    val takenAt: Instant,
)

private const val OPEN = "('PLANNED','DUE','ESCALATING')"

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

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun occurrence(id: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE state IN $OPEN")
    suspend fun openOccurrences(): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE state IN $OPEN")
    fun observeOpenOccurrences(): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state IN $OPEN LIMIT 1")
    suspend fun openOccurrence(reminderId: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state IN ('DONE','CLOSED') ORDER BY resolvedAt DESC LIMIT 1")
    suspend fun lastResolved(reminderId: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE reminderId = :reminderId AND state = 'DONE' ORDER BY resolvedAt DESC LIMIT 1")
    suspend fun lastDone(reminderId: Long): OccurrenceEntity?

    @Query(
        "SELECT COUNT(*) FROM occurrence WHERE reminderId = :reminderId AND resolution = :resolution " +
            "AND resolvedAt >= :from AND resolvedAt < :until",
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
}

class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}

@Database(
    entities = [ReminderEntity::class, OccurrenceEntity::class, EventEntity::class, ProofEntity::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class AvlDatabase : RoomDatabase() {
    abstract fun dao(): AvlDao

    companion object {
        fun create(context: Context): AvlDatabase =
            Room.databaseBuilder(context, AvlDatabase::class.java, "avlreminder.db").build()
    }
}
