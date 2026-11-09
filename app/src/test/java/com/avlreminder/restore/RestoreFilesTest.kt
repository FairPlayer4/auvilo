package com.avlreminder.restore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.cloud.BackupContents
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.SchemaDatabases
import com.avlreminder.data.Settings
import com.avlreminder.data.SuggestionEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The files of restoring (wiederherstellen-entwurf.md): the swap at the start (W-5, step 5), the
 * check file (W-1, W-4) and the comparison without contents (W-1, W-3).
 */
@RunWith(AndroidJUnit4::class)
class RestoreFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files by lazy { RestoreFiles(tmp.newFolder("files")) }
    private val live by lazy { File(tmp.root, "databases/avlreminder.db") }

    /** A database file of the app with one suggestion per text, closed again. */
    private fun database(file: File, vararg texts: String): File {
        file.parentFile?.mkdirs()
        val db = Room.databaseBuilder(context, AvlDatabase::class.java, file.path).build()
        runBlocking {
            for (t in texts) {
                db.suggestions().insert(SuggestionEntity(text = t, important = false, createdAt = Instant.EPOCH, screen = "Heute", appVersion = "t"))
            }
        }
        db.close()
        return file
    }

    private fun texts(file: File): List<String> = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT text FROM suggestion ORDER BY id", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    /** Stages [ready] like [DatabaseRestore.stage]: file first, order last. */
    private fun stage(ready: File, before: String = "vor-wiederherstellung-20261109-120000"): RestoreOrder {
        files.dir.mkdirs()
        ready.copyTo(files.ready, overwrite = true)
        val order = RestoreOrder(RestoreKind.CLOUD, DatabaseSwap.sha256(files.ready), before)
        DatabaseSwap.writeAtomically(files.order, order.json())
        return order
    }

    private fun failure(): String = JSONObject(files.failed.readText()).getString("grund")

    // --- The swap at the start ---------------------------------------------------------------

    // KRITISCH: the swap puts the checked file in place and keeps the data before as a copy.
    @Test
    fun theSwapReplacesTheDatabaseAndKeepsACopyOfTheOldData() {
        database(live, "alt")
        val order = stage(database(File(tmp.root, "sicherung.db"), "neu 1", "neu 2"))

        val outcome = DatabaseSwap.applyPending(live, files)

        assertEquals(SwapOutcome.Swapped(order.copy(swapped = true)), outcome)
        assertEquals(listOf("neu 1", "neu 2"), texts(live))
        assertEquals(listOf("alt"), texts(File(files.backupDir, order.before + ".db")))
        assertFalse(files.ready.exists())
        assertTrue(RestoreOrder.parse(files.order.readText()).swapped)

        // The next start (settings not taken yet) changes nothing more.
        assertEquals(outcome, DatabaseSwap.applyPending(live, files))
        assertEquals(listOf("neu 1", "neu 2"), texts(live))
        DatabaseSwap.finish(files)
        assertEquals(SwapOutcome.None, DatabaseSwap.applyPending(live, files))
        assertFalse(files.order.exists())
    }

    // KRITISCH: a file without an order (interrupted while staging) and a left-over check file are
    // deleted, never swapped in.
    @Test
    fun aFileWithoutAnOrderIsNeverSwappedIn() {
        database(live, "alt")
        files.dir.mkdirs()
        database(File(tmp.root, "x.db"), "fremd").copyTo(files.ready)
        database(File(tmp.root, "y.db"), "geprüft").copyTo(files.check)

        assertEquals(SwapOutcome.None, DatabaseSwap.applyPending(live, files))
        assertEquals(listOf("alt"), texts(live))
        assertFalse(files.ready.exists())
        assertFalse(files.check.exists())
        assertFalse(files.failed.exists())
    }

    // KRITISCH: whatever does not fit the order leaves the data as they are and notes why.
    @Test
    fun aSwapThatDoesNotFitLeavesTheDataAsTheyAre() {
        val cases = listOf<Triple<String, SwapFailure, () -> Unit>>(
            Triple("Auftrag unlesbar", SwapFailure.ORDER_UNREADABLE) {
                stage(database(File(tmp.root, "a.db"), "neu"))
                files.order.writeText("kaputt")
            },
            Triple("Datei nach dem Bereitlegen verändert", SwapFailure.FILE_CHANGED) {
                stage(database(File(tmp.root, "b.db"), "neu"))
                files.ready.appendBytes(byteArrayOf(0))
            },
            Triple("Datei fehlt, Datenbank passt nicht zum Auftrag", SwapFailure.FILE_MISSING) {
                stage(database(File(tmp.root, "c.db"), "neu"))
                files.ready.delete()
            },
            Triple("Kopie der alten Daten unmöglich", SwapFailure.COPY_FAILED) {
                stage(database(File(tmp.root, "d.db"), "neu"))
                files.backupDir.writeText("kein Verzeichnis")
            },
        )
        for ((case, why, prepare) in cases) {
            tmp.root.resolve("files").deleteRecursively()
            database(live.also { DatabaseSwap.deleteWithSidecars(it) }, "alt")
            prepare()
            val sha = DatabaseSwap.sha256(live)

            assertEquals(case, SwapOutcome.Failed(why), DatabaseSwap.applyPending(live, files))
            assertEquals(case, sha, DatabaseSwap.sha256(live))
            assertEquals(case, why.name, failure())
            assertFalse(case, files.order.exists())
            assertFalse(case, files.ready.exists())
            // The next start does not try again.
            assertEquals(case, SwapOutcome.None, DatabaseSwap.applyPending(live, files))
            assertEquals(case, sha, DatabaseSwap.sha256(live))
        }
    }

    // KRITISCH: interrupted after the copy, the next start keeps the first copy (the true data
    // before); interrupted after the move, it only finishes the order.
    @Test
    fun anInterruptedSwapContinuesWithoutLosingTheDataBefore() {
        database(live, "alt")
        val order = stage(database(File(tmp.root, "sicherung.db"), "neu"))
        val before = File(files.backupDir, order.before + ".db")
        database(before, "die echten alten Daten")

        DatabaseSwap.applyPending(live, files)
        assertEquals(listOf("die echten alten Daten"), texts(before))
        assertEquals(listOf("neu"), texts(live))

        // Moved, but the order not yet marked: the database already has the order's SHA-256.
        DatabaseSwap.writeAtomically(files.order, order.json())
        assertEquals(SwapOutcome.Swapped(order.copy(swapped = true)), DatabaseSwap.applyPending(live, files))
        assertEquals(listOf("neu"), texts(live))
    }

    @Test
    fun theOrderSurvivesItsJson() {
        for (kind in RestoreKind.entries) {
            for (swapped in listOf(false, true)) {
                val order = RestoreOrder(kind, "ab12", "vor-wiederherstellung-20261109-120000", swapped)
                assertEquals(order, RestoreOrder.parse(order.json()))
            }
        }
    }

    // --- The check file ----------------------------------------------------------------------

    // KRITISCH: only a database of this app passes; anything else is deleted and nothing else is
    // touched (W-4).
    @Test
    fun theCheckFileRejectsWhatIsNotADatabaseOfThisApp() {
        val newer = File(tmp.root, "neuer.db").also { f ->
            database(f, "x")
            SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = AvlDatabase.VERSION + 1 }
        }
        val noSchema = File(tmp.root, "leer.db").also { f -> SQLiteDatabase.openOrCreateDatabase(f, null).use { it.execSQL("CREATE TABLE t (a)") } }
        val foreign = File(tmp.root, "fremd.db").also { f ->
            SQLiteDatabase.openOrCreateDatabase(f, null).use {
                it.execSQL("CREATE TABLE reminder (id INTEGER PRIMARY KEY)")
                it.version = AvlDatabase.VERSION
            }
        }
        val orphan = File(tmp.root, "verwaist.db").also { f ->
            SchemaDatabases.create(f, AvlDatabase.VERSION) { db ->
                db.execSQL("PRAGMA foreign_keys = OFF")
                db.execSQL("INSERT INTO occurrence (id, reminderId, dueAt, currentAt, state, postponeCount) VALUES (1, 99, 0, 0, 'PLANNED', 0)")
            }
        }
        val cases = listOf<Triple<String, ByteArray, Class<out Exception>>>(
            Triple("keine Datenbank", "kein SQLite".toByteArray(), DamagedDatabase::class.java),
            Triple("leer", ByteArray(0), DamagedDatabase::class.java),
            Triple("neueres Schema", newer.readBytes(), NewerDatabase::class.java),
            Triple("ohne Schema-Version", noSchema.readBytes(), DamagedDatabase::class.java),
            Triple("andere Tabellen", foreign.readBytes(), DamagedDatabase::class.java),
            Triple("Verweis ins Leere", orphan.readBytes(), DamagedDatabase::class.java),
        )
        for ((case, bytes, expected) in cases) {
            try {
                CheckFile.prepare(context, bytes, files)
                fail("$case: accepted")
            } catch (e: Exception) {
                assertTrue("$case: ${e::class.simpleName}", expected.isInstance(e))
            }
            assertFalse(case, files.check.exists())
        }
    }

    @Test
    fun aDatabaseOfThisAppPasses() {
        val bytes = database(File(tmp.root, "ok.db"), "a").readBytes()
        assertEquals(AvlDatabase.VERSION, CheckFile.prepare(context, bytes, files))
        assertEquals(listOf("a"), texts(files.check))
    }

    // KRITISCH: an older backup is migrated in the check file only (W-4).
    @Test
    fun anOlderBackupIsMigratedInTheCheckFileOnly() {
        val old = File(tmp.root, "v7.db")
        SchemaDatabases.create(old, 7) { db ->
            db.execSQL(
                "INSERT INTO reminder (id, title, character, strictness, scheduleType, timeMinutes, weekdays, proof, active, createdAt, " +
                    "proofPhoto, ignoreDnd, snoozeMinutes, solvedBlockerIsMinimum) " +
                    "VALUES (1, 'Training', 'DOCUMENT', 'NORMAL', 'DAILY', 1140, 0, 'CONFIRM', 1, 0, 0, 0, '10', 0)",
            )
        }
        val bytes = old.readBytes()
        database(live, "läuft")
        val sha = DatabaseSwap.sha256(live)

        assertEquals(7, CheckFile.prepare(context, bytes, files))
        SQLiteDatabase.openDatabase(files.check.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(AvlDatabase.VERSION, it.version) }
        assertTrue(bytes.contentEquals(old.readBytes()))
        assertEquals(sha, DatabaseSwap.sha256(live))
    }

    // --- Comparing ---------------------------------------------------------------------------

    @Test
    fun theDiffCountsChangedMissingAndExtraRows() {
        val here = mapOf(1L to "a", 2L to "b", 3L to "c")
        val backup = mapOf(1L to "a", 2L to "B", 4L to "d", 5L to "e")
        assertEquals(TableDiff(BackupTable.REMINDER, 3, 4, changed = 1, onlyHere = 1, onlyBackup = 2), TableCompare.diff(BackupTable.REMINDER, here, backup))
        assertTrue(TableCompare.diff(BackupTable.REMINDER, here, here).same)
    }

    // KRITISCH: every compared table can be read by its key, and a change in any column shows,
    // also NULL against an empty text.
    @Test
    fun rowsDifferInAnyColumnAndEveryTableHasAKey() {
        val a = database(File(tmp.root, "a.db"), "eins", "zwei")
        val b = database(File(tmp.root, "b.db"), "eins", "zwei")
        SQLiteDatabase.openDatabase(b.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("UPDATE suggestion SET reminderTitle = '' WHERE id = 2") }
        SQLiteDatabase.openDatabase(a.path, null, SQLiteDatabase.OPEN_READONLY).use { x ->
            SQLiteDatabase.openDatabase(b.path, null, SQLiteDatabase.OPEN_READONLY).use { y ->
                for (table in BackupTable.entries) {
                    val diff = TableCompare.diff(table, TableCompare.rows({ x.rawQuery(it, null) }, table), TableCompare.rows({ y.rawQuery(it, null) }, table))
                    if (table == BackupTable.SUGGESTION) {
                        assertEquals(TableDiff(table, 2, 2, changed = 1, onlyHere = 0, onlyBackup = 0), diff)
                    } else {
                        assertTrue(table.name, diff.same)
                    }
                }
            }
        }
    }

    @Test
    fun settingsAreComparedByTheirValuesOnly() {
        val a = """{"version":1,"values":{"theme":"DARK","dwell":"5"}}"""
        val cases = listOf(
            Triple("andere Reihenfolge", """{"values":{"dwell":"5","theme":"DARK"},"version":1}""", true),
            Triple("anderer Rahmen", """{"version":2,"values":{"theme":"DARK","dwell":"5"}}""", true),
            Triple("anderer Wert", """{"version":1,"values":{"theme":"LIGHT","dwell":"5"}}""", false),
            Triple("Wert fehlt", """{"version":1,"values":{"theme":"DARK"}}""", false),
        )
        for ((case, b, same) in cases) assertEquals(case, same, TableCompare.settingsSame(a, b))
    }

    // --- Comparing and staging with the running database (DatabaseRestore) ----------------

    // KRITISCH: comparing and staging only read the running database; the next start swaps it,
    // with the settings before kept for undoing (W-5, W-6).
    @Test
    fun compareAndStageLeaveTheRunningDatabaseUntilTheNextStart() = runBlocking {
        database(live, "hier")
        val settings = Settings(context)
        val backup = database(File(tmp.root, "sicherung.db"), "hier", "nur in der Sicherung").readBytes()
        val db = Room.databaseBuilder(context, AvlDatabase::class.java, live.path).build()
        val restore = DatabaseRestore(context, db, settings, files, clock = { Instant.parse("2026-11-09T11:00:00Z") })

        val comparison = restore.compare(BackupContents(backup, settings.backup()))
        assertEquals(TableDiff(BackupTable.SUGGESTION, 1, 2, 0, 0, 1), comparison.count(BackupTable.SUGGESTION))
        assertTrue(comparison.settingsSame)
        assertEquals(AvlDatabase.VERSION, comparison.schema)

        restore.stage(RestoreKind.CLOUD)
        assertTrue(restore.pending())
        assertTrue(restore.blocksBackup())
        assertFalse(files.check.exists())
        try {
            restore.compare(BackupContents(backup, settings.backup()))
            fail("a second compare while a restore waits")
        } catch (_: RestoreBusy) {
        }
        assertTrue("the staged file stays", files.ready.exists())
        db.close()
        assertEquals(listOf("hier"), texts(live))

        assertTrue(DatabaseSwap.applyPending(live, files) is SwapOutcome.Swapped)
        assertEquals(listOf("hier", "nur in der Sicherung"), texts(live))
        val before = files.befores().single()
        assertEquals(listOf("hier"), texts(before))
        assertTrue(File(before.path.removeSuffix(".db") + ".json").exists())
    }

    @Test
    fun discardingLeavesNothingAndAllowsTheNextCompare() = runBlocking {
        database(live, "hier")
        val settings = Settings(context)
        val db = Room.databaseBuilder(context, AvlDatabase::class.java, live.path).build()
        val restore = DatabaseRestore(context, db, settings, files)
        val backup = database(File(tmp.root, "sicherung.db"), "anders").readBytes()

        assertNotEquals(true, restore.compare(BackupContents(backup, settings.backup())).same)
        restore.discard()
        assertFalse(files.check.exists())
        assertFalse(files.settings.exists())
        assertFalse(restore.pending())
        restore.compare(BackupContents(backup, settings.backup()))
        restore.discard()
        db.close()
    }

    // A damaged backup ends the compare, cleans up and allows the next one.
    @Test
    fun aDamagedBackupCleansUpAfterItself() = runBlocking {
        database(live, "hier")
        val settings = Settings(context)
        val db = Room.databaseBuilder(context, AvlDatabase::class.java, live.path).build()
        val restore = DatabaseRestore(context, db, settings, files)
        try {
            restore.compare(BackupContents("kaputt".toByteArray(), "{}"))
            fail("accepted")
        } catch (_: DamagedDatabase) {
        }
        assertFalse(files.check.exists())
        assertFalse(files.settings.exists())
        restore.compare(BackupContents(database(File(tmp.root, "ok.db"), "hier").readBytes(), settings.backup()))
        restore.discard()
        db.close()
    }
}
