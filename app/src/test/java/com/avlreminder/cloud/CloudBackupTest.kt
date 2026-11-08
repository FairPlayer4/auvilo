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
import com.avlreminder.protokoll.GeholtesBackup
import com.avlreminder.protokoll.GeraeteFreischaltung
import com.avlreminder.protokoll.LokaleAblage
import com.avlreminder.protokoll.LokalerStand
import com.avlreminder.protokoll.Schluesselbund
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

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

        override val nonce = "nonce-of-the-device-key"

        override suspend fun signIn(idToken: String): Angemeldet {
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
    }

    private val server = FakeServer()
    private var serversCreated = 0
    private var googleCalls = 0
    private var googleError: Throwable? = null
    private val local = mutableMapOf<String, LokalerStand>()

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
        scope = scope,
        log = { logged += it },
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
}
