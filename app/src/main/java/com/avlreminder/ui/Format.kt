package com.avlreminder.ui

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
    }

    /** Schedule plus the arrival trigger, e.g. "täglich 19:00 · bei Ankunft zuhause". */
    fun plan(r: Reminder): String {
        val arrival = r.arrival
        return when {
            arrival == null || r.schedule == Schedule.OnArrival -> schedule(r.schedule)
            arrival.whenAway == WhenAway.RING -> "${schedule(r.schedule)} · bei Ankunft zuhause, sonst klingeln"
            else -> "${schedule(r.schedule)} · bei Ankunft zuhause, sonst entfällt"
        }
    }
}
