package com.avlreminder.watch

import android.content.Context
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchCheck
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.core.usecases.WatchState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * The way to the watch app (design.md 17). Only to watches nearby, i.e. connected directly (as a
 * rule over Bluetooth): nothing of the watch app goes over Google's servers (Anf. 7.14 G9b).
 */
interface WatchLink {
    /** Ids of the watches with the watch app that are nearby; empty without any or without Play services. */
    suspend fun nearbyWatches(): List<String>

    /** Sends a message; false if it did not go out. */
    suspend fun send(nodeId: String, path: String, data: ByteArray): Boolean
}

/** [WatchLink] over the Wear OS Data Layer, messages only (no data items, they would sync over the cloud). */
class PlayWatchLink(private val context: Context) : WatchLink {
    override suspend fun nearbyWatches(): List<String> = attempt(emptyList()) {
        nearby(Wearable.getCapabilityClient(context).getCapability(Watch.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes)
    }

    override suspend fun send(nodeId: String, path: String, data: ByteArray): Boolean = attempt(false) {
        Wearable.getMessageClient(context).sendMessage(nodeId, path, data).await()
        true
    }

    /** Play services missing, the watch gone or no answer in time: [fallback], never a crash. */
    private suspend fun <T> attempt(fallback: T, block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        // withTimeout ends with a cancellation of its own; any other is the caller's.
        if (e is kotlinx.coroutines.TimeoutCancellationException) fallback else throw e
    } catch (_: Exception) {
        fallback
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L

        /** Only nodes connected directly; one reachable only over the internet counts as away (G9b). */
        fun nearby(nodes: Collection<Node>): List<String> = nodes.filter { it.isNearby }.map { it.id }
    }
}

/**
 * Keeps the watch app up to date (design.md 17): after every command the state, when a check
 * rings the check and its end. Sends only what changed, and only to watches nearby.
 */
class WatchSync(
    private val link: WatchLink,
    private val state: suspend () -> WatchState,
    private val log: (String) -> Unit,
) {
    /**
     * A watch with the app was nearby at the last look. Then the checks are not mirrored to the
     * watch as well (design.md 17, "Kein Doppel").
     */
    @Volatile var nearby: Boolean = false
        private set

    private val mutex = Mutex()

    /** The state last sent to all watches nearby; null after a failure or without any. */
    private var sent: ByteArray? = null

    /** Checks the watch was told about, so only their end is sent. */
    private val checks = mutableSetOf<Long>()

    /** Sends the state if it changed; [force] (the watch asked or came near) also unchanged, to [to] only if given. */
    suspend fun push(force: Boolean = false, to: String? = null) = mutex.withLock {
        val watches = watches().filter { to == null || it == to }
        if (watches.isEmpty()) {
            if (to == null) sent = null
            return@withLock
        }
        val bytes = WatchCodec.encode(state())
        if (!force && sent?.contentEquals(bytes) == true) return@withLock
        val ok = watches.map { link.send(it, Watch.PATH_STATE, bytes) }.all { it }
        if (to == null) sent = bytes.takeIf { ok }
    }

    /** A check of a running session rings: the watch vibrates and shows the answers. */
    suspend fun check(check: WatchCheck) = mutex.withLock {
        val watches = watches()
        if (watches.isEmpty()) return@withLock
        synchronized(checks) { checks += check.occurrenceId }
        watches.forEach { link.send(it, Watch.PATH_CHECK, WatchCodec.encode(check)) }
    }

    /** [occurrenceId] rings no more: the watch takes its check away. */
    suspend fun ended(occurrenceId: Long) = mutex.withLock {
        if (!synchronized(checks) { checks.remove(occurrenceId) }) return@withLock
        watches().forEach { link.send(it, Watch.PATH_CHECK_END, WatchCodec.encodeId(occurrenceId)) }
    }

    /** Sends [data] to [nodeId] only if it is a watch nearby (the answer to a command). */
    suspend fun reply(nodeId: String, path: String, data: ByteArray): Boolean =
        nodeId in watches() && link.send(nodeId, path, data)

    /** Whether [nodeId] is a watch nearby: commands from elsewhere are not taken (G9b). */
    suspend fun isNearby(nodeId: String): Boolean = nodeId in watches()

    private suspend fun watches(): List<String> = link.nearbyWatches().also {
        val now = it.isNotEmpty()
        if (now != nearby) log("watch nearby=$now")
        nearby = now
    }
}
