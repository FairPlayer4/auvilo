package com.avlreminder.cloud

import android.app.Activity
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.avlreminder.data.AvlDatabase
import com.avlreminder.protokoll.AeltereSicherung
import com.avlreminder.protokoll.BackupAblage
import com.avlreminder.protokoll.Diagnose
import com.avlreminder.protokoll.DiagnoseAblage
import com.avlreminder.protokoll.DiagnoseNichtFreigeschaltet
import com.avlreminder.protokoll.DiagnoseTageslimit
import com.avlreminder.protokoll.DiagnoseZuGross
import com.avlreminder.protokoll.Datensicherung
import com.avlreminder.protokoll.EntschluesselnFehlgeschlagen
import com.avlreminder.protokoll.Sicherung
import com.avlreminder.protokoll.Wiederhergestellt
import com.avlreminder.restore.Comparison
import com.avlreminder.restore.DamagedDatabase
import com.avlreminder.restore.NewerDatabase
import com.avlreminder.restore.RestoreBusy
import com.avlreminder.restore.RestoreKind
import com.avlreminder.restore.RestoreTarget
import com.avlreminder.protokoll.Eingerichtet
import com.avlreminder.protokoll.Einrichtung
import com.avlreminder.protokoll.EinrichtungAbgebrochen
import com.avlreminder.protokoll.GeraeteFreischaltung
import com.avlreminder.protokoll.GeraetNichtFreigeschaltet
import com.avlreminder.protokoll.Gesichert
import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.protokoll.NeueresSchema
import com.avlreminder.protokoll.SicherungTageslimit
import com.avlreminder.protokoll.SicherungZuGross
import com.avlreminder.protokoll.Sicherungsablauf
import com.avlreminder.protokoll.SicherungsAblage
import com.avlreminder.protokoll.SicherungsStand
import com.avlreminder.protokoll.Sicherungskopf
import com.avlreminder.protokoll.SymmetrischerSchluessel
import com.avlreminder.protokoll.VersionVeraltet
import com.avlreminder.verbindung.Angemeldet
import com.avlreminder.verbindung.Anmeldung
import com.avlreminder.verbindung.AnmeldungAbgelehnt
import com.avlreminder.verbindung.GeraetGesperrt
import com.avlreminder.verbindung.Geraeteschluessel
import com.avlreminder.verbindung.NichtAngemeldet
import com.avlreminder.verbindung.ServerBackupAblage
import com.avlreminder.verbindung.ServerDiagnoseAblage
import com.avlreminder.verbindung.ServerGeraete
import com.avlreminder.verbindung.ServerSicherungsAblage
import com.avlreminder.verbindung.Serverfehler
import com.avlreminder.verbindung.Sitzung
import com.avlreminder.verbindung.SitzungsAblage
import com.avlreminder.verbindung.Verbindung
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Where the cloud backup stands, for the settings (A-14). */
sealed interface CloudPhase {
    /** Switched off: no sign-in, no network (PS8). */
    data object Off : CloudPhase

    /** On, but not signed in with Google. */
    data object SignedOut : CloudPhase

    /** Something runs; [what] says what. */
    data class Busy(val what: String) : CloudPhase

    /** Signed in, but the keys are missing or their backup is not uploaded yet. */
    data object NotSetUp : CloudPhase

    /**
     * New keys: the person must keep [key] before anything is stored. Never logged, and the
     * screen blocks screenshots while it shows. [saved]: it is in the password service (7.33).
     */
    data class ShowKey(val key: String, val saved: Boolean = false) : CloudPhase

    /** The account has keys already: the person types the recovery key. */
    data object AskKey : CloudPhase

    /**
     * Another device backed up last ([created]): back up from here anyway (its backup is
     * replaced) or keep it (E-B2b-6)?
     */
    data class AskOverwrite(val created: Instant) : CloudPhase

    /** The backup is fetched, checked and compared ([report]): swap it in? (W-5, step 2) */
    data class AskRestore(val report: BackupReport) : CloudPhase

    /** Many reminders are missing since the last backup ([before] then, [now] here): back up anyway? (R-2) */
    data class AskBrake(val before: Int, val now: Int) : CloudPhase

    /**
     * Set up. [fingerprint] of the person key, to compare with another device; [lastBackup]: when
     * this device last backed up, null = not yet. [paused]: another device backs up and the
     * automatic backup rests until the person backs up by hand (A-10). [braked]: many reminders
     * are missing and the automatic backup stopped (R-2).
     */
    data class Ready(
        val account: String,
        val device: String,
        val fingerprint: String,
        val unlocked: Boolean,
        val lastBackup: Instant? = null,
        val paused: Boolean = false,
        val braked: Boolean = false,
    ) : CloudPhase {
        /** The hourly backup runs: unlocked and backed up by hand once (A-9). */
        val automatic get() = unlocked && lastBackup != null
    }
}

/**
 * [notes]: what the setup told the person; [error]: why the last step failed; [report]: the result
 * of the last "Sicherung prüfen".
 */
data class CloudStatus(val phase: CloudPhase, val notes: List<String> = emptyList(), val error: String? = null, val report: BackupReport? = null)

/**
 * The result of "Sicherung prüfen" (W-1, W-2): the backup made at [created], from this device or
 * another, compared table by table with the data here. [unchanged]: the data here are byte for
 * byte those of the backup; otherwise differences are to be expected.
 */
data class BackupReport(val created: Instant, val fromThisDevice: Boolean, val unchanged: Boolean, val comparison: Comparison)

/** The server as far as signing in and setting up need it; replaced in tests. */
interface CloudServer {
    /**
     * Signs in and stores the session. [idToken] fetches the Google ID token for the given
     * nonce ([GoogleSignIn.idToken]).
     *
     * KRITISCH: every sign-in gets a fresh nonce; with the same one Google hands back a cached
     * token for up to an hour, which the server rejects as too old after 5 minutes.
     */
    suspend fun signIn(idToken: suspend (nonce: String) -> String): Angemeldet

    /** A valid session, renewed when it is about to expire. */
    suspend fun session(): Sitzung

    fun keyBackups(token: String): BackupAblage
    fun unlocking(token: String): GeraeteFreischaltung
    fun dataBackups(token: String): SicherungsAblage
    fun diagnosis(token: String): DiagnoseAblage

    /** Deletes the account's data backup with all older versions (none is no error). */
    suspend fun deleteDataBackup(token: String)

    /** Deletes the account's key backup (none is no error). */
    suspend fun deleteKeyBackup(token: String)

    /** Locks [device] for good; the own device may always lock itself. */
    suspend fun lock(token: String, device: String)
}

/** Starts and stops the hourly backup (WorkManager); replaced in tests. */
interface CloudSchedule {
    /** Starts it; already running, it keeps its rhythm. */
    fun start()

    fun stop()
}

/** The two notifications of the automatic backup (A-10, A-13); replaced in tests. */
interface CloudNotices {
    /** Another device backed up last ([created]); the automatic backup rests. */
    fun otherDevice(created: Instant)

    /** Many reminders are missing ([before] at the last backup, [now] here); the automatic backup stops (R-2). */
    fun brake(before: Int, now: Int)

    /** The backup has not held this device's data for over 3 days; [lastBackup] = last upload. */
    fun stale(lastBackup: Instant)

    /** The server rejected the session; nothing is backed up until the person signs in again. */
    fun signInAgain()

    /** Signed in again: [signInAgain] is done with. */
    fun signedIn()

    /** The notices about the backup are done with (backed up by hand). */
    fun clear()
}

/** [CloudServer] over Connect ([Verbindung], from the backend's `verbindung`). */
class ConnectCloudServer(private val verbindung: Verbindung, key: Geraeteschluessel, sessions: SitzungsAblage) : CloudServer {
    private val anmeldung = Anmeldung(verbindung, key, sessions)

    override suspend fun signIn(idToken: suspend (nonce: String) -> String): Angemeldet {
        val attempt = anmeldung.versuch()
        return anmeldung.anmelden(attempt, idToken(attempt.nonce))
    }
    override suspend fun session(): Sitzung = anmeldung.sitzung()
    override fun keyBackups(token: String): BackupAblage = ServerBackupAblage(verbindung, token)
    override fun unlocking(token: String): GeraeteFreischaltung = ServerGeraete(verbindung, token)
    override fun dataBackups(token: String): SicherungsAblage = ServerSicherungsAblage(verbindung, token)
    override fun diagnosis(token: String): DiagnoseAblage = ServerDiagnoseAblage(verbindung, token)
    override suspend fun deleteDataBackup(token: String) = ServerSicherungsAblage(verbindung, token).loeschen()
    override suspend fun deleteKeyBackup(token: String) = ServerBackupAblage(verbindung, token).loeschen()
    override suspend fun lock(token: String, device: String) = ServerGeraete(verbindung, token).sperren(device)
}

/**
 * Switching on, signing in and setting up the cloud backup (backup-entwurf.md, "Ablauf
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
) {
    private val state = MutableStateFlow(CloudStatus(CloudPhase.Off))
    val status: StateFlow<CloudStatus> = state.asStateFlow()

    private val running = Mutex()
    @Volatile private var confirm: CompletableDeferred<Boolean>? = null
    @Volatile private var typed: CompletableDeferred<String?>? = null
    @Volatile private var overwrite: CompletableDeferred<Boolean>? = null
    @Volatile private var restoreAnswer: CompletableDeferred<Boolean>? = null
    @Volatile private var brakeAnswer: CompletableDeferred<Boolean>? = null

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
        val server = server()
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
        val server = server()
        setUp(server, server.session(), activity)
    }

    /** Offers the key on screen to the password service again (the first offer was declined). */
    fun saveKey(activity: Activity): Job = scope.launch {
        val (account, key) = shown ?: return@launch
        if (offer(activity, account, key)) state.update { if (it.phase == CloudPhase.ShowKey(key)) it.copy(phase = CloudPhase.ShowKey(key, saved = true)) else it }
    }

    /** Fetches the key from the password service while [CloudPhase.AskKey] shows. */
    fun fetchKey(activity: Activity): Job = scope.launch {
        val answer = typed ?: return@launch
        fetch(activity)?.let { answer.complete(it) }
    }

    /** The answer to [CloudPhase.ShowKey]: true = written down. */
    fun confirmKey(writtenDown: Boolean) {
        confirm?.complete(writtenDown)
    }

    /** The answer to [CloudPhase.AskKey]: null = cancel. */
    fun submitKey(text: String?) {
        typed?.complete(text)
    }

    /** The answer to [CloudPhase.AskOverwrite]: true = back up from here, replacing the other. */
    fun answerOverwrite(yes: Boolean) {
        overwrite?.complete(yes)
    }

    /** The answer to [CloudPhase.AskRestore]: true = swap in at the restart. */
    fun answerRestore(yes: Boolean) {
        restoreAnswer?.complete(yes)
    }

    /** The answer to [CloudPhase.AskBrake]: true = back up anyway. */
    fun answerBrake(yes: Boolean) {
        brakeAnswer?.complete(yes)
    }

    fun dismissNotes() = state.update { it.copy(notes = emptyList(), error = null, report = null) }

    /**
     * Backs up now, by hand (backup-entwurf.md, step 3): the app's data, encrypted with the
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
        if (before != null && brakes(before, count) && !askBrake(before, count)) {
            note("Nicht gesichert.")
            return false
        }
        val payload = source.payload()
        return when (val result = upload(known.kontoId, local.bund.tresor, payload, automatic = false, ask = ::askOverwrite)) {
            is Gesichert.Ja -> {
                notices.clear()
                done(known.kontoId, payload, result, started, automatic = false, count)
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
        val server = server()
        val session = server.session()
        if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
        val fetched = server.dataBackups(session.token).holen()
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
        val unchanged = sha256(source.payload()) == sha256(backup.nutzlast)
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
        val server = server()
        val session = server.session()
        if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
        var compared = false
        var staged = false
        try {
            val result = Sicherungsablauf(
                session.kontoId,
                session.geraetId,
                local.bund.tresor,
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
                        // W-7: the next upload compares with the server and asks (E-B2b-6).
                        val stand = backupState(session.kontoId)
                        stand.laden()?.let { stand.speichern(it.copy(version = 0)) }
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
        val server = server()
        val session = server.session()
        if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
        Sicherungsablauf.loeschen(server.dataBackups(session.token), backupState(session.kontoId))
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
                val server = server()
                server.lock(server.session().token, session.geraetId)
                locked = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("cloud: sign-out, device not locked (${logDetail(e)})")
                note("Beim Server nicht abgemeldet (${describe(e) ?: "abgebrochen"}). Auf diesem Handy ist trotzdem alles abgemeldet.")
            }
        }
        busy("Auf dem Handy löschen …")
        schedule.stop()
        notices.clear()
        wipe()
        store.clear()
        events.record(CloudEvent.SIGNED_OUT, mapOf("gesperrt" to locked))
        log("cloud: signed out")
    }

    /**
     * The hourly backup (backup-entwurf.md, step 4), called by the worker: like [backUp], but
     * without anyone to ask. Afterwards it checks whether the backup is older than 3 days.
     *
     * KRITISCH:
     *  - Switched off, not set up, not unlocked or never backed up by hand: nothing is read and
     *    the network is not touched.
     *  - Unchanged data (same SHA-256 as the last upload) are not uploaded (A-9); that spares the
     *    daily limit and the battery.
     *  - Did another device back up last, the backup of that device stays. The automatic backup
     *    then rests and a notification says so, until the person backs up by hand (A-10); it does
     *    not ask the server again every hour.
     *  - Errors (no network, kill switch, limit) only show as status; the next try is an hour
     *    later (A-13).
     *  - Are many reminders missing since the last backup (R-2), nothing is uploaded: the backup
     *    stops with a notification until the person backs up by hand. Otherwise a fault in the app
     *    would carry the loss to the server every hour.
     *  - While a restore waits for the restart or after a failed swap, nothing is uploaded.
     */
    suspend fun backUpAutomatically() {
        work(CloudStep.AUTO_BACKUP, clearNotes = false) { automatic() }.join()
        try {
            checkStale()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("cloud: stale check failed (${logDetail(e)})")
        }
    }

    private suspend fun automatic() {
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
        when (val result = upload(account, local.bund.tresor, payload, automatic = true, ask = { false })) {
            is Gesichert.Ja -> {
                notices.clear()
                done(account, payload, result, started, automatic = true, count)
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
    private suspend fun checkStale() {
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

    /** One upload through `Sicherungsablauf` (backend); [ask] answers "back up over another device?". */
    private suspend fun upload(
        account: String,
        vault: SymmetrischerSchluessel,
        payload: ByteArray,
        automatic: Boolean,
        ask: suspend (Sicherungskopf) -> Boolean,
    ): Gesichert {
        val server = server()
        val session = server.session()
        if (session.kontoId != account) error("Die Sitzung gehört zu einem anderen Konto")
        return Sicherungsablauf(
            session.kontoId,
            session.geraetId,
            vault,
            server.dataBackups(session.token),
            backupState(session.kontoId),
            uebernehmen = ask,
            uhr = clock,
        ).sichern(payload, AvlDatabase.VERSION, automatisch = automatic)
    }

    private suspend fun done(account: String, payload: ByteArray, result: Gesichert.Ja, started: Instant, automatic: Boolean, count: Int) {
        store.setLastBackup(account, clock().epochSecond, sha256(payload), count)
        val ms = Duration.between(started, clock()).toMillis()
        events.record(
            CloudEvent.BACKED_UP,
            mapOf("kib" to kib(payload.size), "dauer_ms" to ms, "zaehler" to result.zaehler, "automatisch" to automatic),
        )
        log("cloud: backed up${if (automatic) " automatically" else ""} (#${result.zaehler}, ${kib(payload.size)} KiB, $ms ms)")
    }

    /**
     * Sends the backend logger's events to the server (diagnose-entwurf.md), by hand.
     *
     * KRITISCH: Only [CloudLog.lines] goes out, never `diag.log` (which may hold titles).
     */
    fun sendDiagnosis(): Job = work(CloudStep.DIAGNOSIS) {
        if (!store.enabled()) return@work
        busy("Diagnose senden …")
        val server = server()
        val session = server.session()
        val size = Diagnose.senden(server.diagnosis(session.token), events.lines())
        events.record(CloudEvent.DIAGNOSIS_SENT, mapOf("kib" to kib(size)))
        log("cloud: diagnosis sent (${kib(size)} KiB)")
        note("Diagnose gesendet (${kib(size)} KiB).")
    }

    /**
     * "Cloud-Daten löschen" (R-3): on the server the data backup with all its older versions and
     * the key backup are deleted for good and this device is locked; on the phone the key file,
     * the session and the Keystore keys go. Reminders and settings stay. Afterwards it is off and
     * can be set up again. For a test device "Dieses Gerät abmelden" ([signOut]) is the way: this
     * deletes the backup of every device of the account.
     *
     * KRITISCH: Is the server just not reachable (no network, kill switch, limit), nothing is
     * deleted on the phone: otherwise "reset" would leave the backup on the server unnoticed. Only
     * when this device may not delete there anymore (locked, not unlocked, session rejected) the
     * phone is reset anyway, with a note: waiting would not help.
     */
    fun deleteCloudData(): Job = work(CloudStep.RESET) {
        if (!store.enabled()) return@work
        val session = store.lesen()
        // Without a session nothing is deleted on the server; the log must not say otherwise.
        var cleared = false
        if (session != null) {
            busy("Auf dem Server löschen …")
            val server = server()
            var token: String? = null
            try {
                token = server.session().token
                server.deleteDataBackup(token)
                server.deleteKeyBackup(token)
                server.lock(token, session.geraetId)
                cleared = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!cannotActOnServer(e)) throw e
                // Not unlocked: it cannot delete, but it can still lock itself.
                if (token != null) runCatching { server.lock(token, session.geraetId) }
                log("cloud: reset, server not cleared (${logDetail(e)})")
                state.update {
                    it.copy(
                        notes = it.notes + "Auf dem Server ließ sich nichts löschen (${describe(e)}). Ein Backup dort " +
                            "bleibt mit dem Wiederherstellungsschlüssel erreichbar.",
                    )
                }
            }
        }
        busy("Auf dem Handy löschen …")
        schedule.stop()
        notices.clear()
        wipe()
        store.clear()
        events.record(CloudEvent.RESET, mapOf("server_geleert" to cleared))
        log("cloud: reset")
    }

    private suspend fun setUp(server: CloudServer, session: Sitzung, activity: Activity?) {
        busy("Schlüssel einrichten …")
        // The password service is asked once by itself; a wrong key from it asks the person.
        var trySafe = activity != null
        val result = Einrichtung(
            session.kontoId,
            session.geraetId,
            store.unlocked(),
            server.keyBackups(session.token),
            server.unlocking(session.token),
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
                    val answer = CompletableDeferred<String?>().also { typed = it }
                    phase(CloudPhase.AskKey)
                    answer.await().also { busy("Prüfen …") }
                }
            },
            zeigeWiederherstellung = { key ->
                val text = key.alsText()
                val answer = CompletableDeferred<Boolean>().also { confirm = it }
                shown = session.kontoId to text
                phase(CloudPhase.ShowKey(text))
                // Offered right away (7.33); declined, the person writes it down as before.
                if (activity != null && offer(activity, session.kontoId, text)) {
                    state.update { if (it.phase == CloudPhase.ShowKey(text)) it.copy(phase = CloudPhase.ShowKey(text, saved = true)) else it }
                }
                answer.await().also { shown = null; busy("Hochladen …") }
            },
            meldung = { text -> state.update { it.copy(notes = it.notes + text) } },
        ).ausfuehren()
        // A new account and one restored with the recovery key are unlocked (E-B2b-13).
        if (result !is Eingerichtet.Vorhanden) store.setUnlocked(true)
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
        return scope.launch {
            var error: String? = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is AnmeldungAbgelehnt) {
                    store.forgetSession()
                    // KRITISCH: without a session the hourly backup stops for good; the person
                    // must learn it at once, not only from the notice after 3 days (A-13).
                    if (step != CloudStep.SIGN_IN) notices.signInAgain()
                }
                error = describe(e)
                events.record(CloudEvent.FAILED, failure(step, e), e)
                log("cloud: failed (${logDetail(e)})")
            } finally {
                confirm = null
                typed = null
                overwrite = null
                restoreAnswer = null
                brakeAnswer = null
                shown = null
                running.unlock()
            }
            val phase = runCatching { current() }.getOrElse { e ->
                if (error == null) error = describe(e)
                // Even the switch may be unreadable here; the app must not crash over the backup.
                if (runCatching { store.enabled() }.getOrDefault(false)) CloudPhase.NotSetUp else CloudPhase.Off
            }
            state.update { it.copy(phase = phase, error = error) }
            // KRITISCH: The hourly backup runs only while it may (switched on, set up, unlocked,
            // backed up by hand once); switching off and resetting stop it.
            if (phase is CloudPhase.Ready && phase.automatic) schedule.start()
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
            log("cloud: key not saved in the password service (${logDetail(e)})")
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
            log("cloud: no key from the password service (${logDetail(e)})")
            null
        } finally {
            safeBusy.set(false)
        }
    }

    private suspend fun askRestore(report: BackupReport): Boolean {
        val answer = CompletableDeferred<Boolean>().also { restoreAnswer = it }
        phase(CloudPhase.AskRestore(report))
        return answer.await().also { busy("Wiederherstellen …") }
    }

    private suspend fun askBrake(before: Int, now: Int): Boolean {
        val answer = CompletableDeferred<Boolean>().also { brakeAnswer = it }
        phase(CloudPhase.AskBrake(before, now))
        return answer.await().also { busy("Sichern …") }
    }

    /** The question of [Sicherungsablauf] (E-B2b-6), answered with [answerOverwrite]. */
    private suspend fun askOverwrite(head: Sicherungskopf): Boolean {
        val answer = CompletableDeferred<Boolean>().also { overwrite = it }
        phase(CloudPhase.AskOverwrite(head.erstellt))
        return answer.await().also { busy("Sichern …") }
    }

    private fun note(text: String) = state.update { it.copy(notes = it.notes + text) }
    private fun busy(what: String) = phase(CloudPhase.Busy(what))
    private fun phase(p: CloudPhase) = state.update { it.copy(phase = p) }

    companion object {
        private fun kib(bytes: Int) = (bytes + 1023) / 1024

        /** After this long without a current backup the notice comes (A-13). */
        val STALE_AFTER: Duration = Duration.ofDays(3)

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

        /** The fields of a [CloudEvent.FAILED]: only the step and the kind, no message. */
        fun failure(step: CloudStep, e: Throwable): Map<String, Any> = buildMap {
            put("schritt", step)
            put("art", FailureKind.of(e))
            if (e is Serverfehler) {
                put("code", e.code)
                put("vom_dienst", e.vomDienst)
            }
        }

        /** Errors after which this device can do nothing more on the server (see [reset]). */
        fun cannotActOnServer(e: Throwable): Boolean =
            e is GeraetNichtFreigeschaltet || e is GeraetGesperrt || e is AnmeldungAbgelehnt || e is NichtAngemeldet

        /**
         * For the diag log: the kind of error and what Google or the server said about it. The
         * cause of a failed Google sign-in (wrong SHA-1, client not set up) is only in
         * [GetCredentialException.errorMessage]. No tokens, keys or contents.
         */
        fun logDetail(e: Throwable): String = when (e) {
            is GetCredentialException -> "${e::class.simpleName} ${e.type}: ${e.errorMessage}"
            is CreateCredentialException -> "${e::class.simpleName} ${e.type}: ${e.errorMessage}"
            is Serverfehler -> "Serverfehler ${e.code.codeName}, vomDienst=${e.vomDienst}"
            else -> e::class.simpleName.orEmpty()
        }

        /** A short text for the person; null = nothing to show (cancelled at Google). */
        fun describe(e: Throwable): String? = when (e) {
            is GetCredentialCancellationException -> null
            is NoCredentialException -> "Kein Google-Konto gefunden. Im Handy ein Google-Konto einrichten und erneut versuchen."
            is GetCredentialException -> "Anmeldung bei Google gescheitert (${e.type})."
            is AnmeldungAbgelehnt -> "Der Server hat die Anmeldung abgelehnt. Bitte neu anmelden."
            is GeraetGesperrt -> "Dieses Gerät ist beim Server gesperrt."
            is NichtAngemeldet -> "Nicht angemeldet. Bitte mit Google anmelden."
            is GeraetNichtFreigeschaltet -> "Dieses Gerät ist noch nicht freigeschaltet."
            is EinrichtungAbgebrochen -> "Einrichtung abgebrochen: ${e.message}"
            is KeysOfOtherAccount -> e.message
            is SicherungZuGross -> "Sicherung zu groß, nichts hochgeladen."
            is SicherungTageslimit, is DiagnoseTageslimit -> "Für heute ist das Limit erreicht. Morgen erneut versuchen."
            is AeltereSicherung -> "Der Server liefert eine ältere Sicherung, als dieses Gerät kennt. Nichts geändert."
            is VersionVeraltet -> "Die Sicherung wurde gerade von einem anderen Gerät geändert. Später erneut sichern."
            is NeueresSchema, is NewerDatabase -> "Die Sicherung stammt von einer neueren App-Version. Erst die App aktualisieren."
            is DamagedBackup, is DamagedDatabase -> "Die Sicherung ist beschädigt oder passt nicht zur App. Nichts geändert."
            is EntschluesselnFehlgeschlagen -> "Die Sicherung lässt sich mit diesem Schlüssel nicht öffnen. Nichts geändert."
            is RestoreBusy -> "Es läuft schon eine Prüfung oder ein Wiederherstellen wartet auf den Neustart."
            is DiagnoseNichtFreigeschaltet -> "Dieses Konto darf keine Diagnose senden."
            is DiagnoseZuGross -> "Diagnose zu groß, nichts gesendet."
            // KRITISCH: An answer not from the service (kill switch, proxy) is an outage, not a verdict.
            is Serverfehler ->
                if (e.vomDienst) "Der Server meldet einen Fehler (${e.code.codeName})." else "Server gerade nicht erreichbar (gesperrt oder gestört). Später erneut versuchen."
            is IOException -> "Kein Netz oder Server nicht erreichbar."
            else -> "Unerwarteter Fehler (${e::class.simpleName})."
        }
    }
}

/** The step a [CloudEvent.FAILED] happened in. */
enum class CloudStep { LOAD, SWITCH, SIGN_IN, SETUP, BACKUP, AUTO_BACKUP, DIAGNOSIS, RESET, CHECK, RESTORE, DELETE_BACKUP, SIGN_OUT }

/** How the setup ended, for [CloudEvent.SET_UP]. */
enum class SetupKind {
    NEW, FROM_BACKUP, EXISTING;

    companion object {
        fun of(e: Eingerichtet) = when (e) {
            is Eingerichtet.Neu -> NEW
            is Eingerichtet.AusBackup -> FROM_BACKUP
            is Eingerichtet.Vorhanden -> EXISTING
        }
    }
}

/** The kind of error, for [CloudEvent.FAILED]; the same split as [CloudBackup.describe]. */
enum class FailureKind {
    GOOGLE, REJECTED, LOCKED, SIGNED_OUT, NOT_UNLOCKED, SETUP_CANCELLED, OTHER_ACCOUNT, TOO_LARGE, DAILY_LIMIT,
    OLDER_BACKUP, CHANGED_MEANWHILE, NEWER_SCHEMA, DIAGNOSIS_NOT_ALLOWED, SERVER, NETWORK, DAMAGED, BUSY, OTHER;

    companion object {
        fun of(e: Throwable) = when (e) {
            is GetCredentialException, is CreateCredentialException -> GOOGLE
            is AnmeldungAbgelehnt -> REJECTED
            is GeraetGesperrt -> LOCKED
            is NichtAngemeldet -> SIGNED_OUT
            is GeraetNichtFreigeschaltet -> NOT_UNLOCKED
            is EinrichtungAbgebrochen -> SETUP_CANCELLED
            is KeysOfOtherAccount -> OTHER_ACCOUNT
            is SicherungZuGross, is DiagnoseZuGross -> TOO_LARGE
            is SicherungTageslimit, is DiagnoseTageslimit -> DAILY_LIMIT
            is AeltereSicherung -> OLDER_BACKUP
            is VersionVeraltet -> CHANGED_MEANWHILE
            is NeueresSchema, is NewerDatabase -> NEWER_SCHEMA
            is DamagedBackup, is DamagedDatabase, is EntschluesselnFehlgeschlagen -> DAMAGED
            is RestoreBusy -> BUSY
            is DiagnoseNichtFreigeschaltet -> DIAGNOSIS_NOT_ALLOWED
            is Serverfehler -> SERVER
            is IOException -> NETWORK
            else -> OTHER
        }
    }
}
