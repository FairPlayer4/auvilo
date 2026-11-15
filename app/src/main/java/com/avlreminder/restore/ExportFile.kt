package com.avlreminder.restore

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The file is not one of this app's export files, or from a newer format. */
class NotAnExport(why: String) : Exception("Keine Sicherungsdatei: $why")

/** The password does not open the file, or the file was changed. GCM cannot tell the two apart. */
class WrongPassword : Exception("Passwort falsch oder Datei verändert")

/**
 * The export file (Anf. 7.28 PS14, playstore.md N4): the backup ZIP ([com.avlreminder.cloud.BackupZip],
 * database and settings, no photos or audio), encrypted with a password of the person's own, so it
 * may leave the phone (Anf. 8.4).
 *
 * Format, version 1 (pinned by a test; the header is the additional data of the encryption):
 * ```
 *  16 bytes  "AUVILO-SICHERUNG" (ASCII)
 *   1 byte   version = 1
 *   4 bytes  PBKDF2 iterations (big endian)
 *  16 bytes  salt
 *  12 bytes  GCM nonce
 *   …        AES-256-GCM ciphertext with 16-byte tag
 * ```
 * Key: PBKDF2-HMAC-SHA256 of the password (UTF-16 chars as Java's PBEKeySpec takes them), 256 bit.
 *
 * KRITISCH: a fresh salt and nonce for every file; the password never goes into a file or the log.
 */
object ExportFile {
    private val MAGIC = "AUVILO-SICHERUNG".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private val HEADER = MAGIC.size + 1 + 4 + SALT + NONCE

    /** OWASP's figure for PBKDF2-HMAC-SHA256 (2023); about a second on the phone, once per file. */
    const val ITERATIONS = 600_000

    /** At least this many characters; the password is the only protection outside the phone. */
    const val MIN_PASSWORD = 12

    /** MIME type for Android's file dialogs; the file is opaque bytes. */
    const val MIME = "application/octet-stream"

    fun fileName(date: LocalDate) = "AuViLo-Sicherung-$date.auvilo"

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        require(password.size >= MIN_PASSWORD) { "password too short" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < HEADER + TAG_BITS / 8 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotAnExport("Kennung fehlt")
        val header = ByteBuffer.wrap(file, 0, HEADER)
        header.position(MAGIC.size)
        val version = header.get()
        if (version != VERSION) throw NotAnExport("Version $version")
        val iterations = header.int
        // A changed header must not make the phone work for hours; the tag would fail anyway.
        if (iterations !in 1..10 * ITERATIONS) throw WrongPassword()
        val salt = ByteArray(SALT).also { header.get(it) }
        val nonce = ByteArray(NONCE).also { header.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(file, 0, HEADER)
        return try {
            cipher.doFinal(file, HEADER, file.size - HEADER)
        } catch (_: AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
