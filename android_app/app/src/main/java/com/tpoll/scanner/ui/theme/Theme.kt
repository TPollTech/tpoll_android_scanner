// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
package com.tpoll.scanner.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getMode(context: Context): ThemeMode {
        val ordinal = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal)
        return ThemeMode.entries.getOrElse(ordinal) { ThemeMode.SYSTEM }
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, mode.ordinal)
            .apply()
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAEB4FF),
    onPrimary = Color(0xFF101452),
    primaryContainer = Color(0xFF292E78),
    onPrimaryContainer = Color(0xFFE1E2FF),
    secondary = Color(0xFF75D6C7),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF155048),
    onSecondaryContainer = Color(0xFFA8F2E5),
    tertiary = Color(0xFFFFC56F),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE7E9F4),
    surface = Color(0xFF12182A),
    onSurface = Color(0xFFE7E9F4),
    surfaceVariant = Color(0xFF20283B),
    onSurfaceVariant = Color(0xFFC5C9D7),
    outline = Color(0xFF8F93A3),
    outlineVariant = Color(0xFF3E4659)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F56D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E3FF),
    onPrimaryContainer = Color(0xFF15194F),
    secondary = Color(0xFF087B6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F2E5),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8B5D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDA7),
    onTertiaryContainer = Color(0xFF2C1A00),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF191B24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B24),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF444650),
    outline = Color(0xFF757681),
    outlineVariant = Color(0xFFC5C6D0)
)

val HighRiskColor = Color(0xFFE5484D)
val MediumRiskColor = Color(0xFFF59E0B)
val LowRiskColor = Color(0xFF21A179)
val GreenColor = LowRiskColor

val StatusActive = LowRiskColor
val StatusInactive = Color(0xFF8A8F9D)

val ShieldActiveColor = Color(0xFF6C72E8)
val ShieldDangerColor = HighRiskColor
val ShieldWarningColor = MediumRiskColor

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(letterSpacing = 0.1.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(letterSpacing = 0.15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(letterSpacing = 0.2.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium)
)

@Composable
fun TPollScannerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = ThemeManager.getMode(context)
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
