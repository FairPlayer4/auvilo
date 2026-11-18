package com.avlreminder.cloud

import com.avlreminder.protokoll.Eingerichtet
import com.avlreminder.restore.Comparison
import java.time.Instant

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
 * of the last "Sicherung prüfen"; [deletion]: when the server deletes the account (backend B1),
 * null = no deletion requested; [invitation]: the last step failed because the account is not on
 * the server's list, so the person may ask for an invitation (Anf. 7.28 PS16).
 */
data class CloudStatus(
    val phase: CloudPhase,
    val notes: List<String> = emptyList(),
    val error: String? = null,
    val report: BackupReport? = null,
    val deletion: Instant? = null,
    val invitation: Invitation? = null,
)

/** Not let in by the server (backend ID7); [identifier] is what the operator needs, if the server sent it. */
data class Invitation(val identifier: String?)

/**
 * The result of "Sicherung prüfen" (W-1, W-2): the backup made at [created], from this device or
 * another, compared table by table with the data here. [unchanged]: the data here are byte for
 * byte those of the backup; otherwise differences are to be expected.
 */
data class BackupReport(val created: Instant, val fromThisDevice: Boolean, val unchanged: Boolean, val comparison: Comparison)

/** The step a [CloudEvent.FAILED] happened in. */
enum class CloudStep { LOAD, SWITCH, SIGN_IN, SETUP, BACKUP, AUTO_BACKUP, DIAGNOSIS, RESET, CHECK, RESTORE, DELETE_BACKUP, SIGN_OUT, DELETE_ACCOUNT, CANCEL_DELETION }

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
