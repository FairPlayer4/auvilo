package com.avlreminder.claude

import com.avlreminder.core.model.WhenAway
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Arrival
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.PostponePeriod
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.rules.Presets
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.RoomReminderRepository
import com.avlreminder.data.toModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The interface for Claude (Anf. 7.26 AG11) with the real engine on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class ClaudeApiTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-03T08:00:00Z") // Tuesday 09:00
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AvlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = RoomReminderRepository(db)
    private val alarms = mutableMapOf<Long, Instant>()
    private val engine = ReminderEngine(
        repo,
        object : AlarmPort {
            override fun set(occurrenceId: Long, at: Instant, kind: AlarmKind) { alarms[occurrenceId] = at }
            override fun cancel(occurrenceId: Long) { alarms.remove(occurrenceId) }
        },
        Clock.fixed(now, zone),
    )
    private var enabled = true
    private val api = ClaudeApi(object : ClaudeApi.Access {
        override suspend fun enabled() = enabled
        override suspend fun <T> withEngine(block: suspend (ReminderEngine) -> T): T = block(engine)
        override suspend fun reminders(): List<Reminder> = repo.reminders()
        override suspend fun openOccurrences(): List<Occurrence> = repo.openOccurrences()
        override suspend fun resolvedOccurrences(): List<Occurrence> = db.dao().observeResolved().first().map { it.toModel() }
        override fun now(): Instant = now
        override val zone: ZoneId = this@ClaudeApiTest.zone
    })

    @After
    fun close() = db.close()

    private suspend fun call(method: String, arg: String? = null) = JSONObject(api.call(method, arg))

    private val testReminder = """{"reminder": {"title": "Gerätetest", "strictness": "NORMAL",
        "schedule": {"type": "daily", "times": ["19:15", "12:30"]}, "minimum": "Test nur vorbereiten"}}"""

    @Test
    fun nothingWithoutTheSwitchButTheStatus() = runTest {
        enabled = false
        assertFalse(call("status").getBoolean("enabled"))
        val answer = call("reminders")
        assertFalse(answer.getBoolean("ok"))
        assertTrue(answer.getString("error").startsWith("nicht freigegeben"))
        assertFalse(call("save", testReminder).getBoolean("ok"))
        assertTrue(repo.reminders().isEmpty())
    }

    @Test
    fun createsAReminderSeveralTimesADayWithItsPreset() = runTest {
        val saved = call("save", testReminder)
        assertTrue(saved.toString(), saved.getBoolean("ok"))
        val r = repo.reminder(saved.getLong("id"))!!
        assertEquals("Gerätetest", r.title)
        assertEquals(Schedule.Daily(listOf(LocalTime.of(12, 30), LocalTime.of(19, 15))), r.schedule)
        assertEquals(Presets.of(Strictness.NORMAL).postponeLimit, r.postponeLimit)
        assertEquals("Test nur vorbereiten", r.minimum)
        // Planned for 12:30 today, alarm set.
        val next = repo.openOccurrence(r.id)!!
        assertEquals(Instant.parse("2026-11-03T11:30:00Z"), next.dueAt)
        assertEquals(next.dueAt, alarms[next.id])
    }

    @Test
    fun aNewReminderWithoutStrictnessTakesTheNormalPresetLikeTheForm() = runTest {
        val saved = call("save", """{"reminder": {"title": "x", "schedule": {"type": "daily", "time": "19:00"}}}""")
        val r = repo.reminder(saved.getLong("id"))!!
        assertEquals(Strictness.NORMAL, r.strictness)
        assertEquals(Presets.of(Strictness.NORMAL).postponeLimit, r.postponeLimit)
    }

    // Das Verschiebe-Limit pro Woche oder Monat auch über die Schnittstelle (Z2b); neu pro Termin wie die Vorlagen.
    @Test
    fun thePostponePeriodGoesBothWays() = runTest {
        val id = call("save", """{"reminder": {"title": "x", "schedule": {"type": "daily", "time": "19:00"}}}""").getLong("id")
        assertEquals(PostponePeriod.OCCURRENCE, repo.reminder(id)!!.postponePeriod)
        assertTrue(call("save", """{"reminder": {"id": $id, "postponeLimit": 2, "postponePeriod": "WEEK"}}""").getBoolean("ok"))
        assertEquals(PostponePeriod.WEEK, repo.reminder(id)!!.postponePeriod)
        val listed = call("reminders", "{}").getJSONArray("reminders").getJSONObject(0)
        assertEquals("WEEK", listed.getString("postponePeriod"))
        assertEquals(2, listed.getInt("postponeLimit"))
    }

    @Test
    fun aStrictReminderWithoutMinimumHasNoWeeklyLimit() = runTest {
        val saved = call("save", """{"reminder": {"title": "x", "strictness": "STRICT", "schedule": {"type": "daily", "time": "19:00"}}}""")
        assertTrue(saved.toString(), saved.getBoolean("ok"))
        assertEquals(null, repo.reminder(saved.getLong("id"))!!.minimumLimitPerWeek)
    }

    @Test
    fun anEditChangesOnlyTheGivenKeys() = runTest {
        val id = call("save", testReminder).getLong("id")
        val edited = call("save", """{"reminder": {"id": $id, "strictness": "STRICT", "goal": "zwei Tests"}}""")
        assertTrue(edited.toString(), edited.getBoolean("ok"))
        val r = repo.reminder(id)!!
        assertEquals(Strictness.STRICT, r.strictness)
        assertEquals("zwei Tests", r.goal)
        assertEquals("Test nur vorbereiten", r.minimum)
        // An edit keeps proof and limits; only a new reminder takes the preset.
        assertEquals(Presets.of(Strictness.NORMAL).proof, r.proof)
    }

    // KRITISCH: Ein Edit ohne Zeitraum setzt Vorlauf und Puffer nicht zurück, und die WLANs der Orte bleiben auf dem Handy (Anf. 7.29).
    @Test
    fun theArrivalWindowSurvivesAnEditAndTheWlansStayOnTheDevice() = runTest {
        val home = repo.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5, wifis = listOf(PlaceWifi("aa:bb:cc:dd:ee:01", "HeimNetz"))))
        val created = call(
            "save",
            """{"reminder": {"title": "Training", "strictness": "NORMAL", "schedule": {"type": "daily", "times": ["19:30"]},
                "arrival": {"placeId": $home, "leadMinutes": 60, "bufferMinutes": 10}}}""",
        )
        assertTrue(created.toString(), created.getBoolean("ok"))
        val id = created.getLong("id")
        assertEquals(Arrival(home, WhenAway.DROP, leadMinutes = 60, bufferMinutes = 10), repo.reminder(id)!!.arrival)

        call("save", """{"reminder": {"id": $id, "arrival": {"placeId": $home, "whenAway": "RING"}}}""")
        assertEquals(Arrival(home, WhenAway.RING, leadMinutes = 60, bufferMinutes = 10), repo.reminder(id)!!.arrival)

        val shown = listOf(call("reminders"), call("reminder", """{"id": $id}"""), call("today")).joinToString()
        assertTrue(shown, shown.contains("\"leadMinutes\":60"))
        assertFalse("keine BSSID", shown.contains("aa:bb", ignoreCase = true))
        assertFalse("kein WLAN-Name", shown.contains("HeimNetz"))
    }

    @Test
    fun readsRemindersTodayAndDetails() = runTest {
        val id = call("save", testReminder).getLong("id")
        call("addBlocker", """{"reminderId": $id, "text": "Handy nicht am Rechner"}""")

        val list = call("reminders").getJSONArray("reminders")
        assertEquals(1, list.length())
        assertEquals("2026-11-03T12:30", list.getJSONObject(0).getJSONObject("next").getString("dueAt"))
        assertEquals("12:30", list.getJSONObject(0).getJSONObject("schedule").getJSONArray("times").getString(0))

        val detail = call("reminder", """{"id": $id}""")
        assertEquals("Handy nicht am Rechner", detail.getJSONArray("blockers").getJSONObject(0).getString("text"))
        assertEquals(0, detail.getJSONObject("stats").getJSONObject("total").getInt("goal"))

        assertEquals("Gerätetest", call("today").getJSONArray("laterToday").getJSONObject(0).getString("title"))
        assertEquals(0, call("history").getJSONArray("occurrences").length())
    }

    @Test
    fun pausesAndArchivesButDoesNotDelete() = runTest {
        val id = call("save", testReminder).getLong("id")
        assertTrue(call("setActive", """{"id": $id, "active": false}""").getBoolean("ok"))
        assertFalse(repo.reminder(id)!!.active)
        assertTrue(call("archive", """{"id": $id}""").getBoolean("ok"))
        assertTrue(repo.reminder(id)!!.archivedAt != null)
        assertEquals(0, call("reminders").getJSONArray("reminders").length())
        assertEquals(1, call("reminders", """{"archived": true}""").getJSONArray("reminders").length())
        assertFalse(call("delete", """{"id": $id}""").getBoolean("ok"))
    }

    @Test
    fun aRingingReminderOnlyChangesInTheApp() = runTest {
        val id = call("save", testReminder).getLong("id")
        val open = repo.openOccurrence(id)!!
        repo.saveOccurrence(open.copy(state = OccurrenceState.DUE))
        for ((method, arg) in listOf(
            "setActive" to """{"id": $id, "active": false}""",
            "archive" to """{"id": $id}""",
            "save" to """{"reminder": {"id": $id, "postponeLimit": null}}""",
        )) {
            val answer = call(method, arg)
            assertEquals(method, "läuft gerade – nur in der App", answer.getString("error"))
        }
        assertTrue(repo.reminder(id)!!.active)
        assertEquals(open.id, repo.openOccurrence(id)!!.id)
        // Planned but already past (the alarm is late): the same.
        repo.saveOccurrence(open.copy(currentAt = now))
        assertFalse(call("setActive", """{"id": $id, "active": false}""").getBoolean("ok"))
        // It rang and now waits for the arrival, planned again for later (AG5, Review-Lauf 4 M4).
        repo.saveOccurrence(open.copy(currentAt = now.plusSeconds(3600), awaitsArrival = true))
        assertEquals("läuft gerade – nur in der App", call("archive", """{"id": $id}""").getString("error"))
        assertTrue(repo.reminder(id)!!.archivedAt == null)
        // A blocker is fine.
        assertTrue(call("addBlocker", """{"reminderId": $id, "text": "x"}""").getBoolean("ok"))
    }

    @Test
    fun saysWhatIsWrong() = runTest {
        val noSchedule = call("save", """{"reminder": {"title": "x"}}""")
        assertEquals("neuer Reminder braucht title und schedule", noSchedule.getString("error"))
        val past = call("save", """{"reminder": {"title": "x", "schedule": {"type": "once", "at": "2026-11-02T10:00"}}}""")
        assertEquals("Zeitpunkt liegt in der Vergangenheit", past.getString("error"))
        assertFalse(call("reminder", """{"id": 99}""").getBoolean("ok"))
        assertFalse(call("save", "kein json").getBoolean("ok"))
        assertTrue(call("methods").getJSONArray("methods").toString().contains("addBlocker"))
    }

    @Test
    fun theLogLineHoldsNoContents() {
        val line = ClaudeApi.logLine("save", """{"reminder": {"id": 7, "title": "Geheim"}}""", """{"ok": true}""")
        assertEquals("claude save id=7 ok", line)
        assertEquals("claude reminders error", ClaudeApi.logLine("reminders", null, """{"ok": false}"""))
        // A new reminder: the id from the answer.
        assertEquals("claude save id=9 ok", ClaudeApi.logLine("save", """{"reminder": {"title": "Geheim"}}""", """{"ok": true, "id": 9}"""))
    }
}
