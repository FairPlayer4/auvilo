package com.avlreminder.cloud

import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What goes into the backup, as bytes (A-8); replaced in tests. */
fun interface BackupSource {
    suspend fun payload(): ByteArray
}

/** The two parts of a backup. */
class BackupContents(val database: ByteArray, val settings: String)

/** The ZIP has an entry missing or one it does not know. */
class DamagedBackup(why: String) : Exception("Sicherung beschädigt: $why")

/**
 * The payload of a backup (A-8): a ZIP with `avlreminder.db` and `einstellungen.json`. The
 * backend sees only bytes; the format is described here and pinned by a test.
 *
 * KRITISCH: The entries have a fixed time. The same data then give the same bytes, which the
 * automatic backup (step 4) uses to skip uploads when nothing changed.
 */
object BackupZip {
    const val DATABASE = "avlreminder.db"
    const val SETTINGS = "einstellungen.json"

    /** 1980-01-01 00:00 UTC, the first time a ZIP entry can hold. */
    private const val FIXED_TIME = 315_532_800_000L

    fun pack(contents: BackupContents): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.setLevel(9)
            for ((name, data) in listOf(DATABASE to contents.database, SETTINGS to contents.settings.toByteArray(Charsets.UTF_8))) {
                zip.putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** Reads a [pack]ed backup; anything else is [DamagedBackup]. */
    fun unpack(bytes: ByteArray): BackupContents {
        val found = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name !in setOf(DATABASE, SETTINGS)) throw DamagedBackup("unbekannter Eintrag")
                if (entry.name in found) throw DamagedBackup("Eintrag doppelt")
                found[entry.name] = zip.readBytes()
            }
        }
        val database = found[DATABASE] ?: throw DamagedBackup("Datenbank fehlt")
        val settings = found[SETTINGS] ?: throw DamagedBackup("Einstellungen fehlen")
        return BackupContents(database, String(settings, Charsets.UTF_8))
    }
}

/**
 * The app's data as backup payload: a copy of the database (`VACUUM INTO`, which also takes what
 * is still in the WAL file) and the settings meant for the backup (`Settings.backup`, PS8a).
 * The copy lives in the cache only while the ZIP is built.
 */
class AppBackupSource(private val db: AvlDatabase, private val settings: Settings, private val cacheDir: File) : BackupSource {
    override suspend fun payload(): ByteArray = withContext(Dispatchers.IO) {
        val copy = File(cacheDir, "cloud-backup.db")
        copy.delete() // VACUUM INTO needs a file that does not exist
        try {
            db.openHelper.writableDatabase.execSQL("VACUUM INTO '${copy.path.replace("'", "''")}'")
            BackupZip.pack(BackupContents(copy.readBytes(), settings.backup()))
        } finally {
            copy.delete()
        }
    }
}
