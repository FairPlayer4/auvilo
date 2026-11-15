package com.avlreminder.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avlreminder.core.model.ChecklistItem
import com.avlreminder.core.rules.Escalation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Der Vordergrunddienst, der laut klingelt: Er übernimmt die Knöpfe des Alarms in seine
 * Benachrichtigungen (auch die Kopie für die Uhr) und klingelt eine Runde lang.
 */
// Auch unter Android 14, dem niedrigsten unterstützten Stand (minSdk 34, playstore.md 3),
// und unter Android 17, für das die App gebaut ist.
@Config(sdk = [34, 37])
@RunWith(AndroidJUnit4::class)
class AlarmServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        ShadowLooper.idleMainLooper()
    }

    private val item = ChecklistItem(id = 5, title = "Fenster zu")

    /** Startet den Dienst so, wie [AlarmService.start] es tut, und liefert ihn. */
    private fun ring(info: AlarmInfo): AlarmService {
        AlarmService.start(app, info, loud = false)
        val intent = shadowOf(app).nextStartedService
        return Robolectric.buildService(AlarmService::class.java, intent).create().startCommand(0, 1).get()
    }

    private fun actions(id: Int): List<String> =
        shadowOf(notifications).getNotification(id)?.actions.orEmpty().map { it.title.toString() }

    private fun Notification?.actionTitles() = this?.actions.orEmpty().map { it.title.toString() }

    private data class Fall(val name: String, val info: AlarmInfo, val knopf: String)

    // KRITISCH: Mit Checkliste hakt der Knopf den nächsten Punkt ab. "Erledigt" schlösse den
    // ganzen Termin ohne gespeicherten Punkt ab (Fehler bis 2026-11-11).
    private val faelle = listOf(
        Fall("Checkliste: nächster Punkt", AlarmInfo(41, "Abends", "Als Nächstes: Fenster zu", quickDone = true, nextItem = item), "✓ Fenster zu"),
        Fall("ohne Checkliste: Erledigt", AlarmInfo(42, "Katzenbrunnen", null, quickDone = true), "Erledigt"),
    )

    @Test
    fun `Vollbild, Uhr-Kopie und stille Benachrichtigung tragen den richtigen Knopf`() {
        for (f in faelle) {
            val service = ring(f.info)
            val foreground = shadowOf(service).lastForegroundNotification
            assertEquals(f.name, f.knopf, foreground.actionTitles().first())
            assertEquals(f.name + " (Uhr)", f.knopf, actions(-Notifications.id(f.info.occurrenceId)).first())
            // Nach der Runde bleibt eine stille Benachrichtigung mit demselben Knopf.
            ShadowLooper.idleMainLooper(Escalation.ROUND.toMillis(), TimeUnit.MILLISECONDS)
            assertNull(f.name, AlarmService.ringing.value)
            assertEquals(f.name + " (still)", f.knopf, actions(Notifications.id(f.info.occurrenceId)).first())
            notifications.cancelAll()
        }
    }

    // Typ systemExempted braucht „Wecker und Erinnerungen“ (Anf. 7.28 PS10): ohne die Erlaubnis
    // gar nicht erst starten, sondern laut benachrichtigen.
    @Test
    fun `ohne Wecker und Erinnerungen kommt eine Benachrichtigung statt des Dienstes`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        AlarmService.start(app, AlarmInfo(43, "Ohne Erlaubnis", null, quickDone = true), loud = true)
        val started = generateSequence { shadowOf(app).nextStartedService }.toList()
        assertTrue(started.none { it.component?.className == AlarmService::class.java.name })
        assertNotNull(shadowOf(notifications).getNotification(Notifications.id(43)))
        notifications.cancelAll()
    }

    @Test
    fun `ein zweiter Alarm übernimmt, der erste bleibt still sichtbar`() {
        val service = ring(AlarmInfo(41, "Erster", null, quickDone = true))
        // Derselbe Dienst bekommt den zweiten Start (Android startet ihn nicht neu).
        AlarmService.start(app, AlarmInfo(42, "Zweiter", null, quickDone = true), loud = false)
        service.onStartCommand(shadowOf(app).nextStartedService, 0, 2)
        assertEquals(42L, AlarmService.ringing.value)
        assertEquals("Erster", shadowOf(notifications).getNotification(Notifications.id(41))?.extras?.getString(Notification.EXTRA_TITLE))
        // Ohne Aktion (Android startet den Dienst neu) hört er auf zu klingeln.
        service.onStartCommand(null, 0, 3)
        assertNull(AlarmService.ringing.value)
    }
}
