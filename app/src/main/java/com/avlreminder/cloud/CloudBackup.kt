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

    /**
     * Set up. [fingerprint] of the person key, to compare with another device; [lastBackup]: when
     * this device last backed up, null = not yet.
     */
    data class Ready(
        val account: String,
        val device: String,
        val fingerprint: String,
        val unlocked: Boolean,
        val lastBackup: Instant? = null,
    ) : CloudPhase
}

/** [notes]: what the setup told the person; [error]: why the last step failed. */
data class CloudStatus(val phase: CloudPhase, val notes: List<String> = emptyList(), val error: String? = null)

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
    private val log: (String) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    private val state = MutableStateFlow(CloudStatus(CloudPhase.Off))
    val status: StateFlow<CloudStatus> = state.asStateFlow()

    private val running = Mutex()
    @Volatile private var confirm: CompletableDeferred<Boolean>? = null
    @Volatile private var typed: CompletableDeferred<String?>? = null
    @Volatile private var overwrite: CompletableDeferred<Boolean>? = null

    /** The key on screen ([CloudPhase.ShowKey]) and its account, for [saveKey]. */
    @Volatile private var shown: Pair<String, String>? = null
    private val safeBusy = AtomicBoolean(false)

    /** Reads where things stand (at start and after each step); no network. */
    fun load(): Job = work(CloudStep.LOAD, clearNotes = false) {}

    fun setEnabled(on: Boolean): Job = work(CloudStep.SWITCH) {
        store.setEnabled(on)
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

    fun dismissNotes() = state.update { it.copy(notes = emptyList(), error = null) }

    /**
     * Backs up now, by hand (backup-entwurf.md, step 3): the app's data, encrypted with the
     * account's vault key, through `Sicherungsablauf` from the backend.
     *
     * KRITISCH: Switched off or not set up, nothing is read and the network is not touched. Did
     * another device back up last, it asks ([CloudPhase.AskOverwrite]) and replaces that backup
     * only on yes; on no nothing changes on the server.
     */
    fun backUp(): Job = work(CloudStep.BACKUP) {
        if (!store.enabled()) return@work
        val known = store.lesen() ?: return@work
        val local = keys(known.kontoId).laden() ?: return@work
        if (local.offenesBackup != null) return@work
        busy("Sichern …")
        val started = clock()
        val payload = source.payload()
        val server = server()
        val session = server.session()
        if (session.kontoId != known.kontoId) error("Die Sitzung gehört zu einem anderen Konto")
        val result = Sicherungsablauf(
            session.kontoId,
            session.geraetId,
            local.bund.tresor,
            server.dataBackups(session.token),
            backupState(session.kontoId),
            uebernehmen = ::askOverwrite,
            uhr = clock,
        ).sichern(payload, AvlDatabase.VERSION, automatisch = false)
        when (result) {
            is Gesichert.Ja -> {
                store.setLastBackup(session.kontoId, clock().epochSecond)
                val ms = Duration.between(started, clock()).toMillis()
                events.record(CloudEvent.BACKED_UP, mapOf("kib" to kib(payload.size), "dauer_ms" to ms, "zaehler" to result.zaehler))
                log("cloud: backed up (#${result.zaehler}, ${kib(payload.size)} KiB, $ms ms)")
            }
            is Gesichert.AnderesGeraet -> {
                events.record(CloudEvent.OTHER_DEVICE, mapOf("zaehler" to result.kopf.zaehler))
                log("cloud: not backed up, another device backed up last")
                note("Nicht gesichert: Die Sicherung des anderen Geräts bleibt auf dem Server.")
            }
            // Only the automatic backup rests (step 4); by hand it asks again.
            Gesichert.Ruht -> Unit
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
        val server = server()
        val session = server.session()
        val size = Diagnose.senden(server.diagnosis(session.token), events.lines())
        events.record(CloudEvent.DIAGNOSIS_SENT, mapOf("kib" to kib(size)))
        log("cloud: diagnosis sent (${kib(size)} KiB)")
        note("Diagnose gesendet (${kib(size)} KiB).")
    }

    /**
     * Resets the cloud backup so it can be set up again: on the server the data backup and the
     * key backup are deleted and this device is locked; on the phone the key file, the session
     * and the Keystore keys go. Reminders and settings stay. Afterwards it is off.
     *
     * KRITISCH: Is the server just not reachable (no network, kill switch, limit), nothing is
     * deleted on the phone: otherwise "reset" would leave the backup on the server unnoticed. Only
     * when this device may not delete there anymore (locked, not unlocked, session rejected) the
     * phone is reset anyway, with a note: waiting would not help.
     */
    fun reset(): Job = work(CloudStep.RESET) {
        if (!store.enabled()) return@work
        val session = store.lesen()
        var cleared = session == null
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
                if (e is AnmeldungAbgelehnt) store.forgetSession()
                error = describe(e)
                events.record(CloudEvent.FAILED, failure(step, e), e)
                log("cloud: failed (${logDetail(e)})")
            } finally {
                confirm = null
                typed = null
                overwrite = null
                shown = null
                running.unlock()
            }
            val phase = runCatching { current() }.getOrElse { e ->
                if (error == null) error = describe(e)
                // Even the switch may be unreadable here; the app must not crash over the backup.
                if (runCatching { store.enabled() }.getOrDefault(false)) CloudPhase.NotSetUp else CloudPhase.Off
            }
            state.update { it.copy(phase = phase, error = error) }
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
            is NeueresSchema -> "Die Sicherung stammt von einer neueren App-Version. Erst die App aktualisieren."
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
enum class CloudStep { LOAD, SWITCH, SIGN_IN, SETUP, BACKUP, DIAGNOSIS, RESET }

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
    OLDER_BACKUP, CHANGED_MEANWHILE, NEWER_SCHEMA, DIAGNOSIS_NOT_ALLOWED, SERVER, NETWORK, OTHER;

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
            is NeueresSchema -> NEWER_SCHEMA
            is DiagnoseNichtFreigeschaltet -> DIAGNOSIS_NOT_ALLOWED
            is Serverfehler -> SERVER
            is IOException -> NETWORK
            else -> OTHER
        }
    }
}
