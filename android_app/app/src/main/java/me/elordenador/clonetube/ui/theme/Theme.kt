package me.elordenador.clonetube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The design is dark-only (the prototype hardcodes dark={{true}} and styles.css
// defines a single palette), so the scheme ignores the system light/dark setting
// and dynamic color is not used.
private val ClonetubeColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Accent900,
    primaryContainer = Accent800,
    onPrimaryContainer = Accent100,
    secondary = Accent2,
    onSecondary = Accent900,
    secondaryContainer = Accent2_800,
    onSecondaryContainer = Accent100,
    background = Bg,
    onBackground = TextColor,
    surface = SurfaceColor,
    onSurface = TextColor,
    surfaceVariant = SurfaceColor,
    onSurfaceVariant = Neutral400,
    outline = Neutral600,
    outlineVariant = Neutral800,
)

@Composable
fun ClonetubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClonetubeColorScheme,
        typography = ClonetubeTypography,
        content = content,
    )
}
