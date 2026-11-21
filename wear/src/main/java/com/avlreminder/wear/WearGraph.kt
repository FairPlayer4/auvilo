package com.avlreminder.wear

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchAction
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.core.usecases.WatchCommand
import com.avlreminder.core.usecases.WatchResult
import com.avlreminder.core.usecases.WatchState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import kotlin.random.Random

/** The last state from the phone and when it came. */
data class Shown(val state: WatchState, val receivedAt: Instant)

/** What came of a button (design.md 17). */
sealed interface Outcome {
    /** Done; [message] e.g. after "Rückgängig". */
    data class Done(val message: String?) : Outcome
    /** The phone rejected it, its state counts. */
    data class Rejected(val message: String?) : Outcome
    /** No answer within [Watch.ANSWER_TIMEOUT_MS]. */
    data object NoAnswer : Outcome
    /** No phone nearby: nothing was sent. */
    data object NoConnection : Outcome
}

/**
 * The watch app's parts (design.md 17): the last state, kept across a restart of the app, whether
 * the phone is nearby, and the buttons that send commands and wait for their answer.
 */
class WearGraph(context: Context, private val link: PhoneLink, val clock: Clock) {
    private val prefs = context.getSharedPreferences("watch", Context.MODE_PRIVATE)

    private val _shown = MutableStateFlow(load())
    val shown: StateFlow<Shown?> = _shown.asStateFlow()

    private val _connected = MutableStateFlow(false)

    /** The phone was nearby at the last look. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val results = MutableSharedFlow<WatchResult>(extraBufferCapacity = 16)

    /** A state from the phone; an unreadable one is dropped, the last one stays. */
    fun received(bytes: ByteArray): Shown? {
        val state = WatchCodec.decodeState(bytes) ?: return null
        val shown = Shown(state, clock.instant())
        _shown.value = shown
        _connected.value = true
        prefs.edit {
            putString(KEY_STATE, Base64.encodeToString(bytes, Base64.NO_WRAP))
            putLong(KEY_AT, shown.receivedAt.toEpochMilli())
        }
        return shown
    }

    /** The answer to a command. */
    fun result(bytes: ByteArray) {
        WatchCodec.decodeResult(bytes)?.let { results.tryEmit(it) }
    }

    fun connected(nearby: Boolean) {
        _connected.value = nearby
    }

    /** Looks whether the phone is nearby and asks it for the state (app or tile opened). */
    suspend fun hello(): Boolean {
        val phone = phone() ?: return false
        return link.send(phone, Watch.PATH_HELLO, ByteArray(0))
    }

    /** Sends [action] for [occurrenceId] and waits for the phone's answer. */
    suspend fun act(occurrenceId: Long, action: WatchAction, timeoutMs: Long = Watch.ANSWER_TIMEOUT_MS): Outcome = coroutineScope {
        val phone = phone() ?: return@coroutineScope Outcome.NoConnection
        val command = WatchCommand(Random.nextLong(), occurrenceId, action)
        // Listening before sending: the answer can be faster than the send's own confirmation.
        val answer = async(start = CoroutineStart.UNDISPATCHED) { results.first { it.requestId == command.requestId } }
        if (!link.send(phone, Watch.PATH_ACT, WatchCodec.encode(command))) {
            answer.cancel()
            _connected.value = false
            return@coroutineScope Outcome.NoConnection
        }
        val result = withTimeoutOrNull(timeoutMs) { answer.await() }
        answer.cancel()
        when {
            result == null -> Outcome.NoAnswer
            result.ok -> Outcome.Done(result.message)
            else -> Outcome.Rejected(result.message)
        }
    }

    private suspend fun phone(): String? = link.nearbyPhone().also { _connected.value = it != null }

    private fun load(): Shown? {
        val text = prefs.getString(KEY_STATE, null) ?: return null
        val state = runCatching { Base64.decode(text, Base64.NO_WRAP) }.getOrNull()?.let(WatchCodec::decodeState) ?: return null
        return Shown(state, Instant.ofEpochMilli(prefs.getLong(KEY_AT, 0)))
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_AT = "receivedAt"
    }
}
