package com.avlreminder

import com.avlreminder.alarm.SessionService
import com.avlreminder.alarm.SessionNote
import androidx.annotation.VisibleForTesting
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
import com.avlreminder.claude.ClaudeApi
import com.avlreminder.cloud.AndroidCloudNotices
import com.avlreminder.cloud.CloudWiring
import com.avlreminder.restore.DatabaseSwap
import com.avlreminder.restore.RestoreFiles
import com.avlreminder.restore.RestoreFinish
import com.avlreminder.restore.SnapshotWorker
import com.avlreminder.restore.SwapOutcome
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.core.usecases.Ring
import com.avlreminder.core.usecases.RingPort
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

open class AvlApp : Application() {
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
            graph.rebuild("start", overview = true)
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
    /**
     * Moves the engine's time: the glue tests run on the real time and set a fixed time of day
     * (Review-Lauf 4, E15). Always zero in the app.
     */
    @VisibleForTesting @Volatile var shift: Duration = Duration.ZERO

    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now().plus(shift)
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
    /**
     * The engine's clock. Time the app decides by (*Heute*, widget, alarm screen, age of a WLAN
     * scan) comes from here, not from the system clock; log stamps and file names are free
     * (design.md 1, Review-Lauf 5; checked by `ClockRuleTest`).
     */
    val clock: Clock = SystemZoneClock
    val location = AndroidLocation(context)
    val wifi = AndroidWifi(context, clock)

    private val cloudWiring = CloudWiring(context, db, settings, scope, restoreFiles, ::diagnosisHeader)

    /** Cloud backup (design.md 16); nothing touches the network until it is switched on. */
    val cloud get() = cloudWiring.cloud

    /** Restoring from a daily copy and undoing a restore (R-1, W-6); no network. */
    val local get() = cloudWiring.local

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
        repository, AndroidAlarmPort(context), clock, AndroidDeviceState(context),
        settings = { alert },
        geofences = AndroidGeofences(context) { dwell },
        // The WLAN first; only at the latest time a coarse position when it is unclear (7.29 OZ2).
        location = WifiLocation(wifi, clock) { location.isAt(it) },
        preNotices = PreNotices.Port(context),
        // Ringing and its end are effects of the engine (E17); nobody else dismisses an alarm.
        ringer = object : RingPort {
            override fun ring(ring: Ring) = Ringer.ring(context, ring)
            override fun end(occurrenceId: Long) = Ringer.dismiss(context, occurrenceId)
        },
        placeAlarms = AndroidPlaceAlarms(context),
        diag = Diag::log,
        changed = { refresh() },
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
                text = text.trim(), important = important, createdAt = now(), screen = screen,
                reminderId = reminder?.id, reminderTitle = reminder?.title, appVersion = appVersion,
            ),
        )
    }

    /** Runs use cases one at a time; the engine says after each command what to refresh. */
    override suspend fun <T> withEngine(block: suspend (ReminderEngine) -> T): T = mutex.withLock {
        // Read fresh each time: an alarm may start the process before anything else loaded the settings.
        alert = settings.alert.first()
        dwell = settings.dwellMinutes.first()
        block(engine)
    }

    /** After each command (not after reading): widget and the notification of running sessions (E17). */
    private fun refresh() {
        scope.launch { runCatching { AvlWidget().updateAll(context) } }
        scope.launch { runCatching { SessionService.show(context, withEngine { sessions(it) }) } }
    }

    /** The running sessions for their notification (Anf. 7.11), from the alarm view of the engine (E18). */
    private suspend fun sessions(engine: ReminderEngine): List<SessionNote> =
        openOccurrences().filter { it.isRunning }
            .mapNotNull { o -> engine.alarmView(o.id)?.let(SessionNote::of) }
            .sortedBy { it.startedAt }

    // --- Interface for Claude (Anf. 7.26 AG11) ----------------------------------------------

    override suspend fun enabled(): Boolean = debuggable && settings.claudeAccess.first()
    override suspend fun reminders(): List<Reminder> = repository.reminders()
    override suspend fun openOccurrences(): List<Occurrence> = repository.openOccurrences()
    override suspend fun resolvedOccurrences(): List<Occurrence> = repository.resolvedOccurrences()
    override fun now(): Instant = clock.instant()
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

    /**
     * Builds everything derived from the data again (design.md 1, E19): alarms, geofences, checks
     * of the places and pre-notices (`sync`, the engine rings what was missed), the notification of
     * the pause and the times of the overview; widget and session follow the command. One recipe
     * for every trigger: start, reboot/update/time change, geofences lost, settings, app in front.
     * [checkPlaces]: also look whether one is already at a place it waits for (after a reboot).
     * [overview]: plan the overview again – only at start, after system events and with new times;
     * planned at any other moment it would move a pending one of today to tomorrow (Review-Lauf 6).
     */
    suspend fun rebuild(reason: String, checkPlaces: Boolean = false, overview: Boolean = false) {
        val rings = withEngine { it.sync() + if (checkPlaces) it.checkPlaces() else emptyList() }
        if (rings.isNotEmpty()) Diag.log("rebuild ($reason): ${rings.size} alarm(s) ring now")
        AlarmPause.update(context, settings.alert.first().alarmsPausedUntil)
        if (overview) OverviewNotifications.schedule(context, settings.overview.first())
    }

    /**
     * "Alle Alarme pausieren" (PS7) until [until], or on again with null. A ringing alarm stops;
     * [sync] plans the open occurrences for the end of the pause.
     */
    suspend fun setAlarmsPaused(until: Instant?) {
        val wasPaused = settings.alert.first().alarmsPaused(now())
        settings.setAlarmsPaused(until, now())
        Diag.log(if (until == null) "alarms on again" else "alarms paused until $until")
        // What rang stays in sight as a silent notification (Review-Lauf 1).
        if (until != null) AlarmService.ringing.value?.let { AlarmService.quiet(context, it) }
        if (until == null && wasPaused) {
            // On again by hand: what is due rings now (PS7).
            withEngine { it.resume() }
            AlarmPause.update(context, null)
        } else {
            rebuild(if (until == null) "alarms on" else "alarms paused")
        }
    }

    /**
     * "Hier festlegen" for a place (Anf. 7.29 OZ3, OZ6): the current position and the connected
     * WLAN with all its access points. What is not available now stays as it was; a new place
     * needs a position.
     */
    suspend fun setPlaceHere(placeId: Long?, name: String): PlaceSetResult {
        val here = location.current()
        val network = wifi.currentNetwork()
        // What stays as it was decides the engine (E19); then what waits there rings if already there.
        withEngine { it.setPlaceHere(placeId, name, here?.latitude, here?.longitude, network) } ?: return PlaceSetResult.NoPosition
        withEngine { it.checkPlaces() }
        return PlaceSetResult.Saved(position = here != null, ssid = network?.firstNotNullOfOrNull { it.ssid }, wifi = network != null)
    }
}

/** What "Hier festlegen" could take over; the SSID is only shown on the device, never logged. */
sealed interface PlaceSetResult {
    /** No position (and nothing else to update). */
    data object NoPosition : PlaceSetResult
    data class Saved(val position: Boolean, val wifi: Boolean, val ssid: String?) : PlaceSetResult
}
