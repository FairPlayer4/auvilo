package com.avlreminder.cloud

import com.avlreminder.protokoll.BackupAblage
import com.avlreminder.protokoll.DiagnoseAblage
import com.avlreminder.protokoll.GeraeteFreischaltung
import com.avlreminder.protokoll.KontoLoeschenAmServer
import com.avlreminder.protokoll.SicherungsAblage
import com.avlreminder.verbindung.Angemeldet
import com.avlreminder.verbindung.Anmeldung
import com.avlreminder.verbindung.Geraeteschluessel
import com.avlreminder.verbindung.ServerBackupAblage
import com.avlreminder.verbindung.ServerDiagnoseAblage
import com.avlreminder.verbindung.ServerGeraete
import com.avlreminder.verbindung.ServerKontoLoeschen
import com.avlreminder.verbindung.ServerSicherungsAblage
import com.avlreminder.verbindung.Sitzung
import com.avlreminder.verbindung.SitzungsAblage
import java.util.concurrent.atomic.AtomicBoolean
import com.avlreminder.verbindung.Verbindung
import java.time.Instant

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

    /** A renewed session, also while the stored one is still valid: the server's word on a deletion. */
    suspend fun renew(): Sitzung

    /**
     * Runs [block] with a valid session. Did the server reject the session, it is renewed once
     * and [block] runs again (`Anmeldung.mitSitzung`): the renewal tells a locked device from
     * one that must sign in again. [block] must be safe to repeat.
     */
    suspend fun <T> withSession(block: suspend (Sitzung) -> T): T = block(session())

    /**
     * For the setup: these fetch a current token before every call (backend R2). Between two
     * calls the person may type the recovery key for longer than the session lasts.
     */
    fun keyBackups(): BackupAblage
    fun unlocking(): GeraeteFreischaltung
    fun dataBackups(token: String): SicherungsAblage
    fun diagnosis(token: String): DiagnoseAblage

    /** "Konto löschen" (backend B1) for `KontoLoeschen`: deletes at once or requests it. */
    fun deletion(token: String): KontoLoeschenAmServer

    /**
     * What the server said about a requested deletion of the account (backend B1) at the last
     * sign-in or renewal of this instance; null = it has not been asked yet.
     */
    fun deletionNews(): DeletionNews?

    /** Locks [device] for good; the own device may always lock itself. */
    suspend fun lock(token: String, device: String)
}

/** [at]: when the server deletes the account, null = no deletion requested. */
data class DeletionNews(val at: Instant?)

/** Starts and stops the hourly backup (WorkManager); replaced in tests. */
interface CloudSchedule {
    /** Starts it; already running, it keeps its rhythm. */
    fun start()

    fun stop()
}

/** The notifications of the automatic backup (A-10, A-13); replaced in tests. */
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

    /** The account is to be deleted at [at] (backend B1); a request from elsewhere shows here. */
    fun deletionPending(at: Instant)

    /** No deletion is requested anymore (cancelled, here or on another device). */
    fun deletionGone()
}

/** [CloudServer] over Connect ([Verbindung], from the backend's `verbindung`). */
class ConnectCloudServer(private val verbindung: Verbindung, key: Geraeteschluessel, sessions: SitzungsAblage) : CloudServer {
    // Set when a sign-in or renewal stored a session: then `Anmeldung.loeschenAb` holds the
    // server's answer. Before that it is null without meaning "no deletion".
    private val heard = AtomicBoolean(false)
    private val anmeldung = Anmeldung(
        verbindung,
        key,
        object : SitzungsAblage by sessions {
            override suspend fun speichern(sitzung: Sitzung) {
                sessions.speichern(sitzung)
                heard.set(true)
            }
        },
    )

    override suspend fun signIn(idToken: suspend (nonce: String) -> String): Angemeldet {
        val attempt = anmeldung.versuch()
        return anmeldung.anmelden(attempt, idToken(attempt.nonce))
    }
    override suspend fun session(): Sitzung = anmeldung.sitzung()
    override suspend fun renew(): Sitzung = anmeldung.erneuern()
    override suspend fun <T> withSession(block: suspend (Sitzung) -> T): T = anmeldung.mitSitzung(block)
    override fun keyBackups(): BackupAblage = ServerBackupAblage(verbindung) { anmeldung.sitzung().token }
    override fun unlocking(): GeraeteFreischaltung = ServerGeraete(verbindung) { anmeldung.sitzung().token }
    override fun dataBackups(token: String): SicherungsAblage = ServerSicherungsAblage(verbindung, token)
    override fun diagnosis(token: String): DiagnoseAblage = ServerDiagnoseAblage(verbindung, token)
    override fun deletion(token: String): KontoLoeschenAmServer = ServerKontoLoeschen(verbindung, token)
    override fun deletionNews(): DeletionNews? = if (heard.get()) DeletionNews(anmeldung.loeschenAb) else null
    override suspend fun lock(token: String, device: String) = ServerGeraete(verbindung, token).sperren(device)
}
