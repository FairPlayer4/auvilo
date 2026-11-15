package com.avlreminder.cloud

import android.content.Context
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.Settings
import com.avlreminder.restore.DatabaseRestore
import com.avlreminder.restore.LocalRestore
import com.avlreminder.restore.RestoreFiles
import com.avlreminder.restore.Snapshots
import com.avlreminder.system.Diag
import com.avlreminder.verbindung.Verbindung
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * The cloud backup (design.md 16) and the restore from a daily copy (R-1, W-6), wired for the
 * app; `AppGraph` only holds them (Review-Lauf 5, E19). Nothing touches the network until the
 * backup is switched on. [header]: the first line of a diagnosis.
 */
class CloudWiring(
    context: Context,
    db: AvlDatabase,
    settings: Settings,
    scope: CoroutineScope,
    restoreFiles: RestoreFiles,
    header: () -> Map<String, Any>,
) {
    private val store = CloudStore(context)
    private val log = CloudLog(File(context.filesDir, "cloud-diag"), header = header)
    private val restoreTarget = DatabaseRestore(context, db, settings, restoreFiles)
    private val snapshots = Snapshots(File(context.filesDir, "backup"), Snapshots.vacuumInto(db))
    private val connection by lazy { Verbindung(CloudConfig.SERVER_URL) }

    val cloud = CloudBackup(
        store,
        server = { ConnectCloudServer(connection, KeystoreDeviceKey(), store) },
        keys = { account -> KeyFile(File(context.filesDir, "backend"), KeystoreSealer(), account) },
        google = CredentialManagerSignIn(),
        passwords = CredentialManagerSafe(),
        screenLock = SystemScreenLock(),
        wipe = {
            File(context.filesDir, "backend").deleteRecursively()
            deleteBackendKeystoreEntries()
        },
        scope = scope,
        backupState = { account -> BackupStateFile(File(context.filesDir, "backend"), account) },
        source = AppBackupSource(db, settings, context.cacheDir),
        events = log,
        schedule = WorkManagerSchedule(context, Diag::log),
        notices = AndroidCloudNotices(context),
        restore = restoreTarget,
        reminders = { db.dao().reminderCount() },
        log = Diag::log,
        configured = CloudConfig.configured(),
    )

    /** Restoring from a daily copy and undoing a restore (R-1, W-6); no network. */
    val local = LocalRestore(restoreTarget, restoreFiles, snapshots, settings::backup, scope, log, Diag::log)
}
