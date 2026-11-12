package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.ports.PlaceResult
import com.avlreminder.core.ports.PlaceSource
import com.avlreminder.core.ports.WifiScan
import com.avlreminder.core.reminder
import java.time.Instant
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Ort aus dem WLAN und Zeitraum der Ankunft (Anf. 7.29 OZ1–OZ3) als reine Regeln. */
class ArrivalWindowTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("wlan")
    fun `der Ort aus dem WLAN`(name: String, place: Place, connected: String?, scan: WifiScan?, result: PlaceResult, source: PlaceSource?) {
        val check = PlaceChecks.fromWifi(place, connected, scan, NOW)
        assertEquals(result to source, check.result to check.source, name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pruefungen")
    fun `die naechste Pruefung`(name: String, occurrence: Occurrence, now: Instant, next: Instant?) {
        assertEquals(next, ArrivalWindow.nextCheck(TRAINING, occurrence, now, at(27, 0)), name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ankuenfte")
    fun `was eine Ankunft bedeutet`(name: String, occurrence: Occurrence, now: Instant, plannedCheck: Instant?, decision: ArrivalDecision) {
        assertEquals(decision, ArrivalWindow.decide(TRAINING, occurrence, now, ZONE, plannedCheck), name)
    }

    companion object {
        private val NOW = at(26, 19)
        private val HOME = Place(id = 1, name = "Zuhause", lat = 0.0, lon = 0.0, wifis = listOf(PlaceWifi("aa:01", "Heim"), PlaceWifi("aa:02")))
        private fun scan(minutesAgo: Long, vararg bssids: String) = WifiScan(bssids.toSet(), NOW.minusSeconds(minutesAgo * 60))

        @JvmStatic
        fun wlan() = listOf(
            arguments("verbunden mit dem WLAN des Orts: da", HOME, "AA:01", null, PlaceResult.AT, PlaceSource.WIFI_CONNECTED),
            arguments("verbunden mit einem fremden WLAN, frische Suche ohne den Ort: weg", HOME, "bb:01", scan(1, "bb:01"), PlaceResult.AWAY, PlaceSource.WIFI_SCAN),
            arguments("Suche genau 10 min alt ohne den Ort: weg", HOME, null, scan(10), PlaceResult.AWAY, PlaceSource.WIFI_SCAN),
            arguments("Suche 11 min alt: unklar", HOME, null, scan(11), PlaceResult.UNKNOWN, PlaceSource.WIFI_SCAN),
            arguments("WLAN des Orts in Reichweite, nicht verbunden: unklar", HOME, null, scan(1, "aa:02"), PlaceResult.UNKNOWN, PlaceSource.WIFI_SCAN),
            arguments("keine Suche: unklar", HOME, null, null, PlaceResult.UNKNOWN, null),
            arguments("Ort ohne WLAN: unklar, auch mit frischer Suche", HOME.copy(wifis = emptyList()), null, scan(1), PlaceResult.UNKNOWN, null),
        )

        private val TRAINING = reminder(Schedule.Daily(LocalTime.of(19, 30))).copy(arrival = Arrival(1, checkMinutes = 20))
        private val PLANNED = Occurrence(id = 1, reminderId = 1, dueAt = at(26, 19, 30))

        @JvmStatic
        fun pruefungen() = listOf(
            arguments("vor dem Zeitraum: zu Beginn", PLANNED, at(26, 8), at(26, 18)),
            arguments("genau zu Beginn: im Prüfabstand danach", PLANNED, at(26, 18), at(26, 18, 20)),
            arguments("zwischen zwei Prüfungen: die nächste im Raster", PLANNED, at(26, 18, 25), at(26, 18, 40)),
            arguments("letzte Prüfung vor der spätesten Uhrzeit", PLANNED, at(26, 19, 10), at(26, 19, 20)),
            arguments("die späteste Uhrzeit prüft der eigene Alarm", PLANNED, at(26, 19, 20), null),
            arguments("schon angekommen: keine", PLANNED.copy(arrivedAt = at(26, 18, 5)), at(26, 18, 10), null),
            arguments("verschoben vor der neuen Zeit: keine", PLANNED.copy(postponeCount = 1, currentAt = at(26, 20)), at(26, 18, 10), null),
            arguments("wartet auf die Ankunft: ab der neuen Zeit", PLANNED.copy(firedAt = at(26, 19, 30), currentAt = at(26, 20), awaitsArrival = true), at(26, 20), at(26, 20, 20)),
            arguments("wartet, kurz vor der Tagesgrenze: keine mehr", PLANNED.copy(firedAt = at(26, 19, 30), currentAt = at(26, 20), awaitsArrival = true), at(26, 23, 45), null),
        )

        @JvmStatic
        fun ankuenfte() = listOf(
            arguments("vor dem Zeitraum: zu früh", PLANNED, at(26, 17, 59), null, ArrivalDecision.TooEarly(at(26, 18))),
            arguments("im Zeitraum gemeldet: nach dem Puffer", PLANNED, at(26, 18, 2), null, ArrivalDecision.At(at(26, 18, 7))),
            arguments("zu Beginn gemeldet, nicht geprüft: nach dem Puffer", PLANNED, at(26, 18), null, ArrivalDecision.At(at(26, 18, 5))),
            arguments("Prüfung zu Beginn findet einen da: späteste Uhrzeit (F2)", PLANNED, at(26, 18), at(26, 18), ArrivalDecision.At(at(26, 19, 30))),
            arguments("Prüfung zu Beginn kommt im Doze 12 min zu spät: trotzdem F2", PLANNED, at(26, 18, 12), at(26, 18), ArrivalDecision.At(at(26, 19, 30))),
            arguments("spätere Prüfung, pünktlich: nach dem Puffer", PLANNED, at(26, 18, 20), at(26, 18, 20), ArrivalDecision.At(at(26, 18, 25))),
            arguments("schon angekommen: die erste zählt", PLANNED.copy(arrivedAt = at(26, 18, 2), currentAt = at(26, 18, 7)), at(26, 18, 4), null, ArrivalDecision.Ignore),
            arguments("zur spätesten Uhrzeit: entscheidet der eigene Alarm", PLANNED, at(26, 19, 30), null, ArrivalDecision.Ignore),
            arguments("verschoben vor der neuen Zeit: behält sie", PLANNED.copy(postponeCount = 1, currentAt = at(26, 20)), at(26, 18, 30), null, ArrivalDecision.Ignore),
            arguments("wartet auf die Ankunft: nach dem Puffer", PLANNED.copy(firedAt = at(26, 19, 30), currentAt = at(26, 20), awaitsArrival = true), at(26, 20, 34), null, ArrivalDecision.At(at(26, 20, 39))),
        )
    }
}
