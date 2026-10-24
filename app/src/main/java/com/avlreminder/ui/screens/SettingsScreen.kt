package com.avlreminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.avlreminder.core.rules.EscalationWindows
import com.avlreminder.data.ThemeMode
import com.avlreminder.ui.AvlIcons
import com.avlreminder.ui.Format
import com.avlreminder.ui.SectionTitle
import com.avlreminder.ui.theme.Avl

/** A permission the app needs, with its state and how to ask for it. */
data class PermissionItem(
    val label: String,
    val why: String,
    val granted: Boolean,
    /** Missing required permissions are shown as a warning on "Heute". */
    val required: Boolean = true,
    val request: () -> Unit,
)

@Composable
fun SettingsScreen(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    windows: EscalationWindows,
    permissions: List<PermissionItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Avl.colors
    Column(modifier) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AvlIcons.Back, "Zurück", tint = c.text) }
            Text("Einstellungen", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
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

            SectionTitle("Zeitfenster")
            Card {
                Line("Sichtbar (Vollbild, Vibration)", "${Format.time(windows.visibleFrom)} – ${Format.time(windows.visibleUntil)}")
                HorizontalDivider(color = c.line)
                Line("Laut (mit Alarmton)", "${Format.time(windows.loudFrom)} – ${Format.time(windows.visibleUntil)}")
            }
            Text("In V1.0 fest eingestellt.", Modifier.padding(4.dp), color = c.muted, fontSize = 13.sp)

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
            Text("AVL-Reminder 1.0-Prototyp", Modifier.padding(vertical = 24.dp, horizontal = 4.dp), color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Avl.colors.surface)) { content() }
}

@Composable
private fun Line(label: String, value: String) {
    val c = Avl.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.text, fontSize = 15.sp)
        Text(value, color = c.muted, fontSize = 15.sp)
    }
}
