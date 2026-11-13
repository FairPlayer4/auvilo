package com.avlreminder.core.usecases

import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.ports.PlaceResult
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.ArrivalDecision
import com.avlreminder.core.rules.ArrivalWindow
import java.time.Duration
import java.time.Instant

/**
 * Places and arrivals for the [Scheduler] (Anf. 7.29, design.md 6): what an arrival or a check of
 * a place means, the checks to plan and the geofences to watch. Firing and planning stay with the
 * scheduler.
 */
internal class Arrivals(private val c: EngineContext, private val views: AlarmViews, private val s: Scheduler) {
    private val repo get() = c.repo
    private val zone get() = c.zone
    private fun now() = c.now()

    /** Arrived at [placeId], reported by the geofence or the WLAN connection. */
    suspend fun onArrival(placeId: Long): List<Ring> =
        waitingAt(placeId).mapNotNull { (r, o) -> arrived(r, o, byCheck = false, plannedCheck = null) }

    /**
     * The check alarm of [placeId] (7.29 OZ2): one check for all occurrences in their window or
     * waiting for the arrival there, from the WLAN alone. Only "there" changes anything.
     * [plannedAt]: the time the check was planned for, null if unknown; the check planned for the
     * start of a window decides F2 even when Doze delivers it late.
     */
    suspend fun onPlaceCheck(placeId: Long, plannedAt: Instant?): List<Ring> {
        val place = repo.places().firstOrNull { it.id == placeId } ?: return emptyList()
        val now = now()
        val waiting = waitingAt(placeId).filter { (r, o) -> ArrivalWindow.waits(r, o, now, views.missedAt(r, o)) }
        if (waiting.isEmpty()) return emptyList()
        val check = c.check(place, fallback = false)
        val late = plannedAt?.let { Duration.between(it, now).toMinutes() }?.takeIf { it > 0 }?.let { " late=${it}min" }.orEmpty()
        c.log("place check place=$placeId ${ArrivalWindow.describe(check)}$late for ${waiting.joinToString { "r${it.first.id}" }}")
        if (check.result != PlaceResult.AT) return emptyList()
        return waiting.mapNotNull { (r, o) -> arrived(r, o, byCheck = true, plannedCheck = plannedAt) }
    }

    suspend fun checkPlaces(): List<Ring> {
        val places = repo.places().associateBy { it.id }
        return repo.reminders().filter { it.active && it.arrival != null }.mapNotNull { r ->
            val place = places[r.arrival?.placeId] ?: return@mapNotNull null
            val o = repo.openOccurrence(r.id)
                ?.takeIf { it.state == OccurrenceState.PLANNED && (r.schedule == Schedule.OnArrival || it.awaitsArrival) }
                ?: return@mapNotNull null
            if (c.check(place, fallback = true).result == PlaceResult.AT) arrived(r, o, byCheck = true, plannedCheck = null) else null
        }
    }

    /** Active reminders with an arrival at [placeId] and their open occurrence. */
    private suspend fun waitingAt(placeId: Long): List<Pair<Reminder, Occurrence>> =
        repo.reminders().filter { it.active && it.arrival?.placeId == placeId }
            .mapNotNull { r -> repo.openOccurrence(r.id)?.let { r to it } }

    /** What the arrival means (see [ArrivalWindow.decide]); rings what is due right away. */
    private suspend fun arrived(reminder: Reminder, occurrence: Occurrence, byCheck: Boolean, plannedCheck: Instant?): Ring? {
        val now = now()
        return when (val d = ArrivalWindow.decide(reminder, occurrence, now, zone, plannedCheck)) {
            ArrivalDecision.Ignore -> null
            is ArrivalDecision.TooEarly -> {
                c.log("arrival r${reminder.id}: too early, window from ${local(d.windowStart)}")
                null
            }
            ArrivalDecision.Now -> s.arrive(reminder, occurrence)
            is ArrivalDecision.At -> {
                val arrived = s.apply(reminder, occurrence, Action.Arrived(d.at))
                c.log("arrival r${reminder.id}: rings at ${local(d.at)}" + if (byCheck) " (check)" else "")
                if (d.at <= now) {
                    s.fire(reminder, arrived)
                } else {
                    s.plan(reminder, arrived)
                    null
                }
            }
        }
    }

    private fun local(at: Instant) = at.atZone(zone).toLocalTime().withNano(0)

    /**
     * The next check of each place (design.md 6): the earliest of all occurrences waiting there, so
     * the shortest check interval wins. Runs at the end of every command. The check at the start
     * of a window is exact also in Doze: it decides F2.
     */
    suspend fun planChecks() {
        val now = now()
        val next = mutableMapOf<Long, Instant>()
        val starts = mutableMapOf<Long, MutableSet<Instant>>()
        for (r in repo.reminders()) {
            val placeId = r.arrival?.placeId ?: continue
            if (!r.active) continue
            val o = repo.openOccurrence(r.id) ?: continue
            val at = ArrivalWindow.nextCheck(r, o, now, views.missedAt(r, o)) ?: continue
            next.merge(placeId, at, ::minOf)
            ArrivalWindow.windowStart(r, o)?.let { starts.getOrPut(placeId) { mutableSetOf() } += it }
        }
        for (place in repo.places()) {
            val at = next[place.id]
            if (at != null) c.placeAlarms.set(place.id, at, exact = starts[place.id].orEmpty().contains(at)) else c.placeAlarms.cancel(place.id)
        }
    }

    /** Saved a reminder that waits for an arrival while already there: the geofence would not report it. */
    suspend fun arriveIfThere(reminder: Reminder): Ring? {
        if (!reminder.active || reminder.schedule != Schedule.OnArrival) return null
        val place = repo.places().firstOrNull { it.id == reminder.arrival?.placeId } ?: return null
        val o = repo.openOccurrence(reminder.id)?.takeIf { it.state == OccurrenceState.PLANNED } ?: return null
        return if (c.check(place, fallback = true).result == PlaceResult.AT) s.arrive(reminder, o) else null
    }

    /** Geofences for the places that active reminders wait for. */
    suspend fun watchPlaces() {
        val needed = repo.reminders().filter { it.active }.mapNotNull { it.arrival?.placeId }.toSet()
        c.geofences.watch(repo.places().filter { it.id in needed })
    }
}
