package com.avlreminder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.DayTimes
import com.avlreminder.core.usecases.ReminderSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The silent overview in the morning and evening (Anf. O1); null = off. [weekend]: other times
 * on Saturday and Sunday (VS2), null = the same.
 */
data class OverviewTimes(
    val morning: LocalTime? = LocalTime.of(7, 30),
    val evening: LocalTime? = LocalTime.of(20, 30),
    val weekend: OverviewTimes? = null,
) {
    /** The times that apply on [date]. */
    fun on(date: LocalDate): OverviewTimes =
        if (weekend != null && date.dayOfWeek in AlertSettings.WEEKEND) weekend else this
}

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
    /** Weekend (VS2): absent = the same as on weekdays; overview -1 = off, -2 = absent. */
    private val weekendLoudKey = intPreferencesKey("weekendLoudFrom")
    private val weekendEndKey = intPreferencesKey("weekendDayEnd")
    private val weekendOnKey = intPreferencesKey("weekendOverview")
    private val weekendMorningKey = intPreferencesKey("weekendOverviewMorning")
    private val weekendEveningKey = intPreferencesKey("weekendOverviewEvening")
    /** Interface for Claude over adb (Anf. 7.26 AG11), debug build only; absent = off. */
    private val claudeKey = intPreferencesKey("claudeAccess")
    /** Epoch milliseconds; absent = alarms not paused (PS7). */
    private val pausedUntilKey = longPreferencesKey("alarmsPausedUntil")
    /** Epoch milliseconds when the running pause was switched on. */
    private val pausedFromKey = longPreferencesKey("alarmsPausedFrom")

    /** What goes into the backup (B2b); the pause and the interface stay on the device. */
    private val backupInts = listOf(
        loudFromKey, dayEndKey, askBlockerKey, dwellKey, morningKey, eveningKey, weekendLoudKey,
        weekendEndKey, weekendOnKey, weekendMorningKey, weekendEveningKey,
    )
    private val backupStrings = listOf(themeKey, sortKey)

    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val alert: Flow<AlertSettings> = context.dataStore.data.map { prefs ->
        val default = AlertSettings()
        runCatching {
            AlertSettings(
                loudFrom = prefs[loudFromKey]?.toTime() ?: default.loudFrom,
                dayEnd = prefs[dayEndKey]?.toTime() ?: default.dayEnd,
                askBlockerAfter = prefs[askBlockerKey]?.takeIf { it in 1..5 } ?: default.askBlockerAfter,
                weekend = runCatching {
                    DayTimes(requireNotNull(prefs[weekendLoudKey]?.toTime()), requireNotNull(prefs[weekendEndKey]?.toTime()))
                }.getOrNull(),
                alarmsPausedUntil = prefs[pausedUntilKey]?.let(Instant::ofEpochMilli),
                alarmsPausedFrom = prefs[pausedFromKey]?.let(Instant::ofEpochMilli),
            )
        }.getOrDefault(default)
    }

    /** How long to stay at a place before it counts as arrived (driving past does not). */
    val dwellMinutes: Flow<Int> = context.dataStore.data.map { prefs -> prefs[dwellKey]?.takeIf { it in DWELL_RANGE } ?: DEFAULT_DWELL }

    val reminderSort: Flow<ReminderSort> = context.dataStore.data.map { prefs ->
        prefs[sortKey]?.let { runCatching { ReminderSort.valueOf(it) }.getOrNull() } ?: ReminderSort.NEXT_DUE
    }

    val overview: Flow<OverviewTimes> = context.dataStore.data.map { prefs ->
        val default = OverviewTimes()
        fun read(key: Preferences.Key<Int>, fallback: LocalTime?) =
            when (val v = prefs[key]) {
                null -> fallback
                -1 -> null
                // A value out of range (only an old or foreign backup could bring one) is not a time.
                else -> v.toTime() ?: fallback
            }
        val weekdays = OverviewTimes(read(morningKey, default.morning), read(eveningKey, default.evening))
        val weekend = if (prefs[weekendOnKey] == 1) {
            OverviewTimes(read(weekendMorningKey, weekdays.morning), read(weekendEveningKey, weekdays.evening))
        } else {
            null
        }
        weekdays.copy(weekend = weekend)
    }

    suspend fun setOverview(times: OverviewTimes) {
        context.dataStore.edit {
            it[morningKey] = times.morning?.toMinutes() ?: -1
            it[eveningKey] = times.evening?.toMinutes() ?: -1
            val weekend = times.weekend
            it[weekendOnKey] = if (weekend == null) 0 else 1
            if (weekend != null) {
                it[weekendMorningKey] = weekend.morning?.toMinutes() ?: -1
                it[weekendEveningKey] = weekend.evening?.toMinutes() ?: -1
            }
        }
    }

    val claudeAccess: Flow<Boolean> = context.dataStore.data.map { it[claudeKey] == 1 }

    suspend fun setClaudeAccess(on: Boolean) {
        context.dataStore.edit { it[claudeKey] = if (on) 1 else 0 }
    }

    /**
     * "Alle Alarme pausieren" (PS7); null switches the alarms on again. A running pause that is
     * extended keeps its start.
     */
    suspend fun setAlarmsPaused(until: Instant?, now: Instant) {
        context.dataStore.edit {
            if (until == null) {
                it.remove(pausedUntilKey)
                it.remove(pausedFromKey)
            } else {
                val running = it[pausedUntilKey]?.let { u -> u > now.toEpochMilli() } == true && it[pausedFromKey] != null
                if (!running) it[pausedFromKey] = now.toEpochMilli()
                it[pausedUntilKey] = until.toEpochMilli()
            }
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
            val weekend = settings.weekend
            if (weekend == null) {
                it.remove(weekendLoudKey)
                it.remove(weekendEndKey)
            } else {
                it[weekendLoudKey] = weekend.loudFrom.toMinutes()
                it[weekendEndKey] = weekend.dayEnd.toMinutes()
            }
            // The pause has its own switch, setAlarmsPaused.
        }
    }

    /** The settings for the backup (B2b) as JSON, next to the copy of the database. */
    suspend fun backup(): String {
        val prefs = context.dataStore.data.first()
        val values = JSONObject()
        (backupInts + backupStrings).forEach { key -> prefs[key]?.let { values.put(key.name, it) } }
        return JSONObject().put("version", BACKUP_VERSION).put("values", values).toString()
    }

    /**
     * Takes the settings of a [backup]; what it does not hold goes back to the default. The pause
     * and the interface stay as they are on this device.
     */
    suspend fun restore(json: String) {
        checkBackup(json)
        val values = JSONObject(json).getJSONObject("values")
        context.dataStore.edit { prefs ->
            backupInts.forEach { key ->
                if (values.has(key.name)) prefs[key] = values.getInt(key.name) else prefs.remove(key)
            }
            backupStrings.forEach { key ->
                if (values.has(key.name)) prefs[key] = values.getString(key.name) else prefs.remove(key)
            }
        }
    }

    companion object {
        const val BACKUP_VERSION = 1
        const val DEFAULT_DWELL = 3
        val DWELL_CHOICES = listOf(1, 3, 5, 10)
        private val DWELL_RANGE = 1..60
        private val MINUTES = 0..1439

        /** Minutes after midnight; -1 = off. */
        private val OVERVIEW = -1..1439

        /** What each value of a [backup] may hold; a key not listed here is not taken. */
        private val ALLOWED_INTS: Map<String, IntRange> = mapOf(
            "loudFrom" to MINUTES,
            "dayEnd" to MINUTES,
            "weekendLoudFrom" to MINUTES,
            "weekendDayEnd" to MINUTES,
            "askBlockerAfter" to 1..5,
            "dwellMinutes" to DWELL_RANGE,
            "overviewMorning" to OVERVIEW,
            "overviewEvening" to OVERVIEW,
            "weekendOverview" to 0..1,
            "weekendOverviewMorning" to OVERVIEW,
            "weekendOverviewEvening" to OVERVIEW,
        )
        private val ALLOWED_STRINGS: Map<String, Set<String>> = mapOf(
            "theme" to ThemeMode.entries.map { it.name }.toSet(),
            "reminderSort" to ReminderSort.entries.map { it.name }.toSet(),
        )

        /**
         * Checks the settings of a [backup] before anything is swapped in (R1 of Review-Lauf 10).
         *
         * KRITISCH: A restore takes the settings only after the database is swapped. A value the
         * app cannot read there would either stop the restore half-way or crash every start, so a
         * backup with such settings is rejected while nothing has changed yet.
         *
         * @throws BadSettings with the first key that does not fit.
         */
        fun checkBackup(json: String) {
            val root = runCatching { JSONObject(json) }.getOrElse { throw BadSettings("kein JSON") }
            val version = root.opt("version")
            if (version !is Int || version !in 1..BACKUP_VERSION) throw BadSettings("version")
            val values = root.opt("values") as? JSONObject ?: throw BadSettings("values")
            for (key in values.keys()) {
                val v = values.get(key)
                val ints = ALLOWED_INTS[key]
                val strings = ALLOWED_STRINGS[key]
                val ok = when {
                    ints != null -> v is Int && v in ints
                    strings != null -> v is String && v in strings
                    else -> false
                }
                if (!ok) throw BadSettings(key)
            }
        }
    }

    /** null if the value is no time of day. */
    private fun Int.toTime(): LocalTime? = if (this in MINUTES) LocalTime.of(this / 60, this % 60) else null
    private fun LocalTime.toMinutes(): Int = hour * 60 + minute
}

/** The settings of a backup do not fit this app ([Settings.checkBackup]); [key] names the first. */
class BadSettings(val key: String) : Exception("Einstellungen der Sicherung unbrauchbar: $key")
