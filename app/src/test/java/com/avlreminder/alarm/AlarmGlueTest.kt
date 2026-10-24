package com.avlreminder.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofType
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.ports.AlarmKind
import com.avlreminder.core.rules.AlarmLevel
import com.avlreminder.core.usecases.Ring
import com.avlreminder.graph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val at = Instant.now().plusSeconds(3600)

    @Before
    fun setUp() {
        // The app syncs on start; let that finish so it does not race with the test.
        ShadowLooper.idleMainLooper()
    }

    private fun reminder(strictness: Strictness, proof: ProofType) = Reminder(
        id = 7, title = "Katzenbrunnen", why = "Katzen trinken", strictness = strictness,
        schedule = Schedule.Daily(LocalTime.of(18, 0)), proof = proof, createdAt = Instant.now(),
    )

    private fun ring(level: AlarmLevel, proof: ProofType = ProofType.CONFIRM) =
        Ring(reminder(Strictness.NORMAL, proof), Occurrence(id = 42, reminderId = 7, dueAt = at, state = OccurrenceState.DUE), level)

    @Test
    fun strictAndNormalUseAlarmClockRelaxedUsesExactAlarm() {
        val port = AndroidAlarmPort(context)
        port.set(1, at, AlarmKind.ALARM_CLOCK)
        assertEquals(at.toEpochMilli(), alarmManager.nextAlarmClock?.triggerTime)

        port.set(2, at.plusSeconds(60), AlarmKind.EXACT)
        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, scheduled.size)

        port.cancel(1)
        port.cancel(2)
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun bannerHasDoneAndSnoozeActions() {
        Ringer.ring(context, ring(AlarmLevel.BANNER))
        val n = shadowOf(notifications).getNotification(Notifications.id(42))
        assertNotNull(n)
        assertEquals(listOf("Erledigt", "+10 min"), n.actions.map { it.title.toString() })
        assertEquals("banner", n.channelId)

        Ringer.dismiss(context, 42)
        assertNull(shadowOf(notifications).getNotification(Notifications.id(42)))
    }

    @Test
    fun photoProofCannotBeCompletedFromTheNotification() {
        Ringer.ring(context, ring(AlarmLevel.SILENT, ProofType.PHOTO))
        val n = shadowOf(notifications).getNotification(Notifications.id(42))
        assertEquals(listOf("+10 min"), n.actions.map { it.title.toString() })
        assertEquals("silent", n.channelId)
    }

    @Test
    fun fullScreenStartsTheAlarmService() {
        Ringer.ring(context, ring(AlarmLevel.FULLSCREEN_LOUD))
        val started = shadowOf(context as android.app.Application).nextStartedService
        assertEquals(AlarmService::class.java.name, started.component?.className)
    }

    @Test
    fun doneActionCompletesTheOccurrence() = runBlocking {
        val graph = context.graph
        val id = graph.repository.saveReminder(reminder(Strictness.NORMAL, ProofType.CONFIRM).copy(id = 0))
        val occurrenceId = graph.repository.saveOccurrence(
            Occurrence(reminderId = id, dueAt = Instant.now().minusSeconds(60), state = OccurrenceState.DUE),
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
