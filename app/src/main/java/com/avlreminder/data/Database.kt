package com.avlreminder.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ScheduleType { ONCE, DAILY, WEEKDAYS, AFTER_DONE, ON_ARRIVAL }

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
    /** Column from V1.0 (then NONE/CONFIRM/PHOTO); since version 2 the [ProofKind]. */
    @ColumnInfo(name = "proof") val proofKind: ProofKind,
    val postponeLimit: Int?,
    val active: Boolean,
    val createdAt: Instant,
    @ColumnInfo(defaultValue = "0") val proofPhoto: Boolean = false,
    val proofTextMinChars: Int? = null,
    val proofAudioMinSeconds: Int? = null,
    @ColumnInfo(defaultValue = "10") val proofWindowMinutes: Int = 10,
    @ColumnInfo(defaultValue = "0") val ignoreDnd: Boolean = false,
    /** Comma-separated minutes, e.g. "30,60". */
    @ColumnInfo(defaultValue = "10") val snoozeMinutes: String = "10",
    val motivationAudio: String? = null,
    /** Arrival trigger; both null = time only. */
    val arrivalPlaceId: Long? = null,
    val arrivalWhenAway: WhenAway? = null,
)

@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int,
)

@Entity(
    tableName = "checklist_item",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val position: Int,
    val title: String,
)

@Entity(
    tableName = "item_done",
    foreignKeys = [
        ForeignKey(OccurrenceEntity::class, ["id"], ["occurrenceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ChecklistItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("occurrenceId"), Index("itemId")],
)
data class ItemDoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val itemId: Long,
    val doneAt: Instant,
    val withProof: Boolean,
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
    val pausedUntil: Instant? = null,
    val firstProgressAt: Instant? = null,
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
    val text: String? = null,
    /** Checklist item the proof is for; no foreign key, the proof stays when the item is removed. */
    val itemId: Long? = null,
)

@Entity(
    tableName = "prep_answer",
    foreignKeys = [ForeignKey(ReminderEntity::class, ["id"], ["reminderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reminderId")],
)
data class PrepAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val question: Int,
    val text: String?,
    val unknown: Boolean,
    val at: Instant,
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

    @Query("SELECT * FROM item_done ORDER BY doneAt, id")
    fun observeItemsDone(): Flow<List<ItemDoneEntity>>

    @Insert
    suspend fun insert(done: ItemDoneEntity)
}

class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}

/** V1.1a: combinable proofs, proof window, "do not disturb", snooze buttons, "Startklar" answers. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofPhoto INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofTextMinChars INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofAudioMinSeconds INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofWindowMinutes INTEGER NOT NULL DEFAULT 10")
        db.execSQL("ALTER TABLE reminder ADD COLUMN ignoreDnd INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminder ADD COLUMN snoozeMinutes TEXT NOT NULL DEFAULT '10'")
        db.execSQL("ALTER TABLE reminder ADD COLUMN motivationAudio TEXT")
        db.execSQL("UPDATE reminder SET proof = 'EVIDENCE', proofPhoto = 1 WHERE proof = 'PHOTO'")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN pausedUntil INTEGER")
        db.execSQL("ALTER TABLE proof ADD COLUMN text TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `prep_answer` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`reminderId` INTEGER NOT NULL, `question` INTEGER NOT NULL, `text` TEXT, `unknown` INTEGER NOT NULL, " +
                "`at` INTEGER NOT NULL, FOREIGN KEY(`reminderId`) REFERENCES `reminder`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_prep_answer_reminderId` ON `prep_answer` (`reminderId`)")
    }
}

/** V1.1b: arrival trigger with places, checklists. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalPlaceId INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalWhenAway TEXT")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN firstProgressAt INTEGER")
        db.execSQL("ALTER TABLE proof ADD COLUMN itemId INTEGER")
        CREATE_3.forEach(db::execSQL)
    }
}

/** The new tables of version 3, as Room generates them (see schemas/…/3.json). */
private val CREATE_3 = listOf(
    "CREATE TABLE IF NOT EXISTS `place` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `checklist_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER NOT NULL, " +
        "`position` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
        "FOREIGN KEY(`reminderId`) REFERENCES `reminder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_checklist_item_reminderId` ON `checklist_item` (`reminderId`)",
    "CREATE TABLE IF NOT EXISTS `item_done` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occurrenceId` INTEGER NOT NULL, " +
        "`itemId` INTEGER NOT NULL, `doneAt` INTEGER NOT NULL, `withProof` INTEGER NOT NULL, " +
        "FOREIGN KEY(`occurrenceId`) REFERENCES `occurrence`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`itemId`) REFERENCES `checklist_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_item_done_occurrenceId` ON `item_done` (`occurrenceId`)",
    "CREATE INDEX IF NOT EXISTS `index_item_done_itemId` ON `item_done` (`itemId`)",
)

@Database(
    entities = [
        ReminderEntity::class, OccurrenceEntity::class, EventEntity::class, ProofEntity::class, PrepAnswerEntity::class,
        PlaceEntity::class, ChecklistItemEntity::class, ItemDoneEntity::class,
    ],
    version = 3,
)
@TypeConverters(Converters::class)
abstract class AvlDatabase : RoomDatabase() {
    abstract fun dao(): AvlDao

    companion object {
        const val NAME = "avlreminder.db"
        const val VERSION = 3

        fun create(context: Context): AvlDatabase {
            backupBeforeMigration(context.getDatabasePath(NAME), File(context.filesDir, "backup"))
            return Room.databaseBuilder(context, AvlDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        /**
         * Copies an older database into [dir] before Room migrates it. The copy stays in the app's
         * own storage. VACUUM INTO also takes what is still in the WAL file.
         */
        fun backupBeforeMigration(file: File, dir: File): File? {
            if (!file.exists()) return null
            return try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    if (db.version >= VERSION) return null
                    dir.mkdirs()
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
                    val target = File(dir, "avlreminder-v${db.version}-$stamp.db")
                    db.execSQL("VACUUM INTO '${target.path.replace("'", "''")}'")
                    target
                }
            } catch (e: Exception) {
                // A failed copy must not keep the app from starting; Room still migrates.
                Log.e("AvlDatabase", "backup before migration failed", e)
                null
            }
        }
    }
}
