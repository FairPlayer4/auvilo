package com.avlreminder.ui

import android.app.Application
import android.Manifest
import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.TestClock
import com.avlreminder.alarm.ExactAlarms
import com.avlreminder.core.model.Arrival
import com.avlreminder.core.model.Place
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Schedule
import com.avlreminder.graph
import com.avlreminder.ui.screens.LocationDisclosure
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
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
        // As after an earlier test in this JVM: Android's default view model factory keeps the first
        // application it saw in a static singleton. The activity must not get a view model on that
        // application's database (AppViewModel.Factory).
        ViewModelProvider.AndroidViewModelFactory.getInstance(Application())
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

    private fun exactAlarmsWarning() = compose.onAllNodes(hasText(ExactAlarms.WARNING, substring = true)).fetchSemanticsNodes()

    /**
     * Anf. 7.28 PS10: "Wecker und Erinnerungen" is the app's core. Without it "Heute" says what goes
     * wrong, and the warning goes once it is granted.
     */
    @Test
    fun withoutExactAlarmsHeuteSaysTheAppDoesNotWorkProperly() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(5_000) { exactAlarmsWarning().isNotEmpty() }
            // Granted in Android's settings: back in the app the warning is gone.
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { exactAlarmsWarning().isEmpty() }
        }
    }

    /** A reminder waiting for the arrival at home: only then the list asks for the location. */
    private fun arrivalReminder() = runBlocking {
        val home = context.graph.withEngine { it.savePlace(Place(name = "Zuhause", lat = 48.1, lon = 11.5)) }
        context.graph.repository.saveReminder(
            Reminder(
                title = "Training", schedule = Schedule.Daily(LocalTime.of(19, 30)), createdAt = TestClock.now(),
                arrival = Arrival(home),
            ),
        )
    }

    private fun lastRequested(scenario: ActivityScenario<MainActivity>): List<String>? {
        var requested: List<String>? = null
        scenario.onActivity { requested = shadowOf(it).lastRequestedPermission?.requestedPermissions?.toList() }
        return requested
    }

    private fun tapGrantLocation() {
        val grant = "grant:Standort „Immer erlauben“"
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(grant)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(grant).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /**
     * Anf. 7.28 PS13, playstore.md P5: Google Play wants the app's own words on the location in the
     * background before Android's dialog; "Weiter" asks Android, precise position first.
     */
    @Test
    fun theDisclosureComesBeforeAndroidAsksForTheLocation() {
        arrivalReminder()
        ActivityScenario.launch<MainActivity>(MainActivity.openSettings(context)).use { scenario ->
            tapGrantLocation()
            compose.onNodeWithText(LocationDisclosure.TITLE).assertExists()
            compose.onNodeWithText("auch wenn die App geschlossen ist", substring = true).assertExists()
            assertNull("nothing asked before the disclosure", lastRequested(scenario))

            compose.onNodeWithText(LocationDisclosure.CONTINUE).performClick()
            compose.waitForIdle()
            assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), lastRequested(scenario))
            assertEquals(0, compose.onAllNodes(hasText(LocationDisclosure.TITLE)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun notNowAsksNothing() {
        arrivalReminder()
        ActivityScenario.launch<MainActivity>(MainActivity.openSettings(context)).use { scenario ->
            tapGrantLocation()
            compose.onNodeWithText(LocationDisclosure.LATER).performClick()
            compose.waitForIdle()
            assertEquals(0, compose.onAllNodes(hasText(LocationDisclosure.TITLE)).fetchSemanticsNodes().size)
            assertNull(lastRequested(scenario))
        }
    }
}
