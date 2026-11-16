package com.avlreminder.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Eine Aufnahme (Anf. 7.35 SR2): Stücke sammeln bis „Fertig“, Pausen überbrücken, Fehler melden. */
class VoiceSessionTest {
    private class FakeRecognizer(private val canFallBack: Boolean = false) : Recognizer {
        var starts = 0
        var stops = 0
        var fallbacks = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
        override fun fallback(): Boolean { fallbacks++; return canFallBack }
    }

    private val states = mutableListOf<VoiceState>()
    private fun session(r: Recognizer) = VoiceSession(r) { states += it }.also { it.start() }

    @Test
    fun `Stuecke werden gesammelt und nach Fertig zusammen gemeldet`() {
        val r = FakeRecognizer()
        val s = session(r)
        s.onPartial("Zeit in 5")
        assertEquals(VoiceState.Listening("Zeit in 5"), states.last())
        s.onPiece("Zeit in 5 Minuten")
        assertEquals("nach einer Pause hört sie weiter zu", 2, r.starts)
        s.onPiece("Name Tee machen")
        s.finish()
        assertEquals(1, r.stops)
        s.onPiece("")
        assertEquals(VoiceState.Heard("Zeit in 5 Minuten Name Tee machen"), states.last())
    }

    @Test
    fun `nach Fertig zaehlt auch ein Fehler, das Gehoerte bleibt`() {
        val s = session(FakeRecognizer())
        s.onPiece("Zeit in 5 Minuten")
        s.onPartial("Name Tee")
        s.finish()
        s.onError(ListenError.NOTHING_HEARD)
        assertEquals(VoiceState.Heard("Zeit in 5 Minuten Name Tee"), states.last())
    }

    @Test
    fun `eine Pause ohne Ergebnis behaelt den Zwischentext und hoert weiter zu`() {
        val r = FakeRecognizer()
        val s = session(r)
        s.onPartial("Zeit in 5 Minuten")
        s.onError(ListenError.NOTHING_HEARD)
        assertEquals(2, r.starts)
        s.finish()
        s.onError(ListenError.NOTHING_HEARD)
        assertEquals(VoiceState.Heard("Zeit in 5 Minuten"), states.last())
    }

    @Test
    fun `nichts gehoert wird ein Fehler, keine leere Erinnerung`() {
        val s = session(FakeRecognizer())
        s.finish()
        s.onError(ListenError.NOTHING_HEARD)
        assertEquals(VoiceState.Failed("Nichts verstanden."), states.last())
    }

    @Test
    fun `lange Stille beendet die Aufnahme statt endlos neu zu starten`() {
        val r = FakeRecognizer()
        val s = session(r)
        repeat(VoiceSession.MAX_EMPTY_RESTARTS) { s.onError(ListenError.NOTHING_HEARD) }
        assertEquals("bis zur Grenze startet sie neu", VoiceSession.MAX_EMPTY_RESTARTS + 1, r.starts)
        s.onError(ListenError.NOTHING_HEARD)
        assertEquals(VoiceSession.MAX_EMPTY_RESTARTS + 1, r.starts)
        assertEquals(VoiceState.Failed("Nichts verstanden."), states.last())
    }

    @Test
    fun `fehlt Deutsch auf dem Geraet, nimmt sie den Android-Dienst`() {
        val r = FakeRecognizer(canFallBack = true)
        val s = session(r)
        s.onError(ListenError.LANGUAGE_MISSING)
        assertEquals(1, r.fallbacks)
        assertEquals(2, r.starts)
        assertEquals(VoiceState.Listening(""), states.last())
    }

    @Test
    fun `fehlt Deutsch ohne Ausweg, sagt sie, was zu tun ist`() {
        val s = session(FakeRecognizer(canFallBack = false))
        s.onError(ListenError.LANGUAGE_MISSING)
        val failed = states.last() as VoiceState.Failed
        assert(failed.message.startsWith("Deutsch fehlt")) { failed.message }
    }

    @Test
    fun `nach dem Ende aendert nichts mehr den Zustand`() {
        val r = FakeRecognizer()
        val s = session(r)
        s.onPiece("Tee")
        s.finish()
        s.onPiece("")
        val end = states.size
        s.onPiece("noch was")
        s.onError(ListenError.OTHER)
        s.finish()
        assertEquals(end, states.size)
        assertEquals(1, r.stops)
    }

    @Test
    fun `andere Fehler beenden die Aufnahme mit Meldung`() {
        val s = session(FakeRecognizer())
        s.onError(ListenError.OTHER)
        assertEquals(VoiceState.Failed("Die Spracherkennung ist ausgefallen."), states.last())
    }

    @Test
    fun `Nochmal beginnt ohne die alten Stuecke`() {
        val s = session(FakeRecognizer())
        s.onPiece("alt")
        s.finish()
        s.onPiece("")
        s.start()
        s.onPiece("neu")
        s.finish()
        s.onPiece("")
        assertEquals(VoiceState.Heard("neu"), states.last())
    }
}
