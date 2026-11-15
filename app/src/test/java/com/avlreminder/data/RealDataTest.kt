package com.avlreminder.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.scenarios.Simulation
import com.avlreminder.ui.screens.ReminderForm
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Kevin's real data, pseudonymized (testdata/real-*.json, made by tools/testdata/export_testdata.py):
 * loaded into the schema it was exported from, migrated to the current one, then used by the
 * engine. Catches what made-up data does not: migrations of real rows, old combinations of
 * settings, a week with all of Kevin's reminders at once.
 */
@RunWith(AndroidJUnit4::class)
class RealDataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath("real-data.db")
    private var db: AvlDatabase? = null

    /** The newest export; the tests are skipped without one. */
    private val data: JSONObject? = File("../testdata").listFiles { f -> f.name.startsWith("real-") && f.name.endsWith(".json") }
        ?.maxByOrNull { it.name }?.let { JSONObject(it.readText()) }

    @After
    fun tearDown() {
        db?.close()
    }

    private fun load(): AvlDatabase {
        val data = data
        assumeTrue("no testdata/real-*.json", data != null)
        val tables = data!!.getJSONObject("tables")
        SchemaDatabases.create(file, data.getInt("schemaVersion")) { sql ->
            for (table in tables.keys()) {
                val rows = tables.getJSONArray(table)
                for (i in 0 until rows.length()) sql.insertOrThrow(table, null, rows.getJSONObject(i).toValues())
            }
        }
        return Room.databaseBuilder(context, AvlDatabase::class.java, file.path)
            .addMigrations(*AvlDatabase.MIGRATIONS)
            .allowMainThreadQueries().build().also { db = it }
    }

    private fun JSONObject.toValues() = ContentValues().also { v ->
        for (key in keys()) {
            when (val value = get(key)) {
                JSONObject.NULL -> v.putNull(key)
                is Int -> v.put(key, value)
                is Long -> v.put(key, value)
                is Double -> v.put(key, value)
                is Boolean -> v.put(key, value)
                else -> v.put(key, value.toString())
            }
        }
    }

    private fun count(table: String) = data!!.getJSONObject("tables").getJSONArray(table).length()

    /** Shortly after the export: the newest thing that happened. */
    private fun exportedAt(): Instant {
        val events = data!!.getJSONObject("tables").getJSONArray("event")
        return Instant.ofEpochMilli((0 until events.length()).maxOf { events.getJSONObject(it).getLong("at") }).plusSeconds(60)
    }

    @Test
    fun theRealDataMigratesAndEveryRowReadsAsModel() = runTest {
        val repo = RoomReminderRepository(load())
        val reminders = repo.reminders()
        assertEquals(count("reminder"), reminders.size)
        assertEquals(count("place"), repo.places().size)
        var occurrences = 0
        for (r in reminders) {
            occurrences += repo.recentResolved(r.id, Int.MAX_VALUE).size + listOfNotNull(repo.openOccurrence(r.id)).size
            repo.prepAnswers(r.id)
            repo.blockers(r.id)
            repo.itemsDoneOfReminder(r.id)
        }
        assertEquals(count("occurrence"), occurrences)
    }

    /**
     * Every real reminder survives opening and saving the form unchanged, apart from what the form
     * does not know (Review-Lauf 5, E22): the other form tests only use reminders made by the form.
     */
    @Test
    fun everyRealReminderSurvivesTheForm() = runTest {
        val today = exportedAt().atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDate()
        for (r in RoomReminderRepository(load()).reminders()) {
            val expected = r.copy(archivedAt = null, hintQuietUntil = null)
            assertEquals("${r.id}", expected, ReminderForm.of(r, today).toReminder(exportedAt()))
        }
    }

    @Test
    fun theAlarmScreenCanBeBuiltForEveryOpenOccurrence() = runTest {
        val sim = Simulation(exportedAt(), RoomReminderRepository(load()))
        for (o in sim.repo.openOccurrences()) assertNotNull("occurrence ${o.id}", sim.engine.alarmView(o.id))
    }

    /**
     * All of Kevin's reminders for a week, nobody reacts and the position is unknown: the rules
     * of [Simulation.checkInvariants] hold after every alarm, and each day ends cleanly.
     */
    @Test
    fun aWeekWithAllRemindersKeepsTheRules() = runTest {
        val start = exportedAt()
        val sim = Simulation(start, RoomReminderRepository(load()))
        sim.engine.sync()
        sim.checkInvariants()
        sim.runUntil(start.plus(Duration.ofDays(7)))

        assertTrue("nothing rang in a week", sim.rings.isNotEmpty())
        val missed = sim.repo.reminders().filter { it.active }.sumOf { r ->
            sim.repo.recentResolved(r.id, Int.MAX_VALUE).count { it.resolvedAt!! > start }
        }
        assertTrue("no day ended in a week", missed > 0)
    }

    /** The phone is off for two days in the middle of the week; on start everything is planned again. */
    @Test
    fun twoDaysWithThePhoneOffAreCaughtUp() = runTest {
        val start = exportedAt()
        val sim = Simulation(start, RoomReminderRepository(load()))
        sim.engine.sync()
        sim.runUntil(start.plus(Duration.ofDays(2)))
        sim.phoneOff(until = start.plus(Duration.ofDays(4)))
        sim.runUntil(start.plus(Duration.ofDays(5)))

        for (r in sim.repo.reminders().filter { it.active }) {
            val open = sim.repo.openOccurrence(r.id) ?: continue
            assertTrue("${r.title} still open from before the phone was off", open.state != OccurrenceState.PLANNED || open.currentAt > start.plus(Duration.ofDays(4)))
        }
    }
}
