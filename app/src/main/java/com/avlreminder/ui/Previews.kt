package com.avlreminder.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.avlreminder.core.model.Occurrence
import com.avlreminder.core.model.OccurrenceState
import com.avlreminder.core.model.ProofSpec
import com.avlreminder.core.model.Reminder
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Schedule
import com.avlreminder.core.model.Strictness
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.usecases.AlarmView
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.HistoryDay
import com.avlreminder.core.usecases.Today
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.screens.AlarmContent
import com.avlreminder.ui.screens.AlarmDevices
import com.avlreminder.ui.screens.EditScreen
import com.avlreminder.ui.screens.HistoryScreen
import com.avlreminder.ui.screens.PermissionItem
import com.avlreminder.ui.screens.ReminderForm
import com.avlreminder.ui.screens.SettingsScreen
import com.avlreminder.ui.screens.TodayScreen
import com.avlreminder.ui.theme.Avl
import com.avlreminder.ui.theme.AvlTheme
import com.avlreminder.ui.theme.isDark
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Light and dark preview of every screen (docs/design.md 11.1). */
@Preview(name = "Hell", widthDp = 390, heightDp = 844)
@Preview(name = "Dunkel", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightDark

private object Sample {
    val zone: ZoneId = ZoneId.of("Europe/Berlin")
    val now: Instant = Instant.parse("2026-10-24T16:30:00Z")
    val date = now.atZone(zone).toLocalDate()

    private fun r(id: Long, title: String, s: Strictness, schedule: Schedule, goal: String? = null, minimum: String? = null, proof: ProofSpec = ProofSpec.CONFIRM) =
        Reminder(id = id, title = title, strictness = s, schedule = schedule, goal = goal, minimum = minimum, proof = proof, createdAt = now)

    val cat = r(1, "Katzenbrunnen reinigen", Strictness.STRICT, Schedule.AfterDone(14, LocalTime.of(18, 0)), "komplett reinigen", "Wasser wechseln", ProofSpec.PHOTO)
        .copy(why = "Sauberes Wasser, damit die Katzen genug trinken.", minimumLimitPerWeek = 1, postponeLimit = 1)
    val training = r(2, "Training", Strictness.NORMAL, Schedule.Daily(LocalTime.of(19, 0)), "45 min", "10 min")
    val vitamin = r(3, "Vitamin D nehmen", Strictness.RELAXED, Schedule.Daily(LocalTime.of(8, 0)))

    private fun o(id: Long, r: Reminder, at: String, state: OccurrenceState = OccurrenceState.PLANNED, res: Resolution? = null) =
        Occurrence(id = id, reminderId = r.id, dueAt = Instant.parse(at), state = state, resolution = res, resolvedAt = res?.let { Instant.parse(at).plusSeconds(240) })

    val due = o(10, cat, "2026-10-24T16:00:00Z", OccurrenceState.ESCALATING)
    val today = Today(
        dueNow = listOf(Entry(cat, due)),
        laterToday = listOf(Entry(training, o(11, training, "2026-10-24T17:00:00Z"))),
        resolved = listOf(Entry(vitamin, o(12, vitamin, "2026-10-24T06:00:00Z", OccurrenceState.DONE, Resolution.GOAL))),
        upcoming = emptyList(),
    )
    val history = listOf(
        HistoryDay(date, today.resolved),
        HistoryDay(date.minusDays(1), listOf(Entry(training, o(13, training, "2026-10-23T17:00:00Z", OccurrenceState.DONE, Resolution.MINIMUM)))),
    )
}

@Composable
private fun Themed(content: @Composable () -> Unit) {
    AvlTheme(dark = isDark(ThemeMode.SYSTEM)) { Box(Modifier.background(Avl.colors.background)) { content() } }
}


@LightDark @Composable
private fun TodayPreview() = Themed {
    TodayScreen(Sample.today, Sample.date, Sample.zone, Sample.now, onOpenDue = {}, onEdit = {})
}

@LightDark @Composable
private fun EditPreview() = Themed {
    EditScreen(ReminderForm.of(Sample.cat, Sample.date), emptyList(), onSave = {}, onCancel = {}, onDelete = {})
}

@LightDark @Composable
private fun HistoryPreview() = Themed {
    HistoryScreen(Sample.history, emptyMap(), Sample.date, Sample.zone)
}

@LightDark @Composable
private fun SettingsPreview() = Themed {
    SettingsScreen(
        ThemeMode.SYSTEM, {}, AlertSettings(), {},
        listOf(PermissionItem("Benachrichtigungen", "ohne sie gibt es keinen Alarm", true) {}, PermissionItem("Kamera", "für Foto-Nachweise", false, required = false) {}),
        onBack = {},
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AlarmPreview() = AvlTheme(dark = true) {
    AlarmContent(
        AlarmView(Sample.cat, Sample.due, minimumAllowed = true, minimumsLeft = 1, canPostpone = true, postponesLeft = 1, snooze = listOf(10, 30)),
        Sample.now, Sample.zone, onComplete = { _, _, _ -> }, onSnooze = {}, onPostpone = {}, onSkip = {},
        onStartProof = {}, onAbortProof = {}, devices = AlarmDevices(camera = {}, recorder = {}, play = {}),
    )
}
