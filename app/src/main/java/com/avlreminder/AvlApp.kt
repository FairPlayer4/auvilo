package com.avlreminder

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
import com.avlreminder.verbindung.Verbindung
import java.io.File
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.data.toModel
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.RoomReminderRepository
import com.avlreminder.data.Settings
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.location.AndroidGeofences
import com.avlreminder.system.Diag
import com.avlreminder.location.AndroidLocation
import com.avlreminder.location.WifiProbe
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
        AndroidCloudNotices.createChannel(this)
        // WLAN device test before 7.29, debug build only (design.md *Entwurf 7.29*).
        if (graph.debuggable) runCatching { WifiProbe.start(this) }
        // Alarms may have been lost (reboot, force-stop, update): rebuild them from the database.
        started = graph.scope.launch {
            graph.syncAndRing()
            graph.scheduleOverview()
            graph.cloud.load().join()
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
    val db = AvlDatabase.create(context)
    val repository = RoomReminderRepository(db)
    val settings = Settings(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var alert = AlertSettings()
    @Volatile private var dwell = Settings.DEFAULT_DWELL
    val location = AndroidLocation(context)

    /** Cloud backup (backup-entwurf.md); nothing touches the network until it is switched on. */
    private val cloudStore = CloudStore(context)
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
        events = CloudLog(File(context.filesDir, "cloud-diag"), header = ::diagnosisHeader),
        schedule = WorkManagerSchedule(context, Diag::log),
        notices = AndroidCloudNotices(context),
        log = Diag::log,
    )

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
        location = location,
        preNotices = PreNotices.Port(context),
        ringer = { Ringer.ring(context, it) },
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
        return result
    }

    // --- Interface for Claude (Anf. 7.26 AG11) ----------------------------------------------

    override suspend fun enabled(): Boolean = debuggable && settings.claudeAccess.first()
    override suspend fun reminders(): List<Reminder> = repository.reminders()
    override suspend fun openOccurrences(): List<Occurrence> = db.dao().openOccurrences().map { it.toModel() }
    override suspend fun resolvedOccurrences(): List<Occurrence> = db.dao().observeResolved().first().map { it.toModel() }
    override fun now(): Instant = Instant.now()
    override val zone: ZoneId get() = ZoneId.systemDefault()

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

    /** Sets "Zuhause" to the current position; false if there is no position. */
    suspend fun setHomeHere(): Boolean {
        val here = location.current() ?: return false
        withEngine { e ->
            val home = e.places().firstOrNull()
            e.savePlace(Place(id = home?.id ?: 0, name = "Zuhause", lat = here.latitude, lon = here.longitude))
        }
        checkPlacesAndRing()
        return true
    }
}
