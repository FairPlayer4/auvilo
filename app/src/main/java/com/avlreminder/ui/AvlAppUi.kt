package com.avlreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.EditScreen
import com.avlreminder.ui.screens.HistoryScreen
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.RemindersScreen
import com.avlreminder.ui.screens.SettingsScreen
import com.avlreminder.ui.screens.TodayScreen
import com.avlreminder.ui.theme.Avl
import java.time.Instant
import java.time.ZoneId

/** Everything the main UI shows and can do; the activity fills it from the view model. */
data class AppState(
    val today: Today?,
    val reminders: List<Reminder>,
    val openByReminder: Map<Long, Occurrence>,
    val history: List<HistoryDay>,
    val proofs: Map<Long, List<Proof>>,
    val theme: ThemeMode,
    val permissions: List<PermissionItem>,
    /** Current "Startklar" answers per reminder. */
    val prep: Map<Long, Map<Int, PrepAnswer>> = emptyMap(),
    val alert: AlertSettings = AlertSettings(),
    /** "Nicht stören" is on right now. */
    val doNotDisturb: Boolean = false,
)

interface AppActions {
    fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit)
    fun delete(reminderId: Long)
    fun setActive(reminderId: Long, active: Boolean)
    fun setTheme(mode: ThemeMode)
    fun setAlert(settings: AlertSettings)
    fun openAlarm(occurrenceId: Long)
    /** "Vorab erledigen": the alarm screen for an occurrence that is not due yet. */
    fun completeEarly(occurrenceId: Long)
    fun play(path: String)
}

/** Simple navigation without a library: one route string, back goes to "today". */
object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val NEW = "edit:0"
    fun edit(id: Long) = "edit:$id"
}

@Composable
fun AvlAppUi(
    state: AppState,
    actions: AppActions,
    now: Instant,
    zone: ZoneId,
    startRoute: String = Routes.TODAY,
    /** Record button for the motivation audio; empty in previews and tests. */
    recorder: @Composable (onRecorded: (String, Int) -> Unit) -> Unit = {},
) {
    val c = Avl.colors
    var route by rememberSaveable { mutableStateOf(startRoute) }
    var problems by remember { mutableStateOf(emptyList<String>()) }
    val date = now.atZone(zone).toLocalDate()
    val tab = route in listOf(Routes.TODAY, Routes.HISTORY, Routes.REMINDERS)
    fun go(to: String) {
        problems = emptyList()
        route = to
    }
    BackHandler(enabled = route != Routes.TODAY) { go(Routes.TODAY) }

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            if (tab) {
                NavigationBar(containerColor = c.surface) {
                    listOf(
                        Triple(Routes.TODAY, "Heute", AvlIcons.Today),
                        Triple(Routes.HISTORY, "Verlauf", AvlIcons.History),
                        Triple(Routes.REMINDERS, "Reminder", AvlIcons.List),
                    ).forEach { (r, label, icon) ->
                        NavigationBarItem(selected = route == r, onClick = { go(r) }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == Routes.TODAY || route == Routes.REMINDERS) {
                FloatingActionButton(onClick = { go(Routes.NEW) }, containerColor = c.accentFill, contentColor = Color.White) {
                    Icon(AvlIcons.Add, "Neuer Reminder")
                }
            }
        },
    ) { padding ->
        val m = Modifier.fillMaxSize().padding(padding)
        when {
            route == Routes.TODAY -> Box(m) {
                Column {
                    if (state.permissions.any { it.required && !it.granted }) PermissionBanner { go(Routes.SETTINGS) }
                    state.today?.let { today ->
                        TodayScreen(
                            today = today, date = date, zone = zone,
                            onOpenDue = { actions.openAlarm(it.occurrence.id) },
                            onEdit = { go(Routes.edit(it.id)) },
                            onCompleteEarly = { actions.completeEarly(it.occurrence.id) },
                            doNotDisturb = state.doNotDisturb,
                        )
                    }
                }
                IconButton(onClick = { go(Routes.SETTINGS) }, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(AvlIcons.Settings, "Einstellungen", tint = c.text)
                }
            }
            route == Routes.HISTORY -> HistoryScreen(state.history, state.proofs, date, zone, m, onPlay = actions::play)
            route == Routes.REMINDERS -> RemindersScreen(
                reminders = state.reminders, openByReminder = state.openByReminder, today = date, zone = zone,
                onEdit = { go(Routes.edit(it.id)) },
                onSetActive = { r, active -> actions.setActive(r.id, active) },
                modifier = m,
            )
            route == Routes.SETTINGS -> SettingsScreen(
                theme = state.theme, onTheme = actions::setTheme, alert = state.alert, onAlert = actions::setAlert,
                permissions = state.permissions, onBack = { go(Routes.TODAY) }, modifier = m,
            )
            route.startsWith("edit:") -> {
                val id = route.removePrefix("edit:").toLong()
                val existing = state.reminders.firstOrNull { it.id == id }
                val initial = remember(id, existing != null) {
                    existing?.let { ReminderForm.of(it, date, state.prep[it.id].orEmpty()) } ?: ReminderForm.new(now, zone)
                }
                EditScreen(
                    initial = initial,
                    problems = problems,
                    onSave = { form ->
                        val reminder = form.toReminder(now)
                        if (reminder == null) {
                            problems = listOf("Mindestens einen Wochentag wählen")
                        } else {
                            actions.save(reminder, form.prepAnswers(now)) { result ->
                                when (result) {
                                    is SaveResult.Saved -> go(Routes.TODAY)
                                    is SaveResult.Invalid -> problems = result.problems
                                }
                            }
                        }
                    },
                    onCancel = { go(Routes.TODAY) },
                    onDelete = existing?.let { r -> { actions.delete(r.id); go(Routes.TODAY) } },
                    modifier = m,
                    recorder = recorder,
                    play = actions::play,
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    val c = Avl.colors
    Text(
        "Berechtigungen fehlen – ohne sie kann ein Alarm ausbleiben. Hier einrichten.",
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp, top = 12.dp).clip(RoundedCornerShape(12.dp))
            .background(c.strict).clickable(onClick = onClick).padding(12.dp),
        color = c.onStrict, fontWeight = FontWeight.SemiBold,
    )
}
