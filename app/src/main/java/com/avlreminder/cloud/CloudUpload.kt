package com.avlreminder.cloud

import com.avlreminder.data.AvlDatabase
import com.avlreminder.protokoll.Gesichert
import com.avlreminder.protokoll.Sicherungsablauf
import com.avlreminder.protokoll.SicherungsStand
import com.avlreminder.protokoll.Sicherungskopf
import com.avlreminder.protokoll.SymmetrischerSchluessel
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * One upload through `Sicherungsablauf` (backend) and what this device remembers afterwards;
 * shared by the backup by hand ([CloudBackup.backUp]) and the automatic one ([AutomaticBackup]).
 */
internal class CloudUpload(
    private val store: CloudStore,
    private val server: () -> CloudServer,
    private val backupState: (account: String) -> SicherungsStand,
    private val events: CloudLog,
    private val log: (String) -> Unit,
    private val clock: () -> Instant,
) {
    /** Uploads [payload] for [account]; [ask] answers "back up over another device?" (E-B2b-6). */
    suspend fun upload(
        account: String,
        vault: SymmetrischerSchluessel,
        payload: ByteArray,
        automatic: Boolean,
        ask: suspend (Sicherungskopf) -> Boolean,
    ): Gesichert {
        val server = server()
        return server.withSession { session ->
            if (session.kontoId != account) error("Die Sitzung gehört zu einem anderen Konto")
            Sicherungsablauf(
                session.kontoId,
                session.geraetId,
                vault,
                server.dataBackups(session.token),
                backupState(session.kontoId),
                uebernehmen = ask,
                uhr = clock,
            ).sichern(payload, AvlDatabase.VERSION, automatisch = automatic)
        }
    }

    /** Remembers the upload ([CloudStore.setLastBackup]) and logs it. */
    suspend fun done(account: String, payload: ByteArray, result: Gesichert.Ja, started: Instant, automatic: Boolean, count: Int) {
        store.setLastBackup(account, clock().epochSecond, sha256(payload), count)
        val ms = Duration.between(started, clock()).toMillis()
        events.record(
            CloudEvent.BACKED_UP,
            mapOf("kib" to kib(payload.size), "dauer_ms" to ms, "zaehler" to result.zaehler, "automatisch" to automatic),
        )
        log("cloud: backed up${if (automatic) " automatically" else ""} (#${result.zaehler}, ${kib(payload.size)} KiB, $ms ms)")
    }

    companion object {
        fun kib(bytes: Int) = (bytes + 1023) / 1024

        /** More reminders than this missing since the last backup stop the automatic backup (R-2). */
        const val BRAKE_MAX_LOST = 10

        /**
         * The brake (R-2, Kevin 2026-11-09): fewer reminders than at the last backup, by more than
         * half or by more than [BRAKE_MAX_LOST].
         */
        fun brakes(before: Int, now: Int): Boolean = now < before && (before - now > BRAKE_MAX_LOST || now * 2 < before)

        /** Same data, same hash: the ZIP has fixed entry times ([BackupZip]). */
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
