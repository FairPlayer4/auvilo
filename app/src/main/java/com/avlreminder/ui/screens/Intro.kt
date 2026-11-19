package com.avlreminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.system.Contact
import com.avlreminder.ui.theme.Avl
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The introduction for people other than Kevin (Anf. 7.28 PS15, playstore.md N3): what the app
 * does, the permissions in order and what they are for (PS6), a test alarm, the first reminder
 * and where feedback goes. Shown by itself after a fresh install, again from *Einstellungen →
 * Hilfe*; every page can be skipped.
 */
object Intro {
    const val PAGES = 4
    const val PROBE_TITLE = "Probealarm"

    /**
     * The test alarm rings this many minutes after the start of the current minute, so one to two
     * minutes ahead (14:29:40 → 14:31).
     */
    const val PROBE_MINUTES = 2L

    const val WELCOME = "AuViLo-Reminder ist ein Wecker für Vorhaben, die dir wichtig sind. Zur Uhrzeit klingelt er " +
        "wie ein Wecker und hört erst auf, wenn du reagierst: erledigt, später oder auslassen. Wie streng er dabei ist, " +
        "stellst du für jeden Reminder selbst ein.\n\n" +
        "Wenn etwas schiefläuft: Einstellungen → Notaus schaltet alle Alarme sofort ab.\n\n" +
        "Deine Reminder bleiben auf dem Handy. Ein Cloud-Backup gibt es nur, wenn du es einschaltest."
    const val PERMISSIONS = "Damit Alarme pünktlich kommen und klingeln dürfen, braucht die App diese Erlaubnisse. " +
        "Tippe sie der Reihe nach an; Android öffnet dafür seine Einstellungen, danach geht es hier weiter."
    const val PERMISSIONS_DONE = "Alles Nötige ist erlaubt."
    const val LATER = "Später, wenn du sie brauchst (die App fragt dann selbst):"
    const val PROBE = "Probier aus, wie ein Alarm aussieht: Die App legt einen Reminder „$PROBE_TITLE“ an, der in " +
        "ein bis zwei Minuten klingelt. Sperr das Handy ruhig und tippe dann auf Erledigt. Der Probealarm bleibt " +
        "danach im Verlauf; unter Reminder kannst du ihn löschen."
    const val FIRST = "Neue Reminder legst du mit dem Plus auf Heute an. Wichtig sind der Titel, die Uhrzeit und " +
        "wie streng: Locker erinnert, Normal lässt dich nur begrenzt verschieben, Streng will einen Nachweis, " +
        "z. B. ein Foto. Fang locker an und zieh später an."
    const val FEEDBACK = "Ideen und Fehler: Die Glühbirne oben rechts öffnet auf jeder Seite ein Notizfeld. Unter " +
        "Einstellungen → Verbesserungen sammelt die App deine Notizen; mit Teilen schickst du sie weiter. Oder schreib " +
        "direkt eine Mail an ${Contact.EMAIL}; der Knopf steht auch unter Einstellungen → Hilfe."

    /** On the test alarm's page while *Notaus* is on: the test alarm would stay silent (Review 2026-11-18). */
    const val PAUSED = "Notaus ist an: Alle Alarme sind pausiert, auch der Probealarm bliebe stumm."

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    fun probeAt(at: LocalDateTime) = "Klingelt um ${TIME.format(at)}."
}

@Composable
fun IntroScreen(
    permissions: List<PermissionItem>,
    onProbe: (onResult: (LocalDateTime?) -> Unit) -> Unit,
    onFirstReminder: () -> Unit,
    /** Opens a mail app for feedback ([Contact]). */
    onMail: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** *Notaus* is on (the test alarm would stay silent); [onResume] switches the alarms on again. */
    paused: Boolean = false,
    onResume: () -> Unit = {},
) {
    val c = Avl.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    var probeAt by rememberSaveable { mutableStateOf<LocalDateTime?>(null) }
    var probeFailed by rememberSaveable { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 16.dp).testTag("intro")) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("${page + 1} / ${Intro.PAGES}", Modifier.weight(1f).padding(top = 14.dp), color = c.muted, fontSize = 13.sp)
            TextButton(onClick = onDone, Modifier.testTag("introSkip")) { Text("Überspringen", color = c.muted) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (page) {
                0 -> {
                    Title("Willkommen")
                    Body(Intro.WELCOME)
                }
                1 -> {
                    Title("Berechtigungen")
                    Body(Intro.PERMISSIONS)
                    val (needed, later) = permissions.partition { it.required }
                    Panel { needed.forEachIndexed { i, p -> if (i > 0) HorizontalDivider(color = c.line); PermissionRow(p) } }
                    if (needed.all { it.granted }) Text(Intro.PERMISSIONS_DONE, Modifier.padding(top = 8.dp).testTag("introPermissionsDone"), color = c.text, fontSize = 15.sp)
                    if (later.isNotEmpty()) {
                        Text(Intro.LATER, Modifier.padding(top = 16.dp, bottom = 4.dp), color = c.muted, fontSize = 13.sp)
                        Panel { later.forEachIndexed { i, p -> if (i > 0) HorizontalDivider(color = c.line); PermissionRow(p) } }
                    }
                }
                2 -> {
                    Title("Probealarm")
                    Body(Intro.PROBE)
                    if (paused) {
                        Text(Intro.PAUSED, Modifier.padding(top = 12.dp).testTag("introPaused"), color = c.strict, fontSize = 15.sp)
                        TextButton(onClick = onResume, Modifier.testTag("introResume")) { Text("Wieder einschalten", color = c.accent) }
                    }
                    OutlinedButton(
                        onClick = {
                            probeFailed = false
                            onProbe { at -> probeAt = at; probeFailed = at == null }
                        },
                        Modifier.padding(top = 12.dp).testTag("introProbe"),
                    ) { Text(if (probeAt == null) "Probealarm stellen" else "Noch einen Probealarm") }
                    probeAt?.let { Text(Intro.probeAt(it), Modifier.padding(top = 8.dp).testTag("introProbeAt"), color = c.text, fontSize = 15.sp) }
                    if (probeFailed) Text("Das hat nicht geklappt; der Grund steht unten im Hinweis.", Modifier.padding(top = 8.dp), color = c.strict, fontSize = 14.sp)
                }
                else -> {
                    Title("Dein erster Reminder")
                    Body(Intro.FIRST)
                    Button(
                        onClick = onFirstReminder,
                        Modifier.padding(top = 12.dp).testTag("introFirst"),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentFill, contentColor = Color.White),
                    ) { Text("Ersten Reminder anlegen") }
                    Title("Rückmeldung")
                    Body(Intro.FEEDBACK)
                    OutlinedButton(onClick = onMail, Modifier.padding(top = 12.dp).testTag("introMail")) { Text("Rückmeldung per E-Mail") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            if (page > 0) TextButton(onClick = { page-- }, Modifier.testTag("introBack")) { Text("Zurück", color = c.muted) }
            Spacer(Modifier.weight(1f))
            if (page < Intro.PAGES - 1) {
                Button(
                    onClick = { page++ }, Modifier.testTag("introNext"),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentFill, contentColor = Color.White),
                ) { Text("Weiter") }
            } else {
                TextButton(onClick = onDone, Modifier.testTag("introDone")) { Text("Fertig", color = c.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp), color = Avl.colors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Body(text: String) {
    Text(text, color = Avl.colors.text, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Avl.colors.surface)) { content() }
}
