package com.avlreminder.core.usecases

import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.BlockerOrigin
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.ports.AlarmPort
import com.avlreminder.core.ports.DeviceStatePort
import com.avlreminder.core.ports.DiagPort
import com.avlreminder.core.ports.GeofencePort
import com.avlreminder.core.ports.LocationPort
import com.avlreminder.core.ports.PlaceAlarmPort
import com.avlreminder.core.ports.PlaceCheck
import com.avlreminder.core.ports.PlaceResult
import com.avlreminder.core.ports.PreNoticePort
import com.avlreminder.core.ports.ReminderRepository
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Limits
import com.avlreminder.core.rules.SnoozeHint
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.rules.WeekStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface SaveResult {
    /** [ring]: the reminder is due right away, e.g. a one-off moved to a passed time today. */
    data class Saved(val id: Long, val ring: Ring? = null) : SaveResult
    data class Invalid(val problems: List<String>) : SaveResult
}

/**
 * All use cases, the one way to change reminders and occurrences. Every change goes to the
 * repository first, each command in one transaction; alarms are derived from it, and what is due
 * rings through [ringer] once the command is stored. The work is done by [Scheduler] (time),
 * [AlarmViews] (what applies now) and [Changes] (what the user changes).
 * Not thread-safe: the app calls it from one coroutine scope at a time.
 */
class ReminderEngine(
    private val repo: ReminderRepository,
    alarms: AlarmPort,
    clock: Clock,
    device: DeviceStatePort = DeviceStatePort { false },
    settings: () -> AlertSettings = { AlertSettings() },
    geofences: GeofencePort = GeofencePort {},
    private val location: LocationPort = LocationPort { _, _ -> PlaceCheck(PlaceResult.UNKNOWN, null) },
    preNotices: PreNoticePort = PreNoticePort.NONE,
    private val ringer: RingPort = RingPort {},
    placeAlarms: PlaceAlarmPort = PlaceAlarmPort.NONE,
    diag: DiagPort = DiagPort {},
    /**
     * After each command (not after reading): what the app derives from the data besides the
     * alarms, e.g. widget and the notification of a session (E17).
     */
    private val changed: () -> Unit = {},
) {
    private val c = EngineContext(repo, alarms, clock, device, settings, geofences, preNotices, placeAlarms, diag, ringer)
    private val views = AlarmViews(c)
    private val scheduler = Scheduler(c, views)
    private val changes = Changes(c, views, scheduler)
    private var depth = 0

    /**
     * Runs a command as a whole: stored in one transaction, then its alarm changes take effect and
     * what it found due rings. A command inside another one joins it.
     * No slow work inside the transaction (E6): a position it needs is found outside, the command
     * rolls back and runs again with it. Each place is checked at most once per command.
     */
    private suspend fun <T> command(block: suspend () -> T): T {
        if (depth > 0) return block()
        c.positions.clear()
        while (true) {
            depth++
            c.begin()
            val result = try {
                // The checks of the places follow from what the command left (design.md 6).
                repo.transaction { block().also { scheduler.planChecks() } }
            } catch (e: PositionNeeded) {
                c.rollback()
                depth--
                c.positions[e.place.id to e.fallback] = location.check(e.place, e.fallback)
                continue
            } catch (e: Throwable) {
                c.rollback()
                depth--
                throw e
            }
            depth--
            c.commit().forEach(ringer::ring)
            changed()
            return result
        }
    }

    // --- Reminders -------------------------------------------------------------------------

    /**
     * [prep]: new or changed "Startklar" answers; unchanged ones are not stored again.
     * "Überwindung" needs all four answered (also when an older reminder is edited).
     * Already at the place of a reminder that waits for an arrival, it rings right away.
     */
    suspend fun save(edited: Reminder, prep: List<PrepAnswer> = emptyList()): SaveResult = command { changes.save(edited, prep) }

    /** Also deletes its preparation tasks; deleting a task leaves its blocker open. */
    suspend fun delete(reminderId: Long) = command { changes.delete(reminderId) }

    /**
     * Switching a reminder on also takes it out of the archive. False if nothing changed: unknown,
     * or a session is running (Anf. 7.11 D7).
     */
    suspend fun setActive(reminderId: Long, active: Boolean): Boolean = command { changes.setActive(reminderId, active) }

    /** Archiving pauses the reminder; restoring keeps it paused until it is switched on. False as [setActive]. */
    suspend fun setArchived(reminderId: Long, archived: Boolean): Boolean = command { changes.setArchived(reminderId, archived) }

    // --- Places ----------------------------------------------------------------------------

    suspend fun places(): List<Place> = repo.places()

    /** Sets a place (V1.1b: "Zuhause" from the current position) and watches it if needed. */
    suspend fun savePlace(place: Place): Long = command { changes.savePlace(place) }

    /**
     * "Hier festlegen" with what the phone found here: a position ([lat], [lon]) and/or the
     * connected WLAN ([wifis]); what was not found stays as it was (Changes.setPlaceHere).
     * Null if nothing was saved (a new place without a position).
     */
    suspend fun setPlaceHere(placeId: Long?, name: String, lat: Double?, lon: Double?, wifis: List<PlaceWifi>?): Long? =
        command { changes.setPlaceHere(placeId, name, lat, lon, wifis) }

    /**
     * Arrived at [placeId] (geofence after the dwell time): an occurrence in its window rings after
     * the buffer, one waiting for the arrival too (7.29); before the window it does not count.
     * Without a window today's occurrences ring now. Snoozed or postponed ones keep their time.
     */
    suspend fun onArrival(placeId: Long): List<Ring> = command { scheduler.onArrival(placeId) }

    /** Connected to the WLAN [bssid] (Android reports it): an arrival at each place it belongs to (OZ3). */
    suspend fun onWifi(bssid: String): List<Ring> = command {
        repo.places().filter { it.hasWifi(bssid) }.flatMap { scheduler.onArrival(it.id) }
    }

    /**
     * The check alarm of [placeId] fired: one check for all occurrences that wait there (OZ2).
     * [plannedAt]: the time it was set for (null: unknown).
     */
    suspend fun onPlaceCheck(placeId: Long, plannedAt: Instant?): List<Ring> = command { scheduler.onPlaceCheck(placeId, plannedAt) }

    /**
     * Checks the position for reminders that only wait for an arrival: after a reboot or a new
     * place, where the geofence does not report being inside already.
     */
    suspend fun checkPlaces(): List<Ring> = command { scheduler.checkPlaces() }

    // --- Occurrences -----------------------------------------------------------------------

    /** Applies a user action. On success the next alarm or the next occurrence is planned. */
    suspend fun act(occurrenceId: Long, action: Action): TransitionResult = command { changes.act(occurrenceId, action) }

    /**
     * "Zurücknehmen" (Anf. 7.31): the last done or skipped occurrence of its reminder is open again
     * and rings in 10 minutes. Rejected when it is not the last one, the next one has rung already
     * or it would be past its day (several a day: past the next time).
     */
    suspend fun reopen(occurrenceId: Long): TransitionResult = command { changes.reopen(occurrenceId) }

    /** The proof screen opened: quiet for the reminder's proof window, at most until the end of the day. */
    suspend fun startProof(occurrenceId: Long): TransitionResult = command { changes.startProof(occurrenceId) }

    /** The proof screen was left: after a short grace the escalation goes on. */
    suspend fun abortProof(occurrenceId: Long): TransitionResult = command { changes.abortProof(occurrenceId) }

    /** The reminder's snooze buttons that do not pass the end of the day. */
    fun snoozeOptions(reminder: Reminder, occurrence: Occurrence): List<Int> = views.snoozeOptions(reminder, occurrence)

    /** Current "Startklar" answers, the newest per question. */
    suspend fun prep(reminderId: Long): Map<Int, PrepAnswer> = views.prep(reminderId)

    /**
     * "Ich starte jetzt" (Anf. 7.11 D1): the session runs, the alarm ends; also before it is due
     * (7.20 V2). The checks come through the alarm port; "Noch dran", "Fertig", "Teilschritt" and
     * "Abbruch" are [Action.StillOn], [Action.Complete] and [Action.Abort] through [act].
     */
    suspend fun start(occurrenceId: Long): TransitionResult = act(occurrenceId, Action.Start)

    /** The average duration of the last sessions done (D6); null without any. */
    suspend fun sessionAverage(reminderId: Long): Duration? = views.average(reminderId)

    /** Called when an alarm fires. Returns what rings, or null if nothing (stale alarm). */
    suspend fun onAlarm(occurrenceId: Long): Ring? = command { scheduler.onAlarm(occurrenceId) }

    /**
     * Rebuilds all alarms from the database: on app start, boot, time change and after changes.
     * Detects occurrences whose alarm never fired and returns them, they ring now.
     */
    suspend fun sync(): List<Ring> = command { scheduler.sync() }

    /**
     * The pause was switched off by hand (PS7): what is due rings now, as when the pause ends on
     * its own, not only at the next round.
     */
    suspend fun resume(): List<Ring> = command { scheduler.resume() }

    /** What the alarm screen shows for the occurrence; [early] = "vorab erledigen". Null if it is gone. */
    suspend fun alarmView(occurrenceId: Long, early: Boolean = false): AlarmView? {
        val occurrence = repo.occurrence(occurrenceId) ?: return null
        val reminder = repo.reminder(occurrence.reminderId) ?: return null
        return views.alarmView(reminder, occurrence, early)
    }

    /** Limits that apply to [reminder] right now; with [occurrence] also its checklist progress on [day]. */
    suspend fun limits(reminder: Reminder, occurrence: Occurrence? = null, day: LocalDate = c.now().atZone(c.zone).toLocalDate()): Limits =
        views.limits(reminder, occurrence, day)

    /** Checklist items done in the occurrence. */
    suspend fun itemsDone(occurrenceId: Long): List<ItemDone> = repo.itemsDone(occurrenceId)

    /** The week of [day] for a reminder with a weekly goal; offers counted from [day] on. */
    suspend fun weekStatus(reminder: Reminder, day: LocalDate = c.now().atZone(c.zone).toLocalDate()): WeekStatus? =
        views.weekStatus(reminder, day)

    /** All active reminders with a weekly goal and where they stand this week (overview, O1). */
    suspend fun weekGoals(): List<Pair<Reminder, WeekStatus>> =
        repo.reminders().filter { it.active && it.weeklyGoal != null }.mapNotNull { r -> weekStatus(r)?.let { r to it } }

    // --- Blockers, preparation tasks and the notice before (S5–S8, T6) ------------------------

    suspend fun blockers(reminderId: Long): List<Blocker> = repo.blockers(reminderId)

    /** Adds an open blocker unless the same one is open already; returns its id. */
    suspend fun addBlocker(reminderId: Long, text: String, origin: BlockerOrigin = BlockerOrigin.STARTKLAR): Long =
        command { changes.addBlocker(reminderId, text, origin) }

    /** Solved by hand; a planned task for it is not needed any more. */
    suspend fun solveBlocker(blockerId: Long) = command { changes.solveBlocker(blockerId) }

    /** When to do the task for a blocker: the evening before the reminder is due next. */
    suspend fun suggestTaskTime(reminderId: Long): LocalDateTime = changes.suggestTaskTime(reminderId)

    /** "Als Aufgabe planen": a relaxed one-off at [at]; doing it solves the blocker. */
    suspend fun planTask(blockerId: Long, at: LocalDateTime): SaveResult = command { changes.planTask(blockerId, at) }

    /**
     * The answer to "Was hält dich gerade ab?" (S8); null or blank = skipped. Returns the blocker's
     * id. [planTask]: "Als Aufgabe planen" at [suggestTaskTime], in the same command (E19).
     */
    suspend fun answerAsk(occurrenceId: Long, text: String?, planTask: Boolean = false): Long? = command {
        changes.answerAsk(occurrenceId, text)?.also { blockerId ->
            val reminderId = repo.occurrence(occurrenceId)?.reminderId
            if (planTask && reminderId != null) changes.planTask(blockerId, changes.suggestTaskTime(reminderId))
        }
    }

    /** What the silent notice before the occurrence shows; null if it is no longer planned. */
    suspend fun preNotice(occurrenceId: Long): Digest? = changes.preNotice(occurrenceId)

    // --- Snoozing again and again (NT2) -----------------------------------------------------

    /** Reminders that were snoozed again and again lately, for the card on "Heute". */
    suspend fun snoozeHints(): List<SnoozeHint> = changes.snoozeHints()

    /**
     * The card was answered: move the time, lower the strictness, or "Nein danke" (quiet for
     * [com.avlreminder.core.rules.Snoozing.QUIET_DAYS]). Either way the count starts anew.
     */
    suspend fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice): SaveResult =
        command { changes.answerSnoozeHint(reminderId, choice) }

    /** "Auf morgen" for a deadline; null if it is no deadline or no window before it is left. */
    fun tomorrow(reminder: Reminder, occurrence: Occurrence): Instant? = views.tomorrow(reminder, occurrence)

    /** It got stuck [AlertSettings.askBlockerAfter] times in a row and was not asked yet (S8). */
    suspend fun askBlocker(reminder: Reminder, occurrence: Occurrence): Boolean = views.askBlocker(reminder, occurrence)

    companion object {
        /** A planned occurrence this far past its time without having fired counts as missed alarm. */
        val GRACE: Duration = Duration.ofMinutes(2)
        /** Alarms may fire slightly early. */
        val EARLY_TOLERANCE: Duration = Duration.ofMinutes(1)
    }
}
