package com.avlreminder.watch

/** A [WatchLink] without Play services: [watches] are nearby, [sent] is what went out. */
class FakeWatchLink : WatchLink {
    data class Sent(val nodeId: String, val path: String, val data: ByteArray) {
        override fun equals(other: Any?) = other is Sent && nodeId == other.nodeId && path == other.path && data.contentEquals(other.data)
        override fun hashCode() = 31 * (31 * nodeId.hashCode() + path.hashCode()) + data.contentHashCode()
    }

    @Volatile var watches: List<String> = emptyList()
    @Volatile var fails = false
    val sent: MutableList<Sent> = java.util.Collections.synchronizedList(mutableListOf())

    override suspend fun nearbyWatches(): List<String> = watches

    override suspend fun send(nodeId: String, path: String, data: ByteArray): Boolean {
        if (fails) return false
        sent += Sent(nodeId, path, data)
        return true
    }

    fun sentTo(path: String): List<Sent> = synchronized(sent) { sent.filter { it.path == path } }
}
