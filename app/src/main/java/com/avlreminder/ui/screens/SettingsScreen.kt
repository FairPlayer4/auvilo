package com.avlreminder.ui.screens

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.avlreminder.core.model.Place
import com.avlreminder.core.rules.AlertSettings
import com.avlreminder.data.Settings
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.Format
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.theme.Avl
import java.time.LocalTime

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
    home: Place? = null,
    onSetHome: (onResult: (Boolean) -> Unit) -> Unit = {},
    dwellMinutes: Int = Settings.DEFAULT_DWELL,
    onDwell: (Int) -> Unit = {},
    openSuggestions: Int = 0,
    onSuggestions: () -> Unit = {},
) {
    val c = Avl.colors
    var homeState by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text("Einstellungen", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
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
                    Text("Laut (mit Alarmton) ab", color = c.text, fontSize = 15.sp)
                    Hours(listOf(16, 17, 18, 19, 20).filter { LocalTime.of(it, 0) < alert.dayEnd }, alert.loudFrom) {
                        onAlert(alert.copy(loudFrom = it))
                    }
                    Text("Tagesgrenze", Modifier.padding(top = 12.dp), color = c.text, fontSize = 15.sp)
                    Hours(listOf(21, 22, 23).filter { LocalTime.of(it, 0) > alert.loudFrom }, alert.dayEnd) {
                        onAlert(alert.copy(dayEnd = it))
                    }
                }
            }
            Text(
                "Was bis zur Tagesgrenze offen bleibt, gilt als verpasst. Bei „Nicht stören“ bleibt der Alarm still – " +
                    "außer bei Remindern mit „Auch bei Nicht stören“.",
                Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp,
            )

            SectionTitle("Zuhause")
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(if (home == null) "Noch nicht festgelegt" else "Festgelegt · Umkreis ${home.radiusMeters} m", color = c.text, fontSize = 15.sp)
                    TextButton(onClick = {
                        homeState = "Standort wird bestimmt …"
                        onSetHome { ok -> homeState = if (ok) "Zuhause gespeichert" else "Kein Standort – Berechtigung und GPS prüfen" }
                    }) { Text(if (home == null) "Aktuellen Standort als Zuhause" else "Neu setzen auf aktuellen Standort", color = c.accent) }
                    homeState?.let { Text(it, color = c.muted, fontSize = 13.sp) }
                    Text("Ankunft zählt nach", Modifier.padding(top = 12.dp), color = c.text, fontSize = 15.sp)
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (Settings.DWELL_CHOICES + dwellMinutes).distinct().sorted().forEach { m ->
                            FilterChip(m == dwellMinutes, { onDwell(m) }, label = { Text("$m min") })
                        }
                    }
                }
            }
            Text(
                "Der Standort bleibt auf dem Handy. Vorbeifahren zählt nicht, erst das Bleiben. Für die Ankunft braucht die App " +
                    "den Standort „Immer erlauben“.",
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
            Text("AVL-Reminder 1.2a-Prototyp", Modifier.padding(vertical = 24.dp, horizontal = 4.dp), color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Avl.colors.surface)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hours(hours: List<Int>, selected: LocalTime, onPick: (LocalTime) -> Unit) {
    val all = (hours.map { LocalTime.of(it, 0) } + selected).distinct().sorted()
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { t -> FilterChip(t == selected, { onPick(t) }, label = { Text(Format.time(t)) }) }
    }
}
