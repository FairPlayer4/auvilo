package com.avlreminder.wear

import android.content.Context
import com.avlreminder.core.usecases.Watch
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * The way to the phone (design.md 17): only to the phone nearby, connected directly (as a rule
 * over Bluetooth); nothing goes over Google's servers (Anf. 7.14 G9b).
 */
interface PhoneLink {
    /** The node id of the phone with the app if it is nearby, else null. */
    suspend fun nearbyPhone(): String?

    /** Sends a message; false if it did not go out. */
    suspend fun send(nodeId: String, path: String, data: ByteArray): Boolean
}

/** [PhoneLink] over the Wear OS Data Layer, messages only. */
class PlayPhoneLink(private val context: Context) : PhoneLink {
    override suspend fun nearbyPhone(): String? = attempt(null) {
        nearby(Wearable.getCapabilityClient(context).getCapability(Watch.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await().nodes)
    }

    override suspend fun send(nodeId: String, path: String, data: ByteArray): Boolean = attempt(false) {
        Wearable.getMessageClient(context).sendMessage(nodeId, path, data).await()
        true
    }

    /** Play services missing, the phone gone or no answer in time: [fallback], never a crash. */
    private suspend fun <T> attempt(fallback: T, block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        // withTimeout ends with a cancellation of its own; any other is the caller's.
        if (e is TimeoutCancellationException) fallback else throw e
    } catch (_: Exception) {
        fallback
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L

        /** Only a phone connected directly; one reachable only over the internet counts as away (G9b). */
        fun nearby(nodes: Collection<Node>): String? = nodes.firstOrNull { it.isNearby }?.id
    }
}
