package com.avlreminder.core.ports

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.Event
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import java.time.Duration
import java.time.Instant

/** Storage; the database behind it is the single source of truth. */
interface ReminderRepository {
    /** Runs [block] as a whole: all its changes are stored, or none if it fails. */
    suspend fun <T> transaction(block: suspend () -> T): T = block()

    suspend fun reminders(): List<Reminder>
    suspend fun reminder(id: Long): Reminder?
    /** Inserts when id is 0, otherwise updates; also stores the checklist (items get ids). Returns the id. */
    suspend fun saveReminder(reminder: Reminder): Long
    /** Deletes the reminder with its occurrences, events, proofs, prep answers and blockers. */
    suspend fun deleteReminder(id: Long)

    suspend fun occurrence(id: Long): Occurrence?
    suspend fun openOccurrences(): List<Occurrence>
    suspend fun openOccurrence(reminderId: Long): Occurrence?
    /** The most recently resolved occurrence of the reminder, if any. */
    suspend fun lastResolved(reminderId: Long): Occurrence?
    /** The most recent occurrence that was actually done (any [Resolution] of state DONE). */
    suspend fun lastDone(reminderId: Long): Occurrence?
    /** Resolved occurrences of the reminder, newest first. */
    suspend fun recentResolved(reminderId: Long, limit: Int): List<Occurrence>
    suspend fun countResolved(reminderId: Long, resolution: Resolution, from: Instant, until: Instant): Int
    suspend fun saveOccurrence(occurrence: Occurrence): Long
    /** Deletes the occurrence with its events, proofs and checklist progress. */
    suspend fun deleteOccurrence(id: Long)

    suspend fun addEvent(event: Event)
    /** Events of the occurrence, oldest first. */
    suspend fun events(occurrenceId: Long): List<Event>
    suspend fun addProof(proof: Proof)
    /** Text of the newest text proof of the reminder, if any. */
    suspend fun lastProofText(reminderId: Long): String?

    /** All answers ever given, oldest first; see [com.avlreminder.core.rules.Prep.latest]. */
    suspend fun prepAnswers(reminderId: Long): List<PrepAnswer>
    suspend fun addPrepAnswer(answer: PrepAnswer)

    suspend fun places(): List<Place>
    /** Inserts when id is 0, otherwise updates. Returns the id. */
    suspend fun savePlace(place: Place): Long

    /** Checklist items done within the occurrence, in the order they were done. */
    suspend fun itemsDone(occurrenceId: Long): List<ItemDone>
    /** Checklist items done in any occurrence of the reminder, in the order they were done. */
    suspend fun itemsDoneOfReminder(reminderId: Long): List<ItemDone>
    suspend fun addItemDone(done: ItemDone)
    /** Removes the checklist items done in the occurrence at or after [from]. */
    suspend fun deleteItemsDone(occurrenceId: Long, from: Instant)

    /** Blockers of the reminder, oldest first. */
    suspend fun blockers(reminderId: Long): List<Blocker>
    suspend fun blocker(id: Long): Blocker?
    /** Inserts when id is 0, otherwise updates. Returns the id. */
    suspend fun saveBlocker(blocker: Blocker): Long
}

/** State of the phone that decides how loud an alarm may be. */
fun interface DeviceStatePort {
    /** "Do not disturb" in any form (priority only, alarms only, total silence). */
    fun isDoNotDisturb(): Boolean
}

enum class AlarmKind {
    /** Shown by the system as "next alarm"; most reliable (0 s delay in the spike). */
    ALARM_CLOCK,
    /** Exact but not shown as alarm clock; for relaxed reminders. */
    EXACT,
}

/** Geofences for arrival triggers; like alarms derived from the database on every sync. */
fun interface GeofencePort {
    /** Watches exactly these places; empty stops watching. */
    fun watch(places: List<Place>)
}

/**
 * Is the phone at [Place] right now (Anf. 7.29 OZ2, OZ3)? First the WLAN; with [fallback] (only
 * at the latest time) an unclear WLAN falls back to a coarse position.
 */
fun interface LocationPort {
    suspend fun check(place: Place, fallback: Boolean): PlaceCheck
}

enum class PlaceResult { AT, AWAY, UNKNOWN }

enum class PlaceSource {
    /** Connected to a WLAN of the place. */
    WIFI_CONNECTED,
    /** Android's last WLAN scan. */
    WIFI_SCAN,
    GEOFENCE,
    /** A coarse position, only at the latest time. */
    FALLBACK,
}

/**
 * One check of a place as a fact for the diagnostic log (OZ8): where the answer came from and how
 * old the scan was. [source] null: nothing could tell (no WLAN, no position).
 */
data class PlaceCheck(val result: PlaceResult, val source: PlaceSource?, val scanAge: Duration? = null)

/** The checks of a place in its arrival window (7.29 Z.3): one alarm per place, not per occurrence. */
interface PlaceAlarmPort {
    fun set(placeId: Long, at: Instant)
    fun cancel(placeId: Long)

    companion object {
        val NONE = object : PlaceAlarmPort {
            override fun set(placeId: Long, at: Instant) = Unit
            override fun cancel(placeId: Long) = Unit
        }
    }
}

/**
 * What Android knows about the WLAN without searching itself (OZ7). [connected]: the BSSID of the
 * connected access point, null if none or Android does not tell.
 */
interface WifiPort {
    suspend fun connected(): String?
    suspend fun lastScan(): WifiScan?
}

/** Android's last WLAN scan: the access points seen and when. */
data class WifiScan(val bssids: Set<String>, val at: Instant)

/** Why the engine decided what it did (OZ8), for the diagnostic log; no names, no positions. */
fun interface DiagPort {
    fun log(message: String)
}

/** The silent notice before a reminder (T6); like alarms derived from the database. */
interface PreNoticePort {
    fun set(occurrenceId: Long, at: Instant)
    fun cancel(occurrenceId: Long)

    companion object {
        val NONE = object : PreNoticePort {
            override fun set(occurrenceId: Long, at: Instant) {}
            override fun cancel(occurrenceId: Long) {}
        }
    }
}

/** Derived from the database at any time; see SyncSchedule. */
interface AlarmPort {
    fun set(occurrenceId: Long, at: Instant, kind: AlarmKind)
    fun cancel(occurrenceId: Long)
}
