package com.avlreminder.ui

import android.widget.Toast
import com.avlreminder.PlaceSetResult
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
import com.avlreminder.core.rules.Action
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.Checklists
import com.avlreminder.core.rules.Prep
import com.avlreminder.core.rules.SkipReasons
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.system.Diag
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
import com.avlreminder.graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
    /** Templates only; everything of the reminders is read through [repo] (Review-Lauf 5, E21). */
    private val dao = graph.db.dao()
    private val repo = graph.repository
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Moves entries from "later today" to "due now" and keeps "today" correct after midnight. */
    private val ticks = flow {
        while (true) {
            emit(graph.now())
            delay(30_000)
        }
    }

    private fun <T> state(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** null until loaded, like [places] and [prep]: the form waits for them (Review-Lauf 5). */
    val reminders: StateFlow<List<Reminder>?> = state(
        null,
        repo.observeReminders(),
    )

    /** The places with their WLANs; the first is "Zuhause" (Anf. 7.29 OZ6). */
    val places: StateFlow<List<Place>?> = state(
        null,
        repo.observePlaces(),
    )

    val arrivalHintSeen: StateFlow<Boolean> = state(true, graph.settings.arrivalHintSeen)


    val dwellMinutes: StateFlow<Int> = state(com.avlreminder.data.Settings.DEFAULT_DWELL, graph.settings.dwellMinutes)

    private val open = repo.observeOpenOccurrences()
    private val resolved = repo.observeResolved()

    val today: StateFlow<Today?> = state(
        null,
        combine(reminders.filterNotNull(), open, resolved, ticks) { r, o, d, now -> Overview.today(r, o + d, now, zone) },
    )

    /**
     * Done checklist items per occurrence; for the open one of a list over several times a day
     * those done since the list was last complete (VS1).
     */
    val itemsDone: StateFlow<Map<Long, List<ItemDone>>> = state(
        emptyMap(),
        combine(repo.observeItemsDone(), reminders.filterNotNull(), open, resolved) { l, r, o, d ->
            Checklists.doneByOccurrence(r, o, d, l)
        },
    )

    /** Open occurrence per reminder, for the reminder list. */
    val openByReminder: StateFlow<Map<Long, Occurrence>> = state(emptyMap(), open.map { l -> l.associateBy { it.reminderId } })

    val history: StateFlow<List<HistoryDay>> = state(
        emptyList(),
        combine(reminders.filterNotNull(), resolved) { r, d -> Overview.history(r, d, zone) },
    )

    /** Proofs per occurrence, for the history. */
    val proofs: StateFlow<Map<Long, List<Proof>>> = state(
        emptyMap(),
        repo.observeProofs().map { l -> l.groupBy { it.occurrenceId } },
    )

    /** Current "Startklar" answers per reminder. */
    val prep: StateFlow<Map<Long, Map<Int, PrepAnswer>>?> = state(
        null,
        repo.observePrepAnswers().map { l -> l.groupBy { it.reminderId }.mapValues { Prep.latest(it.value) } },
    )

    val theme: StateFlow<ThemeMode> = state(ThemeMode.SYSTEM, graph.settings.theme)

    val reminderSort: StateFlow<ReminderSort> = state(ReminderSort.NEXT_DUE, graph.settings.reminderSort)

    val suggestions: StateFlow<List<SuggestionEntity>> = state(emptyList(), graph.db.suggestions().observe())

    val alert: StateFlow<AlertSettings> = state(AlertSettings(), graph.settings.alert)

    /** Blockers per reminder, open and solved. */
    val blockers: StateFlow<Map<Long, List<Blocker>>> = state(
        emptyMap(),
        repo.observeBlockers().map { l -> l.groupBy { it.reminderId } },
    )

    /** Most recently used first; null until loaded. */
    val templates: StateFlow<List<TemplateEntity>?> = state(null, dao.observeTemplates())

    /** "Verwerfen" for an overdue deadline. */
    fun discard(occurrenceId: Long) {
        viewModelScope.launch { graph.withEngine { it.act(occurrenceId, Action.Skip(SkipReasons.DISCARDED)) } }
    }

    /** "Zurücknehmen" (Anf. 7.31); [onResult] says what happened. */
    fun reopen(occurrenceId: Long, onResult: (TransitionResult) -> Unit) {
        viewModelScope.launch {
            val result = graph.withEngine { it.reopen(occurrenceId) }
            Diag.log("today reopen occ=$occurrenceId → ${result::class.simpleName}")
            onResult(result)
        }
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
            // Rejected (e.g. the time is past): say why instead of doing nothing (Review-Lauf 5).
            if (result is SaveResult.Invalid) rejected(result.problems)
        }
    }

    fun saveTemplate(template: TemplateEntity) {
        viewModelScope.launch { dao.insert(template) }
    }

    fun templateUsed(id: Long) {
        viewModelScope.launch { dao.templateUsed(id, graph.now()) }
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
            graph.rebuild("alert settings")
        }
    }

    fun save(reminder: Reminder, prep: List<PrepAnswer>, onResult: (SaveResult) -> Unit) {
        viewModelScope.launch {
            val result = graph.withEngine { it.save(reminder, prep) }
            onResult(result)
        }
    }

    fun setPlaceHere(placeId: Long?, name: String, onResult: (PlaceSetResult) -> Unit) {
        viewModelScope.launch { onResult(graph.setPlaceHere(placeId, name)) }
    }

    fun dismissArrivalHint() {
        viewModelScope.launch { graph.settings.setArrivalHintSeen() }
    }

    /** The geofences are registered again with the new time. */
    fun setDwellMinutes(minutes: Int) {
        viewModelScope.launch {
            graph.settings.setDwellMinutes(minutes)
            graph.rebuild("dwell time")
        }
    }

    fun delete(reminderId: Long) {
        viewModelScope.launch { graph.withEngine { it.delete(reminderId) } }
    }

    fun setActive(reminderId: Long, active: Boolean) {
        viewModelScope.launch { if (!graph.withEngine { it.setActive(reminderId, active) }) notChanged(reminderId) }
    }

    fun setArchived(reminderId: Long, archived: Boolean) {
        viewModelScope.launch { if (!graph.withEngine { it.setArchived(reminderId, archived) }) notChanged(reminderId) }
    }

    /**
     * Pausing or archiving was rejected: a running session (design.md 3.1) or the reminder is gone
     * meanwhile (Review-Lauf 5); say which instead of doing nothing.
     */
    private suspend fun notChanged(reminderId: Long) {
        if (graph.repository.reminder(reminderId) == null) rejected(listOf("Reminder nicht gefunden"))
        else Toast.makeText(getApplication(), "Läuft gerade – erst die Sitzung beenden", Toast.LENGTH_LONG).show()
    }

    private fun rejected(problems: List<String>) {
        Toast.makeText(getApplication(), "Nicht möglich: ${problems.joinToString("; ")}", Toast.LENGTH_LONG).show()
    }

    val overview: StateFlow<OverviewTimes> = state(OverviewTimes(), graph.settings.overview)

    /** Asked again whenever a reminder or a resolved occurrence changes (NT2). */
    val snoozeHints: StateFlow<List<SnoozeHint>> = state(
        emptyList(),
        combine(repo.observeReminders(), resolved) { _, _ -> graph.withEngine { it.snoozeHints() } },
    )

    fun answerSnoozeHint(reminderId: Long, choice: SnoozeHintChoice) {
        viewModelScope.launch {
            val result = graph.withEngine { it.answerSnoozeHint(reminderId, choice) }
            // Rejected (e.g. "Überwindung" without the "Startklar" answers): say why (Review-Lauf 1).
            if (result is SaveResult.Invalid) rejected(result.problems)
        }
    }

    /** null in a release build, which has no interface for Claude. */
    val claudeAccess: StateFlow<Boolean?> = state(null, graph.settings.claudeAccess.map { on -> on.takeIf { graph.debuggable } })

    /** Cloud backup (A-14). */
    val cloud: StateFlow<com.avlreminder.cloud.CloudStatus> = graph.cloud.status

    /** Daily copies and undo (R-1, W-6). */
    val local: StateFlow<com.avlreminder.restore.LocalStatus> = graph.local.status

    fun setClaudeAccess(on: Boolean) {
        viewModelScope.launch { graph.setClaudeAccess(on) }
    }

    fun setAlarmsPaused(until: Instant?) {
        viewModelScope.launch { graph.setAlarmsPaused(until) }
    }

    fun setOverview(times: OverviewTimes) {
        viewModelScope.launch {
            graph.settings.setOverview(times)
            graph.rebuild("overview times")
        }
    }

    fun setReminderSort(sort: ReminderSort) {
        viewModelScope.launch { graph.settings.setReminderSort(sort) }
    }

    fun addSuggestion(text: String, important: Boolean, screen: String, reminderId: Long?) {
        val reminder = reminderId?.let { id -> reminders.value.orEmpty().firstOrNull { it.id == id } }
        viewModelScope.launch { graph.addSuggestion(text, important, screen, reminder) }
    }

    /** Edits text and "wichtig", or marks it done / discarded / open again. */
    fun updateSuggestion(suggestion: SuggestionEntity) {
        val old = suggestions.value.firstOrNull { it.id == suggestion.id }
        val statusAt = if (old?.status == suggestion.status) suggestion.statusAt
        else if (suggestion.status == SuggestionStatus.OPEN) null else graph.now()
        viewModelScope.launch { graph.db.suggestions().update(suggestion.copy(text = suggestion.text.trim(), statusAt = statusAt)) }
    }

    fun deleteSuggestion(id: Long) {
        viewModelScope.launch { graph.db.suggestions().delete(id) }
    }

    /** Also catches up on alarms that never fired while the app was not running. */
    fun sync() {
        viewModelScope.launch { graph.rebuild("app in front") }
    }
}
