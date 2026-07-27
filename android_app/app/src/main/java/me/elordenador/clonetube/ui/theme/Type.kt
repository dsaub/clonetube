package me.elordenador.clonetube.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Mirrors the Nocturne type scale: headings at weight 500 with -0.015em tracking,
// body at 15px/1.55. The system sans stands in for Inter so the app keeps no
// network-fetched font dependency.
private val Heading = FontFamily.Default
private val Body = FontFamily.Default

private fun heading(size: Int, line: Int) = TextStyle(
    fontFamily = Heading,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.015 * size).sp,
)

val ClonetubeTypography = Typography(
    displayLarge = heading(42, 47),
    headlineLarge = heading(32, 36),
    headlineMedium = heading(25, 28),
    headlineSmall = heading(20, 23),
    titleLarge = heading(20, 23),
    titleMedium = heading(17, 21),
    titleSmall = heading(16, 19),
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 17.sp,
    ),
    // h6 in the design: 13px, uppercase, 0.08em tracking.
    labelMedium = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.04.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)
