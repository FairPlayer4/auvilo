package com.avlreminder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avlreminder.core.model.Resolution
import com.avlreminder.core.model.Strictness
import com.avlreminder.ui.theme.Avl

private val Pill = RoundedCornerShape(999.dp)

@Composable
private fun Badge(text: String, background: Color, color: Color, border: Color? = null) {
    var modifier = Modifier.clip(Pill).background(background)
    if (border != null) modifier = modifier.border(BorderStroke(1.dp, border), Pill)
    Text(
        text, modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

@Composable
fun StrictnessTag(strictness: Strictness) {
    val c = Avl.colors
    when (strictness) {
        Strictness.STRICT -> Badge(Format.strictness(strictness), Color.Transparent, c.strict, c.strict)
        Strictness.NORMAL -> Badge(Format.strictness(strictness), Color.Transparent, c.accent, c.accent)
        Strictness.RELAXED -> Badge(Format.strictness(strictness), Color.Transparent, c.muted, c.line)
    }
}

@Composable
fun ResultBadge(resolution: Resolution) {
    val c = Avl.colors
    val text = Format.resolution(resolution)
    when (resolution) {
        Resolution.GOAL -> Badge(text, c.accentFill, Color.White)
        Resolution.MINIMUM -> Badge(text, c.accentSoft, c.onAccentSoft)
        Resolution.NO_PROOF -> Badge(text, c.surface, c.strict, c.strict)
        Resolution.SKIPPED -> Badge(text, c.neutral, c.onNeutral)
        Resolution.MISSED -> Badge(text, c.strict, c.onStrict)
        Resolution.DROPPED -> Badge(text, Color.Transparent, c.muted, c.line)
        Resolution.ABORTED -> Badge(text, c.surface, c.muted, c.line)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.padding(top = 20.dp, bottom = 8.dp),
        color = Avl.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
    )
}

/** One row in a list: optional time column, title and detail, something on the right. */
@Composable
fun EntryCard(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    time: String? = null,
    highlight: Color? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = Avl.colors
    var m = modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
    m = m.border(BorderStroke(if (highlight != null) 1.5.dp else 1.dp, highlight ?: c.line), RoundedCornerShape(14.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (time != null) {
            Text(time, Modifier.width(52.dp), color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = if (dimmed) c.muted else c.text, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) Text(detail, color = c.muted, fontSize = 13.sp, maxLines = 2)
        }
        trailing()
    }
}
