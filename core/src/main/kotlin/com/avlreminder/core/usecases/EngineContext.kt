package com.avlreminder.core.usecases

import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.ports.GeofencePort
import com.avlreminder.core.ports.LocationPort
import com.avlreminder.core.ports.PreNoticePort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.AlertSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** What the parts of [ReminderEngine] share. */
internal class EngineContext(
    val repo: ReminderRepository,
    val alarms: AlarmPort,
    val clock: Clock,
    val device: DeviceStatePort,
    val settings: () -> AlertSettings,
    val geofences: GeofencePort,
    val location: LocationPort,
    val preNotices: PreNoticePort,
) {
    val zone: ZoneId get() = clock.zone
    fun now(): Instant = clock.instant()

    /** Rings found by the running command; they ring once it is stored. */
    val pending = mutableListOf<Ring>()
}
