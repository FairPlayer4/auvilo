package com.avlreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.avlreminder.data.ThemeMode

/** Design tokens (docs/design.md 11.4). Screens use only these, never fixed colors. */
@Immutable
data class AvlColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    /** Accent for text and borders. */
    val accent: Color,
    /** Accent as a fill under white text. */
    val accentFill: Color,
    /** Light accent fill, e.g. the "Minimum" badge. */
    val accentSoft: Color,
    val onAccentSoft: Color,
    /** Strict reminders and missed occurrences. */
    val strict: Color,
    val onStrict: Color,
    /** Neutral badge, e.g. "ausgelassen". */
    val neutral: Color,
    val onNeutral: Color,
    val isDark: Boolean,
)

val LightColors = AvlColors(
    background = Color(0xFFF5F6F8), surface = Color(0xFFFFFFFF), text = Color(0xFF16191F),
    muted = Color(0xFF5B6270), line = Color(0xFFE2E5EA), accent = Color(0xFF1F4FD1),
    accentFill = Color(0xFF1F4FD1), accentSoft = Color(0xFFE4EBFB), onAccentSoft = Color(0xFF163A9C),
    strict = Color(0xFFB4400A), onStrict = Color(0xFFFFFFFF), neutral = Color(0xFFE8EAEE),
    onNeutral = Color(0xFF3D434F), isDark = false,
)

val DarkColors = AvlColors(
    background = Color(0xFF101216), surface = Color(0xFF1B1E24), text = Color(0xFFECEEF2),
    muted = Color(0xFFA9B0BC), line = Color(0xFF2E323B), accent = Color(0xFF8AA8FF),
    accentFill = Color(0xFF2F5FE0), accentSoft = Color(0xFF26324F), onAccentSoft = Color(0xFFB4C6FF),
    strict = Color(0xFFFF9A62), onStrict = Color(0xFF101216), neutral = Color(0xFF2A2E37),
    onNeutral = Color(0xFFC3C8D2), isDark = true,
)

val LocalAvlColors = staticCompositionLocalOf { LightColors }

object Avl {
    val colors: AvlColors @Composable get() = LocalAvlColors.current
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** App theme. Dynamic color stays off so the strictness colors always mean the same. */
@Composable
fun AvlTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accentFill, onPrimary = Color.White, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, onSurfaceVariant = c.muted, outline = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.line,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.onAccentSoft, error = c.strict,
        )
    } else {
        lightColorScheme(
            primary = c.accentFill, onPrimary = Color.White, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, onSurfaceVariant = c.muted, outline = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.line,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.onAccentSoft, error = c.strict,
        )
    }
    CompositionLocalProvider(LocalAvlColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
