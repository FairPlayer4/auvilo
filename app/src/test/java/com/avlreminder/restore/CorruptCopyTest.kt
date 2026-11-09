package com.avlreminder.restore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.SuggestionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant

/** A damaged database must survive the attempt to copy it (design.md 13: never delete data). */
@RunWith(AndroidJUnit4::class)
class CorruptCopyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // KRITISCH: a failing copy of a damaged database keeps the database; Android's default
    // handler for damaged databases would delete it.
    @Test
    fun copyingADamagedDatabaseFailsButKeepsTheFile() {
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
        val before = live.readBytes()

        val result = runCatching { DatabaseSwap.copy(live, File(tmp.root, "backup/kopie.db")) }

        assertTrue("the copy must fail", result.isFailure)
        assertTrue("the damaged database is still there", live.exists())
        assertArrayEquals(before, live.readBytes())
        assertFalse(File(tmp.root, "backup/kopie.db").exists())
    }
}
