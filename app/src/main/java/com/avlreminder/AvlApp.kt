package com.avlreminder

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.avlreminder.alarm.AndroidAlarmPort
import com.avlreminder.alarm.Notifications
import com.avlreminder.alarm.Ringer
import com.avlreminder.core.model.Place
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.data.AvlDatabase
import com.avlreminder.data.RoomReminderRepository
import com.avlreminder.data.Settings
import com.avlreminder.location.AndroidGeofences
import com.avlreminder.location.AndroidLocation
import com.avlreminder.widget.AvlWidget
import kotlinx.coroutines.CoroutineScope
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

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        // Alarms may have been lost (reboot, force-stop, update): rebuild them from the database.
        graph.scope.launch { graph.syncAndRing() }
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
class AppGraph(private val context: Context) {
    val db = AvlDatabase.create(context)
    val repository = RoomReminderRepository(db)
    val settings = Settings(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var alert = AlertSettings()
    @Volatile private var dwell = Settings.DEFAULT_DWELL
    val location = AndroidLocation(context)
    private val engine = ReminderEngine(
        repository, AndroidAlarmPort(context), SystemZoneClock, AndroidDeviceState(context),
        settings = { alert },
        geofences = AndroidGeofences(context) { dwell },
        location = location,
    )
    private val mutex = Mutex()

    /** Runs use cases one at a time and refreshes the widget afterwards. */
    suspend fun <T> withEngine(block: suspend (ReminderEngine) -> T): T {
        val result = mutex.withLock {
            // Read fresh each time: an alarm may start the process before anything else loaded the settings.
            alert = settings.alert.first()
            dwell = settings.dwellMinutes.first()
            block(engine)
        }
        scope.launch { runCatching { AvlWidget().updateAll(context) } }
        return result
    }

    /** Rebuilds all alarms and rings what was missed. */
    suspend fun syncAndRing() {
        withEngine { it.sync() }.forEach { Ringer.ring(context, it) }
    }

    /** Rings reminders that wait for an arrival when already there (geofences report only entering). */
    suspend fun checkPlacesAndRing() {
        withEngine { it.checkPlaces() }.forEach { Ringer.ring(context, it) }
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
