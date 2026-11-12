package com.avlreminder.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.AvlApp
import com.avlreminder.alarm.Notifications.Button
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.model.Character
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.rules.SessionAnswer
import com.avlreminder.graph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Die Sitzung mit Timer auf Android (Anf. 7.11, design.md E.3): welche Knöpfe Benachrichtigung
 * und Uhr zeigen, was sie in der Engine auslösen, und die laufende Benachrichtigung mit Timer.
 */
@RunWith(AndroidJUnit4::class)
class SessionGlueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = context.graph
    private val repo = graph.repository
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val start = Instant.parse("2026-11-11T17:42:00Z")

    @Before
    fun setUp() {
        // The engine runs on the real clock; near midnight a due occurrence would count as missed.
        val now = LocalTime.now()
        assumeTrue("not shortly before or after midnight", now > LocalTime.of(0, 30) && now < LocalTime.of(22, 30))
        runBlocking { (context as AvlApp).started.join() }
        ShadowLooper.idleMainLooper()
    }

    // --- Knöpfe ---------------------------------------------------------------------------------

    private fun check(suggestion: SessionAnswer = SessionAnswer.STILL_ON, final: Boolean = false, minimum: Boolean = true, quickDone: Boolean = true) =
        AlarmInfo(1, "Training", null, quickDone = quickDone, session = SessionCheck(start, final, minimum, suggestion))

    private data class Fall(val name: String, val info: AlarmInfo, val handy: List<Button>, val uhr: List<Button>)

    private val faelle = listOf(
        Fall(
            "Nachfrage: Noch dran, Fertig, Abbruch; die Uhr auch Teilschritt", check(),
            listOf(Button.StillOn, Button.SessionDone, Button.Abort),
            listOf(Button.StillOn, Button.SessionDone, Button.Minimum, Button.Abort),
        ),
        Fall(
            "Minimum vorgeschlagen: Teilschritt statt Fertig", check(SessionAnswer.MINIMUM),
            listOf(Button.StillOn, Button.Minimum, Button.Abort),
            listOf(Button.StillOn, Button.Minimum, Button.SessionDone, Button.Abort),
        ),
        // KRITISCH: am letzten Reminder kein "Noch dran" (D5) – die Engine lehnte es ohnehin ab.
        Fall(
            "letzter Reminder: nur beenden", check(final = true, minimum = false),
            listOf(Button.SessionDone, Button.Abort), listOf(Button.SessionDone, Button.Abort),
        ),
        Fall(
            "Nachweis nötig: kein Fertig aus der Benachrichtigung", check(minimum = false, quickDone = false),
            listOf(Button.StillOn, Button.Abort), listOf(Button.StillOn, Button.Abort),
        ),
        Fall(
            "Alarm mit Sitzung: Ich starte jetzt zuerst", AlarmInfo(1, "Training", null, quickDone = true, snooze = listOf(10, 30), canStart = true),
            listOf(Button.Start, Button.Done, Button.Snooze(10)), listOf(Button.Start, Button.Done, Button.Snooze(10), Button.Snooze(30)),
        ),
        Fall(
            "Alarm ohne Sitzung wie bisher", AlarmInfo(1, "Training", null, quickDone = true, snooze = listOf(10, 30)),
            listOf(Button.Done, Button.Snooze(10), Button.Snooze(30)), listOf(Button.Done, Button.Snooze(10), Button.Snooze(30)),
        ),
    )

    @Test
    fun `Handy und Uhr zeigen die passenden Knöpfe`() {
        for (f in faelle) {
            val buttons = Notifications.buttons(f.info)
            assertEquals(f.name, f.handy, buttons.phone)
            assertEquals(f.name + " (Uhr)", f.uhr, buttons.watch)
        }
    }

    // KRITISCH: Der Alarmdienst bekommt AlarmInfo nur über den Intent; was fehlt, fehlt in seinen Benachrichtigungen.
    @Test
    fun `AlarmInfo kommt vollständig durch den Intent des Alarmdienstes`() {
        val infos = listOf(
            check(SessionAnswer.DONE, final = true),
            AlarmInfo(2, "Abends", "Als Nächstes: Fenster zu", quickDone = true, snooze = listOf(10), nextItem = ChecklistItem(5, "Fenster zu"), canStart = true),
            AlarmInfo(3, "Steuer", null, quickDone = false),
        )
        for (info in infos) assertEquals(info, AlarmInfo.of(info.toBundle()))
    }

    @Test
    fun `die Nachfrage im Vollbild trägt die Antworten, auch auf der Uhr`() {
        AlarmService.start(context, check(), loud = false)
        val intent = shadowOf(context as Application).nextStartedService
        val service = Robolectric.buildService(AlarmService::class.java, intent).create().startCommand(0, 1).get()
        val titles = { n: Notification? -> n?.actions.orEmpty().map { it.title.toString() } }
        assertEquals(listOf("Noch dran", "Fertig", "Abbruch"), titles(shadowOf(service).lastForegroundNotification))
        val mirror = shadowOf(notifications).getNotification(-Notifications.id(1))
        assertEquals(
            listOf("Noch dran", "Fertig", "Teilschritt", "Abbruch"),
            Notification.WearableExtender(mirror).actions.map { it.title.toString() },
        )
        service.onStartCommand(null, 0, 2)
    }

    // --- Aktionen aus der Benachrichtigung ------------------------------------------------------

    private suspend fun training(minimum: Boolean): Long = repo.saveReminder(
        Reminder(
            title = "Training", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = Instant.now(), character = Character.DOCUMENT,
            session = SessionSpec(goalMinutes = 45, minimumMinutes = if (minimum) 10 else null),
        ),
    )

    private fun waitFor(what: String, done: suspend () -> Boolean) = runBlocking {
        repeat(100) {
            if (done()) return@runBlocking
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private fun press(action: String, occurrenceId: Long) {
        context.sendBroadcast(Intent(context, ActionReceiver::class.java).setAction(action).putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, occurrenceId))
        ShadowLooper.idleMainLooper()
    }

    private data class Antwort(val name: String, val action: String, val minimum: Boolean, val state: OccurrenceState, val resolution: Resolution?)

    @Test
    fun `die Antworten aus der Benachrichtigung landen in der Engine`() = runBlocking {
        val antworten = listOf(
            Antwort("Noch dran: läuft weiter", ActionReceiver.ACTION_STILL_ON, false, OccurrenceState.RUNNING, null),
            Antwort("Fertig", ActionReceiver.ACTION_DONE, false, OccurrenceState.DONE, Resolution.GOAL),
            Antwort("Teilschritt", ActionReceiver.ACTION_MINIMUM, true, OccurrenceState.DONE, Resolution.MINIMUM),
            Antwort("Abbruch", ActionReceiver.ACTION_ABORT, false, OccurrenceState.CLOSED, Resolution.ABORTED),
            // Ablehnung: ohne Minimum gibt es keinen Teilschritt, die Sitzung läuft weiter.
            Antwort("Teilschritt ohne Minimum abgelehnt", ActionReceiver.ACTION_MINIMUM, false, OccurrenceState.RUNNING, null),
        )
        for (a in antworten) {
            val id = training(a.minimum)
            val o = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = Instant.now().minusSeconds(60), state = OccurrenceState.DUE))
            press(ActionReceiver.ACTION_START, o)
            waitFor("${a.name}: started") { repo.occurrence(o)?.state == OccurrenceState.RUNNING }
            val firstCheck = repo.occurrence(o)!!.nextCheckAt!!
            Thread.sleep(5)
            press(a.action, o)
            waitFor("${a.name}: answered") {
                val now = repo.occurrence(o)!!
                now.state == a.state && now.resolution == a.resolution && (a.action == ActionReceiver.ACTION_MINIMUM || a.state != OccurrenceState.RUNNING || now.nextCheckAt != firstCheck)
            }
            Thread.sleep(100)
            val after = repo.occurrence(o)!!
            assertEquals(a.name, a.state, after.state)
            assertEquals(a.name, a.resolution, after.resolution)
            if (a.state == OccurrenceState.RUNNING) {
                graph.withEngine { it.act(o, com.avlreminder.core.rules.Action.Abort) }
            }
        }
    }

    // KRITISCH: Die laufende Benachrichtigung zählt ab dem Start (D2) und bietet die Antworten, die die Sitzung beenden.
    @Test
    fun `der Sitzungsdienst zeigt Timer und Antworten`() {
        val note = SessionNote(7, "Training", start, quickDone = true, minimum = true)
        SessionService.show(context, listOf(note))
        val intent = shadowOf(context as Application).nextStartedService
        assertEquals(SessionService::class.java.name, intent.component?.className)
        val service = Robolectric.buildService(SessionService::class.java, intent).create().startCommand(0, 1).get()
        val n = shadowOf(service).lastForegroundNotification
        assertEquals(start.toEpochMilli(), n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(listOf("Fertig", "Teilschritt", "Abbruch"), n.actions.map { it.title.toString() })
        // Ohne Minimum und mit Nachweis bleibt nur Abbruch.
        assertEquals(
            listOf("Abbruch"),
            SessionService.notification(context, note.copy(quickDone = false, minimum = false)).actions.map { it.title.toString() },
        )
        // Beendet: der Dienst hört auf.
        SessionService.show(context, emptyList())
        service.onStartCommand(shadowOf(context).nextStartedService, 0, 2)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    // --- Review-Lauf 4 ---------------------------------------------------------------------------

    /** A running session started [minutesAgo] ago, with the given state of its last reminder. */
    private suspend fun running(reminderId: Long, minutesAgo: Long, finalCallAgo: Long? = null): Long {
        val now = Instant.now()
        return repo.saveOccurrence(
            Occurrence(
                reminderId = reminderId, dueAt = now.minus(Duration.ofMinutes(minutesAgo)), state = OccurrenceState.RUNNING,
                startedAt = now.minus(Duration.ofMinutes(minutesAgo)), nextCheckAt = now.minusSeconds(30),
                finalCallAt = finalCallAgo?.let { now.minus(Duration.ofMinutes(it)) },
            ),
        )
    }

    // Abbruch aus Benachrichtigung oder Uhr: 60 s „Rückgängig“, die Sitzung läuft mit dem alten Start weiter.
    @Test
    fun `Abbruch aus der Benachrichtigung laesst sich zuruecknehmen`() = runBlocking {
        val id = training(minimum = false)
        val o = running(id, minutesAgo = 20)
        val started = repo.occurrence(o)!!.startedAt
        press(ActionReceiver.ACTION_ABORT, o)
        waitFor("aborted") { repo.occurrence(o)?.resolution == Resolution.ABORTED }
        waitFor("undo offered") { shadowOf(notifications).getNotification(Undo.id(o)) != null }
        val offered = shadowOf(notifications).getNotification(Undo.id(o))
        assertEquals("„Training“ abgebrochen", offered.extras.getString(Notification.EXTRA_TITLE))
        // The real button of the notification, not a rebuilt intent.
        offered.actions.single { it.title.toString() == "Rückgängig" }.actionIntent.send()
        ShadowLooper.idleMainLooper()
        waitFor("running again") { repo.occurrence(o)?.state == OccurrenceState.RUNNING }
        assertEquals(started, repo.occurrence(o)!!.startedAt)
    }

    // K29: nach dem letzten Reminder ohne Antwort endet die Sitzung, und nichts von ihr bleibt stehen.
    @Test
    fun `nach dem Ende der Sitzung verschwindet die Benachrichtigung des letzten Reminders`() = runBlocking {
        val id = training(minimum = false)
        val o = running(id, minutesAgo = 100, finalCallAgo = 5)
        notifications.notify(Notifications.id(o), Notification.Builder(context, Notifications.CHANNEL_SILENT).setContentTitle("Training").build())
        context.sendBroadcast(Intent(context, AlarmReceiver::class.java).putExtra(AndroidAlarmPort.EXTRA_OCCURRENCE, o))
        ShadowLooper.idleMainLooper()
        waitFor("ended") { repo.occurrence(o)?.isOpen == false }
        assertEquals(Resolution.MISSED, repo.occurrence(o)!!.resolution)
        waitFor("notification gone") { shadowOf(notifications).getNotification(Notifications.id(o)) == null }
    }

    // Teilschritt nur, solange das Wochenlimit reicht; sonst sagt die Benachrichtigung es.
    @Test
    fun `aufgebrauchtes Minimum – kein Teilschritt in der laufenden Benachrichtigung`() = runBlocking {
        val id = repo.saveReminder(
            Reminder(
                title = "Training", schedule = Schedule.Daily(LocalTime.of(18, 0)), createdAt = Instant.now().minus(Duration.ofDays(1)),
                character = Character.DOCUMENT, session = SessionSpec(minimumMinutes = 10), minimumLimitPerWeek = 1,
            ),
        )
        val now = Instant.now()
        repo.saveOccurrence(
            Occurrence(reminderId = id, dueAt = now.minus(Duration.ofHours(2)), state = OccurrenceState.DONE, resolution = Resolution.MINIMUM, resolvedAt = now.minus(Duration.ofHours(1))),
        )
        val o = repo.saveOccurrence(Occurrence(reminderId = id, dueAt = now.minusSeconds(60), state = OccurrenceState.DUE))
        press(ActionReceiver.ACTION_START, o)
        waitFor("started") { repo.occurrence(o)?.state == OccurrenceState.RUNNING }
        val app = shadowOf(context as Application)
        var shown: Notification? = null
        waitFor("session shown") {
            generateSequence { app.nextStartedService }.lastOrNull { it.component?.className == SessionService::class.java.name }?.let { intent ->
                val service = Robolectric.buildService(SessionService::class.java, intent).create().startCommand(0, 1).get()
                shown = shadowOf(service).lastForegroundNotification
            }
            shown != null
        }
        assertEquals(listOf("Fertig", "Abbruch"), shown!!.actions.map { it.title.toString() })
        assertTrue(shown!!.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Teilschritt diese Woche aufgebraucht"))
    }
}
