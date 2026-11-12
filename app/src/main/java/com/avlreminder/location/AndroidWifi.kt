package com.avlreminder.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.core.content.edit
import com.avlreminder.core.model.PlaceWifi
import com.avlreminder.core.ports.PlaceAlarmPort
import com.avlreminder.core.ports.WifiPort
import com.avlreminder.core.ports.WifiScan
import com.avlreminder.graph
import com.avlreminder.system.Diag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * What Android knows about the WLAN (Anf. 7.29 OZ3, OZ7): the connected access point and the
 * last scan. Never starts a scan. BSSIDs need the location permission; without it Android only
 * tells a placeholder, which counts as unknown.
 */
class AndroidWifi(private val context: Context) : WifiPort {
    private val cm get() = context.getSystemService(ConnectivityManager::class.java)
    private val wifi get() = context.getSystemService(WifiManager::class.java)

    override suspend fun connected(): String? = current()?.bssid?.lowercase()?.takeIf(::validBssid)

    override suspend fun lastScan(): WifiScan? {
        val results = scanResults()
        if (results.isEmpty()) return null
        // ScanResult.timestamp: microseconds since boot, when the access point was last seen.
        val newest = results.maxOf { it.timestamp }
        val ageMicros = (SystemClock.elapsedRealtimeNanos() / 1000 - newest).coerceAtLeast(0)
        return WifiScan(results.map { it.BSSID.lowercase() }.toSet(), Instant.now().minusMillis(ageMicros / 1000))
    }

    /**
     * The connected network for "mit diesem WLAN verbunden" (OZ3): its access point and all access
     * points of the same name in the last scan (mesh, 2.4 and 5 GHz). Null when not connected or
     * Android does not tell the BSSID (permission missing).
     */
    suspend fun currentNetwork(): List<PlaceWifi>? {
        val info = current() ?: return null
        val bssid = info.bssid?.lowercase()?.takeIf(::validBssid) ?: return null
        val ssid = cleanSsid(info.ssid)
        val same = if (ssid == null) emptyList() else scanResults()
            .filter { cleanSsid(it.wifiSsid?.toString()) == ssid }
            .map { it.BSSID.lowercase() }
        return (listOf(bssid) + same).distinct().map { PlaceWifi(it, ssid) }
    }

    /** The last scan results; without the location permission Android gives none. */
    private fun scanResults(): List<ScanResult> {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching { wifi.scanResults.orEmpty() }.getOrDefault(emptyList())
    }

    /** The current WLAN with BSSID: a callback with location info reports it right after registering. */
    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is a normal permission
    private suspend fun current(): WifiInfo? {
        val result = CompletableDeferred<WifiInfo>()
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                (caps.transportInfo as? WifiInfo)?.let { result.complete(it) }
            }
        }
        return runCatching {
            cm.registerNetworkCallback(wifiRequest(), callback)
            try {
                withTimeoutOrNull(CALLBACK_MS) { result.await() }
            } finally {
                cm.unregisterNetworkCallback(callback)
            }
        }.getOrNull()
    }

    companion object {
        /** Android's placeholder when the caller may not see the BSSID. */
        private const val REDACTED_BSSID = "02:00:00:00:00:00"
        private const val CALLBACK_MS = 2_000L
        const val ACTION_CONNECT = "com.avlreminder.WIFI_CONNECT"

        fun validBssid(bssid: String?): Boolean = !bssid.isNullOrBlank() && bssid != REDACTED_BSSID

        fun cleanSsid(ssid: String?): String? =
            ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

        fun wifiRequest(): NetworkRequest = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()

        /**
         * Android reports every WLAN connection to [WifiReceiver], also without a running process.
         * At every process start (also after a reboot); the same PendingIntent replaces the old request.
         */
        @SuppressLint("MissingPermission")
        fun watch(context: Context) {
            runCatching {
                context.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                    wifiRequest(),
                    PendingIntent.getBroadcast(
                        context, 0, Intent(context, WifiReceiver::class.java).setAction(ACTION_CONNECT),
                        // The system fills in the network, so the intent must be mutable.
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )
            }.onFailure { Diag.log("wifi: callback not registered: ${it.javaClass.simpleName}") }
        }
    }
}

/**
 * Connected to a WLAN: an arrival at the places it belongs to. The log has no names, no BSSIDs.
 * Android also reports a connection that already exists whenever [AndroidWifi.watch] registers
 * again, so at every process start: only a new connection counts (2026-11-12, Review-Lauf 4).
 */
class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidWifi.ACTION_CONNECT) return
        val network = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
        if (!WifiConnections(context).isNew(network?.networkHandle)) {
            Diag.log("wifi connected: same connection as before, no arrival")
            return
        }
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                val bssid = graph.wifi.connected()
                val rings = if (bssid == null) emptyList() else graph.withEngine { it.onWifi(bssid) }
                Diag.log("wifi connected: ${if (bssid == null) "no bssid" else "${rings.size} ring(s)"}")
            } catch (e: Exception) {
                Diag.log("wifi connect failed: ${e.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * Which WLAN connection was reported last: Android's handle of the network, which a new
 * connection changes and a repeated report of the same one keeps. On the phone only.
 */
class WifiConnections(context: Context) {
    private val prefs = context.getSharedPreferences("wifi", Context.MODE_PRIVATE)

    /** Whether [handle] is a connection not reported before; it is remembered. Null: unknown, counts. */
    fun isNew(handle: Long?): Boolean {
        if (handle == null) return true
        if (prefs.getLong(KEY, NONE) == handle) return false
        prefs.edit { putLong(KEY, handle) }
        return true
    }

    private companion object {
        const val KEY = "lastNetwork"
        const val NONE = Long.MIN_VALUE
    }
}

/**
 * The checks of a place in its arrival window (design.md Z.3): one alarm per place, like the other
 * alarms derived from the database on every sync. The check at the start of a window is exact also
 * in Doze (it decides F2), the others are inexact, `setWindow` with [WINDOW_MS]. The intent carries
 * the planned time.
 */
class AndroidPlaceAlarms(private val context: Context) : PlaceAlarmPort {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    // USE_EXACT_ALARM is granted at install time (manifest); lint only knows SCHEDULE_EXACT_ALARM.
    @SuppressLint("MissingPermission")
    override fun set(placeId: Long, at: Instant, exact: Boolean) {
        val pending = pending(placeId, at)
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW_MS, pending)
        }
    }

    override fun cancel(placeId: Long) {
        alarms.cancel(pending(placeId, null))
    }

    private fun pending(placeId: Long, at: Instant?): PendingIntent = PendingIntent.getBroadcast(
        context, placeId.toInt(),
        Intent(context, PlaceCheckReceiver::class.java).putExtra(EXTRA_PLACE, placeId)
            .apply { at?.let { putExtra(EXTRA_AT, it.toEpochMilli()) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_PLACE = "place"
        /** The planned time of the check, for F2 and to log how late it came. */
        const val EXTRA_AT = "at"
        const val WINDOW_MS = 2 * 60_000L
    }
}

/** A check alarm of a place fired. */
class PlaceCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val placeId = intent.getLongExtra(AndroidPlaceAlarms.EXTRA_PLACE, -1)
        if (placeId < 0) return
        val planned = intent.getLongExtra(AndroidPlaceAlarms.EXTRA_AT, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        val result = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                graph.withEngine { it.onPlaceCheck(placeId, planned) }
            } finally {
                result.finish()
            }
        }
    }
}
