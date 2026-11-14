package com.avlreminder.cloud

import android.app.Activity
import com.avlreminder.data.AvlDatabase
import com.avlreminder.protokoll.Datensicherung
import com.avlreminder.protokoll.Diagnose
import com.avlreminder.protokoll.Eingerichtet
import com.avlreminder.protokoll.Einrichtung
import com.avlreminder.protokoll.KontoLoeschen
import com.avlreminder.protokoll.KontoLoeschung
import com.avlreminder.protokoll.Gesichert
import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.protokoll.NeueresSchema
import com.avlreminder.protokoll.Sicherung
import com.avlreminder.protokoll.Sicherungsablauf
import com.avlreminder.protokoll.SicherungsStand
import com.avlreminder.protokoll.Sicherungskopf
import com.avlreminder.protokoll.SymmetrischerSchluessel
import com.avlreminder.protokoll.Wiederhergestellt
import com.avlreminder.restore.RestoreKind
import com.avlreminder.restore.RestoreTarget
import com.avlreminder.verbindung.AnmeldungAbgelehnt
import com.avlreminder.verbindung.KontoNichtFreigegeben
import com.avlreminder.verbindung.Sitzung
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Switching on, signing in and setting up the cloud backup (cloud-entscheidungen.md, "Einrichtung
 * Einrichtung"). The flows themselves come from the backend (`Anmeldung`, `Einrichtung`); this
 * class only connects them to the settings screen and keeps one step running at a time.
 *
 * KRITISCH: While switched off, nothing here touches the network or creates a key: [server] is
 * only called after the switch was checked.
 *
 * @param server creates the device key on first use, hence a function.
 * @param keys the key file of an account.
 * @param wipe deletes the key file and the Keystore keys of the backend (reset).
 * @param backupState what this device remembers about the backups of an account.
 * @param source the app's data for a backup.
 * @param events the backend logger, the only thing [sendDiagnosis] sends.
 * @param schedule the hourly backup; runs only while [CloudPhase.Ready.automatic].
 * @param notices the notifications of the automatic backup.
 * @param restore checks a backup in its own file and swaps it in at the next start.
 * @param reminders how many reminders there are now (brake, R-2).
 */
class CloudBackup(
    private val store: CloudStore,
    private val server: () -> CloudServer,
    private val keys: (account: String) -> LokaleAblage,
    private val google: GoogleSignIn,
    private val passwords: PasswordSafe,
    private val wipe: () -> Unit,
    private val scope: CoroutineScope,
    private val backupState: (account: String) -> SicherungsStand,
    private val source: BackupSource,
    private val events: CloudLog,
    private val schedule: CloudSchedule,
    private val notices: CloudNotices,
    private val restore: RestoreTarget,
    private val reminders: suspend () -> Int,
    private val log: (String) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
    /** Built with a server ([CloudConfig.configured]); without one nothing runs in the cloud. */
    private val configured: Boolean = true,
) {
    private val state = MutableStateFlow(CloudStatus(CloudPhase.Off))
    val status: StateFlow<CloudStatus> = state.asStateFlow()

    /** The server of the running step, for [heardFromServer]; null = none, or its news is outdated. */
    @Volatile private var stepServer: CloudServer? = null

    /** [server], remembered for [heardFromServer]. Every step gets its server here. */
    private val connect: () -> CloudServer = { server().also { stepServer = it } }

    private val running = Mutex()
    private val confirm = Question<Boolean>()
    private val typed = Question<String?>()
    private val overwrite = Question<Boolean>()
    private val restoreAnswer = Question<Boolean>()
    private val brakeAnswer = Question<Boolean>()
    private val questions = listOf(confirm, typed, overwrite, restoreAnswer, brakeAnswer)

    private val uploads = CloudUpload(store, connect, backupState, events, log, clock)
    private val automatic = AutomaticBackup(store, keys, backupState, source, notices, restore, reminders, uploads, events, log, clock)

    /** The key on screen ([CloudPhase.ShowKey]) and its account, for [saveKey]. */
    @Volatile private var shown: Pair<String, String>? = null
    private val safeBusy = AtomicBoolean(false)

    /** Reads where things stand (at start and after each step); no network. */
    fun load(): Job = work(CloudStep.LOAD, clearNotes = false) {}

    fun setEnabled(on: Boolean): Job = work(CloudStep.SWITCH) {
        store.setEnabled(on)
        if (!on) schedule.stop()
        events.record(if (on) CloudEvent.SWITCHED_ON else CloudEvent.SWITCHED_OFF)
        log("cloud: ${if (on) "on" else "off"}")
    }

    /** Google account picker, then sign-in and setup. */
    fun signIn(activity: Activity): Job = work(CloudStep.SIGN_IN) {
        if (!store.enabled()) return@work
        busy("Anmelden …")
        val server = connect()
        val signedIn = server.signIn { nonce -> google.idToken(activity, nonce) }
        store.setUnlocked(signedIn.freigeschaltet)
        notices.signedIn()
        events.record(CloudEvent.SIGNED_IN, mapOf("neues_konto" to signedIn.neuesKonto, "freigeschaltet" to signedIn.freigeschaltet))
        log("cloud: signed in" + if (signedIn.neuesKonto) " (new account)" else "")
        setUp(server, signedIn.sitzung, activity)
    }

    /**
     * Continues a setup that did not finish (e.g. upload failed, app closed). Without
     * [activity] the password service is not asked.
     */
    fun continueSetup(activity: Activity? = null): Job = work(CloudStep.SETUP) {
        if (!store.enabled()) return@work
        busy("Verbinden …")
        val server = connect()
        server.withSession { setUp(server, it, activity) }
    }

    /** Offers the key on screen to the password service again (the first offer was declined). */
    fun saveKey(activity: Activity): Job = scope.launch {
        val (account, key) = shown ?: return@launch
        saveShown(activity, account, key)
    }

    /** Offers [key] to the password service; saved, the screen says so while it still shows the key. */
    private suspend fun saveShown(activity: Activity, account: String, key: String) {
        if (offer(activity, account, key)) state.update { if (it.phase == CloudPhase.ShowKey(key)) it.copy(phase = CloudPhase.ShowKey(key, saved = true)) else it }
    }

    /** Fetches the key from the password service while [CloudPhase.AskKey] shows. */
    fun fetchKey(activity: Activity): Job = scope.launch { typed.answerWith { fetch(activity) } }

    /** The answer to [CloudPhase.ShowKey]: true = written down. */
    fun confirmKey(writtenDown: Boolean) {
        confirm.answer(writtenDown)
    }

    /** The answer to [CloudPhase.AskKey]: null = cancel. */
    fun submitKey(text: String?) {
        typed.answer(text)
    }

    /** The answer to [CloudPhase.AskOverwrite]: true = back up from here, replacing the other. */
    fun answerOverwrite(yes: Boolean) {
        overwrite.answer(yes)
    }

    /** The answer to [CloudPhase.AskRestore]: true = swap in at the restart. */
    fun answerRestore(yes: Boolean) {
        restoreAnswer.answer(yes)
    }

    /** The answer to [CloudPhase.AskBrake]: true = back up anyway. */
    fun answerBrake(yes: Boolean) {
        brakeAnswer.answer(yes)
    }

    fun dismissNotes() = state.update { it.copy(notes = emptyList(), error = null, report = null) }

    /**
     * Backs up now, by hand (cloud-entscheidungen.md A-9): the app's data, encrypted with the
     * account's vault key, through `Sicherungsablauf` from the backend.
     *
     * KRITISCH: Switched off or not set up, nothing is read and the network is not touched. Did
     * another device back up last, it asks ([CloudPhase.AskOverwrite]) and replaces that backup
     * only on yes; on no nothing changes on the server. Are many reminders missing since the last
     * backup (R-2), it asks first ([CloudPhase.AskBrake]). While a restore waits for the restart,
     * nothing is backed up: the data are about to be replaced.
     */
    fun backUp(): Job = work(CloudStep.BACKUP) { backUpByHand() }

    /** "Jetzt sichern und erneut prüfen" (W-2). */
    fun backUpAndCheck(): Job = work(CloudStep.BACKUP) { if (backUpByHand()) checkNow() }

    /** @return true = uploaded. */
    private suspend fun backUpByHand(): Boolean {
        if (!store.enabled()) return false
        val known = store.lesen() ?: return false
        val local = keys(known.kontoId).laden() ?: return false
        if (local.offenesBackup != null) return false
        if (restore.pending()) {
            note("Ein Wiederherstellen wartet auf den Neustart der App. Nichts gesichert.")
            return false
        }
        busy("Sichern …")
        val started = clock()
        val count = reminders()
        val before = store.lastReminders(known.kontoId)
        if (before != null && CloudUpload.brakes(before, count) && !askBrake(before, count)) {
            note("Nicht gesichert.")
            return false
        }
        val payload = source.payload()
        return when (val result = uploads.upload(known.kontoId, local.bund.tresor, payload, automatic = false, ask = ::askOverwrite)) {
            is Gesichert.Ja -> {
                notices.clear()
                uploads.done(known.kontoId, payload, result, started, automatic = false, count)
                true
            }
            is Gesichert.AnderesGeraet -> {
                events.record(CloudEvent.OTHER_DEVICE, mapOf("zaehler" to result.kopf.zaehler, "automatisch" to false))
                log("cloud: not backed up, another device backed up last")
                note("Nicht gesichert: Die Sicherung des anderen Geräts bleibt auf dem Server.")
                false
            }
            // Only the automatic backup rests; by hand it asks again.
            Gesichert.Ruht -> false
        }
    }

    /**
     * "Sicherung prüfen" (W-1): fetches the backup, checks it in its own file and compares it with
     * the data here, table by table; the file is deleted afterwards.
     *
     * KRITISCH: Nothing here is replaced, and the device's backup state stays as it is (no "seen",
     * no "declined"): checking is not restoring.
     */
    fun check(): Job = work(CloudStep.CHECK) { checkNow() }

    private suspend fun checkNow() {
        if (!store.enabled()) return
        val known = store.lesen() ?: return
        val local = keys(known.kontoId).laden() ?: return
        if (local.offenesBackup != null) return
        busy("Sicherung holen …")
        val server = connect()
        val (session, fetched) = server.withSession { session ->
            if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
            session to server.dataBackups(session.token).holen()
        }
        if (fetched == null) {
            note("Auf dem Server liegt keine Sicherung.")
            return
        }
        val backup = Datensicherung.entschluesseln(fetched.chiffrat, local.bund.tresor, session.kontoId)
        busy("Prüfen …")
        val started = clock()
        val report = report(backup, session.geraetId)
        restore.discard()
        state.update { it.copy(report = report) }
        val c = report.comparison
        events.record(
            CloudEvent.CHECKED,
            mapOf(
                "gleich" to c.same, "unveraendert" to report.unchanged, "eigenes_geraet" to report.fromThisDevice,
                "tabellen" to c.tables.size, "tabellen_abweichend" to c.tables.count { !it.same },
                "einstellungen_gleich" to c.settingsSame, "schema" to c.schema,
                "dauer_ms" to Duration.between(started, clock()).toMillis(),
            ),
        )
        log("cloud: checked (${if (c.same) "same" else "${c.tables.count { !it.same }} table(s) differ"})")
    }

    /**
     * Checks [backup] in its own file and compares it with the data here (W-1, W-4). On success the
     * file stays for [restore]; the caller deletes it ([RestoreTarget.discard]) or swaps it in.
     */
    private suspend fun report(backup: Sicherung, device: String): BackupReport {
        if (backup.kopf.schemaVersion > AvlDatabase.VERSION) throw NeueresSchema(backup.kopf.schemaVersion, AvlDatabase.VERSION)
        val unchanged = CloudUpload.sha256(source.payload()) == CloudUpload.sha256(backup.nutzlast)
        val comparison = restore.compare(BackupZip.unpack(backup.nutzlast))
        return BackupReport(backup.kopf.erstellt, backup.kopf.geraetId == device, unchanged, comparison)
    }

    /**
     * "Wiederherstellen" (W-5): fetch, check in its own file, compare, show, confirm; the data are
     * swapped at the restart that follows.
     *
     * KRITISCH:
     *  - The running database is not touched here. The swap happens at the next start, before
     *    anything opens the database, and keeps a copy of the data before (undo, W-6).
     *  - Nothing is staged unless the person confirms after seeing the comparison; a damaged or
     *    newer backup ends before the question.
     *  - Data from another device (W-7): this device backs up automatically only after the person
     *    backed up by hand, and that asks before it replaces the other device's backup. A fresh
     *    test device therefore never overwrites the backup it was restored from.
     */
    fun restore(): Job = work(CloudStep.RESTORE) {
        if (!store.enabled()) return@work
        val known = store.lesen() ?: return@work
        val local = keys(known.kontoId).laden() ?: return@work
        if (local.offenesBackup != null) return@work
        if (restore.pending()) {
            note("Ein Wiederherstellen wartet schon auf den Neustart der App.")
            return@work
        }
        busy("Sicherung holen …")
        val server = connect()
        server.withSession { session -> restoreWith(server, session, known.kontoId, local.bund.tresor) }
    }

    /** [restore] with a valid [session]; safe to repeat, it asks before anything changes. */
    private suspend fun restoreWith(server: CloudServer, session: Sitzung, account: String, vault: SymmetrischerSchluessel) {
        if (session.kontoId != account) error("Die Sitzung gehört zu einem anderen Konto")
        var compared = false
        var staged = false
        try {
            val result = Sicherungsablauf(
                session.kontoId,
                session.geraetId,
                vault,
                server.dataBackups(session.token),
                backupState(session.kontoId),
                uebernehmen = { false },
                uhr = clock,
            ).wiederherstellen(
                AvlDatabase.VERSION,
                bestaetigen = { backup ->
                    busy("Prüfen …")
                    val report = report(backup, session.geraetId)
                    compared = true
                    askRestore(report)
                },
                anwenden = {
                    restore.stage(RestoreKind.CLOUD)
                    staged = true
                },
            )
            when (result) {
                is Wiederhergestellt.Ja -> {
                    val other = result.kopf.geraetId != session.geraetId
                    if (other) {
                        // W-7: the Sicherungsablauf remembered version 0, so the next upload asks
                        // before it replaces the other device's backup (E-B2b-6). Here: no
                        // automatic backup until the person backs up by hand.
                        store.clearLastBackup()
                        schedule.stop()
                        notices.clear()
                    }
                    events.record(
                        CloudEvent.RESTORE_STAGED,
                        mapOf("art" to RestoreKind.CLOUD, "schema" to result.kopf.schemaVersion, "anderes_geraet" to other),
                    )
                    log("cloud: restore staged (#${result.kopf.zaehler}${if (other) ", other device" else ""})")
                    busy("Neu starten …")
                    restore.restart()
                }
                Wiederhergestellt.KeineSicherung -> note("Auf dem Server liegt keine Sicherung.")
                Wiederhergestellt.Abgelehnt -> {
                    // Without this line a cancelled restore leaves no trace in the diagnose log.
                    log("cloud: restore declined")
                    note("Nichts wiederhergestellt.")
                }
            }
        } finally {
            if (compared && !staged) restore.discard()
        }
    }

    /**
     * The data were restored on the phone (daily copy, undo) and are older than the last backup
     * (W-7, 2026-11-09): this device forgets its last backup, so the automatic backup waits until
     * the person backs up by hand. Runs at the start, before anything backs up.
     */
    suspend fun restoredOnThePhone() {
        store.clearLastBackup()
        schedule.stop()
        notices.clear()
        log("cloud: restored on the phone, automatic backup waits for a backup by hand")
    }

    /**
     * "Sicherung löschen" (W-8, DSGVO Art. 17): deletes the data backup on the server with all its
     * older versions. The key backup stays, so backing up again needs no new setup. The automatic
     * backup stops until the person backs up by hand.
     */
    fun deleteBackup(): Job = work(CloudStep.DELETE_BACKUP) {
        if (!store.enabled()) return@work
        val known = store.lesen() ?: return@work
        busy("Sicherung löschen …")
        val server = connect()
        server.withSession { session ->
            if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
            Sicherungsablauf.loeschen(server.dataBackups(session.token), backupState(session.kontoId))
        }
        schedule.stop()
        notices.clear()
        store.clearLastBackup()
        events.record(CloudEvent.DELETED)
        log("cloud: backup deleted")
        note("Die Sicherung auf dem Server ist gelöscht, mit allen älteren Fassungen. Der Schlüssel bleibt.")
    }

    /**
     * "Dieses Gerät abmelden" (R-3): locks this device on the server and deletes its keys and
     * session here. Reminders and settings stay. Afterwards it is off.
     *
     * KRITISCH: Nothing on the server is deleted; the backup and the key backup stay for the other
     * devices and for restoring. Is the server not reachable, the phone is signed out anyway (a
     * note says so): without its keys this device can do nothing there.
     */
    fun signOut(): Job = work(CloudStep.SIGN_OUT) {
        if (!store.enabled()) return@work
        val session = store.lesen()
        var locked = session == null
        if (session != null) {
            busy("Abmelden …")
            try {
                val server = connect()
                server.withSession { server.lock(it.token, session.geraetId) }
                locked = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("cloud: sign-out, device not locked (${CloudErrors.logDetail(e)})")
                note("Beim Server nicht abgemeldet (${CloudErrors.describe(e) ?: "abgebrochen"}). Auf diesem Handy ist trotzdem alles abgemeldet.")
            }
        }
        busy("Auf dem Handy löschen …")
        schedule.stop()
        notices.clear()
        notices.deletionGone()
        wipe()
        store.clear()
        events.record(CloudEvent.SIGNED_OUT, mapOf("gesperrt" to locked))
        log("cloud: signed out")
    }

    /**
     * The hourly backup (cloud-entscheidungen.md A-9, A-10), called by the worker: like [backUp], but
     * without anyone to ask ([AutomaticBackup.run]). Afterwards it checks whether the backup is
     * older than 3 days ([AutomaticBackup.checkStale]).
     */
    suspend fun backUpAutomatically() {
        // KRITISCH: An app built without a server (installed over one with the cloud backup)
        // stops the hourly backup instead of trying an empty address every hour.
        if (!configured) {
            schedule.stop()
            log("cloud: no server in this build, hourly backup stopped")
            return
        }
        work(CloudStep.AUTO_BACKUP, clearNotes = false) {
            automatic.run(::busy)
            askAboutDeletion()
        }.join()
        try {
            automatic.checkStale()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("cloud: stale check failed (${CloudErrors.logDetail(e)})")
        }
    }

    /**
     * Sends the backend logger's events to the server (diagnose-entwurf.md), by hand.
     *
     * KRITISCH: Only [CloudLog.lines] goes out, never `diag.log` (which may hold titles).
     */
    fun sendDiagnosis(): Job = work(CloudStep.DIAGNOSIS) {
        if (!store.enabled()) return@work
        busy("Diagnose senden …")
        val server = connect()
        val size = server.withSession { Diagnose.senden(server.diagnosis(it.token), events.lines()) }
        val kib = CloudUpload.kib(size)
        events.record(CloudEvent.DIAGNOSIS_SENT, mapOf("kib" to kib))
        log("cloud: diagnosis sent ($kib KiB)")
        note("Diagnose gesendet ($kib KiB).")
    }

    /**
     * "Konto löschen" (backend B1, DSGVO Art. 17; replaces "Cloud-Daten löschen", R-3). An unlocked
     * device deletes the account on the server at once: both backups with all their versions, the
     * devices, sessions, diagnoses and the account itself. This phone then forgets keys and session
     * and is off; reminders and settings stay. A device that is not unlocked only requests it: the
     * server deletes on a date ([CloudStatus.deletion]), any unlocked device can cancel until then,
     * and this phone keeps its keys.
     *
     * KRITISCH: The phone forgets its keys only after the server confirmed the deletion
     * (`KontoLoeschen`). Not reachable, or only requested, the data on the server are still there,
     * and without the keys this phone could neither read them nor cancel.
     */
    fun deleteAccount(): Job = work(CloudStep.DELETE_ACCOUNT) {
        if (!store.enabled()) return@work
        val known = store.lesen() ?: return@work
        busy("Konto löschen …")
        val server = connect()
        val result = server.withSession { session ->
            if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
            KontoLoeschen.loeschen(server.deletion(session.token)) {
                busy("Auf dem Handy löschen …")
                schedule.stop()
                notices.clear()
                notices.deletionGone()
                wipe()
                store.clear()
            }
        }
        // What the server said at the renewal before is outdated now.
        stepServer = null
        when (result) {
            KontoLoeschung.Geloescht -> {
                events.record(CloudEvent.ACCOUNT_DELETED)
                log("cloud: account deleted")
                note("Das Konto ist auf dem Server gelöscht, mit allen Sicherungen. Reminder und Einstellungen auf diesem Handy bleiben.")
            }
            is KontoLoeschung.Beantragt -> {
                // Set before the step reads the server's news: the own request is no notification.
                store.setDeletion(result.ab, clock())
                events.record(CloudEvent.DELETION_REQUESTED)
                log("cloud: account deletion requested")
                note(
                    "Dieses Gerät ist nicht freigeschaltet, deshalb ist die Löschung nur beantragt. Bis zum Termin kann ein " +
                        "freigeschaltetes Gerät sie abbrechen. Die Schlüssel bleiben so lange auf diesem Handy.",
                )
            }
        }
    }

    /**
     * "Löschung abbrechen" (backend B1): a requested deletion of the account does not happen. Only an
     * unlocked device may; due already, the server refuses (`LoeschungLaeuft`).
     */
    fun cancelDeletion(): Job = work(CloudStep.CANCEL_DELETION) {
        if (!store.enabled()) return@work
        val known = store.lesen() ?: return@work
        busy("Löschung abbrechen …")
        val server = connect()
        server.withSession { session ->
            if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
            server.deletion(session.token).abbrechen()
        }
        stepServer = null
        store.setDeletion(null, clock())
        notices.deletionGone()
        events.record(CloudEvent.DELETION_CANCELLED, mapOf("hier" to true))
        log("cloud: account deletion cancelled")
        note("Die Löschung ist abgebrochen, das Konto bleibt.")
    }

    /**
     * "Nur dieses Handy zurücksetzen" (signed out, Review-Lauf 10, 2026-11-09): without a session
     * nothing can be deleted on the server. The keys and the session of the backup on this phone go;
     * reminders and settings stay. Afterwards it is off.
     */
    fun resetPhone(): Job = work(CloudStep.RESET) {
        if (!store.enabled()) return@work
        busy("Auf dem Handy löschen …")
        schedule.stop()
        notices.clear()
        notices.deletionGone()
        wipe()
        store.clear()
        events.record(CloudEvent.RESET)
        log("cloud: phone reset")
    }

    /**
     * KRITISCH: Learns at least once a day whether a deletion of the account is requested (backend
     * B1), also while the data stay unchanged and nothing is uploaded. The server says it at every
     * sign-in and renewal; so this renews the session when its last answer is older than
     * [DELETION_CHECK]. The 7 days of a request leave time to cancel one made with a taken-over
     * Google account. Only under the conditions of the automatic backup itself (A-9): switched on,
     * unlocked (only such a device can cancel) and backed up by hand once.
     */
    private suspend fun askAboutDeletion() {
        if (!store.enabled() || !store.unlocked()) return
        val known = store.lesen() ?: return
        if (store.lastBackup(known.kontoId) == null) return
        val checked = store.deletionChecked()
        if (checked != null && Duration.between(checked, clock()) < DELETION_CHECK) return
        // Not session(): a session still valid comes from the phone, without the server's word.
        connect().renew()
    }

    /**
     * Takes in what the server said about a requested deletion during the step (sign-in, renewal).
     * A new date shows as a notification, its end takes it away. Never throws: the step is done.
     */
    private suspend fun heardFromServer() {
        val news = stepServer?.deletionNews() ?: return
        stepServer = null
        try {
            // Signed out or reset meanwhile: nothing to keep.
            if (!store.enabled() || store.lesen() == null) return
            val before = store.deletion()
            store.setDeletion(news.at, clock())
            if (news.at == before) return
            if (news.at != null) {
                notices.deletionPending(news.at)
                events.record(CloudEvent.DELETION_PENDING)
                log("cloud: account deletion pending")
            } else {
                notices.deletionGone()
                events.record(CloudEvent.DELETION_CANCELLED, mapOf("hier" to false))
                log("cloud: account deletion no longer pending")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("cloud: deletion news not kept (${CloudErrors.logDetail(e)})")
        }
    }

    private suspend fun setUp(server: CloudServer, session: Sitzung, activity: Activity?) {
        busy("Schlüssel einrichten …")
        // The password service is asked once by itself; a wrong key from it asks the person.
        var trySafe = activity != null
        val result = Einrichtung(
            session.kontoId,
            session.geraetId,
            store.unlocked(),
            server.keyBackups(),
            server.unlocking(),
            keys(session.kontoId),
            frageSchluessel = {
                val fromSafe = if (trySafe && activity != null) {
                    trySafe = false
                    busy("Passwortmanager …")
                    fetch(activity)
                } else {
                    null
                }
                if (fromSafe != null) {
                    busy("Prüfen …")
                    fromSafe
                } else {
                    typed.ask { phase(CloudPhase.AskKey) }.also { busy("Prüfen …") }
                }
            },
            zeigeWiederherstellung = { key ->
                val text = key.alsText()
                shown = session.kontoId to text
                confirm.ask {
                    phase(CloudPhase.ShowKey(text))
                    // Offered right away (7.33); declined, the person writes it down as before.
                    if (activity != null) saveShown(activity, session.kontoId, text)
                }.also { shown = null; busy("Hochladen …") }
            },
            meldung = { text -> state.update { it.copy(notes = it.notes + text) } },
        ).ausfuehren()
        // A new account and one restored with the recovery key are unlocked (E-B2b-13), and so is
        // a set-up device that just unlocked itself with the key ("Mit Wiederherstellungsschlüssel
        // freischalten").
        if (result !is Eingerichtet.Vorhanden || result.freigeschaltet) store.setUnlocked(true)
        events.record(CloudEvent.SET_UP, mapOf("art" to SetupKind.of(result)))
        log("cloud: set up (${result::class.simpleName})")
        // The prompts of the setup ("Bitte den Wiederherstellungsschlüssel eingeben", "passt nicht")
        // are answered now; left standing they read as if the key were still missing.
        state.update { it.copy(notes = emptyList()) }
    }

    /**
     * Runs [block] unless another step runs (a second tap does nothing), then reads where
     * things stand. Errors end as a text in [CloudStatus.error], never as a crash.
     */
    private fun work(step: CloudStep, clearNotes: Boolean = true, block: suspend () -> Unit): Job {
        if (!running.tryLock()) return Job().apply { complete() }
        if (clearNotes) dismissNotes()
        stepServer = null
        return scope.launch {
            // KRITISCH: The lock is released only after the final phase is set (Review-Lauf 10).
            // Released before, the next step could show its question (AskKey, AskRestore) and
            // this one would then overwrite it with its own final phase: the question would be
            // gone, and the next step would wait for an answer for ever.
            try {
                var error: String? = null
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A withdrawn approval (backend ID7) ends the session like a rejection: renewing
                    // fails until the operator lets the account in again and the person signs in.
                    if (e is AnmeldungAbgelehnt || e is KontoNichtFreigegeben) {
                        store.forgetSession()
                        // KRITISCH: without a session the hourly backup stops for good; the person
                        // must learn it at once, not only from the notice after 3 days (A-13).
                        if (step != CloudStep.SIGN_IN) notices.signInAgain()
                    }
                    error = CloudErrors.describe(e)
                    events.record(CloudEvent.FAILED, CloudErrors.failure(step, e), e)
                    log("cloud: failed (${CloudErrors.logDetail(e)})")
                } finally {
                    questions.forEach { it.close() }
                    shown = null
                }
                heardFromServer()
                val phase = runCatching { current() }.getOrElse { e ->
                    if (error == null) error = CloudErrors.describe(e)
                    // Even the switch may be unreadable here; the app must not crash over the backup.
                    if (runCatching { store.enabled() }.getOrDefault(false)) CloudPhase.NotSetUp else CloudPhase.Off
                }
                val deletion = runCatching { store.deletion() }.getOrNull()
                state.update { it.copy(phase = phase, error = error, deletion = deletion) }
                // KRITISCH: The hourly backup runs only while it may (switched on, set up, unlocked,
                // backed up by hand once); switching off and resetting stop it.
                if (phase is CloudPhase.Ready && phase.automatic) schedule.start()
            } finally {
                running.unlock()
            }
        }
    }

    /** Where things stand on this device, without network. */
    private suspend fun current(): CloudPhase {
        if (!store.enabled()) return CloudPhase.Off
        val session = store.lesen() ?: return CloudPhase.SignedOut
        val local = keys(session.kontoId).laden()
        if (local == null || local.offenesBackup != null) return CloudPhase.NotSetUp
        return CloudPhase.Ready(
            session.kontoId,
            session.geraetId,
            local.bund.person.oeffentlich.fingerabdruck(),
            store.unlocked(),
            store.lastBackup(session.kontoId)?.let(Instant::ofEpochSecond),
            paused = backupState(session.kontoId).laden()?.abgelehnt == true,
            braked = store.braked(),
        )
    }

    /**
     * KRITISCH: The key goes only to the password service. A failure is no error of the setup:
     * the log names its kind, never the key.
     */
    private suspend fun offer(activity: Activity, account: String, key: String): Boolean {
        if (!safeBusy.compareAndSet(false, true)) return false
        return try {
            passwords.save(activity, account, key)
            log("cloud: key saved in the password service")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("cloud: key not saved in the password service (${CloudErrors.logDetail(e)})")
            false
        } finally {
            safeBusy.set(false)
        }
    }

    /** The key from the password service, or null (declined, none stored, failed). */
    private suspend fun fetch(activity: Activity): String? {
        if (!safeBusy.compareAndSet(false, true)) return null
        return try {
            passwords.read(activity).also { log(if (it == null) "cloud: no key in the password service" else "cloud: key from the password service") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("cloud: no key from the password service (${CloudErrors.logDetail(e)})")
            null
        } finally {
            safeBusy.set(false)
        }
    }

    private suspend fun askRestore(report: BackupReport): Boolean =
        restoreAnswer.ask { phase(CloudPhase.AskRestore(report)) }.also { busy("Wiederherstellen …") }

    private suspend fun askBrake(before: Int, now: Int): Boolean =
        brakeAnswer.ask { phase(CloudPhase.AskBrake(before, now)) }.also { busy("Sichern …") }

    /** The question of [Sicherungsablauf] (E-B2b-6), answered with [answerOverwrite]. */
    private suspend fun askOverwrite(head: Sicherungskopf): Boolean =
        overwrite.ask { phase(CloudPhase.AskOverwrite(head.erstellt)) }.also { busy("Sichern …") }

    private fun note(text: String) = state.update { it.copy(notes = it.notes + text) }

    companion object {
        /** How old the server's last word on a deletion may be before the hourly backup asks again. */
        val DELETION_CHECK: Duration = Duration.ofHours(23)
    }
    private fun busy(what: String) = phase(CloudPhase.Busy(what))
    private fun phase(p: CloudPhase) = state.update { it.copy(phase = p) }

}
