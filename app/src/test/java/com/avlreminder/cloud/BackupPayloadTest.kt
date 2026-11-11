package com.avlreminder.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.protokoll.GemerkteSicherung
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The payload of a backup (A-8) and what the device remembers about its backups. */
@RunWith(AndroidJUnit4::class)
class BackupPayloadTest {
    @get:Rule val tmp = TemporaryFolder()

    private val contents = BackupContents(ByteArray(5000) { (it % 7).toByte() }, """{"theme":"DARK","dwell":5}""")

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> entries.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        return bytes.toByteArray()
    }

    @Test
    fun aPackedBackupComesBackUnchanged() {
        val back = BackupZip.unpack(BackupZip.pack(contents))
        assertArrayEquals(contents.database, back.database)
        assertEquals(contents.settings, back.settings)
    }

    // KRITISCH: same data, same bytes (the automatic backup compares their hash, step 4). The
    // entries carry no time of packing; with today's time, a later backup would differ.
    @Test
    fun theSameDataGiveTheSameBytesLater() {
        val first = BackupZip.pack(contents)
        assertArrayEquals(first, BackupZip.pack(BackupContents(contents.database.copyOf(), contents.settings)))
        ZipInputStream(ByteArrayInputStream(first)).use { zip ->
            val year2000 = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
            generateSequence { zip.nextEntry }.forEach { assertTrue(it.name, it.time < year2000) }
        }
    }

    @Test
    fun otherDataGiveOtherBytes() {
        assertFalse(BackupZip.pack(contents).contentEquals(BackupZip.pack(BackupContents(contents.database, "{}"))))
    }

    @Test
    fun aDamagedBackupIsRefused() {
        val db = BackupZip.DATABASE to byteArrayOf(1)
        val settings = BackupZip.SETTINGS to "{}".toByteArray()
        for ((case, bytes) in listOf(
            "Datenbank fehlt" to zip(settings),
            "Einstellungen fehlen" to zip(db),
            "unbekannter Eintrag" to zip(db, settings, "../boese" to byteArrayOf(2)),
            "Eintrag doppelt" to duplicateEntry(),
            "kein ZIP" to byteArrayOf(1, 2, 3),
        )) {
            assertThrows(case, DamagedBackup::class.java) { BackupZip.unpack(bytes) }
        }
    }

    /** ZipOutputStream refuses a name twice, so the second entry is renamed in the bytes. */
    private fun duplicateEntry(): ByteArray {
        val other = "avlreminder.dX"
        val bytes = zip(BackupZip.DATABASE to byteArrayOf(1), BackupZip.SETTINGS to "{}".toByteArray(), other to byteArrayOf(3))
        val text = String(bytes, Charsets.ISO_8859_1).replace(other, BackupZip.DATABASE)
        return text.toByteArray(Charsets.ISO_8859_1)
    }

    @Test
    fun theRememberedBackupComesBackForItsAccountOnly() {
        val stand = GemerkteSicherung(zaehler = 3, version = 7, gesehen = 4, abgelehnt = true)
        BackupStateFile(tmp.root, "konto-1").speichern(stand)
        assertEquals(stand, BackupStateFile(tmp.root, "konto-1").laden())
        assertNull(BackupStateFile(tmp.root, "konto-2").laden())
        assertFalse(File(tmp.root, "${BackupStateFile.NAME}.tmp").exists())
    }

    @Test
    fun nothingOrADamagedFileMeansNothingRemembered() {
        assertNull(BackupStateFile(tmp.root, "konto-1").laden())
        File(tmp.root, BackupStateFile.NAME).writeText("{kaputt")
        assertNull(BackupStateFile(tmp.root, "konto-1").laden())
    }
}
