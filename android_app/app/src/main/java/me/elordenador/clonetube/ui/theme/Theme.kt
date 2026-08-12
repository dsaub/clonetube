package me.elordenador.clonetube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// The design ships dark and light variants (design.pen), so the scheme follows the
// system dark/light setting. Dynamic color is not used.
private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = Color.White,
    primaryContainer = DarkPalette.chipSelected,
    onPrimaryContainer = DarkPalette.accentLabel,
    secondary = DarkPalette.accentText,
    onSecondary = DarkPalette.bg,
    background = DarkPalette.bg,
    onBackground = DarkPalette.text,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.text,
    surfaceVariant = DarkPalette.surfaceAlt,
    onSurfaceVariant = DarkPalette.textMuted,
    outline = DarkPalette.borderAlt,
    outlineVariant = DarkPalette.border,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    primaryContainer = LightPalette.chipSelected,
    onPrimaryContainer = LightPalette.accentLabel,
    secondary = LightPalette.accentText,
    onSecondary = LightPalette.bg,
    background = LightPalette.bg,
    onBackground = LightPalette.text,
    surface = LightPalette.surface,
    onSurface = LightPalette.text,
    surfaceVariant = LightPalette.surfaceAlt,
    onSurfaceVariant = LightPalette.textMuted,
    outline = LightPalette.borderAlt,
    outlineVariant = LightPalette.border,
)

@Composable
fun ClonetubeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val colorScheme = if (dark) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalClonetubePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ClonetubeTypography,
            content = content,
        )
    }
}
