package com.avlreminder.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.protokoll.BackupAblage
import com.avlreminder.protokoll.Datensicherung
import com.avlreminder.protokoll.DiagnoseAblage
import com.avlreminder.protokoll.DiagnoseNichtFreigeschaltet
import com.avlreminder.protokoll.GemerkteSicherung
import com.avlreminder.protokoll.GeholteSicherung
import com.avlreminder.protokoll.GeholtesBackup
import com.avlreminder.protokoll.GeraeteFreischaltung
import com.avlreminder.protokoll.GeraetNichtFreigeschaltet
import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.protokoll.LokalerStand
import com.avlreminder.protokoll.Schluesselbund
import com.avlreminder.protokoll.Sicherung
import com.avlreminder.protokoll.SicherungTageslimit
import com.avlreminder.protokoll.SicherungsAblage
import com.avlreminder.protokoll.SicherungsStand
import com.avlreminder.protokoll.Sicherungskopf
import com.avlreminder.protokoll.VersionVeraltet
import com.avlreminder.protokoll.Wiederherstellungsschluessel
import com.avlreminder.verbindung.Angemeldet
import com.avlreminder.verbindung.AnmeldungAbgelehnt
import com.avlreminder.verbindung.NichtAngemeldet
import com.avlreminder.verbindung.Serverfehler
import com.avlreminder.verbindung.Sitzung
import com.connectrpc.Code
import com.connectrpc.ConnectException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream

/**
 * Switching on, signing in and setting up (backup-entwurf.md "Ablauf Einrichtung"). Server and
 * Google are replaced; the flows themselves are tested in the backend (`EinrichtungTest`).
 */
@RunWith(AndroidJUnit4::class)
class CloudBackupTest {
    private val store = CloudStore(ApplicationProvider.getApplicationContext<Context>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val account = "konto-1"

    /** The server: one account, its key backup, and what was asked of it. */
    private inner class FakeServer : CloudServer {
        var backup: GeholtesBackup? = null
        var unlockedAtSignIn = false
        var signInError: Throwable? = null
        var sessionError: Throwable? = null
        var uploadError: Throwable? = null
        var uploads = 0
        var signIns = 0
        var unlocks = 0

        override suspend fun signIn(idToken: suspend (nonce: String) -> String): Angemeldet {
            idToken("nonce-${signIns + 1}")
            signInError?.let { throw it }
            signIns++
            val session = Sitzung(account, "geraet-1", "token-$signIns", 4_000_000_000)
            store.speichern(session)
            return Angemeldet(session, neuesKonto = backup == null, freigeschaltet = unlockedAtSignIn)
        }

        override suspend fun session(): Sitzung {
            sessionError?.let { throw it }
            return store.lesen() ?: throw NichtAngemeldet()
        }

        override fun keyBackups(token: String) = object : BackupAblage {
            override suspend fun holen() = backup

            override suspend fun speichern(chiffrat: ByteArray, freischaltschluessel: ByteArray, erwartet: Long): Long {
                uploadError?.let { throw it }
                uploads++
                if ((backup?.version ?: 0) != erwartet) throw VersionVeraltet()
                backup = GeholtesBackup(chiffrat, erwartet + 1)
                return erwartet + 1
            }
        }

        override fun unlocking(token: String) = GeraeteFreischaltung { unlocks++ }

        /** The data backup on the server. */
        var data: GeholteSicherung? = null
        var dataError: Throwable? = null

        override fun dataBackups(token: String) = object : SicherungsAblage {
            override suspend fun holen() = data

            override suspend fun speichern(chiffrat: ByteArray, erwartet: Long): Long {
                dataError?.let { throw it }
                if ((data?.version ?: 0) != erwartet) throw VersionVeraltet()
                data = GeholteSicherung(chiffrat, erwartet + 1)
                return erwartet + 1
            }

            override suspend fun loeschen() {
                data = null
            }
        }

        val diagnoses = mutableListOf<ByteArray>()
        var diagnosisError: Throwable? = null

        override fun diagnosis(token: String) = object : DiagnoseAblage {
            override suspend fun senden(nutzlast: ByteArray) {
                diagnosisError?.let { throw it }
                diagnoses += nutzlast
            }
        }

        var deleteError: Throwable? = null
        var dataBackup = true
        val locked = mutableListOf<String>()

        override suspend fun deleteDataBackup(token: String) {
            deleteError?.let { throw it }
            dataBackup = false
        }

        override suspend fun deleteKeyBackup(token: String) {
            backup = null
        }

        override suspend fun lock(token: String, device: String) {
            locked += device
        }
    }

    private val server = FakeServer()
    private var serversCreated = 0
    private var googleCalls = 0
    private var googleError: Throwable? = null
    private val local = mutableMapOf<String, LokalerStand>()
    private val remembered = mutableMapOf<String, GemerkteSicherung>()
    private var payload = "die Daten".toByteArray()
    private var now = Instant.parse("2026-11-08T12:00:00Z")

    /** The hourly backup: how often it was started and stopped. */
    private val schedule = object : CloudSchedule {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    /** The notifications of the automatic backup, as text. */
    private val notices = object : CloudNotices {
        val shown = mutableListOf<String>()
        override fun otherDevice(created: Instant) { shown += "otherDevice $created" }
        override fun stale(lastBackup: Instant) { shown += "stale $lastBackup" }
        override fun clear() { shown.clear() }
    }
    private var payloadReads = 0

    @get:Rule val tmp = TemporaryFolder()
    private val events by lazy { CloudLog(tmp.newFolder("cloud-diag"), header = { mapOf("app" to "test") }) }

    /** The logged events after the header, as JSON. */
    private fun events() = events.lines().drop(1).map(::JSONObject)

    private fun cloud() = CloudBackup(
        store,
        server = { serversCreated++; server },
        keys = { acc ->
            object : LokaleAblage {
                override fun laden(): LokalerStand? {
                    brokenKeys?.let { throw it }
                    return local[acc]
                }
                override fun speichern(stand: LokalerStand) { local[acc] = stand }
            }
        },
        google = { _, nonce ->
            googleCalls++
            googleError?.let { throw it }
            "id-token-for-$nonce"
        },
        passwords = safe,
        wipe = {
            wiped++
            local.clear()
        },
        scope = scope,
        backupState = { acc ->
            object : SicherungsStand {
                override fun laden() = remembered[acc]
                override fun speichern(stand: GemerkteSicherung) { remembered[acc] = stand }
            }
        },
        source = { payloadReads++; payload },
        events = events,
        schedule = schedule,
        notices = notices,
        log = { logged += it },
        clock = { now },
    )

    /** The password service; by default the phone has none (as the older tests assume). */
    private class FakeSafe : PasswordSafe {
        var available = false
        var declines = false
        val saved = mutableListOf<Pair<String, String>>()
        var stored: String? = null
        var reads = 0

        override suspend fun save(activity: Activity, account: String, key: String) {
            if (!available) throw CreateCredentialNoCreateOptionException()
            if (declines) throw CreateCredentialCancellationException()
            saved += account to key
            stored = key
        }

        override suspend fun read(activity: Activity): String? {
            reads++
            if (!available) throw NoCredentialException()
            return stored ?: throw NoCredentialException()
        }
    }
    private val safe = FakeSafe()
    private var wiped = 0

    /** A new account, set up on this phone. */
    private fun ready(cloud: CloudBackup): CloudPhase.Ready {
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.awaitPhase(CloudPhase.ShowKey::class.java)
        cloud.confirmKey(true)
        blocking { job.join() }
        return cloud.status.value.phase as CloudPhase.Ready
    }
    private val logged = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var brokenKeys: Throwable? = null

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    private fun CloudBackup.await(predicate: (CloudStatus) -> Boolean): CloudStatus = blocking { status.first(predicate) }

    private fun CloudBackup.awaitPhase(phase: Class<out CloudPhase>): CloudStatus = await { phase.isInstance(it.phase) }

    /** An existing account: keys and their backup under [key]. */
    private fun existingAccount(key: Wiederherstellungsschluessel = Wiederherstellungsschluessel.erzeugen()): Schluesselbund {
        val bund = Schluesselbund.erzeugen()
        server.backup = GeholtesBackup(bund.backupVerschluesseln(key, account), 1)
        return bund.also { existingKey = key }
    }
    private var existingKey: Wiederherstellungsschluessel? = null

    @Before
    fun clean() = blocking { store.clear() }

    @After
    fun stop() = scope.cancel()

    // KRITISCH: switched off means no network and no key (PS8).
    @Test
    fun switchedOffNothingReachesGoogleOrTheServer() {
        val cloud = cloud()
        blocking { cloud.load().join() }
        blocking { cloud.signIn(activity).join() }
        blocking { cloud.continueSetup().join() }
        assertEquals(CloudPhase.Off, cloud.status.value.phase)
        assertEquals(0, serversCreated)
        assertEquals(0, googleCalls)
    }

    @Test
    fun switchingOnAsksForSignInWithoutNetwork() {
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        assertEquals(CloudPhase.SignedOut, cloud.status.value.phase)
        assertEquals(0, serversCreated)
    }

    // KRITISCH: no backup on the server whose recovery key nobody has (Einrichtung).
    @Test
    fun aNewAccountUploadsOnlyAfterTheKeyIsWrittenDown() {
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        val shown = (cloud.awaitPhase(CloudPhase.ShowKey::class.java).phase as CloudPhase.ShowKey).key
        assertNull("nothing uploaded before the confirmation", server.backup)
        assertTrue("nothing stored before the confirmation", local.isEmpty())

        cloud.confirmKey(true)
        blocking { job.join() }
        val ready = cloud.status.value.phase as CloudPhase.Ready
        val uploaded = requireNotNull(server.backup)
        // The key on screen is the one that opens the uploaded backup.
        val restored = Schluesselbund.ausBackup(uploaded.chiffrat, Wiederherstellungsschluessel.ausText(shown), account)
        assertEquals(restored.person.oeffentlich.fingerabdruck(), ready.fingerprint)
        assertEquals(ready.fingerprint, local.getValue(account).bund.person.oeffentlich.fingerabdruck())
        assertNull(local.getValue(account).offenesBackup)
        assertTrue(ready.unlocked)
    }

    // KRITISCH: keys whose backup is not on the server yet are not "set up"; continuing uploads
    // the same backup, so the key already written down stays valid.
    @Test
    fun aFailedUploadIsNotSetUpAndContinuingUploadsTheSameKeys() {
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        server.uploadError = Serverfehler(ConnectException(Code.UNAVAILABLE), vomDienst = false)
        val job = cloud.signIn(activity)
        val shown = (cloud.awaitPhase(CloudPhase.ShowKey::class.java).phase as CloudPhase.ShowKey).key
        cloud.confirmKey(true)
        blocking { job.join() }
        assertEquals(CloudPhase.NotSetUp, cloud.status.value.phase)
        assertNull(server.backup)
        assertNotNull("kept locally with the open backup", local[account]?.offenesBackup)

        server.uploadError = null
        blocking { cloud.continueSetup().join() }
        val ready = cloud.status.value.phase as CloudPhase.Ready
        assertEquals(1, server.uploads)
        val restored = Schluesselbund.ausBackup(requireNotNull(server.backup).chiffrat, Wiederherstellungsschluessel.ausText(shown), account)
        assertEquals(restored.person.oeffentlich.fingerabdruck(), ready.fingerprint)
        assertNull(local.getValue(account).offenesBackup)
    }

    // KRITISCH: the backup may fail, the app may not (backend.md).
    @Test
    fun anUnreadableKeyFileAtStartIsAnErrorNotACrash() {
        val cloud = cloud()
        blocking {
            store.setEnabled(true)
            store.speichern(Sitzung(account, "geraet-1", "token", 4_000_000_000))
        }
        brokenKeys = KeysOfOtherAccount()
        blocking { cloud.load().join() }
        assertEquals(CloudPhase.NotSetUp, cloud.status.value.phase)
        assertNotNull(cloud.status.value.error)
    }

    @Test
    fun aFailedGoogleSignInLogsWhatGoogleSaid() {
        googleError = GetCredentialUnknownException("[28444] Developer console is not set up correctly.")
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.signIn(activity).join() }
        assertEquals(CloudPhase.SignedOut, cloud.status.value.phase)
        assertTrue(logged.toString(), logged.any { "28444" in it })
    }

    @Test
    fun aServerErrorIsLoggedWithItsCode() {
        server.signInError = Serverfehler(ConnectException(Code.RESOURCE_EXHAUSTED), vomDienst = true)
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.signIn(activity).join() }
        assertTrue(logged.toString(), logged.any { "resource_exhausted" in it && "vomDienst=true" in it })
    }

    @Test
    fun notConfirmingTheKeyStoresNothing() {
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.awaitPhase(CloudPhase.ShowKey::class.java)
        cloud.confirmKey(false)
        blocking { job.join() }
        assertEquals(CloudPhase.NotSetUp, cloud.status.value.phase)
        assertTrue(cloud.status.value.error.orEmpty().startsWith("Einrichtung abgebrochen"))
        assertNull(server.backup)
        assertTrue(local.isEmpty())
    }

    @Test
    fun anExistingAccountNeedsTheRightKeyAndUnlocksTheDevice() {
        val bund = existingAccount()
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)

        cloud.awaitPhase(CloudPhase.AskKey::class.java)
        cloud.submitKey(Wiederherstellungsschluessel.erzeugen().alsText()) // valid, but not this account's
        cloud.await { it.phase == CloudPhase.AskKey && it.notes.any { n -> "passt nicht" in n } }
        assertEquals(0, server.unlocks)
        assertTrue(local.isEmpty())

        cloud.submitKey(requireNotNull(existingKey).alsText())
        blocking { job.join() }
        val ready = cloud.status.value.phase as CloudPhase.Ready
        assertEquals(bund.person.oeffentlich.fingerabdruck(), ready.fingerprint)
        assertEquals(1, server.unlocks)
        assertTrue(ready.unlocked)
    }

    @Test
    fun cancellingTheKeyEntryLeavesTheDeviceWithoutKeys() {
        existingAccount()
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.awaitPhase(CloudPhase.AskKey::class.java)
        cloud.submitKey(null)
        blocking { job.join() }
        assertEquals(CloudPhase.NotSetUp, cloud.status.value.phase)
        assertNotNull(cloud.status.value.error)
        assertTrue(local.isEmpty())
        assertEquals(0, server.unlocks)
    }

    @Test
    fun cancellingAtGoogleShowsNoError() {
        googleError = GetCredentialCancellationException()
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.signIn(activity).join() }
        assertEquals(CloudPhase.SignedOut, cloud.status.value.phase)
        assertNull(cloud.status.value.error)
        assertEquals(0, server.signIns)
    }

    // KRITISCH: a 403 from the locked service (kill switch) is an outage, not a verdict on the device.
    @Test
    fun theKillSwitchIsShownAsAnOutage() {
        server.signInError = Serverfehler(ConnectException(Code.PERMISSION_DENIED), vomDienst = false)
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.signIn(activity).join() }
        assertEquals(CloudPhase.SignedOut, cloud.status.value.phase)
        assertTrue(cloud.status.value.error.orEmpty().contains("nicht erreichbar"))
    }

    @Test
    fun aRejectedSessionIsForgottenAndAsksForSignIn() {
        val cloud = cloud()
        blocking {
            store.setEnabled(true)
            store.speichern(Sitzung(account, "geraet-1", "alt", 1))
        }
        server.sessionError = AnmeldungAbgelehnt()
        blocking { cloud.continueSetup().join() }
        assertEquals(CloudPhase.SignedOut, cloud.status.value.phase)
        assertNull(blocking { store.lesen() })
    }

    @Test
    fun offKeepsTheKeysAndOnAgainIsReadyWithoutNetwork() {
        val first = cloud()
        blocking { first.setEnabled(true).join() }
        val job = first.signIn(activity)
        first.awaitPhase(CloudPhase.ShowKey::class.java)
        first.confirmKey(true)
        blocking { job.join() }
        val fingerprint = (first.status.value.phase as CloudPhase.Ready).fingerprint
        blocking { first.setEnabled(false).join() }
        assertEquals(CloudPhase.Off, first.status.value.phase)

        // After an app restart.
        serversCreated = 0
        val again = cloud()
        blocking { again.setEnabled(true).join() }
        assertEquals(fingerprint, (again.status.value.phase as CloudPhase.Ready).fingerprint)
        assertEquals(0, serversCreated)
    }

    @Test
    fun aSecondTapWhileSettingUpDoesNothing() {
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.awaitPhase(CloudPhase.ShowKey::class.java)
        blocking { cloud.signIn(activity).join() }
        assertEquals(1, googleCalls)
        assertTrue(cloud.status.value.phase is CloudPhase.ShowKey)
        cloud.confirmKey(false)
        blocking { job.join() }
    }

    @Test
    fun onlyAnAnswerOfTheServiceItselfCountsAsItsVerdict() {
        val fromService = CloudBackup.describe(Serverfehler(ConnectException(Code.RESOURCE_EXHAUSTED), vomDienst = true))
        val fromBefore = CloudBackup.describe(Serverfehler(ConnectException(Code.RESOURCE_EXHAUSTED), vomDienst = false))
        assertTrue(fromService.orEmpty().contains("resource_exhausted"))
        assertTrue(fromBefore.orEmpty().contains("nicht erreichbar"))
    }

    // --- Password service (Anf. 7.33) -------------------------------------------------

    // KRITISCH: the key offered to the password service is the one that opens the uploaded
    // backup, and saving it does not replace the confirmation: nothing is uploaded before.
    @Test
    fun aNewKeyIsOfferedToThePasswordServiceRightAway() {
        safe.available = true
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        val shown = cloud.await { (it.phase as? CloudPhase.ShowKey)?.saved == true }.phase as CloudPhase.ShowKey
        assertEquals(listOf(account to shown.key), safe.saved)
        assertNull("nothing uploaded before the confirmation", server.backup)

        cloud.confirmKey(true)
        blocking { job.join() }
        val ready = cloud.status.value.phase as CloudPhase.Ready
        val restored = Schluesselbund.ausBackup(
            requireNotNull(server.backup).chiffrat, Wiederherstellungsschluessel.ausText(safe.saved.single().second), account,
        )
        assertEquals(restored.person.oeffentlich.fingerabdruck(), ready.fingerprint)
    }

    // KRITISCH: the key never goes into the log, whether saving works or not.
    @Test
    fun theKeyIsNeverLogged() {
        for (available in listOf(true, false)) {
            blocking { store.clear() }
            local.clear()
            server.backup = null
            safe.available = available
            logged.clear()
            val cloud = cloud()
            blocking { cloud.setEnabled(true).join() }
            val job = cloud.signIn(activity)
            val key = (cloud.awaitPhase(CloudPhase.ShowKey::class.java).phase as CloudPhase.ShowKey).key
            cloud.confirmKey(true)
            blocking { job.join() }
            assertTrue("available=$available: $logged", logged.any { "password service" in it })
            assertFalse("available=$available: $logged", logged.any { key in it || key.replace("-", "") in it })
        }
    }

    @Test
    fun declinedTheKeyIsShownToWriteDownAndCanBeSavedLater() {
        safe.available = true
        safe.declines = true
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.awaitPhase(CloudPhase.ShowKey::class.java)
        blocking { while (logged.none { "not saved" in it }) delay(10) }
        val shown = cloud.status.value.phase as CloudPhase.ShowKey
        assertFalse(shown.saved)
        assertNull("declining is no error", cloud.status.value.error)

        safe.declines = false
        blocking { cloud.saveKey(activity).join() }
        assertEquals(CloudPhase.ShowKey(shown.key, saved = true), cloud.status.value.phase)
        assertEquals(listOf(account to shown.key), safe.saved)
        cloud.confirmKey(false)
        blocking { job.join() }
    }

    @Test
    fun withoutAnActivityThePasswordServiceIsNotAsked() {
        safe.available = true
        val cloud = cloud()
        blocking {
            store.setEnabled(true)
            store.speichern(Sitzung(account, "geraet-1", "token", 4_000_000_000))
        }
        val job = cloud.continueSetup()
        val shown = cloud.awaitPhase(CloudPhase.ShowKey::class.java).phase as CloudPhase.ShowKey
        assertFalse(shown.saved)
        assertTrue(safe.saved.isEmpty())
        cloud.confirmKey(false)
        blocking { job.join() }
    }

    @Test
    fun anExistingAccountTakesTheKeyFromThePasswordService() {
        val bund = existingAccount()
        safe.available = true
        safe.stored = requireNotNull(existingKey).alsText()
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.signIn(activity).join() }
        val ready = cloud.status.value.phase as CloudPhase.Ready
        assertEquals(bund.person.oeffentlich.fingerabdruck(), ready.fingerprint)
        assertEquals(1, server.unlocks)
    }

    // KRITISCH: a wrong key in the password service asks the person; it is not tried again and again.
    @Test
    fun aWrongKeyFromThePasswordServiceAsksThePersonOnce() {
        existingAccount()
        safe.available = true
        safe.stored = Wiederherstellungsschluessel.erzeugen().alsText()
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.await { it.phase == CloudPhase.AskKey && it.notes.any { n -> "passt nicht" in n } }
        assertEquals(1, safe.reads)
        assertEquals(0, server.unlocks)

        cloud.submitKey(requireNotNull(existingKey).alsText())
        blocking { job.join() }
        assertTrue(cloud.status.value.phase is CloudPhase.Ready)
        assertEquals(1, safe.reads)
    }

    @Test
    fun withNothingInThePasswordServiceThePersonTypesOrFetchesTheKey() {
        existingAccount()
        safe.available = true
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        val job = cloud.signIn(activity)
        cloud.awaitPhase(CloudPhase.AskKey::class.java)
        assertNull(cloud.status.value.error)

        // Saved there meanwhile, then fetched with the button.
        safe.stored = requireNotNull(existingKey).alsText()
        blocking { cloud.fetchKey(activity).join() }
        blocking { job.join() }
        assertTrue(cloud.status.value.phase is CloudPhase.Ready)
        assertEquals(2, safe.reads)
    }

    // --- Reset ---------------------------------------------------------------------------

    // KRITISCH: after a reset nothing of the old setup is left, and it can be set up anew.
    @Test
    fun aResetDeletesOnTheServerAndOnThePhoneAndAllowsANewSetup() {
        val cloud = cloud()
        val before = ready(cloud)
        blocking { cloud.reset().join() }
        assertEquals(CloudPhase.Off, cloud.status.value.phase)
        assertNull(cloud.status.value.error)
        assertNull("key backup", server.backup)
        assertFalse("data backup", server.dataBackup)
        assertEquals(listOf("geraet-1"), server.locked)
        assertEquals(1, wiped)
        assertTrue(local.isEmpty())
        assertNull(blocking { store.lesen() })

        val again = ready(cloud)
        assertTrue("new keys", again.fingerprint != before.fingerprint)
    }

    // KRITISCH: the server just not reachable, nothing is deleted on the phone.
    @Test
    fun aResetWithoutTheServerDeletesNothingOnThePhone() {
        val cloud = cloud()
        val before = ready(cloud)
        server.deleteError = Serverfehler(ConnectException(Code.UNAVAILABLE), vomDienst = false)
        blocking { cloud.reset().join() }
        assertEquals(before, cloud.status.value.phase)
        assertTrue(cloud.status.value.error.orEmpty().contains("nicht erreichbar"))
        assertEquals(0, wiped)
        assertTrue(local.isNotEmpty())
        assertNotNull(blocking { store.lesen() })
        assertTrue(server.locked.isEmpty())
    }

    @Test
    fun aDeviceThatMayNotDeleteResetsThePhoneLocksItselfAndSaysSo() {
        val cloud = cloud()
        ready(cloud)
        server.deleteError = GeraetNichtFreigeschaltet()
        blocking { cloud.reset().join() }
        assertEquals(CloudPhase.Off, cloud.status.value.phase)
        assertEquals(1, wiped)
        assertEquals(listOf("geraet-1"), server.locked)
        assertTrue(cloud.status.value.notes.toString(), cloud.status.value.notes.any { "nichts löschen" in it })
    }

    @Test
    fun signedOutAResetOnlyClearsThePhone() {
        val cloud = cloud()
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.reset().join() }
        assertEquals(CloudPhase.Off, cloud.status.value.phase)
        assertEquals(1, wiped)
        assertEquals(0, serversCreated)
    }

    @Test
    fun switchedOffAResetDoesNothing() {
        val cloud = cloud()
        blocking { cloud.reset().join() }
        assertEquals(0, wiped)
        assertEquals(0, serversCreated)
    }

    /** The data backup on the server, decrypted with this phone's vault key. */
    private fun onServer(): Sicherung =
        Datensicherung.entschluesseln(requireNotNull(server.data).chiffrat, local.getValue(account).bund.tresor, account)

    /** A backup of another device, already on the server. */
    private fun backupOfAnotherDevice(created: Instant): ByteArray {
        val c = Datensicherung.verschluesseln(
            Sicherung(Sicherungskopf(1, created, "geraet-2", 8), "fremd".toByteArray()), local.getValue(account).bund.tresor, account,
        )
        server.data = GeholteSicherung(c, 1)
        return c
    }

    // KRITISCH: switched off or not set up, nothing is read and nothing reaches the server.
    @Test
    fun switchedOffOrSignedOutBackingUpAndSendingDoNothing() {
        val cloud = cloud()
        blocking { cloud.backUp().join() }
        blocking { cloud.sendDiagnosis().join() }
        blocking { cloud.setEnabled(true).join() }
        blocking { cloud.backUp().join() }
        assertEquals(CloudPhase.SignedOut, cloud.status.value.phase)
        assertEquals(0, serversCreated)
        assertEquals(0, payloadReads)
        assertTrue(server.diagnoses.isEmpty())
    }

    @Test
    fun backingUpUploadsTheEncryptedDataAndShowsWhen() {
        val cloud = cloud()
        assertNull(ready(cloud).lastBackup)
        blocking { cloud.backUp().join() }
        val phase = cloud.status.value.phase as CloudPhase.Ready
        assertNotNull(phase.lastBackup)
        assertNull(cloud.status.value.error)
        val backup = onServer()
        assertTrue(backup.nutzlast.contentEquals(payload))
        assertEquals("geraet-1", backup.kopf.geraetId)
        assertEquals(1L, backup.kopf.zaehler)
        assertEquals(GemerkteSicherung(1, 1), remembered[account])
        assertEquals(listOf("gesichert"), events().map { it.getString("ereignis") }.filter { it == "gesichert" })
    }

    @Test
    fun aSecondBackupCountsOn() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.backUp().join() }
        blocking { cloud.backUp().join() }
        assertEquals(2L, onServer().kopf.zaehler)
        assertEquals(2L, server.data?.version)
    }

    // KRITISCH: another device's backup is replaced only on yes (E-B2b-6).
    @Test
    fun anotherDevicesBackupStaysWhenThePersonSaysNo() {
        val cloud = cloud()
        ready(cloud)
        val created = Instant.parse("2026-11-01T08:00:00Z")
        val before = backupOfAnotherDevice(created)
        val job = cloud.backUp()
        assertEquals(CloudPhase.AskOverwrite(created), cloud.awaitPhase(CloudPhase.AskOverwrite::class.java).phase)
        cloud.answerOverwrite(false)
        blocking { job.join() }
        assertTrue(server.data!!.chiffrat.contentEquals(before))
        assertNull((cloud.status.value.phase as CloudPhase.Ready).lastBackup)
        assertTrue(cloud.status.value.notes.toString(), cloud.status.value.notes.any { "Nicht gesichert" in it })
        assertEquals(true, remembered[account]?.abgelehnt)
    }

    @Test
    fun anotherDevicesBackupIsReplacedWhenThePersonSaysYes() {
        val cloud = cloud()
        ready(cloud)
        backupOfAnotherDevice(Instant.parse("2026-11-01T08:00:00Z"))
        val job = cloud.backUp()
        cloud.awaitPhase(CloudPhase.AskOverwrite::class.java)
        cloud.answerOverwrite(true)
        blocking { job.join() }
        val backup = onServer()
        assertEquals("geraet-1", backup.kopf.geraetId)
        assertTrue(backup.nutzlast.contentEquals(payload))
        assertEquals(2L, backup.kopf.zaehler)
        assertNotNull((cloud.status.value.phase as CloudPhase.Ready).lastBackup)
    }

    @Test
    fun theDailyLimitIsShownAndLoggedWithoutText() {
        val cloud = cloud()
        ready(cloud)
        server.dataError = SicherungTageslimit()
        blocking { cloud.backUp().join() }
        assertTrue(cloud.status.value.error.orEmpty(), cloud.status.value.error.orEmpty().contains("Limit"))
        assertNull((cloud.status.value.phase as CloudPhase.Ready).lastBackup)
        val failed = events().last()
        assertEquals("fehlgeschlagen", failed.getString("ereignis"))
        assertEquals("BACKUP", failed.getJSONObject("felder").getString("schritt"))
        assertEquals("DAILY_LIMIT", failed.getJSONObject("felder").getString("art"))
        assertFalse(failed.toString(), "morgen" in failed.toString())
    }

    @Test
    fun aServerOutageIsLoggedWithItsCode() {
        val cloud = cloud()
        ready(cloud)
        server.sessionError = Serverfehler(ConnectException(Code.UNAVAILABLE), vomDienst = false)
        blocking { cloud.backUp().join() }
        val fields = events().last().getJSONObject("felder")
        assertEquals("SERVER", fields.getString("art"))
        assertEquals("UNAVAILABLE", fields.getString("code"))
        assertFalse(fields.getBoolean("vom_dienst"))
        assertNull(server.data)
    }

    @Test
    fun withoutNetworkItSaysSoAndLogsANetworkError() {
        val cloud = cloud()
        ready(cloud)
        server.sessionError = java.net.ConnectException("Failed to connect")
        blocking { cloud.backUp().join() }
        assertEquals("Kein Netz oder Server nicht erreichbar.", cloud.status.value.error)
        assertEquals("NETWORK", events().last().getJSONObject("felder").getString("art"))
        assertTrue(cloud.status.value.phase is CloudPhase.Ready)
    }

    // KRITISCH: only the backend logger's lines go out, never diag.log (it may hold titles).
    @Test
    fun aDiagnosisSendsTheLoggerAndNothingElse() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.backUp().join() }
        blocking { cloud.sendDiagnosis().join() }
        val sent = GZIPInputStream(ByteArrayInputStream(server.diagnoses.single())).readBytes().toString(Charsets.UTF_8).trim().lines()
        assertEquals(events.lines().dropLast(1), sent) // the last event is "gesendet" itself
        assertEquals("kopf", JSONObject(sent[0]).getString("art"))
        assertTrue(sent.none { line -> logged.any { it in line } })
        assertTrue(cloud.status.value.notes.toString(), cloud.status.value.notes.any { "Diagnose gesendet" in it })
    }

    @Test
    fun anAccountWithoutDiagnosisSaysSo() {
        val cloud = cloud()
        ready(cloud)
        server.diagnosisError = DiagnoseNichtFreigeschaltet()
        blocking { cloud.sendDiagnosis().join() }
        assertEquals("Dieses Konto darf keine Diagnose senden.", cloud.status.value.error)
    }

    @Test
    fun theLastBackupOfAnotherAccountIsNotShown() = blocking {
        store.setLastBackup("konto-2", 1_700_000_000, "hash")
        assertNull(store.lastBackup(account))
        assertEquals(1_700_000_000L, store.lastBackup("konto-2"))
    }

    // --- Step 4: the hourly backup (A-9, A-10, A-13) ---

    private fun auto(cloud: CloudBackup) = blocking { cloud.backUpAutomatically() }

    /** Another device backs up over this one's backup: higher counter, next server version. */
    private fun anotherDeviceBacksUpOver(created: Instant) {
        val before = requireNotNull(server.data)
        val c = Datensicherung.verschluesseln(
            Sicherung(Sicherungskopf(onServer().kopf.zaehler + 1, created, "geraet-2", 8), "fremd".toByteArray()),
            local.getValue(account).bund.tresor,
            account,
        )
        server.data = GeholteSicherung(c, before.version + 1)
    }

    // KRITISCH: the hourly backup touches nothing unless it may: switched on, set up, unlocked and
    // backed up by hand once.
    @Test
    fun theAutomaticBackupDoesNothingUnlessItMay() {
        val cases = listOf<Pair<String, (CloudBackup) -> Unit>>(
            "ausgeschaltet" to { _ -> },
            "eingeschaltet, nicht angemeldet" to { c -> blocking { c.setEnabled(true).join() } },
            "eingerichtet, noch nie von Hand gesichert" to { c -> ready(c) },
            "nicht freigeschaltet" to { c ->
                ready(c)
                blocking { c.backUp().join() }
                blocking { store.setUnlocked(false) }
            },
            "von Hand gesichert, dann ausgeschaltet" to { c ->
                ready(c)
                blocking { c.backUp().join() }
                blocking { c.setEnabled(false).join() }
            },
        )
        for ((case, prepare) in cases) {
            blocking { store.clear() }
            local.clear()
            remembered.clear()
            server.data = null
            server.backup = null
            val cloud = cloud()
            prepare(cloud)
            val servers = serversCreated
            val reads = payloadReads
            val before = server.data
            payload = "geändert $case".toByteArray()
            auto(cloud)
            assertEquals(case, servers, serversCreated)
            assertEquals(case, reads, payloadReads)
            assertEquals(case, before, server.data)
        }
    }

    // KRITISCH: unchanged data are not uploaded again (A-9); changed ones are.
    @Test
    fun theAutomaticBackupUploadsOnlyChangedData() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.backUp().join() }
        val first = server.data
        now = now.plusSeconds(3600)
        auto(cloud)
        assertEquals(first, server.data)
        assertEquals(Instant.parse("2026-11-08T12:00:00Z"), (cloud.status.value.phase as CloudPhase.Ready).lastBackup)

        payload = "neue Daten".toByteArray()
        now = now.plusSeconds(3600)
        auto(cloud)
        assertTrue(onServer().nutzlast.contentEquals(payload))
        assertEquals(2L, onServer().kopf.zaehler)
        assertEquals(now, (cloud.status.value.phase as CloudPhase.Ready).lastBackup)
        val backedUp = events().filter { it.getString("ereignis") == "gesichert" }.map { it.getJSONObject("felder").getBoolean("automatisch") }
        assertEquals(listOf(false, true), backedUp)
    }

    // KRITISCH: another device's backup is never replaced without the person (E-B2b-6); the
    // automatic backup rests, says so once and does not ask the server again every hour.
    @Test
    fun anotherDevicesBackupMakesTheAutomaticBackupRest() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.backUp().join() }
        val created = Instant.parse("2026-11-08T12:30:00Z")
        anotherDeviceBacksUpOver(created)
        val theirs = server.data
        payload = "neue Daten".toByteArray()
        auto(cloud)
        assertEquals(theirs, server.data)
        assertEquals(listOf("otherDevice $created"), notices.shown)
        assertTrue((cloud.status.value.phase as CloudPhase.Ready).paused)
        assertNull(cloud.status.value.error)

        val servers = serversCreated
        val reads = payloadReads
        auto(cloud)
        assertEquals(servers, serversCreated)
        assertEquals(reads, payloadReads)
        assertEquals(1, notices.shown.size)

        // By hand it asks; on yes it backs up from here again and the rest ends.
        val job = cloud.backUp()
        cloud.awaitPhase(CloudPhase.AskOverwrite::class.java)
        cloud.answerOverwrite(true)
        blocking { job.join() }
        val phase = cloud.status.value.phase as CloudPhase.Ready
        assertFalse(phase.paused)
        assertTrue(phase.automatic)
        assertEquals("geraet-1", onServer().kopf.geraetId)
        assertTrue(notices.shown.isEmpty())
    }

    // A-13: an outage only shows as status; the next hour tries again.
    @Test
    fun aFailedAutomaticBackupOnlyShowsAsStatus() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.backUp().join() }
        payload = "neue Daten".toByteArray()
        server.sessionError = Serverfehler(ConnectException(Code.UNAVAILABLE), vomDienst = false)
        auto(cloud)
        assertTrue(cloud.status.value.error.orEmpty(), cloud.status.value.error.orEmpty().contains("nicht erreichbar"))
        assertEquals("AUTO_BACKUP", events().last().getJSONObject("felder").getString("schritt"))
        assertTrue(notices.shown.isEmpty())

        server.sessionError = null
        auto(cloud)
        assertNull(cloud.status.value.error)
        assertTrue(onServer().nutzlast.contentEquals(payload))
    }

    // A-13: after 3 days without a current backup a notice comes, once; unchanged data count as current.
    @Test
    fun theNoticeComesAfterThreeDaysWithoutACurrentBackup() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.backUp().join() }
        val backedUp = now

        // Unchanged for 4 days: the backup is current, no notice.
        now = now.plus(Duration.ofDays(4))
        auto(cloud)
        assertTrue(notices.shown.isEmpty())

        // Changed, but the server is out: just under 3 days no notice, after that one.
        payload = "neue Daten".toByteArray()
        server.sessionError = SicherungTageslimit()
        val unchangedUntil = now
        now = unchangedUntil.plus(Duration.ofDays(3))
        auto(cloud)
        assertTrue(notices.shown.isEmpty())
        now = unchangedUntil.plus(Duration.ofDays(3)).plusSeconds(3600)
        auto(cloud)
        assertEquals(listOf("stale $backedUp"), notices.shown)
        now = now.plusSeconds(3600)
        auto(cloud)
        assertEquals(1, notices.shown.size)
        assertEquals(1, events().count { it.getString("ereignis") == "veraltet" })

        // Backed up again: the notice goes.
        server.sessionError = null
        auto(cloud)
        assertTrue(notices.shown.isEmpty())
        assertFalse(blocking { store.staleNoticeShown() })
    }

    // KRITISCH: the hourly rhythm runs only after the first backup by hand; switching off and
    // resetting stop it.
    @Test
    fun theScheduleRunsOnlyWhileTheAutomaticBackupMay() {
        val cloud = cloud()
        ready(cloud)
        blocking { cloud.load().join() }
        assertEquals(0, schedule.starts)

        blocking { cloud.backUp().join() }
        assertEquals(1, schedule.starts)
        blocking { cloud.load().join() } // app start
        assertEquals(2, schedule.starts)

        blocking { cloud.setEnabled(false).join() }
        assertEquals(1, schedule.stops)
        assertEquals(2, schedule.starts)

        blocking { cloud.setEnabled(true).join() }
        assertEquals(3, schedule.starts)
        blocking { cloud.reset().join() }
        assertEquals(2, schedule.stops)
        assertEquals(CloudPhase.Off, cloud.status.value.phase)
        assertEquals(3, schedule.starts)
    }
}
