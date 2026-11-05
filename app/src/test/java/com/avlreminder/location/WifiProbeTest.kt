package com.avlreminder.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** WLAN device test before 7.29: what the probe learns and logs. */
@RunWith(AndroidJUnit4::class)
class WifiProbeTest {
    private val home = "\"Heim\""
    private fun sample(
        ssid: String? = null,
        bssid: String? = null,
        scan: List<SeenAp> = emptyList(),
        wifiNetwork: Boolean = bssid != null,
    ) = ProbeSample("tick", wifiNetwork, ssid, bssid, if (bssid != null) "cb" else "-", true, true, true, scan)

    @Test
    fun learnsTheConnectedNetworkWithAllItsAccessPointsAndSaysAt() {
        val scan = listOf(
            SeenAp(home, "AA:00:00:00:00:01", -45, 30),
            SeenAp(home, "aa:00:00:00:00:02", -60, 30),
            SeenAp("\"Nachbar\"", "bb:00:00:00:00:01", -80, 30),
        )
        val (networks, line) = WifiProbeLogic.evaluate(emptyList(), sample(home, "aa:00:00:00:00:01", scan))
        assertEquals(listOf(ProbeNetwork("N1", "Heim", setOf("aa:00:00:00:00:01", "aa:00:00:00:00:02"))), networks)
        assertEquals(
            "wifi probe tick: connected=N1/cb wifi=on scanAlways=on location=on scan=3 age=30s N1=AT(2/-45dBm) learned N1(2 ap)",
            line,
        )
    }

    @Test
    fun aFreshScanWithoutTheNetworkIsAwayAnOldOneIsUnknown() {
        val known = listOf(ProbeNetwork("N1", "Heim", setOf("aa:00:00:00:00:01")))
        val other = SeenAp("\"Büro\"", "cc:00:00:00:00:01", -50, 600)
        assertTrue(WifiProbeLogic.evaluate(known, sample(scan = listOf(other))).second.contains("connected=none"))
        assertTrue(WifiProbeLogic.evaluate(known, sample(scan = listOf(other))).second.contains("N1=AWAY(0)"))
        val old = other.copy(ageSeconds = 601)
        assertTrue(WifiProbeLogic.evaluate(known, sample(scan = listOf(old))).second.contains("N1=UNKNOWN(0)"))
        assertTrue(WifiProbeLogic.evaluate(known, sample()).second.contains("age=- N1=UNKNOWN(0)"))
    }

    @Test
    fun inReachButNotConnectedIsUnknown() {
        val known = listOf(ProbeNetwork("N1", "Heim", setOf("aa:00:00:00:00:01")))
        val line = WifiProbeLogic.evaluate(known, sample(scan = listOf(SeenAp(home, "aa:00:00:00:00:01", -85, 10)))).second
        assertTrue(line, line.contains("N1=UNKNOWN(1/-85dBm)"))
    }

    @Test
    fun aRedactedBssidLearnsNothingAndSaysSo() {
        val (networks, line) = WifiProbeLogic.evaluate(
            emptyList(),
            sample("<unknown ssid>", WifiProbeLogic.REDACTED_BSSID, wifiNetwork = true),
        )
        assertTrue(networks.isEmpty())
        assertTrue(line, line.contains("connected=wifi-no-bssid"))
    }

    @Test
    fun aSecondNetworkGetsTheNextLabelAndNewAccessPointsAreAdded() {
        val known = listOf(ProbeNetwork("N1", "Heim", setOf("aa:00:00:00:00:01")))
        val (two, line) = WifiProbeLogic.evaluate(known, sample("\"Büro\"", "cc:00:00:00:00:01"))
        assertEquals("N2", two.last().label)
        assertTrue(line, line.contains("connected=N2/cb") && line.contains("learned N2(1 ap)"))
        val (grown, line2) = WifiProbeLogic.evaluate(two, sample(home, "aa:00:00:00:00:09"))
        assertEquals(setOf("aa:00:00:00:00:01", "aa:00:00:00:00:09"), grown.first().bssids)
        assertTrue(line2, line2.endsWith(" +1 ap N1"))
    }

    @Test
    fun theLogLineHasNoNamesAndNoBssids() {
        val line = WifiProbeLogic.evaluate(
            emptyList(),
            sample(home, "aa:00:00:00:00:01", listOf(SeenAp("\"Nachbar\"", "bb:00:00:00:00:01", -70, 5))),
        ).second
        assertFalse(line.contains("Heim") || line.contains("Nachbar") || line.contains("aa:00") || line.contains("bb:00"))
    }

    @Test
    fun networksSurviveTheStore() {
        val networks = listOf(
            ProbeNetwork("N1", "Heim", setOf("aa:00:00:00:00:01", "aa:00:00:00:00:02")),
            ProbeNetwork("N2", "Büro \"5G\"", setOf("cc:00:00:00:00:01")),
        )
        assertEquals(networks, WifiProbe.decode(WifiProbe.encode(networks)))
        assertTrue(WifiProbe.decode(null).isEmpty())
        assertTrue(WifiProbe.decode("kaputt").isEmpty())
    }
}
