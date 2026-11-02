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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avlreminder.core.usecases.ReminderSort
import com.avlreminder.data.OverviewTimes
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.AndroidDeviceState
import com.avlreminder.alarm.AlarmActivity
import com.avlreminder.alarm.AudioPlayer
import com.avlreminder.alarm.AudioRecorder
import com.avlreminder.alarm.Notifications
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.data.ThemeMode
import com.avlreminder.location.hasBackgroundLocation
import com.avlreminder.location.hasLocation
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.theme.AvlTheme
import com.avlreminder.ui.theme.isDark
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_NEW = "new"
        private const val EXTRA_EDIT = "edit"

        /** Opens "Neuer Reminder" directly (widget). */
        fun newReminder(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_NEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Opens a reminder's form (tap on the notice before it). */
        fun editReminder(context: Context, reminderId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_EDIT, reminderId)
                .setData("avl://edit/$reminderId".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private val vm: AppViewModel by viewModels()
    private var permissions by mutableStateOf(emptyList<PermissionItem>())
    private var startRoute by mutableStateOf(Routes.TODAY)
    private var launch by mutableIntStateOf(0)

    /** Precise position first; "Immer erlauben" can only be asked for afterwards. */
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshPermissions() }

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
            val proofs by vm.proofs.collectAsStateWithLifecycle()
            val prep by vm.prep.collectAsStateWithLifecycle()
            val alert by vm.alert.collectAsStateWithLifecycle()
            val home by vm.home.collectAsStateWithLifecycle()
            val dwell by vm.dwellMinutes.collectAsStateWithLifecycle()
            val itemsDone by vm.itemsDone.collectAsStateWithLifecycle()
            val reminderSort by vm.reminderSort.collectAsStateWithLifecycle()
            val overview by vm.overview.collectAsStateWithLifecycle()
            val suggestions by vm.suggestions.collectAsStateWithLifecycle()
            val blockers by vm.blockers.collectAsStateWithLifecycle()
            val templates by vm.templates.collectAsStateWithLifecycle()
            var now by androidx.compose.runtime.remember { mutableStateOf(Instant.now()) }
            var dnd by androidx.compose.runtime.remember { mutableStateOf(device.isDoNotDisturb()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    now = Instant.now()
                    dnd = device.isDoNotDisturb()
                }
            }
            // The DND exception is only needed once a reminder asks for it.
            LaunchedEffect(reminders) { refreshPermissions() }
            AvlTheme(dark = isDark(theme)) {
                // A new key restarts the UI on the requested route (e.g. widget tap while the app is open).
                androidx.compose.runtime.key(launch) {
                    AvlAppUi(
                        state = AppState(
                            today, reminders, openByReminder, history, proofs, theme, permissions, prep, alert, dnd,
                            home = home, dwellMinutes = dwell, itemsDone = itemsDone,
                            reminderSort = reminderSort, suggestions = suggestions, overview = overview,
                            blockers = blockers, templates = templates,
                        ),
                        actions = actions,
                        now = now,
                        zone = ZoneId.systemDefault(),
                        startRoute = startRoute,
                        recorder = { onRecorded -> AudioRecorder("motivation", "● Botschaft aufnehmen", onRecorded) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private val device by lazy { AndroidDeviceState(this) }

    override fun onPause() {
        super.onPause()
        AudioPlayer.stop()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        vm.sync()
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NEW, false) == true) {
            intent.removeExtra(EXTRA_NEW)
            startRoute = Routes.CHOOSE
            launch++
        }
        val edit = intent?.getLongExtra(EXTRA_EDIT, 0L) ?: 0L
        if (edit != 0L) {
            intent?.removeExtra(EXTRA_EDIT)
            startRoute = Routes.edit(edit)
            launch++
        }
    }

    private val actions = object : AppActions {
        override fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) = vm.save(reminder, prep, onResult)
        override fun delete(reminderId: Long) = vm.delete(reminderId)
        override fun setActive(reminderId: Long, active: Boolean) = vm.setActive(reminderId, active)
        override fun setTheme(mode: ThemeMode) = vm.setTheme(mode)
        override fun setAlert(settings: AlertSettings) = vm.setAlert(settings)
        override fun openAlarm(occurrenceId: Long) = startActivity(AlarmActivity.intent(this@MainActivity, occurrenceId))
        override fun completeEarly(occurrenceId: Long) = startActivity(AlarmActivity.intent(this@MainActivity, occurrenceId, early = true))
        override fun play(path: String) = AudioPlayer.play(this@MainActivity, path)
        override fun setDwellMinutes(minutes: Int) = vm.setDwellMinutes(minutes)
        override fun setReminderSort(sort: ReminderSort) = vm.setReminderSort(sort)
        override fun setOverview(times: OverviewTimes) = vm.setOverview(times)
        override fun setArchived(reminderId: Long, archived: Boolean) = vm.setArchived(reminderId, archived)
        override fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) =
            vm.addSuggestion(text, important, screen, reminderId)
        override fun updateSuggestion(suggestion: SuggestionEntity) = vm.updateSuggestion(suggestion)
        override fun deleteSuggestion(id: Long) = vm.deleteSuggestion(id)
        override fun discard(occurrenceId: Long) = vm.discard(occurrenceId)
        override fun addBlocker(reminderId: Long, text: String) = vm.addBlocker(reminderId, text)
        override fun solveBlocker(blockerId: Long) = vm.solveBlocker(blockerId)
        override fun planTask(blockerId: Long, at: java.time.LocalDateTime) = vm.planTask(blockerId, at)
        override fun saveTemplate(template: com.avlreminder.data.TemplateEntity) = vm.saveTemplate(template)
        override fun templateUsed(id: Long) = vm.templateUsed(id)
        override fun renameTemplate(id: Long, name: String) = vm.renameTemplate(id, name)
        override fun deleteTemplate(id: Long) = vm.deleteTemplate(id)
        /** The user picks the app in the share sheet; nothing is sent by itself. */
        override fun share(text: String) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(send, "Vorschläge teilen"))
        }
        override fun setHomeHere(onResult: (Boolean) -> Unit) {
            if (hasLocation()) {
                vm.setHomeHere(onResult)
            } else {
                requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                onResult(false)
            }
        }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun openSettings(action: String) {
        startActivity(Intent(action, "package:$packageName".toUri()))
    }

    private fun refreshPermissions() {
        val notifications = getSystemService(NotificationManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val blocked = Notifications.channelsBlockedByDnd(this)
        val dndNeeded = vm.reminders.value.any { it.active && it.ignoreDnd }
        val locationNeeded = vm.reminders.value.any { it.active && it.arrival != null }
        permissions = listOfNotNull(
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
            PermissionItem("Mikrofon", "für Audio-Nachweise und die Botschaft an dich", granted(Manifest.permission.RECORD_AUDIO), required = false) {
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            // Only asked for when a reminder waits for the arrival at home.
            if (!locationNeeded) null else PermissionItem(
                "Standort „Immer erlauben“", "für die Ankunft zuhause; der Standort bleibt auf dem Handy", hasBackgroundLocation(),
            ) {
                if (!hasLocation()) {
                    requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    requestPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
            // Only asked for when a reminder should ring despite "Nicht stören".
            if (!dndNeeded) null else PermissionItem(
                "Ausnahme bei „Nicht stören“", "für Reminder mit „Auch bei Nicht stören“: dort „Nicht stören ignorieren“ einschalten",
                blocked.isEmpty(),
            ) {
                startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, blocked.firstOrNull() ?: Notifications.CHANNEL_ALARM),
                )
            },
        )
    }
}
