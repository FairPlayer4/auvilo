package com.avlreminder.alarm

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.TestClock
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofKind
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.rules.Escalation
import com.avlreminder.graph
import com.avlreminder.settle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.time.LocalTime

/** The full-screen alarm as an activity: what it does when Kevin leaves it. */
@RunWith(AndroidJUnit4::class)
class AlarmActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = context.graph
    private val repo = graph.repository

    @Before
    fun setUp() {
        TestClock.at()
        runBlocking { (context as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() = TestClock.reset()

    /**
     * N0: leaving the app with the proof screen open must not keep the alarm quiet for the whole
     * proof window; after the short grace it rings again (Review-Lauf 4, A3).
     */
    @Test
    fun leavingTheScreenWithTheProofOpenEndsTheQuietAfterTheGrace() {
        val o = runBlocking {
            val id = repo.saveReminder(
                Reminder(
                    title = "Katzenbrunnen", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = TestClock.now(),
                    proof = ProofSpec(ProofKind.EVIDENCE, textMinChars = 5),
                ),
            )
            repo.saveOccurrence(Occurrence(reminderId = id, dueAt = TestClock.now().minusSeconds(60), state = OccurrenceState.DUE))
        }
        fun quietUntil() = runBlocking { repo.occurrence(o)?.pausedUntil }

        ActivityScenario.launch<AlarmActivity>(AlarmActivity.intent(context, o)).use { scenario ->
            // The screen loads the alarm from the engine first.
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Erledigt").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Erledigt").performClick()
            compose.waitUntil(5_000) { quietUntil() != null }
            // The proof window: ten minutes quiet.
            assertTrue("${quietUntil()}", quietUntil()!! > TestClock.now().plus(Duration.ofMinutes(5)))

            scenario.moveToState(Lifecycle.State.CREATED)
            graph.settle()

            val until = quietUntil()!!
            assertTrue("$until", until <= TestClock.now().plus(Escalation.PROOF_GRACE).plusSeconds(5))
        }
    }
}
