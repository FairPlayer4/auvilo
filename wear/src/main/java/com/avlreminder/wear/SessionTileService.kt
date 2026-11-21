package com.avlreminder.wear

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.avlreminder.core.usecases.WatchScreen
import com.avlreminder.core.usecases.WatchState
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * The tile (Anf. 7.14 G8, design.md 17): the running session with its time; otherwise the next
 * reminder with a session today and *Ich starte jetzt*; otherwise the next reminder, shown only.
 * It is updated only when a new state comes (battery); the time runs on the watch by itself.
 */
class SessionTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val graph = wear
        val tile = tile(this, graph.shown.value?.state, graph.clock.instant(), ZoneId.systemDefault(), requestParams.deviceConfiguration)
        return CallbackToFutureAdapter.getFuture { it.set(tile) }
    }

    /** The tile comes into view: the phone is asked for the state (a new one updates the tile). */
    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        scope.launch { runCatching { wear.hello() } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Asks the system for a new tile (after a new state). */
        fun update(context: Context) {
            runCatching { getUpdater(context).requestUpdate(SessionTileService::class.java) }
        }

        /** The tile for [state] (null: nothing from the phone yet). */
        fun tile(context: Context, state: WatchState?, now: Instant, zone: ZoneId, device: DeviceParameters): TileBuilders.Tile {
            val session = state?.sessions?.firstOrNull()
            val start = state?.startable?.firstOrNull()
            val layout = materialScope(context, device) {
                when {
                    session != null -> primaryLayout(
                        titleSlot = { text(LayoutString(session.title)) },
                        mainSlot = {
                            // Minutes since the start, counted by the watch itself: no update per minute.
                            val minutes = DynamicInstant.withSecondsPrecision(session.startedAt)
                                .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
                                .toIntMinutes().format().concat(DynamicString.constant(" min"))
                            val fallback = "${WatchScreen.elapsedMillis(session, now) / 60_000} min"
                            text(LayoutString(fallback, minutes, StringLayoutConstraint.Builder("0000 min").build()))
                        },
                        bottomSlot = { textEdgeButton(onClick = launch(context, null, "open")) { text(LayoutString("Öffnen")) } },
                    )
                    start != null -> primaryLayout(
                        titleSlot = { text(LayoutString(start.title)) },
                        mainSlot = { text(LayoutString(WatchScreen.startDetail(start, zone))) },
                        bottomSlot = {
                            textEdgeButton(onClick = launch(context, start.occurrenceId, "start")) {
                                text(LayoutString(WatchScreen.label(com.avlreminder.core.usecases.WatchAction.START)))
                            }
                        },
                    )
                    else -> primaryLayout(
                        mainSlot = { text(LayoutString(state?.let { WatchScreen.next(it, zone) } ?: "Noch kein Stand vom Handy"), maxLines = 3) },
                        bottomSlot = { textEdgeButton(onClick = launch(context, null, "open")) { text(LayoutString("Öffnen")) } },
                    )
                }
            }
            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

        /** Opens the watch app; with [startId] it starts that session at once (*Ich starte jetzt*). */
        private fun launch(context: Context, startId: Long?, id: String): Clickable {
            val activity = ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName(WatchActivity::class.java.name)
            startId?.let { activity.addKeyToExtraMapping(WatchActivity.EXTRA_START, ActionBuilders.AndroidLongExtra.Builder().setValue(it).build()) }
            return clickable(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build(), id)
        }

        private const val RESOURCES = "1"
    }
}
