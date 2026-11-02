package com.avlreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avlreminder.core.model.Blocker
import com.avlreminder.core.model.ItemDone
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.PrepAnswer
import com.avlreminder.core.model.Proof
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.SnoozeHint
import com.avlreminder.core.rules.SnoozeHintChoice
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.Overview
import com.avlreminder.core.usecases.ReminderSort
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.OverviewTimes
import com.avlreminder.data.SuggestionEntity
import com.avlreminder.data.SuggestionStatus
import com.avlreminder.data.TemplateEntity
import com.avlreminder.data.ThemeMode
import com.avlreminder.data.toModel
import com.avlreminder.graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Screen state straight from the database flows; all changes go through the engine. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val dao = graph.db.dao()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Moves entries from "later today" to "due now" and keeps "today" correct after midnight. */
    private val ticks = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    private fun <T> state(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val reminders: StateFlow<List<Reminder>> = state(
        emptyList(),
        combine(dao.observeReminders(), dao.observeItems()) { l, items ->
            val byReminder = items.groupBy { it.reminderId }
            l.map { it.toModel(byReminder[it.id].orEmpty()) }
        },
    )

    /** V1.1b knows one place, "Zuhause". */
    val home: StateFlow<Place?> = state(null, dao.observePlaces().map { l -> l.firstOrNull()?.toModel() })

    /** Done checklist items per occurrence. */
    val itemsDone: StateFlow<Map<Long, List<ItemDone>>> = state(
        emptyMap(),
        dao.observeItemsDone().map { l -> l.map { it.toModel() }.groupBy { it.occurrenceId } },
    )

    val dwellMinutes: StateFlow<Int> = state(com.avlreminder.data.Settings.DEFAULT_DWELL, graph.settings.dwellMinutes)

    private val open = dao.observeOpenOccurrences().map { l -> l.map { it.toModel() } }
    private val resolved = dao.observeResolved().map { l -> l.map { it.toModel() } }

    val today: StateFlow<Today?> = state(
        null,
        combine(reminders, open, resolved, ticks) { r, o, d, now -> Overview.today(r, o + d, now, zone) },
    )

    /** Open occurrence per reminder, for the reminder list. */
    val openByReminder: StateFlow<Map<Long, Occurrence>> = state(emptyMap(), open.map { l -> l.associateBy { it.reminderId } })

    val history: StateFlow<List<HistoryDay>> = state(
        emptyList(),
        combine(reminders, resolved) { r, d -> Overview.history(r, d, zone) },
    )

    /** Proofs per occurrence, for the history. */
    val proofs: StateFlow<Map<Long, List<Proof>>> = state(
        emptyMap(),
        dao.observeProofs().map { l -> l.map { it.toModel() }.groupBy { it.occurrenceId } },
    )

    /** Current "Startklar" answers per reminder. */
    val prep: StateFlow<Map<Long, Map<Int, PrepAnswer>>> = state(
        emptyMap(),
        dao.observePrepAnswers().map { l -> l.map { it.toModel() }.groupBy { it.reminderId }.mapValues { Prep.latest(it.value) } },
    )

    val theme: StateFlow<ThemeMode> = state(ThemeMode.SYSTEM, graph.settings.theme)

    val reminderSort: StateFlow<ReminderSort> = state(ReminderSort.NEXT_DUE, graph.settings.reminderSort)

    val suggestions: StateFlow<List<SuggestionEntity>> = state(emptyList(), graph.db.suggestions().observe())

    val alert: StateFlow<AlertSettings> = state(AlertSettings(), graph.settings.alert)

    /** Blockers per reminder, open and solved. */
    val blockers: StateFlow<Map<Long, List<Blocker>>> = state(
        emptyMap(),
        dao.observeBlockers().map { l -> l.map { it.toModel() }.groupBy { it.reminderId } },
    )

    /** Most recently used first; null until loaded. */
    val templates: StateFlow<List<TemplateEntity>?> = state(null, dao.observeTemplates())

    /** "Verwerfen" for an overdue deadline. */
    fun discard(occurrenceId: Long) {
        viewModelScope.launch { graph.withEngine { it.act(occurrenceId, Action.Skip(SkipReasons.DISCARDED)) } }
    }

    fun addBlocker(reminderId: Long, text: String) {
        viewModelScope.launch { graph.withEngine { it.addBlocker(reminderId, text) } }
    }

    fun solveBlocker(blockerId: Long) {
        viewModelScope.launch { graph.withEngine { it.solveBlocker(blockerId) } }
    }

    fun planTask(blockerId: Long, at: LocalDateTime) {
        viewModelScope.launch {
            val result = graph.withEngine { it.planTask(blockerId, at) }
            if (result is SaveResult.Saved) result.ring?.let(graph::ring)
        }
    }

    fun saveTemplate(template: TemplateEntity) {
        viewModelScope.launch { dao.insert(template) }
    }

    fun templateUsed(id: Long) {
        viewModelScope.launch { dao.templateUsed(id, Instant.now()) }
    }

    fun renameTemplate(id: Long, name: String) {
        viewModelScope.launch { dao.renameTemplate(id, name.trim()) }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { dao.deleteTemplate(id) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { graph.settings.setTheme(mode) }
    }

    /** New times apply to the next alarm; [sync] plans the open occurrences again. */
    fun setAlert(settings: AlertSettings) {
        viewModelScope.launch {
            graph.settings.setAlert(settings)
            graph.syncAndRing()
        }
    }

    fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) {
        viewModelScope.launch {
            val result = graph.withEngine { it.save(reminder, prep) }
            onResult(result)
            if (result is SaveResult.Saved) result.ring?.let(graph::ring)
            // Already home when saving "wenn ich zuhause bin": the geofence would not report it.
            if (result is SaveResult.Saved && reminder.schedule == Schedule.OnArrival) {
                graph.scope.launch { graph.checkPlacesAndRing() }
            }
        }
    }

    fun setHomeHere(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(graph.setHomeHere()) }
    }

    /** The geofences are registered again with the new time. */
    fun setDwellMinutes(minutes: Int) {
        viewModelScope.launch {
            graph.settings.setDwellMinutes(minutes)
            graph.syncAndRing()
        }
    }

    fun delete(reminderId: Long) {
        viewModelScope.launch { graph.withEngine { it.delete(reminderId) } }
    }

    fun setActive(reminderId: Long, active: Boolean) {
        viewModelScope.launch { graph.withEngine { it.setActive(reminderId, active) } }
    }

    fun setArchived(reminderId: Long, archived: Boolean) {
        viewModelScope.launch { graph.withEngine { it.setArchived(reminderId, archived) } }
    }

    val overview: StateFlow<OverviewTimes> = state(OverviewTimes(), graph.settings.overview)

    /** Asked again whenever a reminder or a resolved occurrence changes (NT2). */
    val snoozeHints: StateFlow<List<SnoozeHint>> = state(
        emptyList(),
        combine(dao.observeReminders(), resolved) { _, _ -> graph.withEngine { it.snoozeHints() } },
    )

    fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice) {
        viewModelScope.launch {
            val result = graph.withEngine { it.answerSnoozeHint(reminderId, choice) }
            if (result is SaveResult.Saved) result.ring?.let(graph::ring)
        }
    }

    /** null in a release build, which has no interface for Claude. */
    val claudeAccess: StateFlow<Boolean?> = state(null, graph.settings.claudeAccess.map { on -> on.takeIf { graph.debuggable } })

    fun setClaudeAccess(on: Boolean) {
        viewModelScope.launch { graph.setClaudeAccess(on) }
    }

    fun setAlarmsPaused(until: Instant?) {
        viewModelScope.launch { graph.setAlarmsPaused(until) }
    }

    fun setOverview(times: OverviewTimes) {
        viewModelScope.launch {
            graph.settings.setOverview(times)
            graph.scheduleOverview()
        }
    }

    fun setReminderSort(sort: ReminderSort) {
        viewModelScope.launch { graph.settings.setReminderSort(sort) }
    }

    fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) {
        val reminder = reminderId?.let { id -> reminders.value.firstOrNull { it.id == id } }
        viewModelScope.launch { graph.addSuggestion(text, important, screen, reminder) }
    }

    /** Edits text and "wichtig", or marks it done / discarded / open again. */
    fun updateSuggestion(suggestion: SuggestionEntity) {
        val old = suggestions.value.firstOrNull { it.id == suggestion.id }
        val statusAt = if (old?.status == suggestion.status) suggestion.statusAt
        else if (suggestion.status == SuggestionStatus.OPEN) null else Instant.now()
        viewModelScope.launch { graph.db.suggestions().update(suggestion.copy(text = suggestion.text.trim(), statusAt = statusAt)) }
    }

    fun deleteSuggestion(id: Long) {
        viewModelScope.launch { graph.db.suggestions().delete(id) }
    }

    /** Also catches up on alarms that never fired while the app was not running. */
    fun sync() {
        viewModelScope.launch { graph.syncAndRing() }
    }
}
