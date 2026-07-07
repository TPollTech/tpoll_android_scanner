// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    primary = Color(0xFFB39DDB),
    onPrimary = Color(0xFF1A0033),
    primaryContainer = Color(0xFF4A148C),
    onPrimaryContainer = Color(0xFFE1BEE7),
    secondary = Color(0xFFFFCA28),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF6D4C41),
    onSecondaryContainer = Color(0xFFFFF8E1),
    tertiary = Color(0xFF80DEEA),
    onTertiary = Color(0xFF002F35),
    tertiaryContainer = Color(0xFF006064),
    onTertiaryContainer = Color(0xFFB2EBF2),
    error = Color(0xFFEF5350),
    background = Color(0xFF0D0D1A),
    onBackground = Color(0xFFE8E0F0),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE8E0F0),
    surfaceVariant = Color(0xFF2A2A40),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF49454F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6A1B9A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E5F5),
    onPrimaryContainer = Color(0xFF3E0057),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Color(0xFF4E2600),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF00251E),
    error = Color(0xFFC62828),
    background = Color(0xFFFDF8FF),
    onBackground = Color(0xFF1C0B2B),
    surface = Color.White,
    onSurface = Color(0xFF1C0B2B),
    surfaceVariant = Color(0xFFF3EDF7),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFFCAC4D0)
)

val HighRiskColor = Color(0xFFEF5350)
val MediumRiskColor = Color(0xFFFFA000)
val LowRiskColor = Color(0xFF66BB6A)
val GreenColor = Color(0xFF66BB6A)

val StatusActive = Color(0xFF66BB6A)
val StatusInactive = Color(0xFF9E9E9E)

val ShieldActiveColor = Color(0xFFB39DDB)
val ShieldDangerColor = Color(0xFFEF5350)
val ShieldWarningColor = Color(0xFFFFA000)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(letterSpacing = 0.15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(letterSpacing = 0.25.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(letterSpacing = 0.4.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(letterSpacing = 0.5.sp)
)

@Composable
fun TPollScannerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val mode = ThemeManager.getMode(context)

    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
