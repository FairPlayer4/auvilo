package com.avlreminder.cloud

import com.avlreminder.protokoll.Geraetenachrichten
import com.avlreminder.protokoll.KontoLoeschen
import com.avlreminder.protokoll.KontoLoeschung
import com.avlreminder.schnittstelle.v1.SitzungErneuernResponse
import com.avlreminder.verbindung.Geraeteschluessel
import com.avlreminder.verbindung.Serverfehler
import com.avlreminder.verbindung.Sitzung
import com.avlreminder.verbindung.SitzungsAblage
import com.avlreminder.verbindung.Verbindung
import com.google.protobuf.Timestamp
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import com.avlreminder.schnittstelle.v1.Sitzung as SitzungNachricht

/**
 * [ConnectCloudServer] against answers in the Connect format, without a network: an OkHttp
 * interceptor plays the server. Covers the wiring the fake server in [CloudBackupTest] cannot see.
 */
class ConnectCloudServerTest {
    /** How the server answers, by method name; a method missing here answers "internal". */
    private sealed interface Answer {
        /** An error in the Connect format, as the service itself sends it. */
        data class Error(val code: String) : Answer

        /** From something in front of the service, like Google for a locked service (kill switch). */
        data class Raw(val status: Int, val type: String, val text: String) : Answer

        data class Ok(val bytes: ByteArray) : Answer
    }

    private class FakeServer(val answers: Map<String, Answer>) : Interceptor {
        val methods = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val method = chain.request().url.pathSegments.last()
            methods += method
            val (status, type, body) = when (val a = answers[method] ?: Answer.Error("internal")) {
                is Answer.Error -> Triple(400, "application/json", """{"code":"${a.code}","message":"nachgebaut"}""".toByteArray())
                is Answer.Raw -> Triple(a.status, a.type, a.text.toByteArray())
                is Answer.Ok -> Triple(200, "application/proto", a.bytes)
            }
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("nachgebaut")
                .header("Content-Type", type)
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }
    }

    /** A device key only in memory. */
    private class TestKey : Geraeteschluessel {
        private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        override val oeffentlich: ByteArray get() = pair.public.encoded
        override fun signiere(nachricht: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(nachricht)
                sign()
            }
    }

    private class Sessions(var session: Sitzung?) : SitzungsAblage {
        override suspend fun lesen() = session
        override suspend fun speichern(sitzung: Sitzung) {
            session = sitzung
        }
    }

    private class Case(val name: String, val renewal: Answer, val deleted: Boolean)

    private val renewed = Answer.Ok(
        SitzungErneuernResponse.newBuilder()
            .setSitzung(SitzungNachricht.newBuilder().setToken("token-new").setLaeuftAb(Timestamp.newBuilder().setSeconds(4_000_000_000L)))
            .build()
            .toByteArray(),
    )

    private val cases = listOf(
        Case("device locked: the account is gone", Answer.Error("permission_denied"), deleted = true),
        Case("device unknown: the account is gone", Answer.Error("unauthenticated"), deleted = true),
        Case("renewal works: the account is still there", renewed, deleted = false),
        Case("403 in front of the service (kill switch): unclear", Answer.Raw(403, "text/html", "<html>Forbidden</html>"), deleted = false),
        Case("service unavailable: unclear", Answer.Error("unavailable"), deleted = false),
    )

    // KRITISCH (backend review, run 11): the answer to "Konto löschen" got lost. Only when the
    // service itself turns this device away is the account deleted and the phone forgets its keys;
    // a server that is just not there must not cost the keys.
    @Test
    fun afterALostAnswerOnlyARejectedRenewalCountsAsDeleted() {
        for (case in cases) {
            val key = TestKey()
            val fake = FakeServer(mapOf("KontoLoeschen" to Answer.Error("unavailable"), "SitzungErneuern" to case.renewal))
            val verbindung = Verbindung("http://127.0.0.1:1", OkHttpClient.Builder().addInterceptor(fake).build())
            val device = Geraetenachrichten.geraetId(key.oeffentlich)
            val server = ConnectCloudServer(verbindung, key, Sessions(Sitzung("konto-1", device, "token-old", 4_000_000_000L)))
            var forgotten = 0

            val result = runCatching { runBlocking { KontoLoeschen.loeschen(server.deletion("token-old")) { forgotten++ } } }

            assertEquals(case.name, listOf("KontoLoeschen", "SitzungErneuern"), fake.methods)
            if (case.deleted) {
                assertEquals("${case.name}: $result", KontoLoeschung.Geloescht, result.getOrNull())
                assertEquals(case.name, 1, forgotten)
            } else {
                assertTrue("${case.name}: $result", result.exceptionOrNull() is Serverfehler)
                assertEquals(case.name, 0, forgotten)
            }
        }
    }
}
