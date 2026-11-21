package com.avlreminder.core.usecases

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.rules.SessionAnswer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Was die Uhr aus dem Zustand macht (design.md 17): Texte, Knöpfe, wann sie frei sind. */
class WatchScreenTest {
    private val session = WatchSession(
        7, "Training", at(26, 18, 2), at(26, 19, 32), 45, 10,
        listOf(SessionAnswer.STILL_ON, SessionAnswer.DONE, SessionAnswer.MINIMUM),
        final = false, proofOnPhone = false, minimumUsedUp = false,
    )

    @Test
    fun `Knoepfe in der Reihenfolge der Antworten, Abbruch immer zuletzt`() {
        assertEquals(listOf(WatchAction.STILL_ON, WatchAction.DONE, WatchAction.MINIMUM, WatchAction.ABORT), WatchScreen.buttons(session))
        assertEquals(listOf(WatchAction.ABORT), WatchScreen.buttons(session.copy(answers = emptyList(), final = true)))
        assertEquals(
            listOf("Ich starte jetzt", "Noch dran", "Fertig", "Teilschritt", "Abbruch", "Rückgängig"),
            WatchAction.entries.map(WatchScreen::label),
        )
    }

    @Test
    fun `ohne Verbindung oder waehrend ein Befehl wartet ist kein Knopf frei`() {
        assertTrue(WatchScreen.enabled(connected = true, waiting = false))
        assertFalse(WatchScreen.enabled(connected = false, waiting = false))
        assertFalse(WatchScreen.enabled(connected = true, waiting = true))
        assertFalse(WatchScreen.enabled(connected = false, waiting = true))
    }

    @Test
    fun `Hinweis ohne Verbindung mit dem Alter des Stands`() {
        assertNull(WatchScreen.connection(true, at(26, 14, 5), ZONE))
        assertEquals("Keine Verbindung zum Handy – Stand 14:05", WatchScreen.connection(false, at(26, 14, 5), ZONE))
        assertEquals("Keine Verbindung zum Handy", WatchScreen.connection(false, null, ZONE))
    }

    @Test
    fun `seit wann und wie lange noch bis zum Maximum`() {
        assertEquals("seit 18:02 · noch 25 min", WatchScreen.detail(session, at(26, 19, 7), ZONE))
        assertEquals("seit 18:02 · noch 1 min", WatchScreen.detail(session, at(26, 19, 31).plusSeconds(59), ZONE))
        assertEquals("seit 18:02 · Maximale Dauer erreicht", WatchScreen.detail(session, at(26, 19, 32), ZONE))
        assertEquals("seit 18:02 · Maximale Dauer erreicht", WatchScreen.detail(session.copy(final = true), at(26, 18, 30), ZONE))
    }

    @Test
    fun `Timer in Minuten und Sekunden, ab einer Stunde mit Stunden`() {
        assertEquals("0:00", WatchScreen.timer(session, at(26, 18, 1)))
        assertEquals("12:05", WatchScreen.timer(session, at(26, 18, 14).plusSeconds(5)))
        assertEquals("1:02:00", WatchScreen.timer(session, at(26, 19, 4)))
    }

    @Test
    fun `vergangene Zeit und Text der Nachfrage`() {
        assertEquals(12 * 60_000L, WatchScreen.elapsedMillis(session, at(26, 18, 14)))
        assertEquals(0L, WatchScreen.elapsedMillis(session, at(26, 18)))
        assertEquals("Noch dran?", WatchScreen.checkText(WatchCheck(7, "Training", audible = true, final = false)))
        assertEquals("Maximale Dauer erreicht", WatchScreen.checkText(WatchCheck(7, "Training", audible = true, final = true)))
    }

    @Test
    fun `Ziel und Minimum als Marken`() {
        assertEquals("Ziel 45 min · Minimum 10 min", WatchScreen.marks(session))
        assertEquals("Ziel 45 min · Teilschritt diese Woche aufgebraucht", WatchScreen.marks(session.copy(minimumUsedUp = true)))
        assertNull(WatchScreen.marks(session.copy(goalMinutes = null, minimumMinutes = null)))
    }

    @Test
    fun `Starten, Naechstes, Notaus und Rueckgaengig`() {
        assertEquals("klingelt", WatchScreen.startDetail(WatchStart(1, "A", at(26, 9), true), ZONE))
        assertEquals("18:00", WatchScreen.startDetail(WatchStart(1, "A", at(26, 18), false), ZONE))
        assertEquals("Als Nächstes: Katzenbrunnen · 08:00", WatchScreen.next(WatchState(next = WatchNext("Katzenbrunnen", at(27, 8))), ZONE))
        assertEquals(WatchScreen.NOTHING, WatchScreen.next(WatchState(), ZONE))
        assertEquals("Notaus bis 23:00", WatchScreen.paused(WatchState(pausedUntil = at(26, 23)), ZONE))
        assertNull(WatchScreen.paused(WatchState(), ZONE))
        assertEquals(at(26, 18, 1), WatchScreen.undoUntil(at(26, 18)))
    }
}
