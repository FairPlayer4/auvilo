package com.avlreminder.ui

import com.avlreminder.PlaceSetResult
import com.avlreminder.cloud.CloudConfig
import com.avlreminder.graph
import com.avlreminder.ui.screens.CloudActions

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Bundle
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
import com.avlreminder.alarm.ExactAlarms
import com.avlreminder.alarm.Notifications
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.data.ThemeMode
import com.avlreminder.location.hasBackgroundLocation
import com.avlreminder.restore.ExportFile
import com.avlreminder.system.Contact
import com.avlreminder.system.Battery
import com.avlreminder.location.hasLocation
import com.avlreminder.ui.screens.LocationDisclosureDialog
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.theme.AvlTheme
import com.avlreminder.ui.theme.isDark
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

// Lint reads the declared androidx.fragment 1.1.0 of a transitive dependency; resolved is 1.5.7,
// and this is a ComponentActivity, not a FragmentActivity, so the check does not apply.
@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_NEW = "new"
        private const val EXTRA_EDIT = "edit"
        private const val EXTRA_SETTINGS = "settings"

        /** Opens "Neuer Reminder" directly (widget). */
        fun newReminder(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_NEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        private const val STATE_LAUNCH = "launch"
        private const val STATE_ROUTE = "startRoute"

        /** Opens the settings (tap on a notification of the cloud backup). */
        fun openSettings(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Opens a reminder's form (tap on the notice before it). */
        fun editReminder(context: Context, reminderId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_EDIT, reminderId)
                .setData("avl://edit/$reminderId".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private val vm: AppViewModel by viewModels { AppViewModel.Factory }
    private var permissions by mutableStateOf(emptyList<PermissionItem>())
    private var startRoute by mutableStateOf(Routes.TODAY)
    private var launch by mutableIntStateOf(0)

    /** The password for the export file while Android's file dialog is open (Anf. 7.28 PS14). */
    private var filePassword: CharArray? = null

    private val createExport = registerForActivityResult(ActivityResultContracts.CreateDocument(ExportFile.MIME)) { uri ->
        val password = filePassword.also { filePassword = null } ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult password.fill(' ')
        graph.local.export(displayName(uri), password) { bytes ->
            checkNotNull(contentResolver.openOutputStream(uri, "wt")) { "no output stream" }.use { it.write(bytes) }
        }
    }

    private val openImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = filePassword.also { filePassword = null } ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult password.fill(' ')
        graph.local.fromFile(displayName(uri), password) {
            checkNotNull(contentResolver.openInputStream(uri)) { "no input stream" }.use { it.readBytes() }
        }
    }

    /** The file's name as Android's file dialog shows it. */
    private fun displayName(uri: android.net.Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Sicherungsdatei"

    /** Google Play's prominent disclosure before Android's location dialog (Anf. 7.28 PS13). */
    private var locationDisclosure by mutableStateOf(false)

    /** Precise position first; "Immer erlauben" can only be asked for afterwards. */
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshPermissions() }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Rebuilt (design, font size, process death): the same screen with its draft, not the
        // intent again – it was handled already, its extras are gone (Review-Lauf 6).
        if (savedInstanceState == null) {
            handle(intent)
        } else {
            launch = savedInstanceState.getInt(STATE_LAUNCH)
            startRoute = savedInstanceState.getString(STATE_ROUTE) ?: Routes.TODAY
        }
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val today by vm.today.collectAsStateWithLifecycle()
            val reminders by vm.reminders.collectAsStateWithLifecycle()
            val openByReminder by vm.openByReminder.collectAsStateWithLifecycle()
            val history by vm.history.collectAsStateWithLifecycle()
            val proofs by vm.proofs.collectAsStateWithLifecycle()
            val prep by vm.prep.collectAsStateWithLifecycle()
            val alert by vm.alert.collectAsStateWithLifecycle()
            val places by vm.places.collectAsStateWithLifecycle()
            val arrivalHintSeen by vm.arrivalHintSeen.collectAsStateWithLifecycle()
            val introSeen by vm.introSeen.collectAsStateWithLifecycle()
            val dwell by vm.dwellMinutes.collectAsStateWithLifecycle()
            val itemsDone by vm.itemsDone.collectAsStateWithLifecycle()
            val reminderSort by vm.reminderSort.collectAsStateWithLifecycle()
            val overview by vm.overview.collectAsStateWithLifecycle()
            val suggestions by vm.suggestions.collectAsStateWithLifecycle()
            val blockers by vm.blockers.collectAsStateWithLifecycle()
            val templates by vm.templates.collectAsStateWithLifecycle()
            val snoozeHints by vm.snoozeHints.collectAsStateWithLifecycle()
            val claudeAccess by vm.claudeAccess.collectAsStateWithLifecycle()
            val cloud by vm.cloud.collectAsStateWithLifecycle()
            val local by vm.local.collectAsStateWithLifecycle()
            var now by androidx.compose.runtime.remember { mutableStateOf(graph.now()) }
            var dnd by androidx.compose.runtime.remember { mutableStateOf(device.isDoNotDisturb()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    now = graph.now()
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
                            today, reminders.orEmpty(), openByReminder, history, proofs, theme, permissions, prep.orEmpty(), alert, dnd,
                            places = places.orEmpty(), formReady = reminders != null && prep != null && places != null,
                            appVersion = graph.appVersion, arrivalHint = !arrivalHintSeen, dwellMinutes = dwell, itemsDone = itemsDone,
                            reminderSort = reminderSort, suggestions = suggestions, overview = overview,
                            blockers = blockers, templates = templates, snoozeHints = snoozeHints,
                            claudeAccess = claudeAccess, cloud = cloud.takeIf { CloudConfig.configured() }, local = local,
                            // Only on a fresh install: never in front of existing reminders (Anf. 7.28 PS15).
                            intro = !introSeen && reminders?.isEmpty() == true,
                        ),
                        actions = actions,
                        now = now,
                        zone = ZoneId.systemDefault(),
                        startRoute = startRoute,
                        recorder = { onRecorded -> AudioRecorder("motivation", "● Botschaft aufnehmen", onRecorded) },
                    )
                }
                if (locationDisclosure) {
                    LocationDisclosureDialog(
                        onContinue = {
                            locationDisclosure = false
                            requestBackgroundLocation()
                        },
                        onLater = { locationDisclosure = false },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_LAUNCH, launch)
        outState.putString(STATE_ROUTE, startRoute)
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

    /** A mail app with address, subject and maybe a text; without one the address is shown. */
    private fun openMail(mail: android.content.Intent) {
        try {
            startActivity(mail)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, Contact.NO_MAIL_APP, android.widget.Toast.LENGTH_LONG).show()
        }
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
        if (intent?.getBooleanExtra(EXTRA_SETTINGS, false) == true) {
            intent.removeExtra(EXTRA_SETTINGS)
            startRoute = Routes.SETTINGS
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
        override fun setAlarmsPaused(until: Instant?) = vm.setAlarmsPaused(until)
        override fun setClaudeAccess(on: Boolean) = vm.setClaudeAccess(on)
        override val cloud = object : CloudActions {
            override fun setEnabled(on: Boolean) { graph.cloud.setEnabled(on) }
            // Credential Manager shows Google's account picker over this activity.
            override fun signIn() { graph.cloud.signIn(this@MainActivity) }
            override fun continueSetup() { graph.cloud.continueSetup(this@MainActivity) }
            override fun confirmKey(writtenDown: Boolean) = graph.cloud.confirmKey(writtenDown)
            override fun submitKey(text: String?) = graph.cloud.submitKey(text)
            // The password service (7.33) shows its dialog over this activity, too.
            override fun saveKey() { graph.cloud.saveKey(this@MainActivity) }
            override fun fetchKey() { graph.cloud.fetchKey(this@MainActivity) }
            override fun backUp() { graph.cloud.backUp() }
            override fun sendDiagnosis() { graph.cloud.sendDiagnosis() }
            override fun answerOverwrite(yes: Boolean) = graph.cloud.answerOverwrite(yes)
            override fun check() { graph.cloud.check() }
            override fun backUpAndCheck() { graph.cloud.backUpAndCheck() }
            override fun restore() { graph.cloud.restore() }
            override fun answerRestore(yes: Boolean) = graph.cloud.answerRestore(yes)
            override fun answerBrake(yes: Boolean) = graph.cloud.answerBrake(yes)
            override fun deleteBackup() { graph.cloud.deleteBackup(this@MainActivity) }
            override fun signOut() { graph.cloud.signOut() }
            override fun deleteAccount() { graph.cloud.deleteAccount(this@MainActivity) }
            override fun cancelDeletion() { graph.cloud.cancelDeletion() }
            override fun resetPhone() { graph.cloud.resetPhone() }
            override fun askInvitation(identifier: String?) = openMail(Contact.invitation(graph.appVersion, identifier))
        }
        override val local = object : com.avlreminder.ui.screens.LocalActions {
            override fun fromSnapshot(date: java.time.LocalDate) { graph.local.fromSnapshot(date) }
            override fun undo() { graph.local.undo() }
            override fun exportFile(password: CharArray) {
                filePassword = password
                createExport.launch(ExportFile.fileName(java.time.LocalDate.now(graph.clock)))
            }
            override fun importFile(password: CharArray) {
                filePassword = password
                // The file has no type Android knows; any file can be chosen, the app checks it.
                openImport.launch(arrayOf("*/*"))
            }
            override fun answer(yes: Boolean) = graph.local.answer(yes)
            override fun dismiss() = graph.local.dismiss()
            override fun dismissExported() = graph.local.dismissExported()
        }
        override fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice) = vm.answerSnoozeHint(reminderId, choice)
        override fun setArchived(reminderId: Long, archived: Boolean) = vm.setArchived(reminderId, archived)
        override fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) =
            vm.addSuggestion(text, important, screen, reminderId)
        override fun updateSuggestion(suggestion: SuggestionEntity) = vm.updateSuggestion(suggestion)
        override fun deleteSuggestion(id: Long) = vm.deleteSuggestion(id)
        override fun discard(occurrenceId: Long) = vm.discard(occurrenceId)
        override fun reopen(occurrenceId: Long) = vm.reopen(occurrenceId) { result ->
            android.widget.Toast.makeText(this@MainActivity, com.avlreminder.alarm.Undo.message(result), android.widget.Toast.LENGTH_LONG).show()
        }
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
        override fun dismissArrivalHint() = vm.dismissArrivalHint()
        override fun introDone() = vm.introDone()
        override fun mailFeedback() = openMail(Contact.mail(graph.appVersion))
        override fun probeAlarm(onResult: (java.time.LocalDateTime?) -> Unit) = vm.probeAlarm(onResult)
        override fun setPlaceHere(placeId: Long?, name: String, onResult: (PlaceSetResult) -> Unit) {
            if (hasLocation()) {
                vm.setPlaceHere(placeId, name, onResult)
            } else {
                // Position and WLAN (BSSID) both need the location permission.
                requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                onResult(PlaceSetResult.NoPosition)
            }
        }
    }

    /** Precise position first; "Immer erlauben" can only be asked for once it is granted. */
    private fun requestBackgroundLocation() {
        if (!hasLocation()) {
            requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            requestPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun openSettings(action: String) {
        startActivity(Intent(action, "package:$packageName".toUri()))
    }

    private fun refreshPermissions() {
        val notifications = getSystemService(NotificationManager::class.java)
        val blocked = Notifications.channelsBlockedByDnd(this)
        val dndNeeded = vm.reminders.value.orEmpty().any { it.active && it.ignoreDnd }
        val locationNeeded = vm.reminders.value.orEmpty().any { it.active && it.arrival != null }
        ExactAlarms.refresh(this)
        permissions = listOfNotNull(
            // The core (Anf. 7.28 PS10): without it alarms come late and do not keep ringing.
            PermissionItem(
                ExactAlarms.LABEL, "Kernfunktion: ohne sie klingeln Alarme nicht pünktlich", ExactAlarms.allowed(this),
                warning = ExactAlarms.WARNING,
            ) {
                startActivity(ExactAlarms.settings(this))
            },
            PermissionItem("Benachrichtigungen", "ohne sie gibt es keinen Alarm", granted(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            PermissionItem("Vollbild-Alarm", "für strenge Reminder", notifications.canUseFullScreenIntent()) {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            },
            // Recommended only (Anf. 7.28 PS12): the app works without it, but without guarantee.
            PermissionItem(Battery.LABEL, Battery.WHY, Battery.exempt(this), required = false) {
                startActivity(Battery.settings(this))
            },
            PermissionItem("Kamera", "für Foto-Nachweise", granted(Manifest.permission.CAMERA), required = false) {
                requestPermission.launch(Manifest.permission.CAMERA)
            },
            PermissionItem("Mikrofon", "für Audio-Nachweise und die Botschaft an dich", granted(Manifest.permission.RECORD_AUDIO), required = false) {
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            // Only asked for when a reminder waits for the arrival at home.
            // Android's dialog only after the app's own disclosure (Anf. 7.28 PS13, playstore.md P5).
            if (!locationNeeded) null else PermissionItem(
                "Standort „Immer erlauben“", "für die Ankunft an einem Ort (Standort und WLAN); bleibt auf dem Handy", hasBackgroundLocation(),
            ) {
                locationDisclosure = true
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
