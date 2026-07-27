package me.elordenador.clonetube.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette lifted from the Nocturne design-system tokens (_ds/.../styles.css).
// The design ships a single, dark palette, so there is no light variant to derive.

val Bg = Color(0xFF161826)
val SurfaceColor = Color(0xFF232532)
val TextColor = Color(0xFFE9E9ED)
val Accent = Color(0xFF9184D9)
val Accent2 = Color(0xFFA7A1DB)

// --color-divider: color-mix(in srgb, #e9e9ed 16%, transparent)
val DividerColor = Color(0xFFE9E9ED).copy(alpha = 0.16f)

val Neutral100 = Color(0xFFF3F5FE)
val Neutral200 = Color(0xFFE4E7F5)
val Neutral300 = Color(0xFFCFD3E5)
val Neutral400 = Color(0xFFB2B6CA)
val Neutral500 = Color(0xFF9397AB)
val Neutral600 = Color(0xFF75798C)
val Neutral700 = Color(0xFF595D6C)
val Neutral800 = Color(0xFF3F424D)
val Neutral900 = Color(0xFF292B31)

val Accent100 = Color(0xFFF5F4FF)
val Accent200 = Color(0xFFE7E5FE)
val Accent300 = Color(0xFFD2CEFD)
val Accent400 = Color(0xFFB5ABFC)
val Accent500 = Color(0xFF968AE0)
val Accent600 = Color(0xFF796CBF)
val Accent700 = Color(0xFF5D5294)
val Accent800 = Color(0xFF423A6A)
val Accent900 = Color(0xFF2B2741)

val Accent2_800 = Color(0xFF423E5D)

// Radii — --radius-sm / --radius-md / --radius-lg
const val RADIUS_SM = 4
const val RADIUS_MD = 8
const val RADIUS_LG = 14

// Scrim used over the mock video covers, matching rgba(10,10,16,x) in the design.
val CoverScrim = Color(0xFF0A0A10)

/**
 * The three cover gradients cycled by the prototype's COVERS array
 * (`linear-gradient(135deg, <start>, var(--color-neutral-900) 70%)`).
 */
fun coverBrush(index: Int): Brush = Brush.linearGradient(
    0f to coverStarts[index.mod(coverStarts.size)],
    0.7f to Neutral900,
)

private val coverStarts = listOf(Accent800, Accent2_800, Neutral800)
