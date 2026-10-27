package com.avlreminder.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avlreminder.core.rules.AlertSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore("settings")

class Settings(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    /** Minutes after midnight. */
    private val loudFromKey = intPreferencesKey("loudFrom")
    private val dayEndKey = intPreferencesKey("dayEnd")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val alert: Flow<AlertSettings> = context.dataStore.data.map { prefs ->
        val default = AlertSettings()
        runCatching {
            AlertSettings(
                loudFrom = prefs[loudFromKey]?.toTime() ?: default.loudFrom,
                dayEnd = prefs[dayEndKey]?.toTime() ?: default.dayEnd,
            )
        }.getOrDefault(default)
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setAlert(settings: AlertSettings) {
        context.dataStore.edit {
            it[loudFromKey] = settings.loudFrom.toMinutes()
            it[dayEndKey] = settings.dayEnd.toMinutes()
        }
    }

    private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)
    private fun LocalTime.toMinutes(): Int = hour * 60 + minute
}
