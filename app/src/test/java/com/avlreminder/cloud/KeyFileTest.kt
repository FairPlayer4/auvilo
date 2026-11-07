package com.avlreminder.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.protokoll.LokalerStand
import com.avlreminder.protokoll.OffenesBackup
import com.avlreminder.protokoll.Schluesselbund
import com.google.crypto.tink.subtle.AesGcmJce
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException
import java.util.Base64

/** The key file (A-6); the Keystore is replaced by AES-GCM in memory. */
@RunWith(AndroidJUnit4::class)
class KeyFileTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sealer = object : Sealer {
        private val aead = AesGcmJce(ByteArray(32) { it.toByte() })
        override fun seal(plain: ByteArray, aad: ByteArray): ByteArray = aead.encrypt(plain, aad)
        override fun open(sealed: ByteArray, aad: ByteArray): ByteArray = aead.decrypt(sealed, aad)
    }
    private val bund = Schluesselbund.erzeugen()
    private val open = OffenesBackup(byteArrayOf(1, 2, 3), byteArrayOf(4, 5))

    private fun file(account: String = "konto-1") = KeyFile(tmp.root, sealer, account)

    @Test
    fun keysAndAnOpenBackupComeBackAsStored() {
        file().speichern(LokalerStand(bund, open))
        val loaded = requireNotNull(file().laden())
        assertEquals(bund.person.oeffentlich.fingerabdruck(), loaded.bund.person.oeffentlich.fingerabdruck())
        assertArrayEquals(bund.geheimeBytes(), loaded.bund.geheimeBytes())
        assertArrayEquals(open.chiffrat, requireNotNull(loaded.offenesBackup).chiffrat)
        assertArrayEquals(open.freischaltschluessel, loaded.offenesBackup?.freischaltschluessel)

        // Uploaded: the open backup is gone.
        file().speichern(LokalerStand(bund, null))
        assertNull(file().laden()?.offenesBackup)
    }

    @Test
    fun withoutAFileThereAreNoKeys() {
        assertNull(file().laden())
    }

    @Test
    fun keysOfAnotherAccountAreRejected() {
        file("konto-1").speichern(LokalerStand(bund, null))
        assertThrows(KeysOfOtherAccount::class.java) { file("konto-2").laden() }
    }

    // KRITISCH: the account is bound to the ciphertext, not only written next to it.
    @Test
    fun changingTheAccountInTheFileBreaksDecryption() {
        file("konto-1").speichern(LokalerStand(bund, null))
        val f = File(tmp.root, KeyFile.NAME)
        f.writeText(JSONObject(f.readText()).put("account", "konto-2").toString())
        assertThrows(GeneralSecurityException::class.java) { file("konto-2").laden() }
    }

    @Test
    fun noSecretIsInPlainText() {
        file().speichern(LokalerStand(bund, open))
        val text = File(tmp.root, KeyFile.NAME).readText()
        assertFalse(text.contains(Base64.getEncoder().encodeToString(bund.geheimeBytes())))
        assertFalse(text.contains("keys") || text.contains("openBackup"))
    }
}
