package com.avlreminder.core.scenarios

import com.avlreminder.core.FakeAlarms
import com.avlreminder.core.FakeGeofences
import com.avlreminder.core.FakeLocation
import com.avlreminder.core.FakePlaceAlarms
import com.avlreminder.core.FakeWifi
import com.avlreminder.core.FakePreNotices
import com.avlreminder.core.FakeRepository
import com.avlreminder.core.MutableClock
import com.avlreminder.core.ZONE
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.ports.WifiScan
import com.avlreminder.core.rules.WifiLocation
import com.avlreminder.core.usecases.ReminderEngine
import com.avlreminder.core.usecases.SaveResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Plays whole days through the engine as the phone would: the clock moves on, every alarm fires
 * at its time (also the checks of the places), the position and the WLAN change and the user acts
 * at given times. After each step it checks
 * the rules that must always hold ([checkInvariants]).
 *
 * What rang is in [rings] as "Mo 18:00 Training FULLSCREEN" lines, easy to compare in a test.
 * [repo] is the fake one for scenarios, or the app's Room repository for Kevin's real data.
 */
class Simulation<R : ReminderRepository>(start: Instant, val repo: R, var settings: AlertSettings = AlertSettings()) {
    val clock = MutableClock(start)
    val alarms = FakeAlarms()
    val geofences = FakeGeofences()
    /** The coarse position, only asked at the latest time when the WLAN is unclear. */
    val location = FakeLocation()
    val wifi = FakeWifi()
    val placeAlarms = FakePlaceAlarms()
    val rings = mutableListOf<String>()
    /** What the engine wrote to the diagnostic log. */
    val diag = mutableListOf<String>()
    val preNotices = FakePreNotices()
    val engine = ReminderEngine(
        repo, alarms, clock, settings = { settings }, geofences = geofences,
        location = WifiLocation(wifi, clock) { location.checks++; location.here },
        preNotices = preNotices, ringer = { rings += line(clock.now, it.reminder.title, it.level) },
        placeAlarms = placeAlarms, diag = { diag += it },
    )

    suspend fun create(reminder: Reminder): Long = when (val r = engine.save(reminder)) {
        is SaveResult.Saved -> r.id
        is SaveResult.Invalid -> error("invalid: ${r.problems}")
    }.also { checkInvariants() }

    suspend fun place(place: Place): Long = engine.savePlace(place)

    suspend fun open(reminderId: Long): Occurrence = checkNotNull(repo.openOccurrence(reminderId)) { "no open occurrence of $reminderId" }

    /**
     * Lets time pass until [until]: every alarm planned up to then fires at its time, in order,
     * the alarms of the occurrences and the checks of the places.
     */
    suspend fun runUntil(until: Instant) {
        require(until >= clock.now) { "time only moves forward" }
        repeat(MAX_ALARMS) {
            val occurrence = alarms.set.entries.minByOrNull { it.value.first }?.let { it.key to it.value.first }
            val place = placeAlarms.set.entries.minByOrNull { it.value }?.let { it.key to it.value }
            val placeFirst = place != null && (occurrence == null || place.second < occurrence.second)
            val (id, next) = (if (placeFirst) place else occurrence) ?: return finish(until)
            if (next > until) return finish(until)
            clock.now = maxOf(clock.now, next)
            if (placeFirst) {
                placeChecks += next
                engine.onPlaceCheck(id)
                check(placeAlarms.set[id] != next) { "check of place $id at $next is set again to the same time" }
            } else {
                engine.onAlarm(id)
                check(alarms.set[id]?.first != next) { "alarm of occurrence $id fired at $next and is set again to the same time" }
            }
            checkInvariants()
        }
        error("more than $MAX_ALARMS alarms before $until")
    }

    /** When the places were checked. */
    val placeChecks = mutableListOf<Instant>()

    /**
     * Connects to the WLAN [bssid] at [at] (null: disconnects); Android reports a connection, so
     * the engine hears of it. [reported] false: Android does not report it (OZ2), only a check finds it.
     */
    suspend fun wifi(bssid: String?, at: Instant, reported: Boolean = true) {
        runUntil(at)
        wifi.connected = bssid
        if (bssid != null && reported) engine.onWifi(bssid)
        checkInvariants()
    }

    /** Android's WLAN scan at [at] saw [bssids]. */
    suspend fun scan(bssids: Set<String>, at: Instant) {
        runUntil(at)
        wifi.scan = WifiScan(bssids, at)
    }

    private fun finish(until: Instant) {
        clock.now = until
    }

    /** The geofence reports the arrival at [placeId] at [at]; from then on the phone is there. */
    suspend fun arrive(placeId: Long, at: Instant) {
        runUntil(at)
        location.here = true
        engine.onArrival(placeId)
        checkInvariants()
    }

    /** Leaves the place at [at]: no event, the engine only learns it when it asks for the position. */
    suspend fun leave(at: Instant) {
        runUntil(at)
        location.here = false
    }

    /** The user acts on the open occurrence of [reminderId] at [at]; the action must be accepted. */
    suspend fun act(reminderId: Long, at: Instant, action: (Occurrence) -> Action): Occurrence {
        runUntil(at)
        val o = open(reminderId)
        val result = engine.act(o.id, action(o))
        check(result is TransitionResult.Ok) { "rejected at $at: $result" }
        checkInvariants()
        return result.occurrence
    }

    /** The phone was off: no alarm fires until [until], then the app starts and syncs. */
    suspend fun phoneOff(until: Instant) {
        clock.now = until
        engine.sync()
        checkInvariants()
    }

    /**
     * Rules that hold after every step, whatever the scenario:
     * - each active reminder has exactly one open occurrence, a paused one none with an alarm;
     * - an open occurrence has an alarm, except one that only waits for an arrival;
     * - closed occurrences have no alarm and no notice before;
     * - nothing rings loud before [AlertSettings.loudFrom].
     */
    suspend fun checkInvariants() {
        val open = repo.openOccurrences().groupBy { it.reminderId }
        for (r in repo.reminders()) {
            val mine = open[r.id].orEmpty()
            check(mine.size <= 1) { "${r.title}: ${mine.size} open occurrences" }
            if (!r.active) {
                mine.forEach { check(it.id !in alarms.set) { "${r.title} is paused but has an alarm" } }
                continue
            }
            check(mine.size == 1 || r.schedule is Schedule.Once || r.schedule is Schedule.Deadline) { "${r.title}: no open occurrence" }
            val o = mine.singleOrNull() ?: continue
            val waits = r.schedule == Schedule.OnArrival && o.state == OccurrenceState.PLANNED
            check(waits || o.id in alarms.set) { "${r.title}: open occurrence ${o.id} (${o.state}) has no alarm" }
        }
        for (id in alarms.set.keys) check(repo.occurrence(id)?.isOpen == true) { "occurrence $id is closed or gone but has an alarm" }
        for (id in preNotices.set.keys) check(repo.occurrence(id)?.isOpen == true) { "occurrence $id is closed or gone but has a notice before" }
        rings.lastOrNull()?.let { last ->
            val loud = last.endsWith(AlarmLevel.FULLSCREEN_LOUD.name)
            val time = LocalTime.parse(last.substring(3, 8))
            check(!loud || settings.weekend != null || time >= settings.loudFrom) { "loud before ${settings.loudFrom}: $last" }
        }
    }

    companion object {
        private const val MAX_ALARMS = 50_000
        private val FORMAT = DateTimeFormatter.ofPattern("EE HH:mm", java.util.Locale.GERMAN)

        fun line(at: Instant, title: String, level: AlarmLevel) =
            "${LocalDateTime.ofInstant(at, ZONE).format(FORMAT).replace(".", "")} $title $level"
    }
}

/** A simulation on the fake repository, for scenarios told in a test. */
fun Simulation(start: Instant, settings: AlertSettings = AlertSettings()): Simulation<FakeRepository> =
    Simulation(start, FakeRepository(), settings)
