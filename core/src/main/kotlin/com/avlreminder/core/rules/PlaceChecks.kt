package com.avlreminder.core.rules

import com.avlreminder.core.model.Place
import com.avlreminder.core.ports.LocationPort
import com.avlreminder.core.ports.PlaceCheck
import com.avlreminder.core.ports.PlaceResult
import com.avlreminder.core.ports.PlaceSource
import com.avlreminder.core.ports.WifiPort
import com.avlreminder.core.ports.WifiScan
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Is one at a place, from the WLAN alone (Anf. 7.29 OZ3)? */
object PlaceChecks {
    /** A scan this old at most may say "klar weg". */
    val FRESH_SCAN: Duration = Duration.ofMinutes(10)

    /**
     * Connected to a WLAN of the place: there. Not connected, and a fresh scan shows none of its
     * access points: clearly away. Anything else is unclear: no fresh scan, or one of its access
     * points in reach without being connected.
     */
    fun fromWifi(place: Place, connected: String?, scan: WifiScan?, now: Instant): PlaceCheck {
        if (connected != null && place.hasWifi(connected)) return PlaceCheck(PlaceResult.AT, PlaceSource.WIFI_CONNECTED)
        val age = scan?.let { maxOf(Duration.between(it.at, now), Duration.ZERO) }
        if (place.wifis.isEmpty() || scan == null || age == null) return PlaceCheck(PlaceResult.UNKNOWN, null, age)
        val seen = scan.bssids.any { place.hasWifi(it) }
        val result = if (!seen && age <= FRESH_SCAN) PlaceResult.AWAY else PlaceResult.UNKNOWN
        return PlaceCheck(result, PlaceSource.WIFI_SCAN, age)
    }
}

/**
 * The [LocationPort] of the app: the WLAN first; only with `fallback` (at the latest time) an
 * unclear WLAN asks [coarse] for a position (true: there, false: away, null: unknown).
 */
class WifiLocation(
    private val wifi: WifiPort,
    private val clock: Clock,
    private val coarse: suspend (Place) -> Boolean?,
) : LocationPort {
    override suspend fun check(place: Place, fallback: Boolean): PlaceCheck {
        val byWifi = PlaceChecks.fromWifi(place, wifi.connected(), wifi.lastScan(), clock.instant())
        if (byWifi.result != PlaceResult.UNKNOWN || !fallback) return byWifi
        return when (coarse(place)) {
            true -> PlaceCheck(PlaceResult.AT, PlaceSource.FALLBACK, byWifi.scanAge)
            false -> PlaceCheck(PlaceResult.AWAY, PlaceSource.FALLBACK, byWifi.scanAge)
            null -> byWifi
        }
    }
}
