package com.avlreminder.restore

import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.avlreminder.data.AvlDatabase
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The files of restoring (wiederherstellen-entwurf.md, W-1, W-5), all in the app's own storage:
 *
 *   restore/pruefung.db        the backup, unpacked and checked; never the running database
 *   restore/einstellungen.json the settings that come with it
 *   restore/bereit.db          the checked file, waiting for the next start
 *   restore/auftrag.json       the order: what to swap in, its SHA-256, where the old data go
 *   restore/fehlgeschlagen.json the last swap failed; the data stayed as they were
 *   backup/vor-wiederherstellung-<Zeit>.db/.json  the data before a restore (undo, W-6)
 *
 * `restore` and `databases` are on the same file system, so renaming is atomic.
 */
class RestoreFiles(filesDir: File) {
    val dir = File(filesDir, "restore")
    val backupDir = File(filesDir, "backup")
    val check = File(dir, "pruefung.db")
    val settings = File(dir, "einstellungen.json")
    val ready = File(dir, "bereit.db")
    val order = File(dir, "auftrag.json")
    val failed = File(dir, "fehlgeschlagen.json")

    /** The copies before a restore, newest first. */
    fun befores(): List<File> =
        backupDir.listFiles { f -> f.name.startsWith(BEFORE) && f.name.endsWith(".db") }.orEmpty().sortedByDescending { it.name }

    companion object {
        const val BEFORE = "vor-wiederherstellung-"
    }
}

/** What a restore swaps in. */
enum class RestoreKind { CLOUD, SNAPSHOT, UNDO }

/** The order in `auftrag.json`. [before]: name of the copy of the old data, without extension. */
data class RestoreOrder(val kind: RestoreKind, val sha256: String, val before: String, val swapped: Boolean = false) {
    fun json(): String = JSONObject().put("art", kind.name).put("sha256", sha256).put("vorher", before).put("getauscht", swapped).toString()

    companion object {
        fun parse(text: String): RestoreOrder {
            val o = JSONObject(text)
            return RestoreOrder(RestoreKind.valueOf(o.getString("art")), o.getString("sha256"), o.getString("vorher"), o.getBoolean("getauscht"))
        }
    }
}

/** Why a swap failed, for the settings and the logger (no free text). */
enum class SwapFailure { ORDER_UNREADABLE, FILE_CHANGED, FILE_MISSING, COPY_FAILED, SWAP_FAILED }

/** How the start went with a pending order. */
sealed interface SwapOutcome {
    data object None : SwapOutcome

    /** The database is swapped; the settings still have to be taken ([RestoreFiles.settings]). */
    data class Swapped(val order: RestoreOrder) : SwapOutcome

    /** Nothing swapped, the data are as before. */
    data class Failed(val why: SwapFailure) : SwapOutcome
}

/**
 * Swaps a restored database in at the start, before Room opens it (W-5, step 5).
 *
 * KRITISCH:
 *  - The running data are copied (`VACUUM INTO`, also takes the WAL) before anything changes,
 *    and that copy is never overwritten: a second try after a crash keeps the first copy.
 *  - Only a file whose SHA-256 matches the order is swapped in; anything else leaves the data as
 *    they are and notes the failure.
 *  - A file without an order (crash while staging) is deleted, never swapped in.
 *  - Each step can be interrupted: the next start continues or gives up cleanly, never crashes.
 */
object DatabaseSwap {
    private const val TAG = "DatabaseSwap"

    fun applyPending(db: File, files: RestoreFiles, now: () -> Instant = Instant::now): SwapOutcome {
        // Left over from a check that did not finish; never the running data.
        deleteWithSidecars(files.check)
        if (!files.order.exists()) {
            files.ready.delete()
            return SwapOutcome.None
        }
        val order = runCatching { RestoreOrder.parse(files.order.readText()) }.getOrElse {
            return fail(files, SwapFailure.ORDER_UNREADABLE)
        }
        if (order.swapped) return SwapOutcome.Swapped(order)
        if (files.ready.exists()) {
            if (sha256(files.ready) != order.sha256) return fail(files, SwapFailure.FILE_CHANGED)
            val before = File(files.backupDir, order.before + ".db")
            try {
                if (!before.exists() && db.exists()) copy(db, before)
            } catch (e: Exception) {
                Log.e(TAG, "copy before restore failed", e)
                return fail(files, SwapFailure.COPY_FAILED)
            }
            try {
                // The WAL belongs to the old file; its content is in the copy.
                for (suffix in SIDECARS) File(db.path + suffix).delete()
                db.parentFile?.mkdirs()
                Files.move(files.ready.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                Log.e(TAG, "swap failed", e)
                return fail(files, SwapFailure.SWAP_FAILED)
            }
        } else if (!(db.exists() && sha256(db) == order.sha256)) {
            // Neither the file nor the swapped database: nothing to continue.
            return fail(files, SwapFailure.FILE_MISSING)
        }
        val swapped = order.copy(swapped = true)
        // Swapped already; a failed write is repeated at the next start (the SHA-256 matches).
        runCatching { writeAtomically(files.order, swapped.json()) }
        return SwapOutcome.Swapped(swapped)
    }

    /** The order is done (settings taken): removes it and what belongs to it. */
    fun finish(files: RestoreFiles) {
        files.settings.delete()
        files.order.delete()
        files.ready.delete()
    }

    private fun fail(files: RestoreFiles, why: SwapFailure): SwapOutcome {
        runCatching { writeAtomically(files.failed, JSONObject().put("grund", why.name).toString()) }
        files.order.delete()
        files.ready.delete()
        files.settings.delete()
        return SwapOutcome.Failed(why)
    }

    /** `VACUUM INTO` a temporary file, then renamed: a half-written copy never has the final name. */
    fun copy(db: File, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.delete()
        // In WAL mode like Room opens it: otherwise opening switches the journal mode and so
        // changes the file before anything is decided.
        // KRITISCH: a damaged page only makes the copy fail. Without a handler of its own,
        // Android's default handler deletes the damaged database, the data to be kept.
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
        SQLiteDatabase.openDatabase(db.path, null, flags, KEEP_DAMAGED).use {
            it.execSQL("VACUUM INTO '${tmp.path.replace("'", "''")}'")
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private val SIDECARS = listOf("-wal", "-shm", "-journal")

    /** Leaves a damaged database alone (the default handler deletes it). */
    private val KEEP_DAMAGED = DatabaseErrorHandler { }

    fun deleteWithSidecars(file: File) {
        file.delete()
        for (suffix in SIDECARS) File(file.path + suffix).delete()
    }

    fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Name for the copy before a restore at [at]. */
    fun beforeName(at: Instant): String =
        RestoreFiles.BEFORE + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(at)
}

/** The backup's database is not usable: not SQLite, damaged, or it does not fit the app. */
class DamagedDatabase(why: String, cause: Throwable? = null) : Exception("Datenbank der Sicherung unbrauchbar: $why", cause)

/** The backup comes from a newer app (W-4); nothing is checked or swapped. */
class NewerDatabase(val found: Int) : Exception("Die Sicherung hat Schema $found, diese App kennt nur bis ${AvlDatabase.VERSION}")

/**
 * Turns a backup's database into a checked file (W-1, W-4): writes it to [RestoreFiles.check],
 * migrates it there if it is older (never the running database), lets Room check the schema and
 * runs `integrity_check` and `foreign_key_check`.
 *
 * @return the schema version the backup had before migrating.
 */
object CheckFile {
    fun prepare(context: Context, database: ByteArray, files: RestoreFiles): Int {
        files.dir.mkdirs()
        DatabaseSwap.deleteWithSidecars(files.check)
        files.check.writeBytes(database)
        return prepareFile(context, files.check)
    }

    fun prepareFile(context: Context, file: File): Int {
        val schema = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version }
        } catch (e: Exception) {
            DatabaseSwap.deleteWithSidecars(file)
            throw DamagedDatabase("lässt sich nicht öffnen", e)
        }
        if (schema > AvlDatabase.VERSION) {
            DatabaseSwap.deleteWithSidecars(file)
            throw NewerDatabase(schema)
        }
        if (schema < 1) {
            DatabaseSwap.deleteWithSidecars(file)
            throw DamagedDatabase("kein Schema")
        }
        try {
            // Room migrates an older file here and checks that the schema is the app's.
            Room.databaseBuilder(context, AvlDatabase::class.java, file.absolutePath)
                .addMigrations(*AvlDatabase.MIGRATIONS)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
                .useDatabase { db ->
                    val sql = db.openHelper.writableDatabase
                    val integrity = sql.query("PRAGMA integrity_check").use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    if (integrity != "ok") throw DamagedDatabase("integrity_check")
                    val broken = sql.query("PRAGMA foreign_key_check").use { c -> c.count }
                    if (broken > 0) throw DamagedDatabase("foreign_key_check")
                }
        } catch (e: DamagedDatabase) {
            DatabaseSwap.deleteWithSidecars(file)
            throw e
        } catch (e: Exception) {
            DatabaseSwap.deleteWithSidecars(file)
            throw DamagedDatabase("passt nicht zur App", e)
        }
        for (suffix in listOf("-wal", "-shm", "-journal")) File(file.path + suffix).delete()
        return schema
    }

    private inline fun <T> AvlDatabase.useDatabase(block: (AvlDatabase) -> T): T = try {
        block(this)
    } finally {
        close()
    }
}

/** The tables compared (W-3: a fixed list, no names from the file). */
enum class BackupTable(val sql: String) {
    REMINDER("reminder"),
    OCCURRENCE("occurrence"),
    EVENT("event"),
    PROOF("proof"),
    PREP_ANSWER("prep_answer"),
    PLACE("place"),
    CHECKLIST_ITEM("checklist_item"),
    ITEM_DONE("item_done"),
    SUGGESTION("suggestion"),
    BLOCKER("blocker"),
    TEMPLATE("template"),
}

/**
 * How one table of the backup compares with this device: rows by primary key, [changed] = same
 * key, other content.
 */
data class TableDiff(val table: BackupTable, val here: Int, val backup: Int, val changed: Int, val onlyHere: Int, val onlyBackup: Int) {
    val same get() = changed == 0 && onlyHere == 0 && onlyBackup == 0
}

/** The result of comparing a checked file with the running data. */
data class Comparison(val schema: Int, val tables: List<TableDiff>, val settingsSame: Boolean) {
    val same get() = settingsSame && tables.all { it.same }
    fun count(table: BackupTable) = tables.first { it.table == table }
}

/**
 * Compares tables row by row without showing contents (W-1): per row a SHA-256 over all columns,
 * sorted by name, with type and length, so that `NULL`, `''` and `0` differ.
 */
object TableCompare {
    fun rows(query: (String) -> Cursor, table: BackupTable): Map<Long, String> {
        val result = HashMap<Long, String>()
        query("SELECT * FROM `${table.sql}` ORDER BY id").use { c ->
            val columns = (0 until c.columnCount).sortedBy { c.getColumnName(it) }
            val id = c.getColumnIndexOrThrow("id")
            while (c.moveToNext()) {
                val md = MessageDigest.getInstance("SHA-256")
                for (i in columns) {
                    md.field(c.getColumnName(i).toByteArray())
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> md.update(0)
                        Cursor.FIELD_TYPE_INTEGER -> { md.update(1); md.field(c.getLong(i).toString().toByteArray()) }
                        Cursor.FIELD_TYPE_FLOAT -> { md.update(2); md.field(c.getDouble(i).toBits().toString().toByteArray()) }
                        Cursor.FIELD_TYPE_STRING -> { md.update(3); md.field(c.getString(i).toByteArray()) }
                        else -> { md.update(4); md.field(c.getBlob(i)) }
                    }
                }
                result[c.getLong(id)] = md.digest().joinToString("") { "%02x".format(it) }
            }
        }
        return result
    }

    private fun MessageDigest.field(bytes: ByteArray) {
        update(bytes.size.toString().toByteArray())
        update(':'.code.toByte())
        update(bytes)
    }

    fun diff(table: BackupTable, here: Map<Long, String>, backup: Map<Long, String>) = TableDiff(
        table,
        here = here.size,
        backup = backup.size,
        changed = here.count { (k, v) -> backup[k] != null && backup[k] != v },
        onlyHere = here.keys.count { it !in backup },
        onlyBackup = backup.keys.count { it !in here },
    )

    /** The settings of two backups (JSON from `Settings.backup`), compared by their values only. */
    fun settingsSame(a: String, b: String): Boolean = values(a) == values(b)

    private fun values(json: String): Map<String, String> {
        val v = JSONObject(json).optJSONObject("values") ?: return emptyMap()
        return v.keys().asSequence().associateWith { v.get(it).toString() }.toSortedMap()
    }
}
