package me.elordenador.clonetube.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Design tokens lifted from the mobile screens of `frontend/design.pen`. The design
 * ships dark and light variants, so the app follows the system setting.
 */
data class ClonetubePalette(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val input: Color,
    val chipSelected: Color,
    val avatar: Color,
    val navPill: Color,
    val text: Color,
    val textBright: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textMuted2: Color,
    val accent: Color,
    val accentText: Color,
    val accentIcon: Color,
    val accentLabel: Color,
    val border: Color,
    val borderAlt: Color,
    val borderInput: Color,
    val divider: Color,
    val success: Color,
    val danger: Color,
    val dangerBorder: Color,
    val selectedTab: Color,
    val badge: Color,
    val badgeText: Color,
    val playButton: Color,
    val coverStarts: List<Color>,
    val coverEnd: Color,
)

val DarkPalette = ClonetubePalette(
    bg = Color(0xFF0F0F1A),
    surface = Color(0xFF171725),
    surfaceAlt = Color(0xFF181824),
    input = Color(0xFF10101B),
    chipSelected = Color(0xFF24223D),
    avatar = Color(0xFF26243D),
    navPill = Color(0xE8202033),
    text = Color(0xFFF0EFF8),
    textBright = Color(0xFFF3F2FF),
    textSecondary = Color(0xFFB3B1C0),
    textMuted = Color(0xFF8F8DA2),
    textMuted2 = Color(0xFF777587),
    accent = Color(0xFF6C63FF),
    accentText = Color(0xFF8882FF),
    accentIcon = Color(0xFFB9B5FF),
    accentLabel = Color(0xFFDCD9FF),
    border = Color(0xFF2A2A4A),
    borderAlt = Color(0xFF38364E),
    borderInput = Color(0xFF383654),
    divider = Color(0x14FFFFFF),
    success = Color(0xFF67D69A),
    danger = Color(0xFFFF929F),
    dangerBorder = Color(0xFF5E3540),
    selectedTab = Color(0x266C63FF),
    badge = Color(0xD907070D),
    badgeText = Color(0xFFD8D7E3),
    playButton = Color(0xE86C63FF),
    coverStarts = listOf(Color(0xFF26244B), Color(0xFF2A2850), Color(0xFF24223D)),
    coverEnd = Color(0xFF12121E),
)

val LightPalette = ClonetubePalette(
    bg = Color(0xFFF7F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFFFFFF),
    input = Color(0xFFF5F4FA),
    chipSelected = Color(0xFFFFFFFF),
    avatar = Color(0xFFF0EFF8),
    navPill = Color(0xEBFFFFFF),
    text = Color(0xFF171525),
    textBright = Color(0xFF171525),
    textSecondary = Color(0xFF5D5A69),
    textMuted = Color(0xFF666375),
    textMuted2 = Color(0xFF747081),
    accent = Color(0xFF6C63FF),
    accentText = Color(0xFF5D54D7),
    accentIcon = Color(0xFF584FC8),
    accentLabel = Color(0xFF322B58),
    border = Color(0xFFDDDCEA),
    borderAlt = Color(0xFFD8D6E7),
    borderInput = Color(0xFFD8D5E9),
    divider = Color(0xFFDDDCEA),
    success = Color(0xFF168755),
    danger = Color(0xFFFF929F),
    dangerBorder = Color(0xFF5E3540),
    selectedTab = Color(0x266C63FF),
    badge = Color(0xEBFFFFFF),
    badgeText = Color(0xFF211E2D),
    playButton = Color(0xE86C63FF),
    coverStarts = listOf(Color(0xFFE2DFF4), Color(0xFFE7E4F8), Color(0xFFEAE7FA)),
    coverEnd = Color(0xFFF1F0F7),
)

val LocalClonetubePalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun currentPalette(): ClonetubePalette = LocalClonetubePalette.current

/** The three cover gradients cycled by the mock video covers, per theme. */
@Composable
fun coverBrush(index: Int): Brush {
    val p = currentPalette()
    return Brush.linearGradient(
        0f to p.coverStarts[index.mod(p.coverStarts.size)],
        0.68f to p.coverEnd,
    )
}

/** Soft radial glow drawn behind the play button of large channel thumbnails. */
@Composable
fun coverGlow(index: Int): Brush {
    val p = currentPalette()
    return Brush.radialGradient(
        0f to p.accent.copy(alpha = 0.13f),
        1f to p.coverStarts[index.mod(p.coverStarts.size)].copy(alpha = 0f),
    )
}

// Radii — --radius-sm / --radius-md / --radius-lg
const val RADIUS_SM = 4
const val RADIUS_MD = 8
const val RADIUS_LG = 14
