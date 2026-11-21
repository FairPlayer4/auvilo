package com.avlreminder.wear

import androidx.test.core.app.ApplicationProvider
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.core.usecases.Watch
import com.avlreminder.core.usecases.WatchCodec
import com.avlreminder.core.usecases.WatchNext
import com.avlreminder.core.usecases.WatchSession
import com.avlreminder.core.usecases.WatchStart
import com.avlreminder.core.usecases.WatchState
import java.time.Instant

/** Test data of the watch: the phone's state as it comes, and the test app. */
object Fixtures {
    val app: TestWearApp get() = ApplicationProvider.getApplicationContext()

    val startedAt: Instant = Instant.parse("2026-11-26T17:02:00Z")

    val training = WatchSession(
        7, "Training", startedAt, startedAt.plusSeconds(90 * 60), 45, 10,
        listOf(SessionAnswer.STILL_ON, SessionAnswer.DONE, SessionAnswer.MINIMUM),
        final = false, proofOnPhone = false, minimumUsedUp = false,
    )

    val running = WatchState(sessions = listOf(training))

    val startable = WatchState(startable = listOf(WatchStart(9, "Aufräumen", Instant.parse("2026-11-26T17:00:00Z"), ringing = true)))

    val nextOnly = WatchState(next = WatchNext("Katzenbrunnen", Instant.parse("2026-11-27T07:00:00Z")))

    /** The phone sends [state], through the real message path. */
    fun receive(state: WatchState) = PhoneMessages.received(app, Watch.PATH_STATE, WatchCodec.encode(state))
}
