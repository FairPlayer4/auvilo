package com.avlreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rules about time belong in `:core`, where Kover and the fake clock test them (design.md 1 and
 * 14, V1.3c14 U2): "Regeln in `:app`" came back in four review runs although each case was fixed.
 * Time arithmetic in `:app` is therefore only allowed in the files listed here, each with how many
 * places and why – mostly display, the cloud and previews.
 *
 * The list has to match exactly. A new place fails the test: move the rule to the core or, if it
 * really is no rule, add it here with its reason. A place that moved away fails it too, so the
 * number shrinks with every move and does not quietly grow back.
 */
class TimeRuleTest {
    private val arithmetic = Regex(
        """\.(plus|minus)(Nanos|Seconds|Minutes|Hours|Days|Weeks|Months|Years)?\(|\.truncatedTo\(|\bChronoUnit\.|\bDuration\.between\(|\bTemporalAdjusters\.""",
    )

    /** File (relative to `src/main/java/com/avlreminder`) to how many places it may have and why. */
    private val allowed = mapOf(
        "AvlApp.kt" to 1, // SystemZoneClock.shift for the glue tests
        "alarm/AlarmPause.kt" to 2, // display "bis morgen", timeout of the notification
        "alarm/OverviewNotifications.kt" to 2, // when the overview comes; OverviewTimes lives in :app (candidate for the core)
        "claude/ClaudeApi.kt" to 1, // period of a query of the interface for Claude
        "cloud/AutomaticBackup.kt" to 1, // age of the last backup
        "cloud/CloudBackup.kt" to 2, // duration in the log, distance of the deletion check
        "cloud/CloudUpload.kt" to 1, // duration in the log
        "restore/Snapshots.kt" to 1, // which local copies are kept per week
        "ui/Format.kt" to 2, // display ("morgen", start of the arrival window)
        "ui/Previews.kt" to 2, // preview data
        "ui/screens/EditScreen.kt" to 3, // form: hint text, suggestion for one more time of day
        "ui/screens/ReminderForm.kt" to 3, // suggestion for a new reminder: the next full hour
        "ui/screens/SessionContent.kt" to 1, // display of the running time
        "voice/VoiceActivity.kt" to 1, // display of the spoken time
    )

    @Test
    fun `Zeitrechnung steht in der App nur an den begruendeten Stellen`() {
        val base = File("src/main/java/com/avlreminder")
        assertTrue("sources not found from ${File(".").absolutePath}", base.isDirectory)
        val found = base.walk().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(base).invariantSeparatorsPath to arithmetic.findAll(it.readText()).count() }
            .filterValues { it > 0 }
        assertEquals(
            "Neue Zeitrechnung gehört in den Core; ist sie keine Regel, mit Grund in die Liste. Ist eine Stelle weggezogen, die Zahl verkleinern.",
            allowed.toSortedMap(), found.toSortedMap(),
        )
    }
}
