package com.avlreminder.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Room 1 → 2 (V1.1a) and 2 → 3 (V1.1b): existing reminders, occurrences and proofs survive, the
 * backup is taken first. The old database is built from the exported schema; Room checks the
 * migrated tables against the current version on open.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath("migration-test.db")

    private fun create(version: Int, data: (SQLiteDatabase) -> Unit) {
        file.parentFile?.mkdirs()
        file.delete()
        // Unit tests run in the module directory.
        val schema = JSONObject(File("schemas/com.avlreminder.data.AvlDatabase/$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            data(db)
        }
    }

    private fun createV2() = create(2) { db ->
        db.execSQL(
            "INSERT INTO reminder (id, title, character, strictness, scheduleType, timeMinutes, weekdays, proof, active, createdAt, " +
                "proofPhoto, ignoreDnd, snoozeMinutes) VALUES (1, 'Training', 'OVERCOME', 'NORMAL', 'DAILY', 1140, 0, 'CONFIRM', 1, 0, 0, 1, '10,30')",
        )
        db.execSQL(
            "INSERT INTO occurrence (id, reminderId, dueAt, currentAt, state, postponeCount, pausedUntil) VALUES (5, 1, 1000, 1000, 'DUE', 0, 1500)",
        )
        db.execSQL("INSERT INTO proof (id, occurrenceId, type, takenAt, text) VALUES (3, 5, 'TEXT', 2000, 'erledigt')")
    }

    private fun createV1() = create(1) { db ->
            db.execSQL(
                "INSERT INTO reminder (id, title, why, character, strictness, scheduleType, timeMinutes, onceAt, weekdays, " +
                    "intervalDays, goal, minimum, minimumLimitPerWeek, proof, postponeLimit, active, createdAt) VALUES " +
                    "(1, 'Katzenbrunnen', 'Katzen trinken', 'DOCUMENT', 'STRICT', 'DAILY', 1080, NULL, 0, NULL, " +
                    "'komplett', 'Wasser', 1, 'PHOTO', 1, 1, 0), " +
                    "(2, 'Vitamin D', NULL, 'DOCUMENT', 'RELAXED', 'DAILY', 480, NULL, 0, NULL, NULL, NULL, NULL, 'CONFIRM', NULL, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO occurrence (id, reminderId, dueAt, currentAt, state, postponeCount, resolvedAt, resolution, note) " +
                    "VALUES (5, 1, 1000, 1000, 'DONE', 0, 2000, 'GOAL', NULL)",
            )
            db.execSQL("INSERT INTO proof (id, occurrenceId, type, filePath, takenAt) VALUES (3, 5, 'PHOTO', '/p/x.jpg', 2000)")
    }

    private fun open() = Room.databaseBuilder(context, AvlDatabase::class.java, file.path)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3).allowMainThreadQueries().build()

    @Test
    fun version2KeepsDataAndGetsPlacesAndChecklists() = runTest {
        createV2()
        val db = open()
        try {
            val repo = RoomReminderRepository(db)
            val training = repo.reminder(1)!!
            assertNull(training.arrival)
            assertTrue(training.checklist.isEmpty())
            assertEquals(listOf(10, 30), training.snoozeMinutes)
            assertTrue(training.ignoreDnd)
            val o = repo.occurrence(5)!!
            assertNull(o.firstProgressAt)
            assertEquals(1500L, o.pausedUntil!!.toEpochMilli())
            val proof = db.dao().observeProofs().first().single().toModel()
            assertEquals("erledigt", proof.text)
            assertNull(proof.itemId)
            assertTrue(repo.places().isEmpty())

            // The new tables work, with their foreign keys.
            val withList = repo.reminder(1)!!.copy(checklist = listOf(ChecklistItem(title = "Brunnen 1")))
            repo.saveReminder(withList)
            val item = repo.reminder(1)!!.checklist.single()
            repo.addItemDone(ItemDone(occurrenceId = 5, itemId = item.id, doneAt = Instant.ofEpochMilli(3000)))
            assertEquals(item.id, repo.itemsDone(5).single().itemId)
        } finally {
            db.close()
        }
    }

    @Test
    fun backupIsTakenBeforeTheUpgradeFromVersion2() {
        createV2()
        val dir = File(context.filesDir, "backup-test-2").apply { deleteRecursively() }
        assertTrue(AvlDatabase.backupBeforeMigration(file, dir)!!.name.startsWith("avlreminder-v2-"))
    }

    @Test
    fun keepsDataAndTurnsPhotoIntoEvidenceWithPhoto() = runTest {
        createV1()
        val db = open()
        try {
            val repo = RoomReminderRepository(db)
            val cat = repo.reminder(1)!!
            assertEquals(ProofSpec.PHOTO, cat.proof)
            assertEquals(listOf(10), cat.snoozeMinutes)
            assertEquals(false, cat.ignoreDnd)
            assertNull(cat.motivationAudio)
            assertEquals("Katzen trinken", cat.why)
            assertEquals(ProofSpec.CONFIRM, repo.reminder(2)!!.proof)

            assertNull(repo.occurrence(5)!!.pausedUntil)
            val proof = db.dao().observeProofs().first().single().toModel()
            assertEquals(ProofType.PHOTO, proof.type)
            assertEquals("/p/x.jpg", proof.filePath)
            assertNull(proof.text)
            assertTrue(repo.prepAnswers(1).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun backupIsTakenOnlyBeforeAnUpgrade() {
        createV1()
        val dir = File(context.filesDir, "backup-test").apply { deleteRecursively() }

        val backup = AvlDatabase.backupBeforeMigration(file, dir)
        assertNotNull(backup)
        assertTrue(backup!!.name.startsWith("avlreminder-v1-"))
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery("SELECT COUNT(*) FROM reminder", null).use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
        }

        open().apply { openHelper.writableDatabase }.close()
        assertNull(AvlDatabase.backupBeforeMigration(file, dir))
        assertNull(AvlDatabase.backupBeforeMigration(File(dir, "missing.db"), dir))
    }
}
