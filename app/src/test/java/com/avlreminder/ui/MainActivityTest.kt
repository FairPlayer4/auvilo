package com.avlreminder.ui

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.TestClock
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.graph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import java.time.LocalTime

/** The app's activity as Android starts it: from a notification or the widget, then rebuilt. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        TestClock.at()
        runBlocking { (context as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() = TestClock.reset()

    /**
     * Opened from the notice before a reminder, a half typed form survives a change of design or
     * font size (Kap. 9, 2026-11-12 (6)): the activity is rebuilt with the same, already handled
     * intent, and must come back on the same screen (Review-Lauf 6).
     */
    @Test
    fun aDraftOpenedFromANotificationSurvivesTheActivityBeingRebuilt() {
        val id = runBlocking {
            context.graph.repository.saveReminder(
                Reminder(title = "Katzenbrunnen", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = TestClock.now()),
            )
        }
        fun field(text: String) = hasSetTextAction() and hasText(text)
        ActivityScenario.launch<MainActivity>(MainActivity.editReminder(context, id)).use { scenario ->
            compose.waitUntil(5_000) { compose.onAllNodes(field("Katzenbrunnen")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(field("Katzenbrunnen")).performTextReplacement("Katzenbrunnen halb")
            compose.waitForIdle()
            scenario.recreate()
            compose.waitUntil(5_000) { compose.onAllNodes(field("Katzenbrunnen halb")).fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
