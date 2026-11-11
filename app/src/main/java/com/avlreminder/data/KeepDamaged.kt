package com.avlreminder.data

import android.database.DatabaseErrorHandler
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import java.time.LocalDate

/**
 * What happens when SQLite reports a damaged page in the app's database (R2 of Review-Lauf 10).
 *
 * Android's default (and Room's) deletes the whole database file, so one bad page in a table the
 * daily copy reads would take all reminders with it. Here the file stays; the query that hit the
 * page fails as before, everything else keeps working, and the raw files are copied aside once a
 * day so that they can be rescued (design.md 13).
 */
object KeepDamaged {
    private const val TAG = "KeepDamaged"
    const val PREFIX = "beschaedigt-"

    /** For [android.database.sqlite.SQLiteDatabase.openDatabase]: leaves a damaged file alone. */
    val handler = DatabaseErrorHandler { Log.e(TAG, "corruption reported, file kept") }

    /**
     * Copies the raw files of [db] (with `-wal`) to `<dir>/beschaedigt-<Tag>/`, unless today's
     * copy exists. A raw copy of an open database may be inconsistent; it is only for rescue.
     *
     * @return the folder of the copy, or null if there was one today already or copying failed.
     */
    fun copyAside(db: File, dir: File, today: LocalDate = LocalDate.now()): File? {
        val target = File(dir, PREFIX + today)
        if (target.exists()) return null
        return try {
            target.mkdirs()
            for (suffix in listOf("", "-wal", "-journal")) {
                val f = File(db.path + suffix)
                if (f.exists()) f.copyTo(File(target, f.name), overwrite = true)
            }
            target
        } catch (e: Exception) {
            Log.e(TAG, "copy aside failed", e)
            null
        }
    }

    /**
     * Room's open helper with [onCorruption] instead of deleting. Everything else goes to Room's
     * own callback unchanged.
     */
    class Factory(
        private val onCorruption: (File) -> Unit,
        private val inner: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val room = configuration.callback
            // KRITISCH: only onCorruption differs; the default deletes the database file.
            val keeping = object : SupportSQLiteOpenHelper.Callback(room.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = room.onConfigure(db)
                override fun onCreate(db: SupportSQLiteDatabase) = room.onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onUpgrade(db, oldVersion, newVersion)
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onDowngrade(db, oldVersion, newVersion)
                override fun onOpen(db: SupportSQLiteDatabase) = room.onOpen(db)
                override fun onCorruption(db: SupportSQLiteDatabase) {
                    Log.e(TAG, "corruption reported, file kept")
                    db.path?.let { runCatching { onCorruption(File(it)) } }
                }
            }
            val config = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(keeping)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
            return inner.create(config)
        }
    }
}
