package com.avlreminder.watch

import com.avlreminder.AppGraph
import com.avlreminder.alarm.Undo
import com.avlreminder.core.rules.TransitionResult
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchAction
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.core.usecases.WatchCommand
import com.avlreminder.core.usecases.WatchResult
import com.avlreminder.core.usecases.WatchScreen
import com.avlreminder.graph
import com.avlreminder.system.Diag
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Messages of the watch app (design.md 17): a button (`/avl/act`) runs through the engine like a
 * button of the notification and gets an answer; "hello" asks for the state. Play services start
 * the service and call it on a thread of their own, so it may wait for the engine.
 */
class WatchCommandService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        runBlocking { WatchCommands(applicationContext.graph).received(event.path, event.data, event.sourceNodeId) }
    }

    /** The watch app came near or went away: it gets the state at once, the checks are mirrored or not. */
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name != Watch.CAPABILITY_WATCH) return
        runBlocking { applicationContext.graph.watch.push(force = true) }
    }
}

/** What [WatchCommandService] does with a message; only from a watch nearby (Anf. 7.14 G9b). */
class WatchCommands(private val graph: AppGraph) {
    suspend fun received(path: String, data: ByteArray, from: String) {
        val watch = graph.watch
        if (!watch.isNearby(from)) {
            Diag.log("watch message $path from a watch not nearby, ignored")
            return
        }
        when (path) {
            Watch.PATH_HELLO -> watch.push(force = true, to = from)
            Watch.PATH_ACT -> {
                val command = WatchCodec.decodeCommand(data)
                if (command == null) {
                    Diag.log("watch command not readable (${data.size} bytes)")
                    return
                }
                watch.reply(from, Watch.PATH_RESULT, WatchCodec.encode(act(command)))
            }
            else -> Diag.log("watch message $path unknown")
        }
    }

    /** Runs [command] in the engine, as the notification's buttons do ([com.avlreminder.alarm.ActionReceiver]). */
    suspend fun act(command: WatchCommand): WatchResult {
        val id = command.occurrenceId
        val action = command.action.toAction()
        val result = graph.withEngine { e -> if (action == null) e.reopen(id) else e.act(id, action) }
        Diag.log("watch action occ=$id ${command.action} → ${result::class.simpleName}")
        val message = when {
            command.action == WatchAction.REOPEN -> Undo.message(result)
            result is TransitionResult.Ok -> null
            else -> WatchScreen.NOT_POSSIBLE
        }
        return WatchResult(command.requestId, result is TransitionResult.Ok, message)
    }
}
