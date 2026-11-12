package com.avlreminder.cloud

import com.avlreminder.cloud.CloudUpload.Companion.brakes
import com.avlreminder.cloud.CloudUpload.Companion.sha256
import com.avlreminder.protokoll.Gesichert
import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.protokoll.SicherungsStand
import com.avlreminder.restore.RestoreTarget
import java.time.Duration
import java.time.Instant

/**
 * The hourly backup (cloud-entscheidungen.md A-9) with its brake (R-2) and the notice after 3 days
 * (A-13). [CloudBackup.backUpAutomatically] runs it as one of its steps.
 */
internal class AutomaticBackup(
    private val store: CloudStore,
    private val keys: (account: String) -> LokaleAblage,
    private val backupState: (account: String) -> SicherungsStand,
    private val source: BackupSource,
    private val notices: CloudNotices,
    private val restore: RestoreTarget,
    private val reminders: suspend () -> Int,
    private val upload: CloudUpload,
    private val events: CloudLog,
    private val log: (String) -> Unit,
    private val clock: () -> Instant,
) {
    /**
     * Like the backup by hand, but without anyone to ask.
     *
     * KRITISCH:
     *  - Switched off, not set up, not unlocked or never backed up by hand: nothing is read and
     *    the network is not touched.
     *  - Unchanged data (same SHA-256 as the last upload) are not uploaded (A-9); that spares the
     *    daily limit and the battery.
     *  - Did another device back up last, the backup of that device stays. The automatic backup
     *    then rests and a notification says so, until the person backs up by hand (A-10); it does
     *    not ask the server again every hour.
     *  - Are many reminders missing since the last backup (R-2), nothing is uploaded: the backup
     *    stops with a notification until the person backs up by hand. Otherwise a fault in the app
     *    would carry the loss to the server every hour.
     *  - While a restore waits for the restart or after a failed swap, nothing is uploaded.
     */
    suspend fun run(busy: (String) -> Unit) {
        if (!store.enabled() || !store.unlocked()) return
        val known = store.lesen() ?: return
        val account = known.kontoId
        if (store.lastBackup(account) == null) return
        val local = keys(account).laden() ?: return
        if (local.offenesBackup != null) return
        // Resting: not even the data are read.
        if (backupState(account).laden()?.abgelehnt == true) return
        // A restore waits for the restart or failed (the data may not be what the person wants),
        // or the brake stopped it: the person decides by hand.
        if (restore.blocksBackup() || store.braked()) return
        val started = clock()
        val payload = source.payload()
        if (sha256(payload) == store.lastHash(account)) {
            store.setCurrent(account, clock().epochSecond)
            return
        }
        val count = reminders()
        val before = store.lastReminders(account)
        if (before != null && brakes(before, count)) {
            store.setBraked()
            notices.brake(before, count)
            events.record(CloudEvent.BRAKED, mapOf("vorher" to before, "jetzt" to count))
            log("cloud: automatic backup braked ($before -> $count reminders)")
            return
        }
        busy("Sichern …")
        when (val result = upload.upload(account, local.bund.tresor, payload, automatic = true, ask = { false })) {
            is Gesichert.Ja -> {
                notices.clear()
                upload.done(account, payload, result, started, automatic = true, count)
            }
            is Gesichert.AnderesGeraet -> {
                events.record(CloudEvent.OTHER_DEVICE, mapOf("zaehler" to result.kopf.zaehler, "automatisch" to true))
                log("cloud: automatic backup rests, another device backed up last")
                notices.otherDevice(result.kopf.erstellt)
            }
            Gesichert.Ruht -> Unit
        }
    }

    /**
     * Shows the notice once when the backup has not held this device's data for 3 days (A-13).
     *
     * KRITISCH: It counts for the remembered account, not the session: after the server rejected
     * the session nothing is backed up, and that is exactly when the notice is needed.
     */
    suspend fun checkStale() {
        if (!store.enabled()) return
        val account = store.account() ?: return
        val current = Instant.ofEpochSecond(store.current(account) ?: return)
        if (store.staleNoticeShown()) return
        val age = Duration.between(current, clock())
        if (age <= STALE_AFTER) return
        notices.stale(store.lastBackup(account)?.let(Instant::ofEpochSecond) ?: current)
        store.setStaleNoticeShown()
        events.record(CloudEvent.STALE, mapOf("tage" to age.toDays()))
        log("cloud: stale notice shown")
    }

    companion object {
        /** After this long without a current backup the notice comes (A-13). */
        val STALE_AFTER: Duration = Duration.ofDays(3)
    }
}
