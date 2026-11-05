package com.avlreminder.location

/** One access point from Android's last WLAN scan; [ageSeconds] since it was seen. */
data class SeenAp(val ssid: String?, val bssid: String, val rssi: Int, val ageSeconds: Long)

/**
 * A network the probe has learned while connected: [label] (N1, N2, …) is all that goes to the
 * diagnostic log, [ssid] and [bssids] stay in the app's own storage.
 */
data class ProbeNetwork(val label: String, val ssid: String, val bssids: Set<String>)

/** What the probe read from Android at one moment. */
data class ProbeSample(
    val reason: String,
    /** Android has a WLAN network up, whether or not it tells us which one. */
    val wifiNetwork: Boolean,
    val connectedSsid: String?,
    val connectedBssid: String?,
    /** Where the connected BSSID came from: "cb" (network callback), "info" (WifiManager) or "-". */
    val source: String,
    val wifiOn: Boolean,
    val scanAlways: Boolean,
    val locationOn: Boolean,
    val scan: List<SeenAp>,
)

/**
 * Device test before 7.29 (Anf. 7.29 OZ3, design.md *Entwurf 7.29* Z.3): does Android report a WLAN
 * connection in the background, how old are the scan results, and would "klar weg" work? Learns
 * every network it is connected to and logs per network what 7.29 would decide, without names.
 */
object WifiProbeLogic {
    /** Android's placeholder when the caller may not see the BSSID. */
    const val REDACTED_BSSID = "02:00:00:00:00:00"
    /** 7.29 OZ3: a scan this old at most may say "klar weg". */
    const val FRESH_SCAN_SECONDS = 600L

    fun validBssid(bssid: String?): Boolean = !bssid.isNullOrBlank() && bssid != REDACTED_BSSID

    fun cleanSsid(ssid: String?): String? =
        ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

    /** Learns the connected network with all its access points in the scan; returns the networks and the log line. */
    fun evaluate(known: List<ProbeNetwork>, s: ProbeSample): Pair<List<ProbeNetwork>, String> {
        val ssid = cleanSsid(s.connectedSsid)
        val bssid = s.connectedBssid?.lowercase()?.takeIf { validBssid(it) }
        var networks = known
        var learned = ""
        if (ssid != null && bssid != null) {
            val aps = setOf(bssid) + s.scan.filter { cleanSsid(it.ssid) == ssid }.map { it.bssid.lowercase() }
            val existing = networks.find { it.ssid == ssid }
            if (existing == null) {
                val label = "N${networks.size + 1}"
                networks = networks + ProbeNetwork(label, ssid, aps)
                learned = " learned $label(${aps.size} ap)"
            } else if (!existing.bssids.containsAll(aps)) {
                val grown = existing.copy(bssids = existing.bssids + aps)
                networks = networks.map { if (it.label == existing.label) grown else it }
                learned = " +${grown.bssids.size - existing.bssids.size} ap ${existing.label}"
            }
        }
        val connectedLabel = bssid?.let { b -> networks.find { b in it.bssids }?.label }
        val connected = when {
            connectedLabel != null -> "$connectedLabel/${s.source}"
            bssid != null -> "other/${s.source}"
            s.wifiNetwork -> "wifi-no-bssid"
            else -> "none"
        }
        val newest = s.scan.minOfOrNull { it.ageSeconds }
        val fresh = newest != null && newest <= FRESH_SCAN_SECONDS
        val perNetwork = networks.joinToString(" ") { n ->
            val seen = s.scan.filter { it.bssid.lowercase() in n.bssids }
            val verdict = when {
                n.label == connectedLabel -> "AT"
                fresh && seen.isEmpty() -> "AWAY"
                else -> "UNKNOWN"
            }
            val signal = if (seen.isEmpty()) "0" else "${seen.size}/${seen.maxOf { it.rssi }}dBm"
            "${n.label}=$verdict($signal)"
        }
        val line = "wifi probe ${s.reason}: connected=$connected wifi=${onOff(s.wifiOn)} " +
            "scanAlways=${onOff(s.scanAlways)} location=${onOff(s.locationOn)} " +
            "scan=${s.scan.size} age=${newest?.let { "${it}s" } ?: "-"}" +
            (if (perNetwork.isEmpty()) "" else " $perNetwork") + learned
        return networks to line
    }

    private fun onOff(b: Boolean) = if (b) "on" else "off"
}
