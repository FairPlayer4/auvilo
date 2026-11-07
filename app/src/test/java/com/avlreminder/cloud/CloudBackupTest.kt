package com.avlreminder.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
                override fun laden() = local[acc]
                override fun speichern(stand: LokalerStand) { local[acc] = stand }
            }
        },
        google = { _, nonce ->
            googleCalls++
            googleError?.let { throw it }
            "id-token-for-$nonce"
        },
        scope = scope,
    )

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
}
