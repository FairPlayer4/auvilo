package com.avlreminder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.ReminderSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The silent overview in the morning and evening (Anf. O1); null = off. */
data class OverviewTimes(
    val morning: LocalTime? = LocalTime.of(7, 30),
    val evening: LocalTime? = LocalTime.of(20, 30),
)

private val Context.dataStore by preferencesDataStore("settings")

class Settings(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    /** Minutes after midnight. */
    private val loudFromKey = intPreferencesKey("loudFrom")
    private val dayEndKey = intPreferencesKey("dayEnd")
    private val askBlockerKey = intPreferencesKey("askBlockerAfter")
    private val dwellKey = intPreferencesKey("dwellMinutes")
    private val sortKey = stringPreferencesKey("reminderSort")
    /** Minutes after midnight; -1 = off. */
    private val morningKey = intPreferencesKey("overviewMorning")
    private val eveningKey = intPreferencesKey("overviewEvening")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val alert: Flow<AlertSettings> = context.dataStore.data.map { prefs ->
        val default = AlertSettings()
        runCatching {
            AlertSettings(
                loudFrom = prefs[loudFromKey]?.toTime() ?: default.loudFrom,
                dayEnd = prefs[dayEndKey]?.toTime() ?: default.dayEnd,
                askBlockerAfter = prefs[askBlockerKey] ?: default.askBlockerAfter,
            )
        }.getOrDefault(default)
    }

    /** How long to stay at a place before it counts as arrived (driving past does not). */
    val dwellMinutes: Flow<Int> = context.dataStore.data.map { it[dwellKey] ?: DEFAULT_DWELL }

    val reminderSort: Flow<ReminderSort> = context.dataStore.data.map { prefs ->
        prefs[sortKey]?.let { runCatching { ReminderSort.valueOf(it) }.getOrNull() } ?: ReminderSort.NEXT_DUE
    }

    val overview: Flow<OverviewTimes> = context.dataStore.data.map { prefs ->
        val default = OverviewTimes()
        fun read(key: Preferences.Key<Int>, fallback: LocalTime?) =
            when (val v = prefs[key]) {
                null -> fallback
                -1 -> null
                else -> v.toTime()
            }
        OverviewTimes(read(morningKey, default.morning), read(eveningKey, default.evening))
    }

    suspend fun setOverview(times: OverviewTimes) {
        context.dataStore.edit {
            it[morningKey] = times.morning?.toMinutes() ?: -1
            it[eveningKey] = times.evening?.toMinutes() ?: -1
        }
    }

    suspend fun setReminderSort(sort: ReminderSort) {
        context.dataStore.edit { it[sortKey] = sort.name }
    }

    suspend fun setDwellMinutes(minutes: Int) {
        context.dataStore.edit { it[dwellKey] = minutes }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setAlert(settings: AlertSettings) {
        context.dataStore.edit {
            it[loudFromKey] = settings.loudFrom.toMinutes()
            it[dayEndKey] = settings.dayEnd.toMinutes()
            it[askBlockerKey] = settings.askBlockerAfter
        }
    }

    companion object {
        const val DEFAULT_DWELL = 3
        val DWELL_CHOICES = listOf(1, 3, 5, 10)
    }

    private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
    private fun LocalTime.toMinutes(): Int = hour * 60 + minute
}
