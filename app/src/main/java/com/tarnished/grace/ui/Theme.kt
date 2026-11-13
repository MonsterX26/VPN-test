package com.tarnished.grace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Grace {
    val Ink = Color(0xFF080706)
    val Panel = Color(0xFF12100C)
    val PanelHigh = Color(0xFF1C170F)
    val Gold = Color(0xFFC9A55C)
    val GoldBright = Color(0xFFF2D58F)
    val GoldDim = Color(0xFF7D6A3E)
    val Blood = Color(0xFFA32121)
    val Ember = Color(0xFFC77A3A)
    val Ash = Color(0xFF8C8577)
    val Bone = Color(0xFFE8DFC9)
}

val GraceSerif: FontFamily = FontFamily.Serif

private val GraceTypography = Typography(
    titleLarge = TextStyle(fontFamily = GraceSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 7.sp),
    titleMedium = TextStyle(fontFamily = GraceSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 3.sp),
    bodyLarge = TextStyle(fontFamily = GraceSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = GraceSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = GraceSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 3.sp),
    labelSmall = TextStyle(fontFamily = GraceSerif, fontSize = 11.sp, letterSpacing = 2.sp)
)

@Composable
fun GraceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Grace.Gold,
            onPrimary = Grace.Ink,
            secondary = Grace.GoldDim,
            background = Grace.Ink,
            onBackground = Grace.Bone,
            surface = Grace.Panel,
            onSurface = Grace.Bone,
            error = Grace.Blood
        ),
        typography = GraceTypography,
        content = content
    )
}
