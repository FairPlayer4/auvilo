package com.avlreminder.core.rules

import com.avlreminder.core.ZONE
import com.avlreminder.core.at
import com.avlreminder.core.model.EventType
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.SessionSpec
import com.avlreminder.core.reminder
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Sitzung mit Timer und Nachfragen (Anf. 7.11 D1–D7) als reine Regeln. */
class SessionsTest {
    /** What a transition should give: the state, resolution and times, or a rejection (null). */
    data class Expected(
        val state: OccurrenceState,
        val resolution: Resolution? = null,
        val startedAt: Instant? = null,
        val nextCheckAt: Instant? = null,
        val finalCallAt: Instant? = null,
        val event: EventType? = null,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("uebergaenge")
    fun `Uebergaenge der Sitzung`(name: String, r: Reminder, o: Occurrence, action: Action, now: Instant, limits: Limits, expected: Expected?) {
        val result = Lifecycle.transition(r, o, action, now, limits)
        if (expected == null) {
            assertInstanceOf(TransitionResult.Rejected::class.java, result, name)
            return
        }
        val ok = assertInstanceOf(TransitionResult.Ok::class.java, result, name)
        val got = ok.occurrence
        assertEquals(expected, Expected(got.state, got.resolution, got.startedAt, got.nextCheckAt, got.finalCallAt, expected.event?.let { ok.event.type }), name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schritte")
    fun `was der Alarm einer laufenden Sitzung tut`(name: String, o: Occurrence, now: Instant, quiet: Boolean, paused: Boolean, step: SessionStep) {
        assertEquals(step, Sessions.step(TRAINING, o, now, quiet, paused), name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("alarme")
    fun `wann der Alarm der Sitzung kommt`(name: String, o: Occurrence, at: Instant?) {
        assertEquals(at, Sessions.nextAlarm(TRAINING, o), name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vorschlaege")
    fun `welche Antwort die Dauer vorschlaegt (D4)`(name: String, spec: SessionSpec, minutes: Long, answer: SessionAnswer) {
        assertEquals(answer, Sessions.suggestion(spec, START, START.plus(Duration.ofMinutes(minutes))), name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schnitte")
    fun `der Schnitt der letzten Sitzungen (D6)`(name: String, resolved: List<Occurrence>, average: Duration?) {
        assertEquals(average, Sessions.average(resolved), name)
    }

    @Test
    fun `abgebrochen bricht die Serie wie verpasst, ausgelassen nicht`() {
        val daily = reminder(Schedule.Daily(LocalTime.of(18, 0)))
        fun day(d: Int, resolution: Resolution) = Occurrence(
            id = d.toLong(), reminderId = 1, dueAt = at(d, 18), resolvedAt = at(d, 19), resolution = resolution,
            state = if (resolution == Resolution.GOAL) OccurrenceState.DONE else OccurrenceState.CLOSED,
        )
        // Two misses in one week: the joker covers the first, the second ends the series.
        val aborted = listOf(day(26, Resolution.GOAL), day(27, Resolution.ABORTED), day(28, Resolution.ABORTED))
        val skipped = listOf(day(26, Resolution.GOAL), day(27, Resolution.SKIPPED), day(28, Resolution.SKIPPED))
        assertEquals(0, Stats.streak(daily, aborted, at(29, 8), ZONE)!!.current)
        assertEquals(1, Stats.streak(daily, skipped, at(29, 8), ZONE)!!.current)
        assertEquals(2, Stats.counts(aborted).aborted)
    }

    /** Screen, notification and watch offer the same answers in the same order (Review-Lauf 5, E18). */
    // Ohne Bildschirm (Benachrichtigung, Uhr) kein Fertig und kein Teilschritt mit Nachweis (Review-Lauf 6, Antwort 2).
    @Test
    fun `without the screen a proof leaves only Noch dran`() {
        assertEquals(
            listOf(SessionAnswer.MINIMUM, SessionAnswer.STILL_ON, SessionAnswer.DONE),
            Sessions.quickAnswers(final = false, suggestion = SessionAnswer.MINIMUM, minimum = true, quick = true),
        )
        assertEquals(listOf(SessionAnswer.STILL_ON), Sessions.quickAnswers(final = false, suggestion = SessionAnswer.MINIMUM, minimum = true, quick = false))
        assertEquals(emptyList<SessionAnswer>(), Sessions.quickAnswers(final = true, suggestion = SessionAnswer.DONE, minimum = true, quick = false))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("antworten")
    fun `the answers of a session`(@Suppress("unused") name: String, final: Boolean, suggestion: SessionAnswer, minimum: Boolean, expected: List<SessionAnswer>) {
        assertEquals(expected, Sessions.answers(final, suggestion, minimum))
    }

    companion object {
        private val START = at(26, 18)
        private fun min(m: Long) = START.plus(Duration.ofMinutes(m))

        /** Every 10 min, at most 90, 5 to react; goal 45 min, minimum 10 min. */
        private val SPEC = SessionSpec(goalMinutes = 45, minimumMinutes = 10)
        private val TRAINING = reminder(Schedule.Daily(LocalTime.of(18, 0))).copy(session = SPEC)
        private val PLAIN = reminder(Schedule.Daily(LocalTime.of(18, 0)))
        private val NO_MINIMUM = TRAINING.copy(session = SessionSpec())

        private val PLANNED = Occurrence(id = 1, reminderId = 1, dueAt = at(26, 19))
        private val DUE = PLANNED.copy(dueAt = START, state = OccurrenceState.DUE, firedAt = START)
        private val ESCALATING = DUE.copy(state = OccurrenceState.ESCALATING, pausedUntil = min(5))
        private val RUNNING = DUE.copy(state = OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(10))
        private val FINAL = RUNNING.copy(nextCheckAt = min(95), finalCallAt = min(90))
        private val NONE = Limits()
        private val ITEM = Limits(itemsDone = mapOf(1L to true))

        @JvmStatic
        fun uebergaenge() = listOf(
            arguments("Start aus dem Alarm: läuft, erste Nachfrage nach dem Intervall", TRAINING, DUE, Action.Start, min(2), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = min(2), nextCheckAt = min(12), event = EventType.STARTED)),
            arguments("Start vorab (V2): läuft vor der Uhrzeit", TRAINING, PLANNED, Action.Start, START, NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(10))),
            arguments("Start beendet die Eskalation", TRAINING, ESCALATING, Action.Start, min(3), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = min(3), nextCheckAt = min(13))),
            arguments("Start mit Intervall über der maximalen Dauer: erste Nachfrage ist der letzte Reminder", TRAINING.copy(session = SessionSpec(checkMinutes = 60, maxMinutes = 30)),
                DUE, Action.Start, START, NONE, Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(30))),
            arguments("Start ohne Sitzung am Reminder: abgelehnt", PLAIN, DUE, Action.Start, START, NONE, null),
            arguments("Start, wenn sie schon läuft: abgelehnt", TRAINING, RUNNING, Action.Start, min(5), NONE, null),
            arguments("Noch dran: nächste Nachfrage ab jetzt", TRAINING, RUNNING, Action.StillOn, min(13), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(23), event = EventType.CHECK_ANSWERED)),
            arguments("Noch dran kurz vor dem Maximum: Nachfrage höchstens bis dahin", TRAINING, RUNNING, Action.StillOn, min(85), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(90))),
            arguments("Noch dran ab der maximalen Dauer: abgelehnt", TRAINING, RUNNING, Action.StillOn, min(90), NONE, null),
            arguments("Noch dran nach dem letzten Reminder: abgelehnt", TRAINING, FINAL, Action.StillOn, min(91), NONE, null),
            arguments("Noch dran ohne laufende Sitzung: abgelehnt", TRAINING, DUE, Action.StillOn, min(5), NONE, null),
            arguments("Fertig: Ziel, der Start bleibt für die Dauer", TRAINING, RUNNING, Action.Complete(minimumOnly = false), min(48), NONE,
                Expected(OccurrenceState.DONE, Resolution.GOAL, startedAt = START)),
            arguments("Fertig nach dem letzten Reminder: Ziel", TRAINING, FINAL, Action.Complete(minimumOnly = false), min(92), NONE,
                Expected(OccurrenceState.DONE, Resolution.GOAL, startedAt = START, finalCallAt = min(90))),
            arguments("Teilschritt mit Minimum als Dauer: Minimum", TRAINING, RUNNING, Action.Complete(minimumOnly = true), min(12), NONE,
                Expected(OccurrenceState.DONE, Resolution.MINIMUM, startedAt = START)),
            arguments("Teilschritt ohne Minimum: abgelehnt", NO_MINIMUM, RUNNING, Action.Complete(minimumOnly = true), min(12), NONE, null),
            arguments("Abbruch: abgebrochen", TRAINING, RUNNING, Action.Abort, min(20), NONE,
                Expected(OccurrenceState.CLOSED, Resolution.ABORTED, startedAt = START, event = EventType.ABORTED)),
            arguments("Abbruch ohne laufende Sitzung: abgelehnt", TRAINING, DUE, Action.Abort, min(5), NONE, null),
            arguments("Später während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.Snooze(Duration.ofMinutes(10)), min(5), NONE, null),
            arguments("Verschieben während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.Postpone(at(27, 18)), min(5), NONE, null),
            arguments("Auslassen während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.Skip(null), min(5), NONE, null),
            arguments("Verpasst zur Tagesgrenze während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.Miss, at(26, 23), NONE, null),
            arguments("Eskalation während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.Escalate, min(5), NONE, null),
            arguments("Fensterende während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.CloseWindow(at(27, 18)), min(5), NONE, null),
            arguments("Rest auf morgen während der Sitzung: abgelehnt", TRAINING, RUNNING, Action.DeferRest(at(27, 18)), min(5), NONE, null),
            arguments("Nachweis-Bildschirm während der Sitzung: erlaubt", TRAINING, RUNNING, Action.StartProof(min(20)), min(10), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(10))),
            arguments("Nachfrage: fragt ohne Antwort wieder", TRAINING, RUNNING, Action.AskCheck(min(20)), min(10), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(20), event = EventType.CHECK_ASKED)),
            arguments("Nachfrage ohne laufende Sitzung: abgelehnt", TRAINING, DUE, Action.AskCheck(min(20)), min(10), NONE, null),
            arguments("letzter Reminder hörbar: die Reaktionszeit läuft", TRAINING, RUNNING, Action.FinalCall(true, min(95)), min(90), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(95), finalCallAt = min(90), event = EventType.FINAL_CALL)),
            arguments("letzter Reminder still (DND): die Reaktionszeit läuft noch nicht", TRAINING, RUNNING, Action.FinalCall(false, min(95)), min(90), NONE,
                Expected(OccurrenceState.RUNNING, startedAt = START, nextCheckAt = min(95))),
            arguments("Ende ohne Antwort: verpasst", TRAINING, FINAL, Action.EndSession(paused = false), min(95), NONE,
                Expected(OccurrenceState.CLOSED, Resolution.MISSED, startedAt = START, finalCallAt = min(90), event = EventType.MISSED)),
            arguments("Ende ohne Antwort mit erledigtem Punkt: Minimum (D5)", TRAINING, FINAL, Action.EndSession(paused = false), min(95), ITEM,
                Expected(OccurrenceState.DONE, Resolution.MINIMUM, startedAt = START, finalCallAt = min(90), event = EventType.COMPLETED)),
            arguments("Ende im Notaus: abgebrochen", TRAINING, RUNNING, Action.EndSession(paused = true), min(90), NONE,
                Expected(OccurrenceState.CLOSED, Resolution.ABORTED, startedAt = START, event = EventType.ABORTED)),
            arguments("Ende im Notaus mit erledigtem Punkt: Minimum", TRAINING, RUNNING, Action.EndSession(paused = true), min(90), ITEM,
                Expected(OccurrenceState.DONE, Resolution.MINIMUM, startedAt = START)),
            arguments("Ende ohne laufende Sitzung: abgelehnt", TRAINING, DUE, Action.EndSession(paused = false), min(95), NONE, null),
            arguments("Zurücknehmen nach der Sitzung: wieder geplant, ohne Start", TRAINING,
                RUNNING.copy(state = OccurrenceState.DONE, resolution = Resolution.GOAL, resolvedAt = min(40), nextCheckAt = null),
                Action.Reopen(min(50)), min(41), NONE, Expected(OccurrenceState.PLANNED)),
        )

        @JvmStatic
        fun schritte() = listOf(
            arguments("Nachfrage vor dem Maximum: fragt im Intervall wieder", RUNNING, min(10), false, false, SessionStep.Check(min(20))),
            arguments("Nachfrage kurz vor dem Maximum: fragt höchstens bis dahin wieder", RUNNING.copy(nextCheckAt = min(85)), min(85), false, false, SessionStep.Check(min(90))),
            arguments("Nachfrage im Notaus: kommt (still)", RUNNING, min(10), false, true, SessionStep.Check(min(20))),
            arguments("Alarm eine Minute vor dem Maximum: letzter Reminder, keine Nachfrage", RUNNING.copy(nextCheckAt = min(90)), min(89), false, false,
                SessionStep.FinalCall(audible = true, again = min(94))),
            arguments("Maximum bei „Nicht stören“: letzter Reminder still", RUNNING.copy(nextCheckAt = min(90)), min(90), true, false,
                SessionStep.FinalCall(audible = false, again = min(95))),
            arguments("Maximum im Notaus: Ende ohne letzten Alarm (D7)", RUNNING.copy(nextCheckAt = min(90)), min(90), false, true, SessionStep.TimeOut),
            arguments("Reaktionszeit nach dem letzten Reminder um: Ende", FINAL, min(95), false, false, SessionStep.TimeOut),
            arguments("Nachfrage erst nach dem Maximum gefunden (Handy aus): letzter Reminder", RUNNING, min(100), false, false,
                SessionStep.FinalCall(audible = true, again = min(105))),
        )

        @JvmStatic
        fun alarme() = listOf(
            arguments("läuft: die nächste Nachfrage", RUNNING, min(10)),
            arguments("ohne Nachfrage: das Maximum", RUNNING.copy(nextCheckAt = null), min(90)),
            arguments("still gerufen (DND): fragt wieder", RUNNING.copy(nextCheckAt = min(95)), min(95)),
            arguments("letzter Reminder gerufen: Ende der Reaktionszeit", FINAL.copy(nextCheckAt = null), min(95)),
            arguments("läuft nicht: kein Alarm der Sitzung", DUE, null),
        )

        @JvmStatic
        fun vorschlaege() = listOf(
            arguments("vor der Ziel-Dauer: Noch dran", SPEC, 44L, SessionAnswer.STILL_ON),
            arguments("Ziel-Dauer erreicht: Fertig", SPEC, 45L, SessionAnswer.DONE),
            arguments("mit Ziel-Dauer ist das Minimum kein Vorschlag", SPEC, 20L, SessionAnswer.STILL_ON),
            arguments("ohne Ziel, Minimum-Dauer erreicht: Teilschritt", SessionSpec(minimumMinutes = 10), 10L, SessionAnswer.MINIMUM),
            arguments("ohne Ziel, vor der Minimum-Dauer: Noch dran", SessionSpec(minimumMinutes = 10), 9L, SessionAnswer.STILL_ON),
            arguments("ohne Dauern: Noch dran", SessionSpec(), 80L, SessionAnswer.STILL_ON),
        )

        private fun done(minutes: Long, resolution: Resolution = Resolution.GOAL) = Occurrence(
            reminderId = 1, dueAt = START, startedAt = START, resolvedAt = min(minutes), resolution = resolution,
            state = if (resolution == Resolution.GOAL || resolution == Resolution.MINIMUM) OccurrenceState.DONE else OccurrenceState.CLOSED,
        )

        @JvmStatic
        fun antworten() = listOf(
            arguments("Noch dran vorgeschlagen", false, SessionAnswer.STILL_ON, true, listOf(SessionAnswer.STILL_ON, SessionAnswer.DONE, SessionAnswer.MINIMUM)),
            arguments("Ziel erreicht: Fertig zuerst", false, SessionAnswer.DONE, true, listOf(SessionAnswer.DONE, SessionAnswer.STILL_ON, SessionAnswer.MINIMUM)),
            arguments("Minimum erreicht: Teilschritt zuerst", false, SessionAnswer.MINIMUM, true, listOf(SessionAnswer.MINIMUM, SessionAnswer.STILL_ON, SessionAnswer.DONE)),
            arguments("letzter Reminder: nur beenden", true, SessionAnswer.STILL_ON, true, listOf(SessionAnswer.DONE, SessionAnswer.MINIMUM)),
            arguments("ohne Minimum kein Teilschritt", false, SessionAnswer.MINIMUM, false, listOf(SessionAnswer.STILL_ON, SessionAnswer.DONE)),
        )

        @JvmStatic
        fun schnitte() = listOf(
            arguments("Ziel und Minimum zählen, abgebrochen und ohne Sitzung nicht", listOf(
                done(20), done(30, Resolution.MINIMUM), done(5, Resolution.ABORTED), done(40).copy(startedAt = null),
            ), Duration.ofMinutes(25)),
            arguments("nur die letzten zehn", List(10) { done(10) } + done(70), Duration.ofMinutes(10)),
            arguments("ohne Sitzung: kein Schnitt", listOf(done(5, Resolution.ABORTED)), null),
        )
    }
}
