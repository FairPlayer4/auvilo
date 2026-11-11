package com.avlreminder.cloud

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.avlreminder.verbindung.Geraeteschluessel
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The device key (backup-entwurf.md A-5): ECDSA P-256 in the Android Keystore, created on first
 * use, never exportable. The server knows this device by its public key; if the key is lost
 * (app data cleared), the app signs in as a new device.
 */
class KeystoreDeviceKey(private val alias: String = ALIAS) : Geraeteschluessel {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    private val entry: KeyStore.PrivateKeyEntry by lazy {
        if (!keyStore.containsAlias(alias)) create()
        keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    /** PKIX/DER, as the server expects it. */
    override val oeffentlich: ByteArray get() = entry.certificate.publicKey.encoded

    override fun signiere(nachricht: ByteArray): ByteArray = sign(entry.privateKey, nachricht)

    private fun create() {
        // StrongBox (separate secure chip) where there is one; the S24 has it.
        try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply { initialize(spec) }.generateKeyPair()
    }

    companion object {
        const val ALIAS = "backend-device"
        private const val PROVIDER = "AndroidKeyStore"

        /** ASN.1/DER signature, the format the server checks (`Geraetenachrichten`). */
        fun sign(key: PrivateKey, message: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(key)
                update(message)
                sign()
            }
    }
}

/**
 * Encrypts what the app keeps on disk for the backend (A-6). [aad] binds the ciphertext to its
 * purpose and account: it does not decrypt under another one.
 */
interface Sealer {
    fun seal(plain: ByteArray, aad: ByteArray): ByteArray
    fun open(sealed: ByteArray, aad: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a key in the Android Keystore (Tink `AndroidKeystore`). The key never leaves
 * the Keystore, so a copy of the file is useless elsewhere; after reinstalling, the keys are gone
 * and the recovery key brings them back (intended).
 */
class KeystoreSealer(private val alias: String = ALIAS) : Sealer {
    private val aead: Aead by lazy {
        if (!AndroidKeystore.hasKey(alias)) AndroidKeystore.generateNewAes256GcmKey(alias)
        AndroidKeystore.getAead(alias)
    }

    override fun seal(plain: ByteArray, aad: ByteArray): ByteArray = aead.encrypt(plain, aad)
    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray = aead.decrypt(sealed, aad)

    companion object {
        const val ALIAS = "backend-keys"
    }
}

/**
 * Deletes the device key and the key of the key file from the Keystore (reset). The next
 * sign-in creates a new device key, which the server needs: a locked device key stays locked.
 */
fun deleteBackendKeystoreEntries() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    for (alias in listOf(KeystoreDeviceKey.ALIAS, KeystoreSealer.ALIAS)) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}
