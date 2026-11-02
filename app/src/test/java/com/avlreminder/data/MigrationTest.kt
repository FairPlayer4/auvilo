package com.avlreminder.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.WeeklyGoal
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Room 1 → 2 (V1.1a), 2 → 3 (V1.1b), 3 → 4 (V1.2a), 4 → 5 (V1.2b) and 5 → 6 (V1.2c): existing reminders, occurrences and proofs survive, the
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

    private fun createV3() = create(3) { db ->
        db.execSQL(
            "INSERT INTO reminder (id, title, character, strictness, scheduleType, timeMinutes, weekdays, proof, active, createdAt, " +
                "proofPhoto, ignoreDnd, snoozeMinutes, arrivalPlaceId, arrivalWhenAway) " +
                "VALUES (1, 'Brunnen', 'DOCUMENT', 'NORMAL', 'DAILY', 1140, 0, 'CONFIRM', 1, 0, 0, 0, '10', 2, 'RING')",
        )
        db.execSQL("INSERT INTO place (id, name, lat, lon, radiusMeters) VALUES (2, 'Zuhause', 50.0, 8.0, 150)")
        db.execSQL("INSERT INTO checklist_item (id, reminderId, position, title) VALUES (7, 1, 0, 'Brunnen 1')")
    }

    private fun createV4() = create(4) { db ->
        db.execSQL(
            "INSERT INTO reminder (id, title, character, strictness, scheduleType, timeMinutes, weekdays, proof, active, createdAt, " +
                "proofPhoto, ignoreDnd, snoozeMinutes, archivedAt) " +
                "VALUES (1, 'Training', 'OVERCOME', 'NORMAL', 'WEEKDAYS', 1140, 31, 'CONFIRM', 1, 0, 0, 0, '10', NULL)",
        )
    }

    /** Three reminders: a newer "Was fehlt?" answer that names something, "Ja, sofort", "weiß ich noch nicht". */
    private fun createV5() = create(5) { db ->
        db.execSQL(
            "INSERT INTO reminder (id, title, character, strictness, scheduleType, timeMinutes, weekdays, proof, active, createdAt, " +
                "proofPhoto, ignoreDnd, snoozeMinutes) VALUES " +
                "(1, 'Steuer', 'OVERCOME', 'NORMAL', 'DAILY', 1140, 0, 'CONFIRM', 1, 0, 0, 0, '10'), " +
                "(2, 'Training', 'OVERCOME', 'NORMAL', 'DAILY', 1080, 0, 'CONFIRM', 1, 0, 0, 0, '10'), " +
                "(3, 'Keller', 'OVERCOME', 'NORMAL', 'DAILY', 1080, 0, 'CONFIRM', 1, 0, 0, 0, '10')",
        )
        db.execSQL(
            "INSERT INTO prep_answer (reminderId, question, text, unknown, at) VALUES " +
                "(1, 3, 'Belege suchen', 0, 100), (1, 3, ' Login fehlt ', 0, 200), (1, 2, 'keine Lust', 0, 200), " +
                "(2, 3, 'Schuhe fehlen', 0, 100), (2, 3, 'Ja, sofort', 0, 200), " +
                "(3, 3, NULL, 1, 100)",
        )
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()

    @Test
    fun version5KeepsDataAndTurnsTheMissingThingIntoABlocker() = runTest {
        createV5()
        val db = open()
        try {
            val repo = RoomReminderRepository(db)
            val r = repo.reminder(1)!!
            assertEquals("Steuer", r.title)
            assertNull(r.preNotice)
            assertNull(r.prepFor)
            assertEquals(false, r.solvedBlockerIsMinimum)
            // Only the newest answer, trimmed; "Ja, sofort" and "weiß ich noch nicht" are none.
            val blockers = repo.blockers(1)
            assertEquals(listOf("Login fehlt"), blockers.map { it.text })
            assertEquals(BlockerOrigin.STARTKLAR, blockers.single().origin)
            assertTrue(blockers.single().isOpen)
            assertEquals(Instant.ofEpochMilli(200), blockers.single().createdAt)
            assertTrue(repo.blockers(2).isEmpty())
            assertTrue(repo.blockers(3).isEmpty())

            // The new columns hold a deadline.
            val deadline = Schedule.Deadline(
                LocalDateTime.of(2026, 11, 6, 12, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30,
            )
            repo.saveReminder(r.copy(schedule = deadline, solvedBlockerIsMinimum = true))
            assertEquals(deadline, repo.reminder(1)!!.schedule)
            assertTrue(repo.reminder(1)!!.solvedBlockerIsMinimum)

            // Deleting the reminder takes its blockers along.
            repo.deleteReminder(1)
            assertTrue(repo.blockers(1).isEmpty())
            assertTrue(db.dao().observeTemplates().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun backupIsTakenBeforeTheUpgradeFromVersion5() {
        createV5()
        val dir = File(context.filesDir, "backup-test-5").apply { deleteRecursively() }
        assertTrue(AvlDatabase.backupBeforeMigration(file, dir)!!.name.startsWith("avlreminder-v5-"))
    }

    @Test
    fun version4KeepsDataAndGetsWeeklyGoal() = runTest {
        createV4()
        val db = open()
        try {
            val repo = RoomReminderRepository(db)
            val r = repo.reminder(1)!!
            assertEquals("Training", r.title)
            assertNull(r.weeklyGoal)
            repo.saveReminder(r.copy(weeklyGoal = WeeklyGoal(target = 3, max = 4, minimum = 2)))
            assertEquals(WeeklyGoal(3, 4, 2), repo.reminder(1)!!.weeklyGoal)
            repo.saveReminder(repo.reminder(1)!!.copy(weeklyGoal = null))
            assertNull(repo.reminder(1)!!.weeklyGoal)
        } finally {
            db.close()
        }
    }

    @Test
    fun backupIsTakenBeforeTheUpgradeFromVersion4() {
        createV4()
        val dir = File(context.filesDir, "backup-test-4").apply { deleteRecursively() }
        assertTrue(AvlDatabase.backupBeforeMigration(file, dir)!!.name.startsWith("avlreminder-v4-"))
    }

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
    fun version3KeepsDataAndGetsArchiveAndSuggestions() = runTest {
        createV3()
        val db = open()
        try {
            val repo = RoomReminderRepository(db)
            val r = repo.reminder(1)!!
            assertNull(r.archivedAt)
            assertEquals(2L, r.arrival!!.placeId)
            assertEquals("Brunnen 1", r.checklist.single().title)
            repo.saveReminder(r.copy(archivedAt = Instant.ofEpochMilli(5000)))
            assertEquals(5000L, repo.reminder(1)!!.archivedAt!!.toEpochMilli())

            val id = db.suggestions().insert(
                SuggestionEntity(text = "Knopf größer", important = true, createdAt = Instant.ofEpochMilli(1), screen = "Heute", appVersion = "1.2a"),
            )
            val saved = db.suggestions().observe().first().single()
            assertEquals(id, saved.id)
            assertEquals(SuggestionStatus.OPEN, saved.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun backupIsTakenBeforeTheUpgradeFromVersion3() {
        createV3()
        val dir = File(context.filesDir, "backup-test-3").apply { deleteRecursively() }
        assertTrue(AvlDatabase.backupBeforeMigration(file, dir)!!.name.startsWith("avlreminder-v3-"))
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
