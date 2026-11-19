package com.onemoreyou.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFFF6B4A)
val Amber = Color(0xFFFFC857)
val Mint = Color(0xFF3DDC97)
val Sky = Color(0xFF5BA8FF)
val Violet = Color(0xFFB388FF)
val Ink = Color(0xFF12141C)
val Card = Color(0xFF1C2030)

val BarColors = listOf(Coral, Amber, Mint, Sky, Violet)

private val Colors = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF2A0C06),
    primaryContainer = Color(0xFF5A2418),
    onPrimaryContainer = Color(0xFFFFD8CF),
    secondary = Mint,
    onSecondary = Color(0xFF042216),
    tertiary = Sky,
    background = Ink,
    onBackground = Color(0xFFF6F3EE),
    surface = Ink,
    onSurface = Color(0xFFF6F3EE),
    surfaceVariant = Card,
    onSurfaceVariant = Color(0xFFC9C3B8),
    error = Color(0xFFFF8A9A),
    outline = Color(0xFF3A4054),
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun OneMoreYouTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        shapes = AppShapes,
        content = content,
    )
}
