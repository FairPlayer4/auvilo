package com.avlreminder.wear

import android.content.Context
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.core.usecases.WatchCommand
import com.avlreminder.core.usecases.WatchResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Collections

/** The watch app for the tests: no Play services, a clock the test sets. */
class TestWearApp : WearApp() {
    val link = FakePhoneLink(this)
    val clock = SetClock(Instant.parse("2026-11-26T17:20:00Z"))

    override fun phoneLink(): PhoneLink = link

    override fun clock(): Clock = clock
}

class SetClock(@Volatile var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

/**
 * A [PhoneLink] without Play services: [phone] is nearby (null: none), [sent] is what went out.
 * [answer] answers a command as the phone would, through the real message path.
 */
class FakePhoneLink(private val context: Context) : PhoneLink {
    data class Sent(val nodeId: String, val path: String, val data: ByteArray)

    @Volatile var phone: String? = PHONE
    @Volatile var answer: ((WatchCommand) -> WatchResult?)? = { WatchResult(it.requestId, true) }
    val sent: MutableList<Sent> = Collections.synchronizedList(mutableListOf())

    override suspend fun nearbyPhone(): String? = phone

    override suspend fun send(nodeId: String, path: String, data: ByteArray): Boolean {
        if (phone == null) return false
        sent += Sent(nodeId, path, data)
        if (path == Watch.PATH_ACT) {
            val command = WatchCodec.decodeCommand(data)!!
            answer?.invoke(command)?.let { PhoneMessages.received(context, Watch.PATH_RESULT, WatchCodec.encode(it)) }
        }
        return true
    }

    /** The commands sent so far. */
    fun commands(): List<WatchCommand> = synchronized(sent) { sent.filter { it.path == Watch.PATH_ACT }.map { WatchCodec.decodeCommand(it.data)!! } }

    fun reset() {
        phone = PHONE
        answer = { WatchResult(it.requestId, true) }
        sent.clear()
    }

    companion object {
        const val PHONE = "phone-1"
    }
}
