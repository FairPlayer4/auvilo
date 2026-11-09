package com.avlreminder.restore

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.avlreminder.cloud.BackupContents
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.BadSettings
import com.avlreminder.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Checking and swapping in a backup's data (W-1, W-5); replaced in the tests of the flows. */
interface RestoreTarget {
    /**
     * Writes [contents] to the check file, checks and migrates it there and compares it with the
     * running data (W-1, W-4). The file stays until [stage] or [discard].
     */
    suspend fun compare(contents: BackupContents): Comparison

    /** The last compared file is swapped in at the next start (W-5, step 3). */
    fun stage(kind: RestoreKind)

    /** Deletes the check file; nothing changes. Only after a [compare] that returned. */
    fun discard()

    /** A restore waits for the next start. */
    fun pending(): Boolean

    /** A restore waits for the next start, or the last one failed: the automatic backup waits too. */
    fun blocksBackup(): Boolean

    /** Starts the app anew, so that the swap happens before anything opens the database. */
    fun restart()
}

/**
 * [RestoreTarget] on the app's files.
 *
 * KRITISCH: Only the check file is written; the running database is only read until the next
 * start swaps it ([DatabaseSwap]).
 */
class DatabaseRestore(
    private val context: Context,
    private val db: AvlDatabase,
    private val settings: Settings,
    val files: RestoreFiles,
    private val clock: () -> Instant = Instant::now,
) : RestoreTarget {
    /** The settings before the restore, kept with the copy of the data (undo, W-6). */
    @Volatile private var current: String? = null

    /** One check at a time: from [compare] until [stage] or [discard]. */
    private val inUse = AtomicBoolean(false)

    override suspend fun compare(contents: BackupContents): Comparison {
        if (!inUse.compareAndSet(false, true)) throw RestoreBusy()
        // A staged restore waits for the restart; its files must stay as they are.
        if (files.order.exists()) {
            inUse.set(false)
            throw RestoreBusy()
        }
        return try {
            compareNow(contents)
        } catch (e: Throwable) {
            discard()
            throw e
        }
    }

    private suspend fun compareNow(contents: BackupContents): Comparison = withContext(Dispatchers.IO) {
        files.failed.delete()
        // KRITISCH: settings the app cannot take are rejected here, before anything is staged
        // (R1 of Review-Lauf 10); after the swap they could only stop the restore half-way.
        try {
            Settings.checkBackup(contents.settings)
        } catch (e: BadSettings) {
            throw DamagedDatabase("Einstellungen (${e.key})", e)
        }
        val schema = CheckFile.prepare(context, contents.database, files)
        DatabaseSwap.writeAtomically(files.settings, contents.settings)
        val now = settings.backup().also { current = it }
        val live = db.openHelper.readableDatabase
        val tables = SQLiteDatabase.openDatabase(files.check.path, null, SQLiteDatabase.OPEN_READONLY).use { backup ->
            BackupTable.entries.map { t ->
                TableCompare.diff(t, TableCompare.rows({ live.query(it) }, t), TableCompare.rows({ backup.rawQuery(it, null) }, t))
            }
        }
        Comparison(schema, tables, TableCompare.settingsSame(now, contents.settings))
    }

    override fun stage(kind: RestoreKind) {
        check(inUse.get()) { "Erst vergleichen, dann tauschen" }
        val before = current ?: error("Erst vergleichen, dann tauschen")
        check(files.check.exists()) { "Keine geprüfte Datei" }
        val name = DatabaseSwap.beforeName(clock())
        DatabaseSwap.writeAtomically(File(files.backupDir, "$name.json"), before)
        Files.move(files.check.toPath(), files.ready.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        // KRITISCH: The order comes last; a file without an order is never swapped in.
        DatabaseSwap.writeAtomically(files.order, RestoreOrder(kind, DatabaseSwap.sha256(files.ready), name).json())
        inUse.set(false)
    }

    override fun discard() {
        DatabaseSwap.deleteWithSidecars(files.check)
        if (!files.order.exists()) files.settings.delete()
        inUse.set(false)
    }

    override fun pending(): Boolean = files.order.exists()

    override fun blocksBackup(): Boolean = pending() || files.failed.exists()

    override fun restart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        // A process kill does not cancel alarms (only "force stop" does); the start plans them anew.
        Runtime.getRuntime().exit(0)
    }
}
