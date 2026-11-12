package com.avlreminder.alarm

import com.avlreminder.TestClock
import com.avlreminder.settle
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.Ring
import com.avlreminder.graph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class AlarmGlueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val at by lazy { TestClock.now().plusSeconds(3600) }

    @Before
    fun setUp() {
        TestClock.at()
        // The app syncs on start (on Dispatchers.Default); let that finish so it does not race
        // with the test. Idling the main looper alone did not wait for it.
        runBlocking { (context as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() = TestClock.reset()

    private fun reminder(strictness: Strictness, proof: ProofSpec) = Reminder(
        id = 7, title = "Katzenbrunnen", why = "Katzen trinken", strictness = strictness,
        schedule = Schedule.Daily(LocalTime.of(18, 0)), proof = proof, createdAt = TestClock.now(),
    )

    private fun ring(level: AlarmLevel, proof: ProofSpec = ProofSpec.CONFIRM, snooze: List<Int> = listOf(10, 30)) =
        Ring(AlarmView(reminder(Strictness.NORMAL, proof), Occurrence(id = 42, reminderId = 7, dueAt = at, state = OccurrenceState.DUE), true, null, true, null, snooze), level)

    @Test
    fun strictAndNormalUseAlarmClockRelaxedUsesExactAlarm() {
        val port = AndroidAlarmPort(context)
        // Without the WLAN probe's tick (debug build, elapsed-time alarm); the overview from the
        // app start is already planned, so count what the port adds.
        fun reminderAlarms() = shadowOf(alarmManager).scheduledAlarms.filter { it.getType() != AlarmManager.ELAPSED_REALTIME_WAKEUP }
        val before = reminderAlarms().size
        port.set(1, at, AlarmKind.ALARM_CLOCK)
        assertEquals(at.toEpochMilli(), alarmManager.nextAlarmClock?.triggerTime)

        port.set(2, at.plusSeconds(60), AlarmKind.EXACT)
        assertEquals(before + 2, reminderAlarms().size)

        port.cancel(1)
        port.cancel(2)
        assertEquals(before, reminderAlarms().size)
    }

    @Test
    fun eachNotificationOpensItsOwnAlarm() {
        val first = ring(AlarmLevel.BANNER)
        Ringer.ring(context, first)
        Ringer.ring(context, first.copy(view = first.view.copy(occurrence = first.occurrence.copy(id = 43))))
        for (id in listOf(42L, 43L)) {
            val n = shadowOf(notifications).getNotification(Notifications.id(id))
            assertEquals(id, shadowOf(n.contentIntent).savedIntent.getLongExtra("occurrence", -1))
        }
        Ringer.dismiss(context, 42)
        Ringer.dismiss(context, 43)
    }

    @Test
    fun bannerHasDoneAndSnoozeActions() {
        Ringer.ring(context, ring(AlarmLevel.BANNER))
        val n = shadowOf(notifications).getNotification(Notifications.id(42))
        assertNotNull(n)
        assertEquals(listOf("Erledigt", "+10 min", "+30 min"), n.actions.map { it.title.toString() })
        assertEquals("banner", n.channelId)

        Ringer.dismiss(context, 42)
        assertNull(shadowOf(notifications).getNotification(Notifications.id(42)))
    }

    @Test
    fun photoProofCannotBeCompletedFromTheNotification() {
        Ringer.ring(context, ring(AlarmLevel.SILENT, ProofSpec.PHOTO))
        val n = shadowOf(notifications).getNotification(Notifications.id(42))
        assertEquals(listOf("+10 min", "+30 min"), n.actions.map { it.title.toString() })
        assertEquals("silent", n.channelId)
    }

    @Test
    fun noSnoozeButtonsShortlyBeforeTheDayEnds() {
        Ringer.ring(context, ring(AlarmLevel.BANNER, snooze = emptyList()))
        val n = shadowOf(notifications).getNotification(Notifications.id(42))
        assertEquals(listOf("Erledigt"), n.actions.map { it.title.toString() })
    }

    @Test
    fun pauseForTheProofReplacesTheAlarmWithAQuietNote() {
        Ringer.ring(context, ring(AlarmLevel.BANNER))
        Ringer.pause(context, AlarmInfo(42, "Katzenbrunnen", null, quickDone = false), at.plusSeconds(600))
        val n = shadowOf(notifications).getNotification(Notifications.id(42))
        assertNotNull(n)
        assertEquals(true, n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().startsWith("Nachweis läuft"))
        assertEquals(0, n.actions?.size ?: 0)
    }

    @Test
    fun fullScreenStartsTheAlarmServiceLoudOnlyAtTheLoudLevel() {
        val app = shadowOf(context as android.app.Application)
        for ((level, loud) in listOf(AlarmLevel.FULLSCREEN_LOUD to true, AlarmLevel.FULLSCREEN to false)) {
            Ringer.ring(context, ring(level))
            val started = app.nextStartedService
            assertEquals(AlarmService::class.java.name, started.component?.className)
            // The tone is the point of the loud level (Review-Lauf 4, A4).
            assertEquals(level.name, loud, started.getBooleanExtra("loud", !loud))
        }
    }

    @Test
    fun doneActionCompletesTheOccurrence() = runBlocking {
        val graph = context.graph
        val id = graph.repository.saveReminder(reminder(Strictness.NORMAL, ProofSpec.CONFIRM).copy(id = 0))
        val occurrenceId = graph.repository.saveOccurrence(
            Occurrence(reminderId = id, dueAt = TestClock.now().minusSeconds(60), state = OccurrenceState.DUE),
        )

        context.sendBroadcast(
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_DONE)
                .putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId),
        )
        ShadowLooper.idleMainLooper()
        // The receiver works asynchronously; wait until the engine has written the result.
        repeat(100) {
            if (graph.repository.occurrence(occurrenceId)?.state == OccurrenceState.DONE) return@runBlocking
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
        assertEquals(Resolution.GOAL, graph.repository.occurrence(occurrenceId)?.resolution)
    }
}
