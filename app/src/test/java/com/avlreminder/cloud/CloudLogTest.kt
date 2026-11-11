package com.avlreminder.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.connectrpc.Code
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** The backend logger (A-12, E-D-1): only what may be sent as a diagnosis. */
@RunWith(AndroidJUnit4::class)
class CloudLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-11-08T10:15:30.123Z")
    private fun log(max: Int = 500) = CloudLog(tmp.root, header = { mapOf("app" to "1.3 (14)", "schema" to 8) }, clock = { now }, max = max)

    @Test
    fun aSendingStartsWithTheHeaderThenTheEvents() {
        val log = log()
        log.record(CloudEvent.BACKED_UP, mapOf("kib" to 12, "zaehler" to 3L))
        val lines = log.lines()
        val head = JSONObject(lines[0])
        assertEquals("kopf", head.getString("art"))
        assertEquals("avlreminder/diagnose/v1", head.getString("format"))
        assertEquals("1.3 (14)", head.getString("app"))
        assertEquals("knopf", head.getString("anlass"))
        val event = JSONObject(lines[1])
        assertEquals("2026-11-08T10:15:30.123Z", event.getString("zeit"))
        assertEquals("INFO", event.getString("stufe"))
        assertEquals("sicherung", event.getString("bereich"))
        assertEquals("gesichert", event.getString("ereignis"))
        assertEquals(12, event.getJSONObject("felder").getInt("kib"))
        assertEquals(2, lines.size)
    }

    @Test
    fun numbersBooleansAndEnumsAreFields() {
        val log = log()
        log.record(CloudEvent.FAILED, mapOf("schritt" to CloudStep.BACKUP, "code" to Code.UNAVAILABLE, "vom_dienst" to false))
        val fields = JSONObject(log.lines()[1]).getJSONObject("felder")
        assertEquals("BACKUP", fields.getString("schritt"))
        assertEquals("UNAVAILABLE", fields.getString("code"))
        assertFalse(fields.getBoolean("vom_dienst"))
    }

    // KRITISCH: no free text (E-D-1); a title passed by mistake must not get in.
    @Test
    fun textAndOtherObjectsAreRefusedAsFields() {
        for (value in listOf<Any>("Zahnarzt", StringBuilder("x"), 1.5, listOf(1), File("x"))) {
            assertThrows(value.javaClass.name, IllegalArgumentException::class.java) {
                log().record(CloudEvent.FAILED, mapOf("wert" to value))
            }
        }
        assertEquals(1, log().lines().size)
    }

    // KRITISCH: an exception's message may hold anything (a title, a token); only class and frames go in.
    @Test
    fun anExceptionKeepsClassAndFramesButNotItsMessage() {
        val log = log()
        log.record(CloudEvent.FAILED, error = IllegalStateException("Zahnarzt am Dienstag"))
        val line = log.lines()[1]
        assertFalse(line, "Zahnarzt" in line)
        val error = JSONObject(line).getJSONObject("ausnahme")
        assertEquals("java.lang.IllegalStateException", error.getString("klasse"))
        val frames = error.getJSONArray("stapel")
        assertTrue(frames.length() in 1..20)
        assertTrue(frames.getString(0), frames.getString(0).contains("CloudLogTest"))
    }

    @Test
    fun onlyTheNewestEventsAreKept() {
        val log = log(max = 10)
        repeat(30) { log.record(CloudEvent.BACKED_UP, mapOf("zaehler" to it)) }
        val events = log.lines().drop(1)
        assertEquals(10, events.size)
        assertEquals((20..29).toList(), events.map { JSONObject(it).getJSONObject("felder").getInt("zaehler") })
        assertTrue(File(tmp.root, "events.jsonl").readLines().size <= 12)
    }

    @Test
    fun aDirectoryThatCannotBeWrittenBreaksNothing() {
        val blocked = File(tmp.root, "datei").apply { writeText("x") }
        val log = CloudLog(blocked, header = { emptyMap() })
        log.record(CloudEvent.SWITCHED_ON)
        assertEquals(1, log.lines().size)
    }
}
