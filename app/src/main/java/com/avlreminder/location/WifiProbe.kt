package com.avlreminder.location

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.core.content.edit
import com.avlreminder.graph
import com.avlreminder.system.Diag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * WLAN device test for 7.29, debug build only: logs on every WLAN connection Android reports
 * (callback with a PendingIntent, works without a running process) and every 20 minutes (exact
 * alarm, like the checks of 7.29). Reads only what Android already has; never starts a scan.
 * The learned networks stay in the app's own storage; the log has only their labels.
 */
object WifiProbe {
    private const val EVERY_MS = 20 * 60_000L
    private const val PREFS = "wifi_probe"
    private const val KEY = "networks"
    const val ACTION_TICK = "com.avlreminder.WIFI_PROBE_TICK"
    const val ACTION_CONNECT = "com.avlreminder.WIFI_PROBE_CONNECT"

    /** At every process start; registering the same PendingIntent again replaces the old request. */
    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is a normal permission
    fun start(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        runCatching {
            cm.registerNetworkCallback(wifiRequest(), pending(context, ACTION_CONNECT, PendingIntent.FLAG_MUTABLE)!!)
        }.onFailure { Diag.log("wifi probe: callback not registered: ${it.message}") }
        if (pending(context, ACTION_TICK, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) == null) {
            schedule(context)
            Diag.log("wifi probe started")
        }
    }

    fun schedule(context: Context) {
        context.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + EVERY_MS,
            pending(context, ACTION_TICK, PendingIntent.FLAG_IMMUTABLE)!!,
        )
    }

    /** Reads connection and scan, learns the connected network, writes one line to the diagnostic log. */
    suspend fun sample(context: Context, reason: String) {
        val wifi = context.getSystemService(WifiManager::class.java)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromCallback = currentWifi(cm)
        @Suppress("DEPRECATION") // the old way, to compare on Android 17
        val fromInfo = runCatching { wifi.connectionInfo }.getOrNull()
        val (info, source) = when {
            WifiProbeLogic.validBssid(fromCallback?.bssid) -> fromCallback to "cb"
            WifiProbeLogic.validBssid(fromInfo?.bssid) -> fromInfo to "info"
            else -> null to "-"
        }
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val now = SystemClock.elapsedRealtimeNanos() / 1000
        val scan = runCatching { wifi.scanResults }.getOrDefault(emptyList()).map {
            SeenAp(it.wifiSsid?.toString(), it.BSSID, it.level, ((now - it.timestamp) / 1_000_000).coerceAtLeast(0))
        }
        val sample = ProbeSample(
            reason = reason,
            wifiNetwork = fromCallback != null || caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            connectedSsid = info?.ssid,
            connectedBssid = info?.bssid,
            source = source,
            wifiOn = wifi.isWifiEnabled,
            // Deprecated without a replacement for apps; the probe only logs it for diagnosis.
            scanAlways = runCatching { @Suppress("DEPRECATION") wifi.isScanAlwaysAvailable }.getOrDefault(false),
            locationOn = context.getSystemService(LocationManager::class.java).isLocationEnabled,
            scan = scan,
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (networks, line) = WifiProbeLogic.evaluate(decode(prefs.getString(KEY, null)), sample)
        prefs.edit { putString(KEY, encode(networks)) }
        Diag.log(line)
    }

    /** The current WLAN with BSSID: a callback with location info reports it right after registering. */
    @SuppressLint("MissingPermission")
    private suspend fun currentWifi(cm: ConnectivityManager): WifiInfo? {
        val result = CompletableDeferred<WifiInfo>()
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
                (caps.transportInfo as? WifiInfo)?.let { result.complete(it) }
            }
        }
        return runCatching {
            cm.registerNetworkCallback(wifiRequest(), callback)
            try {
                withTimeoutOrNull(2_000) { result.await() }
            } finally {
                cm.unregisterNetworkCallback(callback)
            }
        }.getOrNull()
    }

    private fun wifiRequest() = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()

    private fun pending(context: Context, action: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, WifiProbeReceiver::class.java).setAction(action),
        if (flags and PendingIntent.FLAG_NO_CREATE != 0) flags else flags or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun encode(networks: List<ProbeNetwork>): String = JSONArray(
        networks.map { JSONObject().put("label", it.label).put("ssid", it.ssid).put("bssids", JSONArray(it.bssids.sorted())) },
    ).toString()

    fun decode(json: String?): List<ProbeNetwork> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val b = o.getJSONArray("bssids")
            ProbeNetwork(o.getString("label"), o.getString("ssid"), (0 until b.length()).map { b.getString(it) }.toSet())
        }
    }.getOrDefault(emptyList())
}

/** WLAN connected (from the system) or the 20-minute tick. */
class WifiProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            WifiProbe.ACTION_TICK -> "tick".also { WifiProbe.schedule(context) }
            WifiProbe.ACTION_CONNECT -> "connect"
            else -> return
        }
        val result = goAsync()
        context.graph.scope.launch {
            try {
                WifiProbe.sample(context, reason)
            } catch (e: Exception) {
                Diag.log("wifi probe $reason failed: ${e.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }
}
