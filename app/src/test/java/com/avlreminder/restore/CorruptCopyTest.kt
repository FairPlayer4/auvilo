package com.avlreminder.restore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.KeepDamaged
import com.avlreminder.data.SuggestionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.LocalDate

/** A damaged database must survive the attempt to copy it (design.md 13: never delete data). */
@RunWith(AndroidJUnit4::class)
class CorruptCopyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A database of the app whose pages after the schema are garbage: it opens, reading all fails. */
    private fun damaged(): File {
        val live = File(tmp.root, "databases/avlreminder.db").also { it.parentFile!!.mkdirs() }
        val db = Room.databaseBuilder(context, AvlDatabase::class.java, live.path).build()
        runBlocking {
            repeat(400) { i ->
                db.suggestions().insert(SuggestionEntity(text = "Vorschlag $i ".repeat(20), important = false, createdAt = Instant.EPOCH, screen = "Heute", appVersion = "t"))
            }
        }
        db.close()
        // Overwrites the pages after the schema with garbage: the file opens, reading all fails.
        val pageSize = SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READONLY).use { it.pageSize }.toInt()
        RandomAccessFile(live, "rw").use { f ->
            for (page in 5 until (f.length() / pageSize).toInt()) {
                f.seek(page.toLong() * pageSize)
                f.write(ByteArray(pageSize) { 0x5A })
            }
        }
        return live
    }

    // KRITISCH: a failing copy of a damaged database keeps the database; Android's default
    // handler for damaged databases would delete it.
    @Test
    fun copyingADamagedDatabaseFailsButKeepsTheFile() {
        val live = damaged()
        val before = live.readBytes()

        val result = runCatching { DatabaseSwap.copy(live, File(tmp.root, "backup/kopie.db")) }

        assertTrue("the copy must fail", result.isFailure)
        assertTrue("the damaged database is still there", live.exists())
        assertArrayEquals(before, live.readBytes())
        assertFalse(File(tmp.root, "backup/kopie.db").exists())
    }

    // KRITISCH: Room with the app's open helper keeps a damaged database when a query hits a bad
    // page, and copies the raw file aside once (R2 of Review-Lauf 10).
    @Test
    fun roomKeepsADamagedDatabaseAndCopiesItAside() {
        val cases = listOf(
            // Counter-check: Room's own helper deletes the file, so the test can see the difference.
            Triple("Room ohne eigene Fabrik", false, false),
            Triple("Room mit KeepDamaged", true, true),
        )
        for ((case, keeping, expectKept) in cases) {
            val live = damaged()
            val before = live.readBytes()
            val aside = File(tmp.root, "backup-$keeping")
            val builder = Room.databaseBuilder(context, AvlDatabase::class.java, live.path)
            if (keeping) builder.openHelperFactory(KeepDamaged.Factory({ KeepDamaged.copyAside(it, aside, LocalDate.of(2026, 11, 9)) }))
            val db = builder.build()

            val read = runCatching { db.openHelper.writableDatabase.query("SELECT * FROM suggestion").use { c -> while (c.moveToNext()) c.getString(1) } }
            db.close()

            // Room's own helper deletes the file and starts empty, so reading then succeeds.
            assertEquals("$case: reading the damaged pages fails", expectKept, read.isFailure)
            assertEquals("$case: file unchanged", expectKept, live.exists() && live.readBytes().contentEquals(before))
            if (expectKept) {
                assertArrayEquals(case, before, File(aside, "beschaedigt-2026-11-09/avlreminder.db").readBytes())
                // Once a day: a second report the same day copies nothing.
                assertNull(case, KeepDamaged.copyAside(live, aside, LocalDate.of(2026, 11, 9)))
            }
            live.delete()
            File(live.path + "-wal").delete()
            File(live.path + "-shm").delete()
        }
    }
}
