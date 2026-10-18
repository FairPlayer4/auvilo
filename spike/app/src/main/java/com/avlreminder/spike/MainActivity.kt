package com.avlreminder.spike

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Control panel: permissions, test plan, manual tests, home location and the log. */
class MainActivity : ComponentActivity() {
    /** Bumped on resume and after actions so the screen re-reads all state. */
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(refresh) { refresh++ }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.checkMissed(this, "App geöffnet")
        refresh++
    }
}

@Composable
// `refresh` is not read directly: a new value makes Compose run this function again,
// which re-reads permissions, plan and log.
@Suppress("UNUSED_PARAMETER")
private fun MainScreen(refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun openSettings(action: String) = context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")))
    val notifications = context.getSystemService(NotificationManager::class.java)
    val power = context.getSystemService(PowerManager::class.java)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Heading("Berechtigungen")
        PermissionRow("Benachrichtigungen", granted(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermissionRow("Vollbild-Benachrichtigungen", notifications.canUseFullScreenIntent()) {
            openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        }
        PermissionRow("Akku: nicht optimieren", power.isIgnoringBatteryOptimizations(context.packageName)) {
            openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
        PermissionRow("Standort (genau)", granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Android only offers "Immer erlauben" after the foreground location was granted.
        PermissionRow("Standort (immer erlauben)", granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            requestPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        Text("Zustand: ${SpikeLog.deviceState(context)}", fontSize = 12.sp)

        Heading("Schnelltest (60 min, alle 5 min)")
        Text(AlarmScheduler.quickStatus(context) ?: "Gestoppt")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AlarmScheduler.startQuickTest(context); onChanged() }) { Text("Start") }
            OutlinedButton(onClick = { AlarmScheduler.stopQuickTest(context, "manuell"); onChanged() }) { Text("Stopp") }
        }

        Heading("Mehrtagestest (02:00, 08:00, 13:00, 19:00, 20:30)")
        Text(
            if (AlarmScheduler.isPlanActive(context)) "Aktiv – nächster: ${AlarmScheduler.nextPlanAlarm(context)}"
            else "Gestoppt",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AlarmScheduler.startPlan(context); onChanged() }) { Text("Start") }
            OutlinedButton(onClick = { AlarmScheduler.stopPlan(context); onChanged() }) { Text("Stopp") }
        }

        Heading("Sofort-Test (Alarm in 10 s – Handy sperren!)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in AlarmMode.entries) {
                OutlinedButton(onClick = {
                    AlarmScheduler.scheduleTest(context, 10, mode, "Sofort-Test ${mode.name}")
                    onChanged()
                }) { Text(mode.name.lowercase()) }
            }
        }

        Heading("Zuhause (Geofence 150 m)")
        Text(
            if (HomeLocation.home(context) == null) "Nicht gesetzt"
            else "Gesetzt – letzter Geofence-Status: ${HomeLocation.lastGeofenceState(context)}",
        )
        Button(
            enabled = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            onClick = { HomeLocation.setHomeToCurrentLocation(context, onChanged) },
        ) { Text("Aktuellen Standort als Zuhause setzen") }
        OutlinedButton(
            enabled = HomeLocation.home(context) != null,
            onClick = { HomeLocation.checkAtHome(context, onChanged) },
        ) { Text("Zuhause-Prüfung jetzt") }

        Row(verticalAlignment = Alignment.Bottom) {
            Heading("Protokoll (neueste zuerst)")
            TextButton(onClick = onChanged) { Text("Aktualisieren") }
        }
        for (line in SpikeLog.newestFirst(context)) {
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${if (ok) "✅" else "❌"} $title", Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text("Erteilen") }
    }
}
