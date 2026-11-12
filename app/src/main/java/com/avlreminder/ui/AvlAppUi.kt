package com.avlreminder.ui

import com.avlreminder.core.model.Schedule
import com.avlreminder.PlaceSetResult
import com.avlreminder.cloud.CloudStatus
import com.avlreminder.ui.screens.CloudActions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
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
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.Reminder
import com.avlreminder.alarm.AlarmPause
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Blockers
import com.avlreminder.core.rules.SnoozeHint
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.rules.Stats
import com.avlreminder.core.rules.WeeklyGoals
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.ReminderSort
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.OverviewTimes
import com.avlreminder.data.Settings
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.data.SuggestionStatus
import com.avlreminder.data.TemplateEntity
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.BlockerActions
import com.avlreminder.ui.screens.EditScreen
import com.avlreminder.ui.screens.HistoryScreen
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.RemindersScreen
import com.avlreminder.ui.screens.SettingsScreen
import com.avlreminder.ui.screens.StatsScreen
import com.avlreminder.ui.screens.SuggestButton
import com.avlreminder.ui.screens.TopActions
import com.avlreminder.ui.screens.SuggestionDialog
import com.avlreminder.ui.screens.SuggestionsScreen
import com.avlreminder.ui.screens.TemplateChooser
import com.avlreminder.ui.screens.TodayScreen
import com.avlreminder.ui.screens.toTemplate
import com.avlreminder.ui.screens.withTemplate
import com.avlreminder.ui.theme.Avl
import java.time.Instant
import java.time.LocalDateTime
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
    /** The first is "Zuhause" (Anf. 7.29 OZ6). */
    val places: List<Place> = emptyList(),
    /** The one-time hint about the arrival window (OZ9) is still to show. */
    val arrivalHint: Boolean = false,
    val dwellMinutes: Int = Settings.DEFAULT_DWELL,
    /** Done checklist items per occurrence. */
    val itemsDone: Map<Long, List<ItemDone>> = emptyMap(),
    val reminderSort: ReminderSort = ReminderSort.NEXT_DUE,
    /** Open ones first (Anf. 7.24). */
    val suggestions: List<SuggestionEntity> = emptyList(),
    val overview: OverviewTimes = OverviewTimes(),
    /** Blockers per reminder, open and solved (S5). */
    val blockers: Map<Long, List<Blocker>> = emptyMap(),
    /** Most recently used first; null until loaded. */
    val templates: List<TemplateEntity>? = emptyList(),
    /** Reminders snoozed again and again (NT2). */
    val snoozeHints: List<SnoozeHint> = emptyList(),
    /** Interface for Claude (Anf. 7.26 AG11): null = not in this build. */
    val claudeAccess: Boolean? = null,
    /** Cloud backup (A-14): null = not shown. */
    val cloud: CloudStatus? = null,
    val local: com.avlreminder.restore.LocalStatus? = null,
    /** Reminders, "Startklar" answers and places are loaded; the form is built only then (Review-Lauf 5). */
    val formReady: Boolean = true,
    /** versionName of the build, for "Einstellungen". */
    val appVersion: String = "",
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
    /** Sets a place (null: a new one) to the current position and WLAN. */
    fun setPlaceHere(placeId: Long?, name: String, onResult: (PlaceSetResult) -> Unit) {}
    fun dismissArrivalHint() {}
    fun setDwellMinutes(minutes: Int) {}
    fun setReminderSort(sort: ReminderSort) {}
    /** Archive pauses; restore keeps it paused. */
    fun setArchived(reminderId: Long, archived: Boolean) {}
    fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) {}
    fun updateSuggestion(suggestion: SuggestionEntity) {}
    fun deleteSuggestion(id: Long) {}
    /** Hands the text to another app of the user's choice. */
    fun share(text: String) {}
    fun setOverview(times: OverviewTimes) {}
    /** "Verwerfen" for an overdue deadline. */
    fun discard(occurrenceId: Long) {}
    /** "Zurücknehmen" after a misclick (Anf. 7.31). */
    fun reopen(occurrenceId: Long) {}
    fun addBlocker(reminderId: Long, text: String) {}
    fun solveBlocker(blockerId: Long) {}
    /** "Als Aufgabe planen": a relaxed one-off for the blocker. */
    fun planTask(blockerId: Long, at: LocalDateTime) {}
    fun saveTemplate(template: TemplateEntity) {}
    fun templateUsed(id: Long) {}
    fun renameTemplate(id: Long, name: String) {}
    fun deleteTemplate(id: Long) {}
    /** "Alle Alarme pausieren" (PS7); null switches them on again. */
    fun setAlarmsPaused(until: Instant?) {}
    fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice) {}
    fun setClaudeAccess(on: Boolean) {}
    val cloud: CloudActions get() = object : CloudActions {}
    val local: com.avlreminder.ui.screens.LocalActions get() = object : com.avlreminder.ui.screens.LocalActions {}
}

/** Simple navigation without a library: one route string, back goes to "today". */
object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val SUGGESTIONS = "suggestions"
    const val NEW = "edit:0"
    /** "Neuer Reminder": pick a template or start empty. */
    const val CHOOSE = "choose"
    fun edit(id: Long) = "edit:$id"
    fun fromTemplate(id: Long) = "template:$id"
    fun stats(id: Long) = "stats:$id"
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
    // Saveable like the form draft: they survive the activity being recreated (Review-Lauf 5).
    var problems by rememberSaveable { mutableStateOf(emptyList<String>()) }
    /** Screen name and reminder for the open suggestion dialog. */
    var suggestFrom by rememberSaveable { mutableStateOf<Pair<String, Long?>?>(null) }
    val date = now.atZone(zone).toLocalDate()
    val tab = route in listOf(Routes.TODAY, Routes.HISTORY, Routes.REMINDERS)
    fun go(to: String) {
        problems = emptyList()
        route = to
    }
    BackHandler(enabled = route != Routes.TODAY) {
        go(
            when {
                route == Routes.SUGGESTIONS -> Routes.SETTINGS
                route.startsWith("stats:") -> "edit:" + route.removePrefix("stats:")
                else -> Routes.TODAY
            },
        )
    }
    val suggestHere: (String) -> Unit = { screen -> suggestFrom = screen to null }
    val resolved = remember(state.history) { state.history.flatMap { d -> d.entries.map { it.occurrence } } }

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
                FloatingActionButton(onClick = { go(if (state.templates?.isEmpty() == true) Routes.NEW else Routes.CHOOSE) }, containerColor = c.accentFill, contentColor = Color.White) {
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
                            today = today, date = date, zone = zone, now = now,
                            onOpenDue = { actions.openAlarm(it.occurrence.id) },
                            onEdit = { go(Routes.edit(it.id)) },
                            onCompleteEarly = { actions.completeEarly(it.occurrence.id) },
                            onDiscard = { actions.discard(it.occurrence.id) },
                            doNotDisturb = state.doNotDisturb,
                            itemsDone = state.itemsDone,
                            weeks = state.reminders.filter { it.active }.mapNotNull { r -> WeeklyGoals.status(r, resolved, date, zone)?.let { r to it } },
                            pausedUntil = state.alert.alarmsPausedUntil?.takeIf { state.alert.alarmsPaused(now) }?.let { AlarmPause.untilText(it, now, zone) },
                            onResume = { actions.setAlarmsPaused(null) },
                            places = state.places,
                            arrivalHint = state.arrivalHint && state.reminders.any { r ->
                                r.active && r.schedule != Schedule.OnArrival && r.arrival?.hasWindow == true
                            },
                            onDismissArrivalHint = actions::dismissArrivalHint,
                            hints = state.snoozeHints,
                            onHint = { h, choice -> actions.answerSnoozeHint(h.reminder.id, choice) },
                            onReopen = { actions.reopen(it.occurrence.id) },
                        )
                    }
                }
                TopActions(Modifier.align(Alignment.TopEnd), { suggestHere("Heute") }) { go(Routes.SETTINGS) }
            }
            route == Routes.HISTORY -> Box(m) {
                HistoryScreen(state.history, state.proofs, date, zone, onPlay = actions::play, itemsDone = state.itemsDone)
                TopActions(Modifier.align(Alignment.TopEnd), { suggestHere("Verlauf") }) { go(Routes.SETTINGS) }
            }
            route == Routes.REMINDERS -> Box(m) {
                RemindersScreen(
                    reminders = state.reminders, openByReminder = state.openByReminder, today = date, zone = zone,
                    onEdit = { go(Routes.edit(it.id)) },
                    onSetActive = { r, active -> actions.setActive(r.id, active) },
                    sort = state.reminderSort, onSort = actions::setReminderSort,
                    onRestore = { actions.setArchived(it.id, false) },
                    places = state.places,
                )
                TopActions(Modifier.align(Alignment.TopEnd), { suggestHere("Reminder") }) { go(Routes.SETTINGS) }
            }
            route == Routes.SETTINGS -> Box(m) {
                SettingsScreen(
                    theme = state.theme, onTheme = actions::setTheme, alert = state.alert, onAlert = actions::setAlert,
                    onPause = actions::setAlarmsPaused, now = now, zone = zone, version = state.appVersion,
                    claudeAccess = state.claudeAccess, onClaudeAccess = actions::setClaudeAccess,
                    cloud = state.cloud, cloudActions = actions.cloud,
                    local = state.local, localActions = actions.local,
                    permissions = state.permissions, onBack = { go(Routes.TODAY) },
                    places = state.places, onSetPlace = actions::setPlaceHere,
                    dwellMinutes = state.dwellMinutes, onDwell = actions::setDwellMinutes,
                    openSuggestions = state.suggestions.count { it.status == SuggestionStatus.OPEN },
                    onSuggestions = { go(Routes.SUGGESTIONS) },
                    overview = state.overview, onOverview = actions::setOverview,
                    templates = state.templates.orEmpty(), onRenameTemplate = actions::renameTemplate, onDeleteTemplate = actions::deleteTemplate,
                )
                SuggestButton({ suggestHere("Einstellungen") }, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            route == Routes.SUGGESTIONS -> Box(m) {
                SuggestionsScreen(
                    state.suggestions, zone, onBack = { go(Routes.SETTINGS) },
                    onUpdate = actions::updateSuggestion, onDelete = actions::deleteSuggestion, onShare = actions::share,
                )
                SuggestButton({ suggestHere("Verbesserungen") }, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            route.startsWith("stats:") -> {
                val reminder = state.reminders.firstOrNull { it.id == route.removePrefix("stats:").toLong() }
                if (reminder == null) {
                    // Deleted meanwhile.
                    LaunchedEffect(Unit) { go(Routes.REMINDERS) }
                } else {
                    Box(m) {
                        StatsScreen(
                            reminder, Stats.of(reminder, resolved, now, zone), WeeklyGoals.status(reminder, resolved, date, zone),
                            onBack = { go(Routes.edit(reminder.id)) },
                        )
                        SuggestButton({ suggestFrom = "Statistik" to reminder.id }, Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                }
            }
            route == Routes.CHOOSE -> when (val templates = state.templates) {
                null -> {}
                // The last template was deleted meanwhile: straight to the empty form.
                emptyList<TemplateEntity>() -> LaunchedEffect(Unit) { go(Routes.NEW) }
                else -> TemplateChooser(
                    templates,
                    onPick = { t ->
                        if (t == null) {
                            go(Routes.NEW)
                        } else {
                            actions.templateUsed(t.id)
                            go(Routes.fromTemplate(t.id))
                        }
                    },
                    onCancel = { go(Routes.TODAY) },
                    modifier = m,
                )
            }
            route.startsWith("edit:") || route.startsWith("template:") -> if (!state.formReady || (route.startsWith("template:") && state.templates == null)) {
                // Built once reminders, "Startklar" answers, places (and templates) are loaded: a cold
                // start on this route would otherwise build it from what was there first (Review-Lauf 5).
                Box(m)
            } else {
                    val fromTemplate = route.startsWith("template:")
                    val id = if (fromTemplate) 0L else route.removePrefix("edit:").toLong()
                    val existing = state.reminders.firstOrNull { it.id == id }
                    val initial = remember(route, existing != null) {
                        val template = if (fromTemplate) state.templates?.firstOrNull { it.id == route.removePrefix("template:").toLong() } else null
                        existing?.let { ReminderForm.of(it, date, state.prep[it.id].orEmpty(), state.places.firstOrNull()?.id) }
                            ?: ReminderForm.new(now, zone, state.places.firstOrNull()?.id).let { f -> template?.let { f.withTemplate(it) } ?: f }
                    }
                    val blockers = existing?.let { state.blockers[it.id] }.orEmpty()
                    EditScreen(
                        initial = initial,
                        problems = problems,
                        places = state.places,
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
                        onSuggest = { suggestFrom = (if (id == 0L) "Neuer Reminder" else "Bearbeiten") to existing?.id },
                        onArchive = existing?.let { r -> { archived -> actions.setArchived(r.id, archived); go(Routes.REMINDERS) } },
                        archived = existing?.archivedAt != null,
                        onStats = existing?.let { r -> { go(Routes.stats(r.id)) } },
                        onCheckItems = existing?.takeIf { it.checklist.isNotEmpty() && it.active }
                            ?.let { state.openByReminder[it.id] }?.let { o -> { actions.completeEarly(o.id) } },
                        onSaveTemplate = { name, form -> actions.saveTemplate(form.toTemplate(name, now)) },
                        blockers = blockers,
                        blockerActions = existing?.let { r ->
                            BlockerActions(
                                add = { actions.addBlocker(r.id, it) },
                                solve = actions::solveBlocker,
                                plan = actions::planTask,
                                suggestedTime = Blockers.suggestTaskTime(state.openByReminder[r.id]?.currentAt, now, zone),
                                taskAt = { taskId -> state.openByReminder[taskId]?.currentAt },
                            )
                        },
                    )
            }
        }
    }
    suggestFrom?.let { (screen, reminderId) ->
        SuggestionDialog(
            onSave = { text, important ->
                actions.addSuggestion(text, important, screen, reminderId)
                suggestFrom = null
            },
            onDismiss = { suggestFrom = null },
        )
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
