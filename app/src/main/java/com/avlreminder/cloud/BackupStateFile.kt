package com.avlreminder.cloud

import com.avlreminder.protokoll.GemerkteSicherung
import com.avlreminder.protokoll.SicherungsStand
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * What this device remembers about its backups (`GemerkteSicherung`, E-B2b-3), in
 * `filesDir/backend/sicherung.json` next to the keys, so that resetting deletes it as well:
 *
 *   {"account": "<kontoId>", "counter": 3, "version": 7, "seen": 0, "declined": false}
 *
 * A file of another account or a damaged one counts as "nothing remembered". That is safe: the
 * backend then compares with the server's backup before it uploads (`Sicherungsablauf.sichern`).
 */
class BackupStateFile(private val dir: File, private val account: String) : SicherungsStand {
    private val file get() = File(dir, NAME)

    override fun laden(): GemerkteSicherung? {
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            if (o.getString("account") != account) return null
            GemerkteSicherung(o.getLong("counter"), o.getLong("version"), o.getLong("seen"), o.getBoolean("declined"))
        }.getOrNull()
    }

    override fun speichern(stand: GemerkteSicherung) {
        val o = JSONObject()
            .put("account", account)
            .put("counter", stand.zaehler)
            .put("version", stand.version)
            .put("seen", stand.gesehen)
            .put("declined", stand.abgelehnt)
        dir.mkdirs()
        val tmp = File(dir, "$NAME.tmp")
        tmp.writeText(o.toString())
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val NAME = "sicherung.json"
    }
}
