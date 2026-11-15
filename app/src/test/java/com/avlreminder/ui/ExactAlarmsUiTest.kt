package com.avlreminder.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.TestClock
import com.avlreminder.alarm.ExactAlarms
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper

/**
 * Anf. 7.28 PS10: "Wecker und Erinnerungen" is the app's core. Without it "Heute" says what goes
 * wrong, and the warning goes once it is granted.
 *
 * Its own class: a test that starts MainActivity before MainActivityTest's draft test makes the
 * latter fail (the form opens without the saved reminder), also without anything of this test.
 */
@RunWith(AndroidJUnit4::class)
class ExactAlarmsUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun setUp() {
        TestClock.at()
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() = TestClock.reset()

    private fun warning() = compose.onAllNodes(hasText(ExactAlarms.WARNING, substring = true)).fetchSemanticsNodes()

    @Test
    fun withoutExactAlarmsHeuteSaysTheAppDoesNotWorkProperly() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(5_000) { warning().isNotEmpty() }
            // Granted in Android's settings: back in the app the warning is gone.
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { warning().isEmpty() }
        }
    }
}
