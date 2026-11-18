package com.avlreminder.cloud

import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.avlreminder.protokoll.AeltereSicherung
import com.avlreminder.protokoll.DiagnoseNichtFreigeschaltet
import com.avlreminder.protokoll.DiagnoseTageslimit
import com.avlreminder.protokoll.DiagnoseZuGross
import com.avlreminder.protokoll.EinrichtungAbgebrochen
import com.avlreminder.protokoll.EntschluesselnFehlgeschlagen
import com.avlreminder.protokoll.GeraetNichtFreigeschaltet
import com.avlreminder.protokoll.LoeschungLaeuft
import com.avlreminder.protokoll.NeueresSchema
import com.avlreminder.protokoll.SicherungTageslimit
import com.avlreminder.protokoll.SicherungZuGross
import com.avlreminder.protokoll.VersionVeraltet
import com.avlreminder.restore.DamagedDatabase
import com.avlreminder.restore.NewerDatabase
import com.avlreminder.restore.RestoreBusy
import com.avlreminder.verbindung.AnmeldungAbgelehnt
import com.avlreminder.verbindung.GeraetGesperrt
import com.avlreminder.verbindung.KontoNichtFreigegeben
import com.avlreminder.verbindung.NichtAngemeldet
import com.avlreminder.verbindung.Serverfehler
import com.avlreminder.verbindung.SitzungAbgelehnt
import java.io.IOException

/**
 * What the cloud backup makes of an error: a text for the person ([describe]), a line for the
 * diag log ([logDetail]) and the fields of [CloudEvent.FAILED] ([failure]). One place, so the three
 * split errors the same way.
 */
object CloudErrors {
    /** The fields of a [CloudEvent.FAILED]: only the step and the kind, no message. */
    fun failure(step: CloudStep, e: Throwable): Map<String, Any> = buildMap {
        put("schritt", step)
        put("art", FailureKind.of(e))
        if (e is Serverfehler) {
            put("code", e.code)
            put("vom_dienst", e.vomDienst)
        }
    }

    /**
     * For the diag log: the kind of error and what Google or the server said about it. The
     * cause of a failed Google sign-in (wrong SHA-1, client not set up) is only in
     * [GetCredentialException.errorMessage]. No tokens, keys or contents.
     */
    fun logDetail(e: Throwable): String = when (e) {
        is GetCredentialException -> "${e::class.simpleName} ${e.type}: ${e.errorMessage}"
        is CreateCredentialException -> "${e::class.simpleName} ${e.type}: ${e.errorMessage}"
        is Serverfehler -> "${e::class.simpleName} ${e.code.codeName}, vomDienst=${e.vomDienst}"
        else -> e::class.simpleName.orEmpty()
    }

    /** A short text for the person; null = nothing to show (cancelled at Google). */
    fun describe(e: Throwable): String? = when (e) {
        is GetCredentialCancellationException -> null
        is NoCredentialException -> "Kein Google-Konto gefunden. Im Handy ein Google-Konto einrichten und erneut versuchen."
        is GetCredentialException -> "Anmeldung bei Google gescheitert (${e.type})."
        is AnmeldungAbgelehnt -> "Der Server hat die Anmeldung abgelehnt. Bitte neu anmelden."
        // KRITISCH: The identifier is what the operator needs to let the account in (backend ID7);
        // without it the person could not ask for access.
        is KontoNichtFreigegeben -> notAllowed(e.kennung)
        // Renewed once already (CloudServer.withSession) and rejected again.
        is SitzungAbgelehnt -> "Der Server hat die Sitzung abgelehnt. Später erneut versuchen oder neu anmelden."
        is GeraetGesperrt -> "Dieses Gerät ist beim Server gesperrt."
        is NichtAngemeldet -> "Nicht angemeldet. Bitte mit Google anmelden."
        is GeraetNichtFreigeschaltet -> "Dieses Gerät ist noch nicht freigeschaltet."
        is LoeschungLaeuft -> "Die Löschung des Kontos ist schon fällig, abbrechen geht nicht mehr."
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

    /**
     * The server lets in only the Google accounts on its list (backend ID7): the cloud backup is
     * by invitation (Anf. 7.28 PS16). The identifier is a pseudonym of the Google account (no
     * address, no name), so it may be shown and passed on.
     */
    fun notAllowed(kennung: String?): String =
        "Das Cloud-Backup gibt es nur auf Einladung; dieses Google-Konto ist noch nicht freigegeben. " +
            "Die App funktioniert ohne Cloud-Backup genauso." +
            (kennung?.let { " Zum Freischalten diese Kennung schicken: $it" } ?: "")
}

/** The kind of error, for [CloudEvent.FAILED]; the same split as [CloudErrors.describe]. */
enum class FailureKind {
    GOOGLE, REJECTED, NOT_ALLOWED, LOCKED, SIGNED_OUT, NOT_UNLOCKED, SETUP_CANCELLED, OTHER_ACCOUNT, TOO_LARGE, DAILY_LIMIT,
    OLDER_BACKUP, CHANGED_MEANWHILE, NEWER_SCHEMA, DIAGNOSIS_NOT_ALLOWED, DELETION_DUE, SERVER, NETWORK, DAMAGED, BUSY, OTHER;

    companion object {
        fun of(e: Throwable) = when (e) {
            is GetCredentialException, is CreateCredentialException -> GOOGLE
            is AnmeldungAbgelehnt, is SitzungAbgelehnt -> REJECTED
            is KontoNichtFreigegeben -> NOT_ALLOWED
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
            is LoeschungLaeuft -> DELETION_DUE
            is Serverfehler -> SERVER
            is IOException -> NETWORK
            else -> OTHER
        }
    }
}
