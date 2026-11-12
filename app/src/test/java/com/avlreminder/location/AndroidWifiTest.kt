package com.avlreminder.location

import org.junit.Assert.assertEquals
import org.junit.Test

/** What Android tells about the WLAN and what of it counts (Anf. 7.29 OZ3, OZ7). */
class AndroidWifiTest {
    @Test
    fun androidsPlaceholderWithoutLocationPermissionIsNoBssid() {
        // Counted as an access point, the placeholder could be learned as a place's WLAN and
        // would then match wherever the permission is missing (Review-Lauf 4, M5).
        assertEquals(false, AndroidWifi.validBssid("02:00:00:00:00:00"))
        assertEquals(false, AndroidWifi.validBssid(null))
        assertEquals(false, AndroidWifi.validBssid(" "))
        assertEquals(true, AndroidWifi.validBssid("aa:bb:cc:00:00:01"))
    }

    @Test
    fun theSsidLosesItsQuotesAndTheUnknownOneIsNone() {
        assertEquals("Heimnetz", AndroidWifi.cleanSsid("\"Heimnetz\""))
        assertEquals(null, AndroidWifi.cleanSsid("<unknown ssid>"))
        assertEquals(null, AndroidWifi.cleanSsid("\"\""))
        assertEquals(null, AndroidWifi.cleanSsid(null))
    }
}
