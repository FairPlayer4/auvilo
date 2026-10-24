package com.avlreminder.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avlreminder.alarm.AlarmActivity
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.theme.AvlTheme
import com.avlreminder.ui.theme.isDark
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_NEW = "new"

        /** Opens "Neuer Reminder" directly (widget). */
        fun newReminder(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_NEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private val vm: AppViewModel by viewModels()
    private var permissions by mutableStateOf(emptyList<PermissionItem>())
    private var startRoute by mutableStateOf(Routes.TODAY)
    private var launch by mutableStateOf(0)

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val today by vm.today.collectAsStateWithLifecycle()
            val reminders by vm.reminders.collectAsStateWithLifecycle()
            val openByReminder by vm.openByReminder.collectAsStateWithLifecycle()
            val history by vm.history.collectAsStateWithLifecycle()
            val photos by vm.photos.collectAsStateWithLifecycle()
            var now by androidx.compose.runtime.remember { mutableStateOf(Instant.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    now = Instant.now()
                }
            }
            AvlTheme(dark = isDark(theme)) {
                // A new key restarts the UI on the requested route (e.g. widget tap while the app is open).
                androidx.compose.runtime.key(launch) {
                    AvlAppUi(
                        state = AppState(today, reminders, openByReminder, history, photos, theme, permissions),
                        actions = actions,
                        now = now,
                        zone = ZoneId.systemDefault(),
                        startRoute = startRoute,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        vm.sync()
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NEW, false) == true) {
            intent.removeExtra(EXTRA_NEW)
            startRoute = Routes.NEW
            launch++
        }
    }

    private val actions = object : AppActions {
        override fun save(reminder: Reminder, onResult: (SaveResult) -> Unit) = vm.save(reminder, onResult)
        override fun delete(reminderId: Long) = vm.delete(reminderId)
        override fun setActive(reminderId: Long, active: Boolean) = vm.setActive(reminderId, active)
        override fun setTheme(mode: ThemeMode) = vm.setTheme(mode)
        override fun openAlarm(occurrenceId: Long) = startActivity(AlarmActivity.intent(this@MainActivity, occurrenceId))
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun openSettings(action: String) {
        startActivity(Intent(action, "package:$packageName".toUri()))
    }

    private fun refreshPermissions() {
        val notifications = getSystemService(NotificationManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        permissions = listOf(
            PermissionItem("Benachrichtigungen", "ohne sie gibt es keinen Alarm", granted(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            PermissionItem("Vollbild-Alarm", "für strenge Reminder", notifications.canUseFullScreenIntent()) {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            },
            PermissionItem("Akku nicht optimieren", "damit Android die App nicht schlafen legt", power.isIgnoringBatteryOptimizations(packageName)) {
                openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            },
            PermissionItem("Kamera", "für Foto-Nachweise", granted(Manifest.permission.CAMERA), required = false) {
                requestPermission.launch(Manifest.permission.CAMERA)
            },
        )
    }
}
