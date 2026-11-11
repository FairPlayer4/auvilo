package com.avlreminder.ui.screens

import androidx.compose.material3.OutlinedTextField
import com.avlreminder.PlaceSetResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.avlreminder.cloud.CloudStatus
import com.avlreminder.core.model.Place
import com.avlreminder.alarm.AlarmPause
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.core.rules.DayTimes
import com.avlreminder.data.OverviewTimes
import com.avlreminder.data.Settings
import com.avlreminder.data.TemplateEntity
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.Format
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.TimeDialog
import com.avlreminder.ui.theme.Avl
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A permission the app needs, with its state and how to ask for it. */
data class PermissionItem(
    val label: String,
    val why: String,
    val granted: Boolean,
    /** Missing required permissions are shown as a warning on "Heute". */
    val required: Boolean = true,
    val request: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    alert: AlertSettings,
    onAlert: (AlertSettings) -> Unit,
    permissions: List<PermissionItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    places: List<Place> = emptyList(),
    onSetPlace: (placeId: Long?, name: String, onResult: (PlaceSetResult) -> Unit) -> Unit = { _, _, _ -> },
    dwellMinutes: Int = Settings.DEFAULT_DWELL,
    onDwell: (Int) -> Unit = {},
    openSuggestions: Int = 0,
    onSuggestions: () -> Unit = {},
    overview: OverviewTimes = OverviewTimes(),
    onOverview: (OverviewTimes) -> Unit = {},
    templates: List<TemplateEntity> = emptyList(),
    onRenameTemplate: (Long, String) -> Unit = { _, _ -> },
    onDeleteTemplate: (Long) -> Unit = {},
    onPause: (Instant?) -> Unit = {},
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    claudeAccess: Boolean? = null,
    onClaudeAccess: (Boolean) -> Unit = {},
    cloud: CloudStatus? = null,
    cloudActions: CloudActions = object : CloudActions {},
    local: com.avlreminder.restore.LocalStatus? = null,
    localActions: LocalActions = object : LocalActions {},
) {
    val c = Avl.colors
    Column(modifier) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text("Einstellungen", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Notaus")
            Card { PauseSetting(alert, onPause, now, zone) }

            SectionTitle("Verbesserungen")
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(if (openSuggestions == 0) "Keine offenen Vorschläge" else "$openSuggestions offen", color = c.text, fontSize = 15.sp)
                    TextButton(onClick = onSuggestions) { Text("Vorschläge ansehen", color = c.accent) }
                }
            }

            SectionTitle("Darstellung")
            Card {
                listOf(
                    Triple(ThemeMode.SYSTEM, "System", "folgt der Einstellung des Handys (Standard)"),
                    Triple(ThemeMode.LIGHT, "Hell", "immer hell"),
                    Triple(ThemeMode.DARK, "Dunkel", "immer dunkel"),
                ).forEachIndexed { i, (mode, label, hint) ->
                    if (i > 0) HorizontalDivider(color = c.line)
                    Row(
                        Modifier.fillMaxWidth().background(if (mode == theme) c.accentSoft else c.surface)
                            .clickable { onTheme(mode) }.heightIn(min = 64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == theme, onClick = { onTheme(mode) })
                        Column {
                            Text(label, color = c.text, fontSize = 16.sp)
                            Text(hint, color = c.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            Text("Der Alarm bleibt immer dunkel. Das Widget folgt dem System.", Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp)

            SectionTitle("Alarmzeiten")
            Card {
                Column(Modifier.padding(16.dp)) {
                    TimeSetting(
                        "Laut (mit Alarmton) ab", alert.loudFrom, "loudFrom",
                        error = { if (it >= alert.dayEnd) "muss vor der Tagesgrenze (${Format.time(alert.dayEnd)}) liegen" else null },
                    ) { onAlert(alert.copy(loudFrom = it)) }
                    TimeSetting(
                        "Tagesgrenze", alert.dayEnd, "dayEnd",
                        error = { if (it <= alert.loudFrom) "muss nach „laut ab“ (${Format.time(alert.loudFrom)}) liegen" else null },
                    ) { onAlert(alert.copy(dayEnd = it)) }
                    WeekendSwitch(alert.weekend != null) { on ->
                        onAlert(alert.copy(weekend = if (on) DayTimes(alert.loudFrom, alert.dayEnd) else null))
                        onOverview(overview.copy(weekend = if (on) overview.copy(weekend = null) else null))
                    }
                    alert.weekend?.let { w ->
                        TimeSetting(
                            "Sa–So laut ab", w.loudFrom, "weekendLoudFrom",
                            error = { if (it >= w.dayEnd) "muss vor der Tagesgrenze (${Format.time(w.dayEnd)}) liegen" else null },
                        ) { onAlert(alert.copy(weekend = w.copy(loudFrom = it))) }
                        TimeSetting(
                            "Sa–So Tagesgrenze", w.dayEnd, "weekendDayEnd",
                            error = { if (it <= w.loudFrom) "muss nach „laut ab“ (${Format.time(w.loudFrom)}) liegen" else null },
                        ) { onAlert(alert.copy(weekend = w.copy(dayEnd = it))) }
                    }
                }
            }
            Text(
                "Was bis zur Tagesgrenze offen bleibt, gilt als verpasst. Bei „Nicht stören“ bleibt der Alarm still – " +
                    "außer bei Remindern mit „Auch bei Nicht stören“.",
                Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp,
            )

            SectionTitle("Nachfragen")
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("„Was hält dich ab?“ nach", color = c.text, fontSize = 15.sp)
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { n ->
                            FilterChip(
                                n == alert.askBlockerAfter, { onAlert(alert.copy(askBlockerAfter = n)) },
                                label = { Text(if (n == 1) "1× hängen" else "$n× hängen") }, modifier = Modifier.testTag("askAfter$n"),
                            )
                        }
                    }
                }
            }
            Text(
                "Hängen heißt: verpasst, 3× „Später“ oder verschoben, mehrmals in Folge. Die Antwort wird ein Blocker.",
                Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp,
            )

            SectionTitle("Vorlagen")
            Card { TemplateList(templates, onRenameTemplate, onDeleteTemplate) }

            SectionTitle("Übersicht")
            Card {
                Column(Modifier.padding(16.dp)) {
                    TimeSetting(
                        "Morgens: was heute ansteht", overview.morning, "overviewMorning", fallback = LocalTime.of(7, 30),
                        onOff = { onOverview(overview.copy(morning = null)) },
                    ) { onOverview(overview.copy(morning = it)) }
                    TimeSetting(
                        "Abends: Rückblick, Wochenziele, morgen", overview.evening, "overviewEvening", fallback = LocalTime.of(20, 30),
                        onOff = { onOverview(overview.copy(evening = null)) },
                    ) { onOverview(overview.copy(evening = it)) }
                    overview.weekend?.let { w ->
                        TimeSetting(
                            "Sa–So morgens", w.morning, "weekendOverviewMorning", fallback = LocalTime.of(9, 0),
                            onOff = { onOverview(overview.copy(weekend = w.copy(morning = null))) },
                        ) { onOverview(overview.copy(weekend = w.copy(morning = it))) }
                        TimeSetting(
                            "Sa–So abends", w.evening, "weekendOverviewEvening", fallback = LocalTime.of(20, 30),
                            onOff = { onOverview(overview.copy(weekend = w.copy(evening = null))) },
                        ) { onOverview(overview.copy(weekend = w.copy(evening = it))) }
                    }
                }
            }
            Text("Eine stille Benachrichtigung ohne Ton; ein Tipp öffnet die App.", Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp)

            if (cloud != null) {
                SectionTitle("Cloud-Backup (experimentell)")
                Card { CloudBackupSetting(cloud, cloudActions) }
            }

            if (local != null) {
                SectionTitle("Lokale Kopien")
                Card { LocalRestoreSetting(local, localActions, zone) }
            }

            if (claudeAccess != null) {
                SectionTitle("Schnittstelle für Claude")
                Card { ClaudeSetting(claudeAccess, onClaudeAccess) }
            }

            SectionTitle("Orte")
            Card {
                Column(Modifier.padding(16.dp)) {
                    PlaceSetting(places.getOrNull(0), fixedName = "Zuhause", onSetPlace = onSetPlace)
                    // Zuhause first: the second place gets the next id (OZ6).
                    if (places.isNotEmpty()) PlaceSetting(places.getOrNull(1), fixedName = null, onSetPlace = onSetPlace, Modifier.padding(top = 16.dp))
                    Text("Ankunft ohne WLAN zählt nach", Modifier.padding(top = 12.dp), color = c.text, fontSize = 15.sp)
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (Settings.DWELL_CHOICES + dwellMinutes).distinct().sorted().forEach { m ->
                            FilterChip(m == dwellMinutes, { onDwell(m) }, label = { Text("$m min") })
                        }
                    }
                }
            }
            Text(
                "Standort und WLAN bleiben auf dem Handy. Mit dem WLAN eines Orts verbunden heißt: da. Ohne WLAN zählt der " +
                    "Standort, und Vorbeifahren zählt nicht, erst das Bleiben. Für die Ankunft braucht die App den Standort „Immer erlauben“.",
                Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp,
            )

            SectionTitle("Berechtigungen")
            Card {
                permissions.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = c.line)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.label, color = c.text, fontSize = 15.sp)
                            Text(p.why, color = c.muted, fontSize = 13.sp)
                        }
                        if (p.granted) Text("erteilt", color = c.muted, fontSize = 14.sp)
                        else TextButton(onClick = p.request) { Text("Erteilen", color = c.accent, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            Text("AVL-Reminder 1.3b0.1-Prototyp", Modifier.padding(vertical = 24.dp, horizontal = 4.dp), color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Avl.colors.surface)) { content() }
}

/**
 * One line per time: label left, the time as a button right that opens a free entry
 * (Vorschlag 1). With [onOff] the time can be switched off; null then shows "aus".
 */
/**
 * The interface for Claude over adb (Anf. 7.26 AG11), only in the debug build. Switching it on
 * asks first: Claude then reads all reminders and may create and change them.
 */
@Composable
private fun ClaudeSetting(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Avl.colors
    var asking by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (on) "Freigegeben" else "Aus", color = if (on) c.strict else c.text, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else null)
                Text("Nur über adb vom Rechner", color = c.muted, fontSize = 13.sp)
            }
            Switch(on, { if (it) asking = true else onChange(false) }, Modifier.testTag("claudeAccess"))
        }
        Text(
            "Claude liest dann alle Reminder, Verlauf, Statistik und Blocker und kann Reminder anlegen, ändern, pausieren " +
                "und archivieren. Erledigen, auslassen, Alarme beenden und löschen kann Claude nicht.",
            Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp,
        )
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Schnittstelle für Claude freigeben?") },
            text = { Text("Die Inhalte deiner Reminder gehen dann an Claude. Ausschalten jederzeit hier.") },
            confirmButton = { TextButton(onClick = { onChange(true); asking = false }, Modifier.testTag("claudeOk")) { Text("Freigeben") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Abbrechen") } },
        )
    }
}

/** "Am Wochenende andere Zeiten" (VS2): alarm times and overview together. */
@Composable
private fun WeekendSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Avl.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Am Wochenende andere Zeiten", color = c.text, fontSize = 15.sp)
            Text("Sa–So: laut ab, Tagesgrenze und Übersicht", color = c.muted, fontSize = 13.sp)
        }
        Switch(on, onChange, Modifier.testTag("weekend"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** "Alle Alarme pausieren" (PS7), with a question first; while paused, one button to switch them on again. */
@Composable
private fun PauseSetting(alert: AlertSettings, onPause: (Instant?) -> Unit, now: Instant, zone: ZoneId) {
    val c = Avl.colors
    var asking by remember { mutableStateOf<Pair<String, Instant>?>(null) }
    Column(Modifier.padding(16.dp)) {
        val until = alert.alarmsPausedUntil
        if (until != null && alert.alarmsPaused(now)) {
            Text("Alle Alarme pausiert ${AlarmPause.untilText(until, now, zone)}", color = c.strict, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onPause(null) }, Modifier.testTag("resume")) { Text("Wieder einschalten", color = c.accent) }
        } else {
            Text("Alle Alarme pausieren", color = c.text, fontSize = 15.sp)
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("1 Stunde", "pause1h", { n: Instant -> n.plus(Duration.ofHours(1)) }),
                    Triple("bis morgen früh", "pauseMorning", { n: Instant -> AlarmPause.untilMorning(n, zone) }),
                    Triple("bis ich einschalte", "pauseForGood", { _: Instant -> AlertSettings.PAUSED_FOR_GOOD }),
                ).forEach { (label, tag, end) ->
                    OutlinedButton(onClick = { asking = label to end(Instant.now()) }, Modifier.testTag(tag)) { Text(label) }
                }
            }
            Text(
                "Für Notfälle: kein Vollbild, kein Ton. Fällige Reminder kommen nur still; was bis zur Tagesgrenze offen bleibt, " +
                    "zählt als ausgelassen (Notaus) und bricht keine Serie.",
                Modifier.padding(top = 6.dp), color = c.muted, fontSize = 13.sp,
            )
        }
    }
    asking?.let { (label, end) ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Alle Alarme pausieren?") },
            text = { Text("${AlarmPause.untilText(end, now, zone).replaceFirstChar { it.uppercase() }} klingelt nichts – auch nicht, was ansteht.") },
            confirmButton = { TextButton(onClick = { onPause(end); asking = null }, Modifier.testTag("pauseOk")) { Text("Pausieren ($label)") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun TimeSetting(
    label: String,
    time: LocalTime?,
    tag: String,
    fallback: LocalTime = LocalTime.NOON,
    error: (LocalTime) -> String? = { null },
    onOff: (() -> Unit)? = null,
    onPick: (LocalTime) -> Unit,
) {
    val c = Avl.colors
    var pick by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.text, fontSize = 15.sp)
        if (onOff != null && time != null) TextButton(onClick = onOff, Modifier.testTag(tag + "Off")) { Text("aus", color = c.muted) }
        OutlinedButton(onClick = { pick = true }, Modifier.testTag(tag)) { Text(time?.let { Format.time(it) } ?: "aus") }
    }
    if (pick) TimeDialog(time ?: fallback, onDismiss = { pick = false }, error = error, onPick = onPick)
}

/**
 * One place in *Orte* (Anf. 7.29 OZ3, OZ6): "Hier festlegen" takes the current position and the
 * connected WLAN with all its access points. [fixedName] null: the name can be typed.
 */
@Composable
private fun PlaceSetting(
    place: Place?,
    fixedName: String?,
    onSetPlace: (Long?, String, (PlaceSetResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Avl.colors
    var name by remember(place?.id) { mutableStateOf(place?.name.orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        if (fixedName != null) {
            Text(fixedName, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        } else {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth().testTag("placeName"),
                label = { Text("Weiterer Ort, z. B. Arbeit") }, singleLine = true,
            )
        }
        Text(describe(place), color = c.muted, fontSize = 13.sp)
        TextButton(
            onClick = {
                status = "Standort und WLAN werden bestimmt …"
                onSetPlace(place?.id, fixedName ?: name) { status = message(it) }
            },
            Modifier.testTag("setPlace${place?.id ?: "New"}"),
            enabled = fixedName != null || name.isNotBlank(),
        ) { Text(if (place == null) "Hier festlegen" else "Neu festlegen: hier", color = c.accent) }
        status?.let { Text(it, color = c.muted, fontSize = 13.sp) }
    }
}

private fun describe(place: Place?): String = when {
    place == null -> "Noch nicht festgelegt"
    place.wifis.isEmpty() -> "Umkreis ${place.radiusMeters} m · ohne WLAN"
    else -> "Umkreis ${place.radiusMeters} m · WLAN „${place.wifis.firstNotNullOfOrNull { it.ssid } ?: "ohne Namen"}“" +
        " (${place.wifis.size} ${if (place.wifis.size == 1) "Zugangspunkt" else "Zugangspunkte"})"
}

private fun message(result: PlaceSetResult): String = when (result) {
    PlaceSetResult.NoPosition -> "Kein Standort – Berechtigung und GPS prüfen"
    is PlaceSetResult.Saved -> listOfNotNull(
        if (result.position) "Standort übernommen" else "Standort unverändert",
        if (result.wifi) "WLAN „${result.ssid ?: "ohne Namen"}“ übernommen" else "nicht mit WLAN verbunden, WLAN unverändert",
    ).joinToString(" · ")
}
