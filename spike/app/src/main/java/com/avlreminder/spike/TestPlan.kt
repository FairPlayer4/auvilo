package com.avlreminder.spike

import java.time.LocalTime
import java.time.ZonedDateTime

/** How an alarm gets the user's attention, derived from the time windows in the requirements. */
enum class AlarmMode(val label: String) {
    SILENT("still"),
    VISIBLE("sichtbar: Vollbild + Vibration"),
    LOUD("laut: Vollbild + Vibration + Alarmton");

    companion object {
        /** 18–21 loud, 7–21 visible, otherwise silent notification only. */
        fun forTime(time: LocalTime): AlarmMode = when (time.hour) {
            in 18..20 -> LOUD
            in 7..20 -> VISIBLE
            else -> SILENT
        }
    }
}

data class Slot(val time: LocalTime, val label: String, val homeCheck: Boolean = false)

/** Fixed daily slots for the multi-day test. */
object TestPlan {
    val slots = listOf(
        Slot(LocalTime.of(2, 0), "Nacht (Doze-Test)"),
        Slot(LocalTime.of(8, 0), "Morgen"),
        Slot(LocalTime.of(13, 0), "Mittag"),
        Slot(LocalTime.of(19, 0), "19 Uhr + Zuhause-Prüfung", homeCheck = true),
        Slot(LocalTime.of(20, 30), "Abend"),
    )

    /** The first slot strictly after [now], possibly tomorrow. */
    fun next(now: ZonedDateTime): Pair<Slot, ZonedDateTime> {
        for (day in 0..1) {
            val date = now.toLocalDate().plusDays(day.toLong())
            for (slot in slots) {
                val at = date.atTime(slot.time).atZone(now.zone)
                if (at.isAfter(now)) return slot to at
            }
        }
        error("unreachable: slots list is empty")
    }
}
