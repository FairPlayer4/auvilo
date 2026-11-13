package com.avlreminder.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Database(
    entities = [
        ReminderEntity::class, OccurrenceEntity::class, EventEntity::class, ProofEntity::class, PrepAnswerEntity::class,
        PlaceEntity::class, PlaceWifiEntity::class, ChecklistItemEntity::class, ItemDoneEntity::class, SuggestionEntity::class,
        BlockerEntity::class, TemplateEntity::class,
    ],
    version = AvlDatabase.VERSION,
)
@TypeConverters(Converters::class)
abstract class AvlDatabase : RoomDatabase() {
    abstract fun dao(): AvlDao
    abstract fun suggestions(): SuggestionDao

    companion object {
        const val NAME = "avlreminder.db"

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
        const val VERSION = 10

        fun create(context: Context): AvlDatabase {
            val backups = File(context.filesDir, "backup")
            backupBeforeMigration(context.getDatabasePath(NAME), backups)
            return Room.databaseBuilder(context, AvlDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // KRITISCH: a damaged page must not delete the database (R2 of Review-Lauf 10).
                .openHelperFactory(KeepDamaged.Factory({ KeepDamaged.copyAside(it, backups) }))
                .build()
        }

        /**
         * Copies an older database into [dir] before Room migrates it. The copy stays in the app's
         * own storage. VACUUM INTO also takes what is still in the WAL file.
         */
        fun backupBeforeMigration(file: File, dir: File): File? {
            if (!file.exists()) return null
            return try {
                // WAL like Room, so that only looking at the version does not switch the journal mode.
                // Its own error handler: the default one deletes a damaged database.
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING, KeepDamaged.handler).use { db ->
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
