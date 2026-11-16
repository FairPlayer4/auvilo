package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Schedule
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Schnellerinnerung aus Sprache (Anf. 7.35 SR3, SR4, SR6): was der Parser aus dem Gehörten macht. */
class VoiceRemindersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("verstanden")
    fun `das Muster wird verstanden`(name: String, heard: String, title: String, minutes: Long) {
        assertEquals(SpokenReminder(title, minutes, timeHeard = true, titleHeard = true), VoiceReminders.parse(heard), name)
    }

    // KRITISCH: Zu jeder erkannten Zeit ein Fall, in dem sie nicht gilt; dann Standardzeit und Hinweis.
    @ParameterizedTest(name = "{0}")
    @MethodSource("nichtVerstanden")
    fun `was nicht verstanden wird, bekommt den Standard`(name: String, heard: String, expected: SpokenReminder) {
        assertEquals(expected, VoiceReminders.parse(heard), name)
    }

    @Test
    fun `das angezeigte Muster selbst wird verstanden`() {
        val spoken = VoiceReminders.parse(VoiceReminders.PATTERN)
        assertTrue(spoken.timeHeard && spoken.titleHeard)
        assertEquals(emptyList<String>(), spoken.hints)
    }

    @Test
    fun `Hinweise nennen, was fehlte`() {
        assertEquals(
            listOf("Keine Zeit erkannt, Standard 10 min", "Kein Name erkannt"),
            VoiceReminders.parse("").hints,
        )
    }

    @Test
    fun `die Erinnerung ist einmalig, ohne Nachweis und gueltig`() {
        val r = VoiceReminders.reminder(SpokenReminder("Tee machen", 5, timeHeard = true, titleHeard = true), at(26, 14, 5), ZONE)
        assertEquals("Tee machen", r.title)
        assertEquals(Schedule.Once(LocalDateTime.of(2026, 10, 26, 14, 10)), r.schedule)
        assertEquals(ProofSpec.NONE, r.proof)
        assertTrue(Validation.problems(r).isEmpty())
    }

    companion object {
        @JvmStatic
        fun verstanden() = listOf(
            arguments("Muster mit Doppelpunkten", "Zeit: in 5 Minuten. Name: Tee machen", "Tee machen", 5L),
            arguments("wie die Erkennung es liefert, ohne Satzzeichen", "Zeit in 5 Minuten Name Tee machen", "Tee machen", 5L),
            arguments("Großschreibung egal", "ZEIT IN 20 MINUTEN NAME Wäsche aufhängen", "Wäsche aufhängen", 20L),
            arguments("Name zuerst, Zeit danach", "Name Anna zurückrufen Zeit in 20 Minuten", "Anna zurückrufen", 20L),
            arguments("ohne Schlüsselwörter", "in 20 Minuten Anna zurückrufen", "Anna zurückrufen", 20L),
            arguments("ohne Schlüsselwörter, Zeit am Ende", "Müll rausbringen in 5 min", "Müll rausbringen", 5L),
            arguments("Zahl als Wort", "Zeit in fünf Minuten Name Tee", "Tee", 5L),
            arguments("eine Minute", "Zeit in einer Minute Name Herd aus", "Herd aus", 1L),
            arguments("Viertelstunde", "Zeit in einer Viertelstunde Name Pizza", "Pizza", 15L),
            arguments("halbe Stunde, nicht eine Stunde", "Zeit in einer halben Stunde Name Pizza", "Pizza", 30L),
            arguments("Dreiviertelstunde", "Zeit in einer Dreiviertelstunde Name Pizza", "Pizza", 45L),
            arguments("Stunden", "Zeit in 2 Stunden Name Auto umparken", "Auto umparken", 120L),
            arguments("eineinhalb Stunden", "Zeit in eineinhalb Stunden Name Brot", "Brot", 90L),
            arguments("Komma", "Zeit in 1,5 Stunden Name Brot", "Brot", 90L),
            arguments("Abkürzung Std", "in 3 Std. Paket abholen", "Paket abholen", 180L),
            arguments("Tage", "Zeit in 2 Tagen Name Pflanzen gießen", "Pflanzen gießen", 2880L),
            arguments("Namen statt Name", "Zeit in 5 Minuten Namen Tee", "Tee", 5L),
            arguments("Zeit mitten im Namen bleibt", "Zeit in 5 Minuten Name Zeit für Tee", "Zeit für Tee", 5L),
            arguments("Namensschild ist kein Schlüsselwort", "in 5 Minuten Namensschild drucken", "Namensschild drucken", 5L),
            arguments("kleiner Anfang wird groß", "in 5 Minuten tee", "Tee", 5L),
            arguments("genau 7 Tage", "in 7 Tagen Reifen", "Reifen", VoiceReminders.MAX_MINUTES),
        )

        @JvmStatic
        fun nichtVerstanden() = listOf(
            arguments("keine Zeit gesagt", "Name Tee machen", SpokenReminder("Tee machen", 10, timeHeard = false, titleHeard = true)),
            arguments("Zeit ohne in", "Zeit 5 Minuten Name Tee", SpokenReminder("Tee", 10, timeHeard = false, titleHeard = true)),
            arguments("Zeit ohne Einheit", "in 5 Name Tee", SpokenReminder("Tee", 10, timeHeard = false, titleHeard = true)),
            arguments("null Minuten", "Zeit in 0 Minuten Name Tee", SpokenReminder("Tee", 10, timeHeard = false, titleHeard = true)),
            arguments("über 7 Tage", "Zeit in 8 Tagen Name Tee", SpokenReminder("Tee", 10, timeHeard = false, titleHeard = true)),
            arguments("nur die Zeit", "Zeit in 5 Minuten", SpokenReminder(VoiceReminders.DEFAULT_TITLE, 5, timeHeard = true, titleHeard = false)),
            arguments("Name leer", "Zeit in 5 Minuten Name", SpokenReminder(VoiceReminders.DEFAULT_TITLE, 5, timeHeard = true, titleHeard = false)),
            arguments("nichts gehört", "", SpokenReminder(VoiceReminders.DEFAULT_TITLE, 10, timeHeard = false, titleHeard = false)),
        )
    }
}
