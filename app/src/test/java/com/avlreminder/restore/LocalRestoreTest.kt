package com.avlreminder.restore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.cloud.BackupContents
import com.avlreminder.cloud.BackupZip
import com.avlreminder.cloud.CloudLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Daily copies (R-1) and restoring from them or undoing a restore (W-6), without the cloud. */
@RunWith(AndroidJUnit4::class)
class LocalRestoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val files by lazy { RestoreFiles(tmp.newFolder("files")) }
    private var copies = 0
    private val snapshots by lazy { Snapshots(files.backupDir) { target -> copies++; target.writeText("kopie $copies") } }
    private val events by lazy { CloudLog(tmp.newFolder("diag"), header = { mapOf("app" to "test") }) }
    private var now = Instant.parse("2026-11-09T12:00:00Z")

    @After
    fun stop() = scope.cancel()

    private class FakeTarget : RestoreTarget {
        val compared = mutableListOf<BackupContents>()
        val staged = mutableListOf<RestoreKind>()
        var discards = 0
        var restarts = 0
        var error: Throwable? = null

        override suspend fun compare(contents: BackupContents): Comparison {
            error?.let { throw it }
            compared += contents
            return oneReminderChanged()
        }
        override fun stage(kind: RestoreKind) { staged += kind }
        override fun discard() { discards++ }
        override fun pending() = staged.isNotEmpty()
        override fun blocksBackup() = pending()
        override fun restart() { restarts++ }
    }
    private val target = FakeTarget()

    private val backup = BackupContents("datenbank".toByteArray(), "einstellungen")
    private val password = "richtig-langes-passwort"

    private fun local() = LocalRestore(
        target, files, snapshots, currentSettings = { "jetzt" }, scope = scope, events = events,
        clock = { now }, zone = { ZoneOffset.UTC },
        source = { BackupZip.pack(backup) }, iterations = 1_000,
    )

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    private fun LocalRestore.awaitAsk(): LocalPhase.Ask = blocking { status.first { it.phase is LocalPhase.Ask } }.phase as LocalPhase.Ask

    private fun events() = events.lines().drop(1).map(::JSONObject).map { it.getString("ereignis") }

    // --- Daily copies --------------------------------------------------------------------------

    // R-1: the newest 7 days, and of the older ones the newest of each of the last 4 weeks.
    @Test
    fun theNewest7DaysAndOnePerWeekFor4WeeksAreKept() {
        val today = LocalDate.parse("2026-11-09") // a Monday
        val days = (0L until 60L).map { today.minusDays(it) }
        val kept = Snapshots.keep(days)
        val expected = (0L until 7L).map { today.minusDays(it) } +
            listOf("2026-11-02", "2026-11-01", "2026-10-25", "2026-10-18").map(LocalDate::parse)
        assertEquals(expected.toSet(), kept)

        val cases = listOf(
            "keine" to emptyList(),
            "drei" to days.take(3),
            "mit Lücken" to listOf("2026-11-09", "2026-11-01", "2026-09-01").map(LocalDate::parse),
        )
        for ((case, dates) in cases) assertEquals(case, dates.toSet(), Snapshots.keep(dates))
    }

    // KRITISCH: one copy a day, and thinning out never deletes another file in the folder.
    @Test
    fun oneCopyADayAndOnlyDailyCopiesAreDeleted() {
        files.backupDir.mkdirs()
        val others = listOf("vor-wiederherstellung-20261101-120000.db", "vor-migration-v7.db", "taeglich-kaputt.db")
            .map { File(files.backupDir, it).apply { writeText("bleibt") } }
        val start = LocalDate.parse("2026-09-01")
        for (day in 0L until 70L) {
            assertTrue(snapshots.takeIfDue(start.plusDays(day)))
            assertFalse(snapshots.takeIfDue(start.plusDays(day)))
        }
        assertEquals(70, copies)
        assertEquals(11, snapshots.dates().size)
        assertEquals(start.plusDays(69), snapshots.dates().first())
        assertTrue(others.all { it.exists() })
        assertTrue(files.backupDir.list()!!.none { it.endsWith(".tmp") })
    }

    // Found on the emulator: the copy at the start came after the status was loaded, so the
    // settings said "no copy" until the next start.
    @Test
    fun aNewDailyCopyShowsAtOnceAndOnlyOnceADay() {
        val local = local()
        blocking { local.load().join() }
        assertTrue(local.status.value.snapshots.isEmpty())

        assertTrue(local.takeSnapshot())
        assertEquals(listOf(LocalDate.parse("2026-11-09")), local.status.value.snapshots)
        assertFalse(local.takeSnapshot())
        assertEquals(1, copies)

        now = Instant.parse("2026-11-09T23:30:00Z")
        val berlin = LocalRestore(
            target, files, snapshots, currentSettings = { "jetzt" }, scope = scope, events = events,
            clock = { now }, zone = { java.time.ZoneId.of("Europe/Berlin") },
        )
        assertTrue("the day follows the phone's time zone", berlin.takeSnapshot())
        assertEquals(LocalDate.parse("2026-11-10"), berlin.status.value.snapshots.first())
    }

    // --- Restoring on the phone ----------------------------------------------------------------

    // KRITISCH: a daily copy is swapped in only after yes; it takes the database only, the
    // settings stay.
    @Test
    fun aDailyCopyIsRestoredOnlyAfterYes() {
        val day = LocalDate.parse("2026-11-08")
        snapshots.takeIfDue(day)
        for (yes in listOf(false, true)) {
            val local = local()
            local.fromSnapshot(day)
            val ask = local.awaitAsk()
            assertEquals(RestoreKind.SNAPSHOT, ask.kind)
            assertEquals("Kopie vom 08.11.2026", ask.label)
            local.answer(yes)
            blocking { local.status.first { it.phase == LocalPhase.Idle } }
        }
        assertEquals("kopie 1", String(target.compared.last().database))
        assertEquals("jetzt", target.compared.last().settings)
        assertEquals(listOf(RestoreKind.SNAPSHOT), target.staged)
        assertEquals(1, target.discards)
        assertEquals(1, target.restarts)
        assertEquals(listOf("bereit"), events())
    }

    // KRITISCH: undo takes the newest copy before a restore, with its settings (W-6), for 7 days.
    @Test
    fun undoTakesTheNewestCopyBeforeWithItsSettingsFor7Days() {
        files.backupDir.mkdirs()
        File(files.backupDir, "vor-wiederherstellung-20261101-120000.db").writeText("älter")
        File(files.backupDir, "vor-wiederherstellung-20261105-120000.db").writeText("neuer")
        File(files.backupDir, "vor-wiederherstellung-20261105-120000.json").writeText("einstellungen vorher")

        val local = local()
        blocking { local.load().join() }
        assertEquals(Instant.parse("2026-11-12T12:00:00Z"), local.status.value.undoUntil)

        local.undo()
        val ask = local.awaitAsk()
        assertEquals(RestoreKind.UNDO, ask.kind)
        local.answer(true)
        blocking { local.status.first { it.phase == LocalPhase.Idle } }
        assertEquals("neuer", String(target.compared.single().database))
        assertEquals("einstellungen vorher", target.compared.single().settings)
        assertEquals(listOf(RestoreKind.UNDO), target.staged)

        now = Instant.parse("2026-11-12T12:00:01Z")
        blocking { local.load().join() }
        assertNull(local.status.value.undoUntil)
    }

    @Test
    fun aDamagedCopyShowsAnErrorAndLeavesNothing() {
        snapshots.takeIfDue(LocalDate.parse("2026-11-08"))
        target.error = DamagedDatabase("kaputt")
        val local = local()
        blocking { local.fromSnapshot(LocalDate.parse("2026-11-08")).join() }
        assertEquals(LocalRestore.describe(DamagedDatabase("kaputt")), local.status.value.error)
        assertEquals(LocalPhase.Idle, local.status.value.phase)
        assertEquals("a failed compare cleans up itself", 0, target.discards)
        assertTrue(target.staged.isEmpty())
    }

    // A failed swap shows until the person confirms it; confirming lets the automatic backup run again.
    @Test
    fun aFailedSwapShowsUntilConfirmed() {
        DatabaseSwap.writeAtomically(files.failed, JSONObject().put("grund", SwapFailure.FILE_CHANGED.name).toString())
        val local = local()
        local.started(SwapOutcome.Failed(SwapFailure.FILE_CHANGED))
        blocking { local.load().join() }
        assertEquals(SwapFailure.FILE_CHANGED, local.status.value.failed)
        assertEquals(listOf("fehlgeschlagen"), events())

        local.dismiss()
        assertFalse(files.failed.exists())
        assertNull(local.status.value.failed)
    }

    @Test
    fun aSwapAtTheStartIsShownAndLogged() {
        val local = local()
        local.started(SwapOutcome.Swapped(RestoreOrder(RestoreKind.CLOUD, "ab", "vor", swapped = true)))
        assertEquals(RestoreKind.CLOUD, local.status.value.restored)
        assertEquals(listOf("getauscht"), events())
        local.started(SwapOutcome.None)
        assertEquals(listOf("getauscht"), events())
    }

    // --- Export file (Anf. 7.28 PS14) -----------------------------------------------------------

    // KRITISCH: the file holds the backup ZIP, encrypted; the password is wiped afterwards.
    @Test
    fun anExportFileHoldsTheBackupEncryptedAndCanBeRestored() {
        val local = local()
        var written: ByteArray? = null
        val exportPassword = password.toCharArray()
        blocking { local.export("AuViLo-Sicherung-2026-11-09.auvilo", exportPassword) { written = it }.join() }
        val file = checkNotNull(written)
        assertEquals("AuViLo-Sicherung-2026-11-09.auvilo", local.status.value.exported)
        assertNull(local.status.value.error)
        assertTrue("password wiped", exportPassword.all { it == ' ' })
        assertFalse("not readable without the password", String(file, Charsets.ISO_8859_1).contains("datenbank"))

        val importPassword = password.toCharArray()
        local.fromFile("AuViLo-Sicherung-2026-11-09.auvilo", importPassword) { file }
        val ask = local.awaitAsk()
        assertEquals(RestoreKind.FILE, ask.kind)
        assertEquals("Datei „AuViLo-Sicherung-2026-11-09.auvilo“", ask.label)
        local.answer(true)
        blocking { local.status.first { it.phase == LocalPhase.Idle } }
        assertTrue("password wiped", importPassword.all { it == ' ' })
        assertEquals("datenbank", String(target.compared.single().database))
        assertEquals("the settings come with the file", "einstellungen", target.compared.single().settings)
        assertEquals(listOf(RestoreKind.FILE), target.staged)
        assertEquals(1, target.restarts)

        local.dismiss()
        assertNull(local.status.value.exported)
    }

    @Test
    fun aWrongPasswordOrAnotherFileChangesNothing() {
        val local = local()
        var written: ByteArray? = null
        blocking { local.export("datei", password.toCharArray()) { written = it }.join() }
        val cases = listOf(
            Triple(checkNotNull(written), "falsches-passwort", LocalRestore.describe(WrongPassword())),
            Triple(BackupZip.pack(backup), password, LocalRestore.describe(NotAnExport("x"))),
        )
        for ((bytes, pw, expected) in cases) {
            blocking { local.fromFile("datei", pw.toCharArray()) { bytes }.join() }
            assertEquals(expected, local.status.value.error)
            assertEquals(LocalPhase.Idle, local.status.value.phase)
            local.dismiss()
        }
        assertTrue(target.compared.isEmpty())
        assertTrue(target.staged.isEmpty())
    }

    @Test
    fun aFailedWriteIsShownAndNothingCountsAsExported() {
        val local = local()
        blocking { local.export("datei", password.toCharArray()) { throw java.io.IOException("voll") }.join() }
        assertNull(local.status.value.exported)
        assertEquals(LocalRestore.describe(java.io.IOException("voll")), local.status.value.error)
    }
}
