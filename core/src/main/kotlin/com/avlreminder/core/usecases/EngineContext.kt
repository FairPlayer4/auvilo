package com.avlreminder.core.usecases

import com.avlreminder.core.model.Place
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.ports.DiagPort
import com.avlreminder.core.ports.GeofencePort
import com.avlreminder.core.ports.PlaceAlarmPort
import com.avlreminder.core.ports.PlaceCheck
import com.avlreminder.core.ports.PreNoticePort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.AlertSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** What the parts of [ReminderEngine] share. */
internal class EngineContext(
    val repo: ReminderRepository,
    alarms: AlarmPort,
    val clock: Clock,
    val device: DeviceStatePort,
    val settings: () -> AlertSettings,
    val geofences: GeofencePort,
    preNotices: PreNoticePort,
    placeAlarms: PlaceAlarmPort,
    private val diag: DiagPort,
    private val ringer: RingPort,
) {
    val zone: ZoneId get() = clock.zone
    fun now(): Instant = clock.instant()

    /** Rings found by the running command; they ring once it is stored. */
    val pending = mutableListOf<Ring>()

    /** Occurrences the running command ended (E17); they stop ringing once it is stored. */
    private val ended = linkedSetOf<Long>()

    /** [occurrenceId] rings no more once the command is stored ([RingPort.end]). */
    fun end(occurrenceId: Long) {
        if (buffering) ended += occurrenceId else ringer.end(occurrenceId)
    }

    /** Alarm and notice changes of the running command; applied once it is stored, dropped on a rollback. */
    private val effects = mutableListOf<() -> Unit>()
    private var buffering = false

    private fun effect(block: () -> Unit) {
        if (buffering) effects += block else block()
    }

    val alarms: AlarmPort = object : AlarmPort {
        override fun set(occurrenceId: Long, at: Instant, kind: AlarmKind) = effect { alarms.set(occurrenceId, at, kind) }
        override fun cancel(occurrenceId: Long) = effect { alarms.cancel(occurrenceId) }
    }

    val preNotices: PreNoticePort = object : PreNoticePort {
        override fun set(occurrenceId: Long, at: Instant) = effect { preNotices.set(occurrenceId, at) }
        override fun cancel(occurrenceId: Long) = effect { preNotices.cancel(occurrenceId) }
    }

    val placeAlarms: PlaceAlarmPort = object : PlaceAlarmPort {
        override fun set(placeId: Long, at: Instant, exact: Boolean) = effect { placeAlarms.set(placeId, at, exact) }
        override fun cancel(placeId: Long) = effect { placeAlarms.cancel(placeId) }
    }

    /** Written once the command is stored, so a command run again does not log twice. */
    fun log(message: String) = effect { diag.log(message) }

    /**
     * Positions found before the running command (E6): a command never waits for a position
     * inside its transaction. One it does not have yet ends the try with [PositionNeeded]; the
     * engine finds it outside and runs the command again. One check per place and command.
     */
    val positions = mutableMapOf<Pair<Long, Boolean>, PlaceCheck>()

    /** [fallback]: may fall back to a coarse position when the WLAN is unclear (only at the latest time). */
    fun check(place: Place, fallback: Boolean): PlaceCheck =
        positions[place.id to fallback] ?: throw PositionNeeded(place, fallback)

    fun begin() {
        buffering = true
        effects.clear()
        pending.clear()
        ended.clear()
    }

    /**
     * The command was stored: its alarm changes take effect, what it ended stops, its rings are
     * handed out. No command ends and rings the same occurrence; a filter for that had no case and
     * is gone (Review-Lauf 6, E24; V1.3c14 U6) – a command that needs it brings its own test.
     */
    fun commit(): List<Ring> {
        buffering = false
        effects.forEach { it() }
        effects.clear()
        val rings = pending.toList().also { pending.clear() }
        ended.forEach(ringer::end)
        ended.clear()
        return rings
    }

    fun rollback() {
        buffering = false
        effects.clear()
        pending.clear()
        ended.clear()
    }
}

/** A command needs the position at [place]; thrown inside the transaction, so it rolls back. */
internal class PositionNeeded(val place: Place, val fallback: Boolean) : RuntimeException("position needed for place ${place.id}") {
    override fun fillInStackTrace(): Throwable = this
}
