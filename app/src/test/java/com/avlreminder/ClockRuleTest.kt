package com.avlreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Time the app decides by comes from the engine's clock (`AppGraph.clock`, [SystemZoneClock]),
 * not from the system clock (design.md 1, Review-Lauf 5, E21): otherwise tests with a set time
 * see two different "now" and the shift for the glue tests does not reach it.
 * Log stamps and file names may read the system clock; they are listed here with the reason.
 */
class ClockRuleTest {
    private val systemClock = Regex("""\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\(\)|System\.currentTimeMillis\(\)|Clock\.systemDefaultZone\(\)""")

    /** File (relative to `src/main/java/com/avlreminder`) to how many reads it may have and why. */
    private val allowed = mapOf(
        "AvlApp.kt" to 1, // SystemZoneClock itself
        "system/Diag.kt" to 1, // log stamp
        "alarm/AndroidAlarmPort.kt" to 1, // "late=" in the log
        "alarm/AudioRecorder.kt" to 1, // file name
        "alarm/ProofCamera.kt" to 1, // file name
        "data/Database.kt" to 1, // file name of the backup before a migration
        "data/KeepDamaged.kt" to 1, // folder name of the rescued files
    )

    @Test
    fun `die App liest die Zeit, nach der sie entscheidet, von der Uhr der Engine`() {
        val base = File("src/main/java/com/avlreminder")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val found = base.walk().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(base).invariantSeparatorsPath to systemClock.findAll(it.readText()).count() }
            .filterValues { it > 0 }
        assertEquals(allowed, found)
    }
}
