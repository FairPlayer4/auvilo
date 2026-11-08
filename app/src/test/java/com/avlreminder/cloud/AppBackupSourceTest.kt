package com.avlreminder.cloud

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.Settings
import com.avlreminder.data.SuggestionEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** The app's data as backup payload: a database copy that opens, and the settings. */
@RunWith(AndroidJUnit4::class)
class AppBackupSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db by lazy { Room.databaseBuilder(context, AvlDatabase::class.java, File(tmp.root, "app.db").path).build() }

    @After
    fun close() = db.close()

    @Test
    fun thePayloadHoldsAWorkingCopyOfTheDatabaseAndTheSettings() = runBlocking {
        db.suggestions().insert(SuggestionEntity(text = "Testeintrag", important = false, createdAt = Instant.EPOCH, screen = "Heute", appVersion = "t"))
        val cache = tmp.newFolder("cache")

        val contents = BackupZip.unpack(AppBackupSource(db, Settings(context), cache).payload())

        val copy = File(tmp.root, "copy.db").apply { writeBytes(contents.database) }
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { c ->
            assertEquals(AvlDatabase.VERSION, c.version)
            c.rawQuery("SELECT text FROM suggestion", null).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Testeintrag", row.getString(0))
            }
        }
        JSONObject(contents.settings) // the settings are JSON
        assertEquals(emptyList<String>(), cache.list()!!.toList()) // the copy is gone again
    }
}
