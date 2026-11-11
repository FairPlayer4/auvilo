package com.avlreminder.ui

import java.time.Duration
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.model.WhenAway
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** German texts for model values; kept in one place so screens and widget say the same. */
object Format {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM.")
    private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun time(at: Instant, zone: ZoneId): String = TIME.format(at.atZone(zone))
    fun time(at: LocalTime): String = TIME.format(at)
    fun date(date: LocalDate): String = DATE.format(date)
    fun longDay(date: LocalDate): String = LONG_DAY.format(date)
    fun weekday(d: DayOfWeek): String = WEEKDAYS[d.value - 1]

    /** "Sa 24.10." */
    fun shortDay(date: LocalDate): String = "${weekday(date.dayOfWeek)} ${DAY_MONTH.format(date)}"

    /** "Heute, Sa 24.10.", "Fr 23.10." */
    fun day(date: LocalDate, today: LocalDate): String =
        if (date == today) "Heute, ${shortDay(date)}" else shortDay(date)

    /** Time, prefixed with the day if it is not today. */
    fun moment(at: Instant, zone: ZoneId, today: LocalDate): String {
        val date = at.atZone(zone).toLocalDate()
        return when (date) {
            today -> time(at, zone)
            today.plusDays(1) -> "morgen ${time(at, zone)}"
            else -> "${shortDay(date)} ${time(at, zone)}"
        }
    }

    /** The session timer (Anf. 7.11 D2): "12:34", from an hour on "1:02:03". */
    fun elapsed(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        val (h, m, sec) = Triple(s / 3600, s % 3600 / 60, s % 60)
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** "45 min", "1 h", "1 h 30 min". */
    fun minutes(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }

    /** A duration in whole minutes, rounded: "42 min". */
    fun duration(d: Duration): String = minutes(((d.seconds + 30) / 60).toInt())

    /**
     * How [duration] compares with the [average] (D6): "6 min schneller als im Schnitt", "3 min
     * länger als im Schnitt", within a minute "wie im Schnitt".
     */
    fun pace(duration: Duration, average: Duration): String {
        val diff = ((duration.seconds - average.seconds) / 60.0).let { kotlin.math.round(it).toInt() }
        return when {
            diff == 0 -> "wie im Schnitt"
            diff < 0 -> "${minutes(-diff)} schneller als im Schnitt"
            else -> "${minutes(diff)} länger als im Schnitt"
        }
    }

    fun strictness(s: Strictness) = when (s) {
        Strictness.RELAXED -> "locker"
        Strictness.NORMAL -> "normal"
        Strictness.STRICT -> "streng"
    }

    fun resolution(r: Resolution) = when (r) {
        Resolution.GOAL -> "Ziel"
        Resolution.MINIMUM -> "Minimum"
        Resolution.NO_PROOF -> "ohne Nachweis"
        Resolution.SKIPPED -> "ausgelassen"
        Resolution.MISSED -> "verpasst"
        Resolution.DROPPED -> "entfallen"
        Resolution.ABORTED -> "abgebrochen"
    }

    fun schedule(s: Schedule): String = when (s) {
        is Schedule.Once -> "einmalig ${shortDay(s.at.toLocalDate())} ${time(s.at.toLocalTime())}"
        is Schedule.Daily -> "täglich ${time(s.time)}"
        is Schedule.Weekdays -> {
            val days = if (s.days.size == 7) "täglich" else s.days.sorted().joinToString(", ") { weekday(it) }
            "$days ${time(s.time)}"
        }
        is Schedule.AfterDone -> "alle ${s.intervalDays} Tage nach Erledigung, ${time(s.time)}"
        Schedule.OnArrival -> "wenn ich zuhause bin"
        is Schedule.Deadline -> "bis ${shortDay(s.due.toLocalDate())} ${time(s.due.toLocalTime())} · ${days(s.days)} ${time(s.from)}–${time(s.until)}"
    }

    /** "Mo–Fr", "Mo, Mi, Fr", "täglich". */
    fun days(days: Set<DayOfWeek>): String {
        val sorted = days.sorted()
        return when {
            sorted.size == 7 -> "täglich"
            sorted.size >= 3 && sorted.last().value - sorted.first().value == sorted.size - 1 -> "${weekday(sorted.first())}–${weekday(sorted.last())}"
            else -> sorted.joinToString(", ") { weekday(it) }
        }
    }

    /** "zuhause" for the first place or an unknown one, otherwise "am Ort „Arbeit“". */
    fun at(placeId: Long, places: List<Place>): String {
        val place = places.firstOrNull { it.id == placeId }
        return if (place == null || place.id == places.first().id) "zuhause" else "am Ort „${place.name}“"
    }

    /** Schedule plus the arrival trigger, e.g. "täglich 19:00 · ab 90 min vorher bei Ankunft zuhause, sonst klingeln". */
    fun plan(r: Reminder, places: List<Place> = emptyList()): String {
        val arrival = r.arrival ?: return schedule(r.schedule)
        val at = at(arrival.placeId, places)
        if (r.schedule == Schedule.OnArrival) return "wenn ich $at bin"
        val window = if (arrival.hasWindow) "ab ${arrival.leadMinutes} min vorher " else ""
        val away = if (arrival.whenAway == WhenAway.RING) "sonst klingeln" else "sonst entfällt"
        return "${schedule(r.schedule)} · ${window}bei Ankunft $at, $away"
    }

    /**
     * For *Heute* (Anf. 7.29 Z.5): "ab 18:00 bei Ankunft zuhause, sonst 19:30", or "angekommen –
     * klingelt 18:35"; null without an arrival window.
     */
    fun arrival(r: Reminder, o: Occurrence, zone: ZoneId, places: List<Place>): String? {
        val arrival = r.arrival?.takeIf { it.hasWindow && r.schedule != Schedule.OnArrival } ?: return null
        if (o.arrivedAt != null) return "angekommen – klingelt ${time(o.currentAt, zone)}"
        if (o.awaitsArrival) return null
        val from = o.currentAt.minus(Duration.ofMinutes(arrival.leadMinutes.toLong()))
        return "ab ${time(from, zone)} bei Ankunft ${at(arrival.placeId, places)}, sonst ${time(o.currentAt, zone)}"
    }
}
