package com.avlreminder.cloud

import android.app.Activity
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.avlreminder.protokoll.BackupAblage
import com.avlreminder.protokoll.Eingerichtet
import com.avlreminder.protokoll.Einrichtung
import com.avlreminder.protokoll.EinrichtungAbgebrochen
import com.avlreminder.protokoll.GeraeteFreischaltung
import com.avlreminder.protokoll.GeraetNichtFreigeschaltet
import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.verbindung.Angemeldet
import com.avlreminder.verbindung.Anmeldung
import com.avlreminder.verbindung.AnmeldungAbgelehnt
import com.avlreminder.verbindung.GeraetGesperrt
import com.avlreminder.verbindung.Geraeteschluessel
import com.avlreminder.verbindung.NichtAngemeldet
import com.avlreminder.verbindung.ServerBackupAblage
import com.avlreminder.verbindung.ServerGeraete
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

    /** Set up. [fingerprint] of the person key, to compare with another device. */
    data class Ready(val account: String, val device: String, val fingerprint: String, val unlocked: Boolean) : CloudPhase
}

/** [notes]: what the setup told the person; [error]: why the last step failed. */
data class CloudStatus(val phase: CloudPhase, val notes: List<String> = emptyList(), val error: String? = null)

/** The server as far as signing in and setting up need it; replaced in tests. */
interface CloudServer {
    /** Goes with the Google sign-in ([GoogleSignIn.idToken]). */
    val nonce: String

    /** Signs in with an ID token and stores the session. */
    suspend fun signIn(idToken: String): Angemeldet

    /** A valid session, renewed when it is about to expire. */
    suspend fun session(): Sitzung

    fun keyBackups(token: String): BackupAblage
    fun unlocking(token: String): GeraeteFreischaltung
}

/** [CloudServer] over Connect ([Verbindung], from the backend's `verbindung`). */
class ConnectCloudServer(private val verbindung: Verbindung, key: Geraeteschluessel, sessions: SitzungsAblage) : CloudServer {
    private val anmeldung = Anmeldung(verbindung, key, sessions)

    override val nonce: String get() = anmeldung.nonce
    override suspend fun signIn(idToken: String): Angemeldet = anmeldung.anmelden(idToken)
    override suspend fun session(): Sitzung = anmeldung.sitzung()
    override fun keyBackups(token: String): BackupAblage = ServerBackupAblage(verbindung, token)
    override fun unlocking(token: String): GeraeteFreischaltung = ServerGeraete(verbindung, token)
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
 */
class CloudBackup(
    private val store: CloudStore,
    private val server: () -> CloudServer,
    private val keys: (account: String) -> LokaleAblage,
    private val google: GoogleSignIn,
    private val passwords: PasswordSafe,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    private val state = MutableStateFlow(CloudStatus(CloudPhase.Off))
    val status: StateFlow<CloudStatus> = state.asStateFlow()

    private val running = Mutex()
    @Volatile private var confirm: CompletableDeferred<Boolean>? = null
    @Volatile private var typed: CompletableDeferred<String?>? = null

    /** The key on screen ([CloudPhase.ShowKey]) and its account, for [saveKey]. */
    @Volatile private var shown: Pair<String, String>? = null
    private val safeBusy = AtomicBoolean(false)

    /** Reads where things stand (at start and after each step); no network. */
    fun load(): Job = work(clearNotes = false) {}

    fun setEnabled(on: Boolean): Job = work {
        store.setEnabled(on)
        log("cloud: ${if (on) "on" else "off"}")
    }

    /** Google account picker, then sign-in and setup. */
    fun signIn(activity: Activity): Job = work {
        if (!store.enabled()) return@work
        busy("Anmelden …")
        val server = server()
        val signedIn = server.signIn(google.idToken(activity, server.nonce))
        store.setUnlocked(signedIn.freigeschaltet)
        log("cloud: signed in" + if (signedIn.neuesKonto) " (new account)" else "")
        setUp(server, signedIn.sitzung, activity)
    }

    /**
     * Continues a setup that did not finish (e.g. upload failed, app closed). Without
     * [activity] the password service is not asked.
     */
    fun continueSetup(activity: Activity? = null): Job = work {
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

    fun dismissNotes() = state.update { it.copy(notes = emptyList(), error = null) }

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
        log("cloud: set up (${result::class.simpleName})")
    }

    /**
     * Runs [block] unless another step runs (a second tap does nothing), then reads where
     * things stand. Errors end as a text in [CloudStatus.error], never as a crash.
     */
    private fun work(clearNotes: Boolean = true, block: suspend () -> Unit): Job {
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
                log("cloud: failed (${logDetail(e)})")
            } finally {
                confirm = null
                typed = null
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
        return CloudPhase.Ready(session.kontoId, session.geraetId, local.bund.person.oeffentlich.fingerabdruck(), store.unlocked())
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

    private fun busy(what: String) = phase(CloudPhase.Busy(what))
    private fun phase(p: CloudPhase) = state.update { it.copy(phase = p) }

    companion object {
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
            // KRITISCH: An answer not from the service (kill switch, proxy) is an outage, not a verdict.
            is Serverfehler ->
                if (e.vomDienst) "Der Server meldet einen Fehler (${e.code.codeName})." else "Server gerade nicht erreichbar (gesperrt oder gestört). Später erneut versuchen."
            is IOException -> "Kein Netz oder Server nicht erreichbar."
            else -> "Unerwarteter Fehler (${e::class.simpleName})."
        }
    }
}
