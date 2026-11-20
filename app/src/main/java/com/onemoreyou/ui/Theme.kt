package com.onemoreyou.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppLook(
    val id: String,
    val name: String,
    val blurb: String,
    val background: Color,
    val card: Color,
    val raised: Color,
    val line: Color,
    val tabBar: Color,
    val strongBorder: Color,
    val block: Color,
    val accent: Color,
) {
    val text = Color(0xFFF6F3EE)
    val muted = Color(0xFFC9C3B8)
    val faint = Color(0xFF9A958C)
    val good = Color(0xFF3DDC97)
    val timeUp = Color(0xFFFF6B4A)
    val delete = Color(0xFFFF8A8A)
    val warning = Color(0xFFFFC857)
    val onAccent = Color(0xFF0B1220)
    val accentSoft get() = accent.copy(alpha = 0.15f)
}

object AppLooks {
    val Midnight = AppLook("midnight", "Midnight", "Deep navy with blue", Color(0xFF12141C), Color(0xFF1C2030), Color(0xFF242A3D), Color(0xFF2C3348), Color(0xFF171A26), Color(0xFF4A5170), Color(0xFF0D0F15), Color(0xFF5BA8FF))
    val Ember = AppLook("ember", "Ember", "Warm brown with coral", Color(0xFF17110F), Color(0xFF251A17), Color(0xFF30221E), Color(0xFF3B2B26), Color(0xFF1E1613), Color(0xFF5A4038), Color(0xFF100B0A), Color(0xFFFF6B4A))
    val Sand = AppLook("sand", "Sand", "Dark olive with amber", Color(0xFF15130D), Color(0xFF231F15), Color(0xFF2E291C), Color(0xFF3A3322), Color(0xFF1B1810), Color(0xFF5A4E30), Color(0xFF0E0D08), Color(0xFFFFC857))
    val Forest = AppLook("forest", "Forest", "Dark green with mint", Color(0xFF0E1512), Color(0xFF16231C), Color(0xFF1E2E25), Color(0xFF27392F), Color(0xFF121B16), Color(0xFF3A5446), Color(0xFF09100C), Color(0xFF3DDC97))
    val Dusk = AppLook("dusk", "Dusk", "Deep plum with violet", Color(0xFF13111C), Color(0xFF1F1A2D), Color(0xFF292238), Color(0xFF322A44), Color(0xFF18142A), Color(0xFF4C3F66), Color(0xFF0D0B14), Color(0xFFB388FF))
    val all = listOf(Midnight, Ember, Sand, Forest, Dusk)

    fun fromId(id: String): AppLook = when (id) {
        "ember", "coral" -> Ember
        "sand", "amber" -> Sand
        "forest", "mint", "green" -> Forest
        "dusk", "violet" -> Dusk
        else -> Midnight
    }
}

val LocalAppLook = staticCompositionLocalOf { AppLooks.Midnight }

val BarColors = listOf(AppLooks.Ember.accent, AppLooks.Sand.accent, AppLooks.Forest.accent, AppLooks.Midnight.accent, AppLooks.Dusk.accent)
val Coral = AppLooks.Ember.accent

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun OneMoreYouTheme(accentId: String = AppLooks.Midnight.id, content: @Composable () -> Unit) {
    val look = AppLooks.fromId(accentId)
    CompositionLocalProvider(LocalAppLook provides look) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = look.accent,
                onPrimary = look.onAccent,
                primaryContainer = look.accentSoft,
                onPrimaryContainer = look.text,
                secondary = look.good,
                onSecondary = look.onAccent,
                tertiary = look.accent,
                background = look.background,
                onBackground = look.text,
                surface = look.card,
                onSurface = look.text,
                surfaceVariant = look.raised,
                onSurfaceVariant = look.muted,
                error = look.timeUp,
                outline = look.line,
            ),
            typography = Type,
            shapes = AppShapes,
            content = content,
        )
    }
}
