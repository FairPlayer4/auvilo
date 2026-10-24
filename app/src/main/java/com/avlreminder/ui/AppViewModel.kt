package com.avlreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.Overview
import com.avlreminder.core.usecases.SaveResult
import com.avlreminder.core.usecases.Today
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

    val reminders: StateFlow<List<Reminder>> = state(emptyList(), dao.observeReminders().map { l -> l.map { it.toModel() } })

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

    /** Occurrence ids that have a photo proof. */
    val photos: StateFlow<Set<Long>> = state(
        emptySet(),
        dao.observeProofs().map { l -> l.filter { it.filePath != null }.map { it.occurrenceId }.toSet() },
    )

    val theme: StateFlow<ThemeMode> = state(ThemeMode.SYSTEM, graph.settings.theme)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { graph.settings.setTheme(mode) }
    }

    fun save(reminder: Reminder, onResult: (SaveResult) -> Unit) {
        viewModelScope.launch { onResult(graph.withEngine { it.save(reminder) }) }
    }

    fun delete(reminderId: Long) {
        viewModelScope.launch { graph.withEngine { it.delete(reminderId) } }
    }

    fun setActive(reminderId: Long, active: Boolean) {
        viewModelScope.launch { graph.withEngine { it.setActive(reminderId, active) } }
    }

    /** Also catches up on alarms that never fired while the app was not running. */
    fun sync() {
        viewModelScope.launch { graph.syncAndRing() }
    }
}
