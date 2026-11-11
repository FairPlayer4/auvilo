package com.avlreminder

import com.avlreminder.alarm.SessionService
import com.avlreminder.alarm.SessionNote
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.glance.appwidget.updateAll
import com.avlreminder.alarm.AlarmPause
import com.avlreminder.alarm.AlarmService
import com.avlreminder.alarm.AndroidAlarmPort
import com.avlreminder.alarm.Notifications
import com.avlreminder.alarm.OverviewNotifications
import com.avlreminder.alarm.PreNotices
import com.avlreminder.alarm.Ringer
import com.avlreminder.core.model.Place
import com.avlreminder.claude.ClaudeApi
import com.avlreminder.cloud.AndroidCloudNotices
import com.avlreminder.cloud.AppBackupSource
import com.avlreminder.cloud.BackupStateFile
import com.avlreminder.cloud.CloudBackup
import com.avlreminder.cloud.CloudLog
import com.avlreminder.cloud.CloudConfig
import com.avlreminder.cloud.CloudStore
import com.avlreminder.cloud.ConnectCloudServer
import com.avlreminder.cloud.CredentialManagerSafe
import com.avlreminder.cloud.CredentialManagerSignIn
import com.avlreminder.cloud.KeyFile
import com.avlreminder.cloud.KeystoreDeviceKey
import com.avlreminder.cloud.KeystoreSealer
import com.avlreminder.cloud.WorkManagerSchedule
import com.avlreminder.cloud.deleteBackendKeystoreEntries
import com.avlreminder.restore.DatabaseRestore
import com.avlreminder.restore.DatabaseSwap
import com.avlreminder.restore.LocalRestore
import com.avlreminder.restore.RestoreFiles
import com.avlreminder.restore.RestoreFinish
import com.avlreminder.restore.SnapshotWorker
import com.avlreminder.restore.Snapshots
import com.avlreminder.restore.SwapOutcome
import com.avlreminder.verbindung.Verbindung
import java.io.File
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.data.toModel
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.core.usecases.Today
import com.avlreminder.core.usecases.Overview
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.RoomReminderRepository
import com.avlreminder.data.Settings
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.location.AndroidGeofences
import com.avlreminder.system.Diag
import com.avlreminder.location.AndroidLocation
import com.avlreminder.location.AndroidPlaceAlarms
import com.avlreminder.location.AndroidWifi
import com.avlreminder.core.rules.WifiLocation
import com.avlreminder.widget.AvlWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AvlApp : Application() {
    lateinit var graph: AppGraph
        private set

    /** The work at start (rebuilding alarms, reading the cloud backup state); tests wait for it. */
    lateinit var started: Job
        private set

    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        Diag.state(this, "app start ${packageManager.getPackageInfo(packageName, 0).versionName}")
        graph = AppGraph(this)
        Notifications.createChannels(this)
        OverviewNotifications.createChannel(this)
        PreNotices.createChannel(this)
        AlarmPause.createChannel(this)
        SessionService.createChannel(this)
        AndroidCloudNotices.createChannel(this)
        // Android reports WLAN connections from now on, also without a running process (7.29 OZ3).
        AndroidWifi.watch(this)
        // Alarms may have been lost (reboot, force-stop, update): rebuild them from the database.
        started = graph.scope.launch {
            // A restore swapped data in at this start: take its settings before anything plans alarms.
            graph.finishRestore()
            graph.syncAndRing()
            graph.scheduleOverview()
            graph.cloud.load().join()
            graph.local.load().join()
            // R-1: the daily copy; the worker takes it on days the app does not start.
            graph.takeSnapshot()
            SnapshotWorker.schedule(this@AvlApp)
        }
    }
}

val Context.graph: AppGraph get() = (applicationContext as AvlApp).graph

/** Clock that always follows the current system time zone. */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}

/** "Do not disturb" in any form; an unknown filter counts as off. */
class AndroidDeviceState(private val context: Context) : DeviceStatePort {
    override fun isDoNotDisturb(): Boolean {
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}

/** Manually wired dependencies; no DI library needed for one user and a handful of classes. */
class AppGraph(private val context: Context) : ClaudeApi.Access {
    /**
     * KRITISCH: A staged restore (W-5) is swapped in here, before anything opens the database;
     * [db] is the only place that opens it.
     */
    private val restoreFiles = RestoreFiles(context.filesDir)
    private val swap: SwapOutcome = runCatching { DatabaseSwap.applyPending(context.getDatabasePath(AvlDatabase.NAME), restoreFiles) }
        .getOrElse { e -> Diag.log("restore: swap crashed (${e::class.simpleName})"); SwapOutcome.None }
    val db = AvlDatabase.create(context)
    val repository = RoomReminderRepository(db)
    val settings = Settings(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var alert = AlertSettings()
    @Volatile private var dwell = Settings.DEFAULT_DWELL
    val location = AndroidLocation(context)
    val wifi = AndroidWifi(context)

    /** Cloud backup (backup-entwurf.md); nothing touches the network until it is switched on. */
    private val cloudStore = CloudStore(context)
    private val cloudLog = CloudLog(File(context.filesDir, "cloud-diag"), header = ::diagnosisHeader)
    private val restoreTarget = DatabaseRestore(context, db, settings, restoreFiles)
    private val snapshots = Snapshots(File(context.filesDir, "backup"), Snapshots.vacuumInto(db))
    private val connection by lazy { Verbindung(CloudConfig.SERVER_URL) }
    val cloud = CloudBackup(
        cloudStore,
        server = { ConnectCloudServer(connection, KeystoreDeviceKey(), cloudStore) },
        keys = { account -> KeyFile(File(context.filesDir, "backend"), KeystoreSealer(), account) },
        google = CredentialManagerSignIn(),
        passwords = CredentialManagerSafe(),
        wipe = {
            File(context.filesDir, "backend").deleteRecursively()
            deleteBackendKeystoreEntries()
        },
        scope = scope,
        backupState = { account -> BackupStateFile(File(context.filesDir, "backend"), account) },
        source = AppBackupSource(db, settings, context.cacheDir),
        events = cloudLog,
        schedule = WorkManagerSchedule(context, Diag::log),
        notices = AndroidCloudNotices(context),
        restore = restoreTarget,
        reminders = { db.dao().reminderCount() },
        log = Diag::log,
    )

    /** Restoring from a daily copy and undoing a restore (R-1, W-6); no network. */
    val local = LocalRestore(restoreTarget, restoreFiles, snapshots, settings::backup, scope, cloudLog, Diag::log)

    /**
     * Ends a restore swapped in at this start (W-5, step 5): takes its settings (without the pause
     * and the interface, `Settings.restore`), then removes the order. Interrupted, the next start
     * does it again.
     */
    suspend fun finishRestore() {
        local.started(swap)
        if (swap !is SwapOutcome.Swapped) return
        RestoreFinish.run(restoreFiles, swap.order, settings::restore, cloud::restoredOnThePhone, Diag::log)
    }

    /** The daily copy of the database (R-1); a failure is only logged. */
    fun takeSnapshot() {
        runCatching { if (local.takeSnapshot()) Diag.log("snapshot taken") }
            .onFailure { Diag.log("snapshot failed (${it::class.simpleName})") }
    }

    /** The first line of a diagnosis (formate.md): which app on which phone, nothing personal. */
    private fun diagnosisHeader(): Map<String, Any> = mapOf(
        "app" to "$appVersion ($appVersionCode)",
        "variante" to if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug" else "release",
        "android" to Build.VERSION.SDK_INT,
        "modell" to Build.MODEL,
        "schema" to AvlDatabase.VERSION,
    )
    private val engine = ReminderEngine(
        repository, AndroidAlarmPort(context), SystemZoneClock, AndroidDeviceState(context),
        settings = { alert },
        geofences = AndroidGeofences(context) { dwell },
        // The WLAN first; only at the latest time a coarse position when it is unclear (7.29 OZ2).
        location = WifiLocation(wifi, SystemZoneClock) { location.isAt(it) },
        preNotices = PreNotices.Port(context),
        ringer = { Ringer.ring(context, it) },
        placeAlarms = AndroidPlaceAlarms(context),
        diag = Diag::log,
    )
    private val mutex = Mutex()

    val appVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    private val appVersionCode: Long by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(0L)
    }

    /** Notes an improvement (Anf. 7.24) with where it came from; stays on the phone. */
    suspend fun addSuggestion(text: String, important: Boolean, screen: String, reminder: Reminder? = null) {
        db.suggestions().insert(
            SuggestionEntity(
                text = text.trim(), important = important, createdAt = Instant.now(), screen = screen,
                reminderId = reminder?.id, reminderTitle = reminder?.title, appVersion = appVersion,
            ),
        )
    }

    /** Runs use cases one at a time and refreshes the widget afterwards. */
    override suspend fun <T> withEngine(block: suspend (ReminderEngine) -> T): T {
        val result = mutex.withLock {
            // Read fresh each time: an alarm may start the process before anything else loaded the settings.
            alert = settings.alert.first()
            dwell = settings.dwellMinutes.first()
            block(engine)
        }
        scope.launch { runCatching { AvlWidget().updateAll(context) } }
        scope.launch { runCatching { SessionService.show(context, sessions()) } }
        return result
    }

    /** The running sessions for their notification (Anf. 7.11). */
    private suspend fun sessions(): List<SessionNote> {
        val running = openOccurrences().filter { it.isRunning }
        if (running.isEmpty()) return emptyList()
        return running.mapNotNull { o ->
            val r = repository.reminder(o.reminderId) ?: return@mapNotNull null
            SessionNote(
                occurrenceId = o.id, title = r.title, startedAt = o.startedAt ?: return@mapNotNull null,
                quickDone = !r.proof.needsEvidence,
                minimum = r.minimum != null || r.session?.minimumMinutes != null,
            )
        }.sortedBy { it.startedAt }
    }

    // --- Interface for Claude (Anf. 7.26 AG11) ----------------------------------------------

    override suspend fun enabled(): Boolean = debuggable && settings.claudeAccess.first()
    override suspend fun reminders(): List<Reminder> = repository.reminders()
    override suspend fun openOccurrences(): List<Occurrence> = db.dao().openOccurrences().map { it.toModel() }
    override suspend fun resolvedOccurrences(): List<Occurrence> = db.dao().observeResolved().first().map { it.toModel() }
    override fun now(): Instant = Instant.now()
    override val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * *Heute* from the database, for everything outside the screens (overview notification,
     * widget, interface for Claude); the screens build it from the same queries as flows.
     */
    suspend fun today(now: Instant = now()): Today =
        Overview.today(reminders(), openOccurrences() + resolvedOccurrences(), now, zone)

    /** The interface for Claude exists only in the debug build (sideload). */
    val debuggable: Boolean get() = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    suspend fun setClaudeAccess(on: Boolean) {
        settings.setClaudeAccess(on)
        Diag.log("claude access ${if (on) "on" else "off"}")
    }

    /** Rebuilds all alarms; the engine rings what was missed. */
    suspend fun syncAndRing() {
        val rings = withEngine { it.sync() }
        if (rings.isNotEmpty()) Diag.log("sync: ${rings.size} missed alarm(s) ring now")
        AlarmPause.update(context, settings.alert.first().alarmsPausedUntil)
    }

    /**
     * "Alle Alarme pausieren" (PS7) until [until], or on again with null. A ringing alarm stops;
     * [sync] plans the open occurrences for the end of the pause.
     */
    suspend fun setAlarmsPaused(until: Instant?) {
        val wasPaused = settings.alert.first().alarmsPaused(SystemZoneClock.instant())
        settings.setAlarmsPaused(until, SystemZoneClock.instant())
        Diag.log(if (until == null) "alarms on again" else "alarms paused until $until")
        if (until != null) AlarmService.ringing.value?.let { AlarmService.stop(context, it) }
        if (until == null && wasPaused) {
            // On again by hand: what is due rings now (PS7).
            withEngine { it.resume() }
            AlarmPause.update(context, null)
        } else {
            syncAndRing()
        }
    }

    /** Sets the morning and evening overview from the settings. */
    suspend fun scheduleOverview() = OverviewNotifications.schedule(context, settings.overview.first())

    /** Rings reminders that wait for an arrival when already there (geofences report only entering). */
    suspend fun checkPlacesAndRing() {
        withEngine { it.checkPlaces() }
    }

    /**
     * "Hier festlegen" for a place (Anf. 7.29 OZ3, OZ6): the current position and the connected
     * WLAN with all its access points. What is not available now stays as it was; a new place
     * needs a position.
     */
    suspend fun setPlaceHere(placeId: Long?, name: String): PlaceSetResult {
        val here = location.current()
        val network = wifi.currentNetwork()
        val saved = withEngine { e ->
            val old = e.places().firstOrNull { it.id == placeId }
            val lat = here?.latitude ?: old?.lat
            val lon = here?.longitude ?: old?.lon
            if (lat == null || lon == null || (here == null && network == null)) return@withEngine false
            e.savePlace(
                Place(
                    id = old?.id ?: 0, name = name.trim().ifEmpty { old?.name ?: "Ort" }, lat = lat, lon = lon,
                    radiusMeters = old?.radiusMeters ?: 150, wifis = network ?: old?.wifis.orEmpty(),
                ),
            )
            true
        }
        if (!saved) return PlaceSetResult.NoPosition
        checkPlacesAndRing()
        return PlaceSetResult.Saved(position = here != null, ssid = network?.firstNotNullOfOrNull { it.ssid }, wifi = network != null)
    }
}

/** What "Hier festlegen" could take over; the SSID is only shown on the device, never logged. */
sealed interface PlaceSetResult {
    /** No position (and nothing else to update). */
    data object NoPosition : PlaceSetResult
    data class Saved(val position: Boolean, val wifi: Boolean, val ssid: String?) : PlaceSetResult
}
