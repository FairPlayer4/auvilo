package com.avlreminder.restore

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.time.LocalDate

/** The export file (Anf. 7.28 PS14): encrypted with the person's password, format version 1. */
class ExportFileTest {
    private val plain = "PK… die Sicherung".toByteArray()
    private val password = "richtig-langes-passwort"
    private fun encrypt(pw: String = password) = ExportFile.encrypt(plain, pw.toCharArray(), iterations = 1_000)

    @Test
    fun theRightPasswordOpensTheFile() {
        assertArrayEquals(plain, ExportFile.decrypt(encrypt(), password.toCharArray()))
    }

    // KRITISCH: neither a wrong password nor a changed byte gives data; both say the same.
    @Test
    fun aWrongPasswordOrAChangedByteGivesNothing() {
        val file = encrypt()
        assertThrows(WrongPassword::class.java) { ExportFile.decrypt(file, "falsches-passwort".toCharArray()) }
        for (at in listOf(17, 25, 40, file.size - 1)) { // iterations, salt, nonce, tag
            val changed = file.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }
            assertThrows("byte $at", WrongPassword::class.java) { ExportFile.decrypt(changed, password.toCharArray()) }
        }
    }

    @Test
    fun anotherFileIsNotTakenForAnExport() {
        assertThrows(NotAnExport::class.java) { ExportFile.decrypt("PK\u0003\u0004 ein ZIP".toByteArray(), password.toCharArray()) }
        assertThrows(NotAnExport::class.java) { ExportFile.decrypt(ByteArray(0), password.toCharArray()) }
        val newer = encrypt().also { it[16] = 2 }
        assertThrows(NewerExport::class.java) { ExportFile.decrypt(newer, password.toCharArray()) }
    }

    // KRITISCH: a fresh salt and nonce for every file, so two exports never share a key stream.
    @Test
    fun everyFileHasItsOwnSaltAndNonce() {
        val a = encrypt()
        val b = encrypt()
        assertFalse("salt", a.copyOfRange(21, 37).contentEquals(b.copyOfRange(21, 37)))
        assertFalse("nonce", a.copyOfRange(37, 49).contentEquals(b.copyOfRange(37, 49)))
    }

    // The format is read by later versions of the app; it must not change unnoticed.
    @Test
    fun theFormatIsPinned() {
        val file = ExportFile.encrypt(plain, password.toCharArray())
        assertEquals("AUVILO-SICHERUNG", String(file, 0, 16, Charsets.US_ASCII))
        assertEquals(1, file[16].toInt())
        assertEquals(ExportFile.ITERATIONS, ByteBuffer.wrap(file, 17, 4).int)
        assertEquals(16 + 1 + 4 + 16 + 12 + plain.size + 16, file.size)
        assertEquals("AuViLo-Sicherung-2026-11-15.auvilo", ExportFile.fileName(LocalDate.parse("2026-11-15")))
    }

    @Test
    fun aShortPasswordIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { encrypt("zu-kurz") }
    }
}
