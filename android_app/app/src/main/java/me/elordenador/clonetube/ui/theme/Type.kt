package me.elordenador.clonetube.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Mirrors the mobile design's type scale: bold headings (700-800) with light
// tracking, eyebrows at weight 800 with wide tracking, muted body text.
private val Heading = FontFamily.Default
private val Body = FontFamily.Default

val ClonetubeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 25.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 19.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
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
        fontSize = 12.5f.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
    ),
    // Eyebrows: 10-11px, weight 800, wide tracking.
    labelMedium = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.5f.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.45.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Heading,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    ),
)
