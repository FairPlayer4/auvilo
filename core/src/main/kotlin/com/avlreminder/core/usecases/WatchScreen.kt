package com.avlreminder.core.usecases

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the watch app makes of a [WatchState] (design.md 17): its texts and when its buttons are
 * free. The watch computes only this, the phone decides everything else (G9).
 */
object WatchScreen {
    /** The phone gave no answer to a button in [Watch.ANSWER_TIMEOUT_MS]. */
    const val NO_ANSWER = "Keine Antwort vom Handy"

    /** A button the phone rejected (already ended there, another state): the phone's state counts. */
    const val NOT_POSSIBLE = "Geht nicht mehr – auf der Uhr steht jetzt der Stand vom Handy."

    /** Shown for a session whose "Fertig" needs photo, text or audio (G4). */
    const val DONE_ON_PHONE = "Fertig am Handy"

    /** Nothing running, nothing to start, nothing planned. */
    const val NOTHING = "Heute nichts mehr"

    fun label(action: WatchAction): String = when (action) {
        WatchAction.START -> "Ich starte jetzt"
        WatchAction.STILL_ON -> "Noch dran"
        WatchAction.DONE -> "Fertig"
        WatchAction.MINIMUM -> "Teilschritt"
        WatchAction.ABORT -> "Abbruch"
        WatchAction.REOPEN -> "Rückgängig"
    }

    /** The buttons of a running session: its answers, suggested first, and always "Abbruch" last (3.1). */
    fun buttons(session: WatchSession): List<WatchAction> = session.answers.map(WatchAction::of) + WatchAction.ABORT

    /**
     * A button may be pressed only with the phone nearby and while no other command waits for its
     * answer (G9): without a connection the session runs on at the phone.
     */
    fun enabled(connected: Boolean, waiting: Boolean): Boolean = connected && !waiting

    /** Null with a connection; otherwise the hint, with the age of the last state if there is one. */
    fun connection(connected: Boolean, receivedAt: Instant?, zone: ZoneId): String? = when {
        connected -> null
        receivedAt == null -> "Keine Verbindung zum Handy"
        else -> "Keine Verbindung zum Handy – Stand ${time(receivedAt, zone)}"
    }

    /** "seit 18:02 · noch 25 min" until the maximum duration, "Maximale Dauer erreicht" after it. */
    fun detail(session: WatchSession, now: Instant, zone: ZoneId): String {
        val since = "seit ${time(session.startedAt, zone)}"
        if (session.final || !now.isBefore(session.maxAt)) return "$since · Maximale Dauer erreicht"
        // Rounded up: "noch 1 min" until the last second, never "noch 0 min".
        val left = (Duration.between(now, session.maxAt).toSeconds() + 59) / 60
        return "$since · noch $left min"
    }

    /** The time of the timer: "12:05" (minutes:seconds), from one hour "1:02:05". */
    fun timer(session: WatchSession, now: Instant): String {
        val s = Duration.between(session.startedAt, now).toSeconds().coerceAtLeast(0)
        val (h, m, sec) = Triple(s / 3600, s % 3600 / 60, s % 60)
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** How long the session runs, for the stopwatch of the watch face. */
    fun elapsedMillis(session: WatchSession, now: Instant): Long = Duration.between(session.startedAt, now).toMillis().coerceAtLeast(0)

    /** The text of a check on the watch, as the question of the notification (3.1). */
    fun checkText(check: WatchCheck): String = if (check.final) "Maximale Dauer erreicht" else "Noch dran?"

    /** The goal and minimum as marks: "Ziel 45 min · Minimum 10 min"; null without either. */
    fun marks(session: WatchSession): String? = listOfNotNull(
        session.goalMinutes?.let { "Ziel $it min" },
        session.minimumMinutes?.let { if (session.minimumUsedUp) "Teilschritt diese Woche aufgebraucht" else "Minimum $it min" },
    ).joinToString(" · ").ifEmpty { null }

    /** A reminder to start: "klingelt" or its time. */
    fun startDetail(start: WatchStart, zone: ZoneId): String = if (start.ringing) "klingelt" else time(start.at, zone)

    /** "Als Nächstes: Katzenbrunnen · 08:00", or [NOTHING]. */
    fun next(state: WatchState, zone: ZoneId): String =
        state.next?.let { "Als Nächstes: ${it.title} · ${time(it.at, zone)}" } ?: NOTHING

    /** "Notaus bis 23:00" while all alarms are paused (PS7). */
    fun paused(state: WatchState, zone: ZoneId): String? = state.pausedUntil?.let { "Notaus bis ${time(it, zone)}" }

    /** After "Abbruch" on the watch it offers "Rückgängig" until then. */
    fun undoUntil(abortedAt: Instant): Instant = abortedAt.plusMillis(Watch.UNDO_MS)

    private fun time(at: Instant, zone: ZoneId): String = DateTimeFormatter.ofPattern("HH:mm").format(at.atZone(zone))
}
