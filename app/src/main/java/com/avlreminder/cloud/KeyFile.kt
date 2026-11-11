package com.avlreminder.cloud

import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.protokoll.LokalerStand
import com.avlreminder.protokoll.OffenesBackup
import com.avlreminder.protokoll.Schluesselbund
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/** The key file belongs to another account than the one signed in. */
class KeysOfOtherAccount : Exception("Auf diesem Gerät liegen Schlüssel eines anderen Kontos")

/**
 * The person and vault keys of one account (A-6), plus a backup that is not yet safely
 * uploaded. One file `filesDir/backend/keys.json`:
 *
 *   {"version": 1, "account": "<kontoId>", "sealed": "<Base64>"}
 *
 * Only the account is in plain text, so a key file of another account is recognised.
 *
 * KRITISCH: Everything secret is inside [Sealer] (Keystore), and the account is part of the
 * associated data: a file whose account was changed by hand does not decrypt.
 */
class KeyFile(private val dir: File, private val sealer: Sealer, private val account: String) : LokaleAblage {
    private val file get() = File(dir, NAME)

    override fun laden(): LokalerStand? {
        if (!file.exists()) return null
        val outer = JSONObject(file.readText())
        if (outer.getString("account") != account) throw KeysOfOtherAccount()
        val inner = JSONObject(String(sealer.open(b64d(outer.getString("sealed")), aad(account))))
        val open = if (inner.has("openBackup")) {
            OffenesBackup(b64d(inner.getString("openBackup")), b64d(inner.getString("unlockKey")))
        } else {
            null
        }
        return LokalerStand(Schluesselbund.ausGeheimenBytes(b64d(inner.getString("keys"))), open)
    }

    override fun speichern(stand: LokalerStand) {
        val inner = JSONObject().put("keys", b64e(stand.bund.geheimeBytes()))
        stand.offenesBackup?.let {
            inner.put("openBackup", b64e(it.chiffrat))
            inner.put("unlockKey", b64e(it.freischaltschluessel))
        }
        val outer = JSONObject()
            .put("version", VERSION)
            .put("account", account)
            .put("sealed", b64e(sealer.seal(inner.toString().toByteArray(), aad(account))))
        dir.mkdirs()
        // Write next to it, then rename in one step: an abort leaves the old file whole.
        val tmp = File(dir, "$NAME.tmp")
        tmp.writeText(outer.toString())
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Removes the keys, e.g. a first backup the server rejected (Einrichtung). */
    override fun loeschen() {
        File(dir, "$NAME.tmp").delete()
        file.delete()
    }

    companion object {
        const val NAME = "keys.json"
        private const val VERSION = 1

        fun aad(account: String): ByteArray = "avlreminder-app-keys-v1:$account".toByteArray()
        private fun b64e(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        private fun b64d(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
