package com.avlreminder.cloud

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avlreminder.verbindung.Sitzung
import com.avlreminder.verbindung.SitzungsAblage
import kotlinx.coroutines.flow.first

// A damaged file counts as empty (switched off, signed out) instead of crashing the app at
// every start: the backup may fail, the app may not (backend.md). The keys are in another file.
private val Context.backendStore by preferencesDataStore(
    "backend",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * What the app keeps for the backend besides the keys (A-7): the switch, the session and whether
 * the device is unlocked. Its own DataStore, not the settings: the settings themselves go into
 * the backup (PS8a), and none of this may.
 */
class CloudStore(private val context: Context) : SitzungsAblage {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val accountKey = stringPreferencesKey("account")
    private val deviceKey = stringPreferencesKey("device")
    private val tokenKey = stringPreferencesKey("token")
    private val expiresKey = longPreferencesKey("expiresUnix")
    private val unlockedKey = booleanPreferencesKey("unlocked")
    private val lastBackupKey = longPreferencesKey("lastBackupUnix")
    private val lastBackupAccountKey = stringPreferencesKey("lastBackupAccount")
    private val hashKey = stringPreferencesKey("lastBackupHash")
    private val currentKey = longPreferencesKey("currentUnix")
    private val staleNoticeKey = booleanPreferencesKey("staleNoticeShown")

    private suspend fun prefs() = context.backendStore.data.first()

    /** The switch (PS8); off = no sign-in and no network. */
    suspend fun enabled(): Boolean = prefs()[enabledKey] == true

    suspend fun setEnabled(on: Boolean) {
        context.backendStore.edit { it[enabledKey] = on }
    }

    /** Whether the server lets this device change data (E-B2b-13). */
    suspend fun unlocked(): Boolean = prefs()[unlockedKey] == true

    suspend fun setUnlocked(on: Boolean) {
        context.backendStore.edit { it[unlockedKey] = on }
    }

    /** When [account] last backed up from this device; another account's time does not count. */
    suspend fun lastBackup(account: String): Long? {
        val p = prefs()
        return if (p[lastBackupAccountKey] == account) p[lastBackupKey] else null
    }

    /**
     * [account] backed up at [unix]; [hash] is the SHA-256 of what went up, so the automatic
     * backup can skip an unchanged payload (A-9). The backup is current from then on.
     */
    suspend fun setLastBackup(account: String, unix: Long, hash: String) {
        context.backendStore.edit {
            it[lastBackupAccountKey] = account
            it[lastBackupKey] = unix
            it[hashKey] = hash
            it[currentKey] = unix
            it.remove(staleNoticeKey)
        }
    }

    /** The SHA-256 of the last payload [account] uploaded from here. */
    suspend fun lastHash(account: String): String? {
        val p = prefs()
        return if (p[lastBackupAccountKey] == account) p[hashKey] else null
    }

    /**
     * When the backup of [account] last held this device's data: uploaded, or the data were
     * unchanged since. The notice after 3 days (A-13) counts from here.
     */
    suspend fun current(account: String): Long? {
        val p = prefs()
        return if (p[lastBackupAccountKey] == account) p[currentKey] ?: p[lastBackupKey] else null
    }

    /** The data of [account] are unchanged since its last backup, checked at [unix]. */
    suspend fun setCurrent(account: String, unix: Long) {
        context.backendStore.edit {
            if (it[lastBackupAccountKey] == account) {
                it[currentKey] = unix
                it.remove(staleNoticeKey)
            }
        }
    }

    /** Whether the notice "no backup for 3 days" was shown since the backup was last current. */
    suspend fun staleNoticeShown(): Boolean = prefs()[staleNoticeKey] == true

    suspend fun setStaleNoticeShown() {
        context.backendStore.edit { it[staleNoticeKey] = true }
    }

    override suspend fun lesen(): Sitzung? {
        val p = prefs()
        return Sitzung(p[accountKey] ?: return null, p[deviceKey] ?: return null, p[tokenKey] ?: return null, p[expiresKey] ?: return null)
    }

    override suspend fun speichern(sitzung: Sitzung) {
        context.backendStore.edit {
            it[accountKey] = sitzung.kontoId
            it[deviceKey] = sitzung.geraetId
            it[tokenKey] = sitzung.token
            it[expiresKey] = sitzung.laeuftAbUnix
        }
    }

    /** Forgets everything here (switch, session, unlocked); the key file is not touched. */
    suspend fun clear() {
        context.backendStore.edit { it.clear() }
    }

    /** Forgets the session (rejected by the server); the switch and the keys stay. */
    suspend fun forgetSession() {
        context.backendStore.edit {
            it.remove(tokenKey)
            it.remove(expiresKey)
        }
    }
}
