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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
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

@Immutable
data class ExtendedColors(
    val gradientPrimary: Brush,
    val gradientDanger: Brush,
    val gradientSuccess: Brush,
    val gradientSurface: Brush,
    val glassBg: Color,
    val glassBorder: Color,
    val glowCyan: Color,
    val glowPurple: Color,
    val shieldActive: Color,
    val shieldWarning: Color,
    val shieldDanger: Color,
    val shieldInactive: Color
)

private val DarkExtended = ExtendedColors(
    gradientPrimary = Brush.linearGradient(listOf(Color(0xFF00D2FF), Color(0xFF7B2FFF))),
    gradientDanger = Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFFFF6B35))),
    gradientSuccess = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00BFA5))),
    gradientSurface = Brush.verticalGradient(listOf(Color(0xFF0A0E1A), Color(0xFF0F1525))),
    glassBg = Color(0x1A1A2E),
    glassBorder = Color(0x33FFFFFF),
    glowCyan = Color(0xFF00D2FF),
    glowPurple = Color(0xFF7B2FFF),
    shieldActive = Color(0xFF00E676),
    shieldWarning = Color(0xFFFFAB00),
    shieldDanger = Color(0xFFFF2D55),
    shieldInactive = Color(0xFF546E7A)
)

private val LightExtended = ExtendedColors(
    gradientPrimary = Brush.linearGradient(listOf(Color(0xFF0066FF), Color(0xFF7B2FFF))),
    gradientDanger = Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFFFF6B35))),
    gradientSuccess = Brush.linearGradient(listOf(Color(0xFF00C853), Color(0xFF00BFA5))),
    gradientSurface = Brush.verticalGradient(listOf(Color(0xFFF0F2F8), Color(0xFFE8ECF4))),
    glassBg = Color(0xCCFFFFFF),
    glassBorder = Color(0x33000000),
    glowCyan = Color(0xFF0066FF),
    glowPurple = Color(0xFF7B2FFF),
    shieldActive = Color(0xFF00C853),
    shieldWarning = Color(0xFFFF8F00),
    shieldDanger = Color(0xFFFF2D55),
    shieldInactive = Color(0xFF90A4AE)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D2FF),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF0D3B47),
    onPrimaryContainer = Color(0xFFB8EAFF),
    secondary = Color(0xFF7B2FFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2A1066),
    onSecondaryContainer = Color(0xFFE8DDFF),
    tertiary = Color(0xFFFFAB00),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF594300),
    onTertiaryContainer = Color(0xFFFFDEA1),
    error = Color(0xFFFF2D55),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5C1020),
    onErrorContainer = Color(0xFFFFBDC5),
    background = Color(0xFF050810),
    onBackground = Color(0xFFE4E7F0),
    surface = Color(0xFF0A0E1A),
    onSurface = Color(0xFFE4E7F0),
    surfaceVariant = Color(0xFF121828),
    onSurfaceVariant = Color(0xFFBFC5D5),
    surfaceContainerLow = Color(0xFF0E1220),
    surfaceContainer = Color(0xFF121828),
    surfaceContainerHigh = Color(0xFF1A2035),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF2A3045),
    inverseSurface = Color(0xFFE4E7F0),
    inverseOnSurface = Color(0xFF1A1F2E)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0066FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF7B2FFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF22005E),
    tertiary = Color(0xFFFF8F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA1),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLow = Color(0xFFF3F4F8),
    surfaceContainer = Color(0xFFECEEF3),
    surfaceContainerHigh = Color(0xFFE2E4EA),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7)
)

val HighRiskColor = Color(0xFFFF2D55)
val MediumRiskColor = Color(0xFFFFAB00)
val LowRiskColor = Color(0xFF00E676)
val GreenColor = LowRiskColor

val StatusActive = LowRiskColor
val StatusInactive = Color(0xFF546E7A)

val ShieldActiveColor = Color(0xFF00D2FF)
val ShieldDangerColor = HighRiskColor
val ShieldWarningColor = MediumRiskColor

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(letterSpacing = 0.15.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(letterSpacing = 0.2.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(letterSpacing = 0.3.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
)

val LocalExtendedColors = androidx.compose.runtime.staticCompositionLocalOf { DarkExtended }

@Composable
fun TPollScannerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = ThemeManager.getMode(context)
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val extended = if (darkTheme) DarkExtended else LightExtended

    androidx.compose.runtime.CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

object AppGradients {
    @Composable
    fun primary() = LocalExtendedColors.current.gradientPrimary

    @Composable
    fun danger() = LocalExtendedColors.current.gradientDanger

    @Composable
    fun success() = LocalExtendedColors.current.gradientSuccess

    @Composable
    fun surface() = LocalExtendedColors.current.gradientSurface
}

object AppDimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val radiusSm = 8.dp
    val radiusMd = 14.dp
    val radiusLg = 20.dp
    val radiusXl = 28.dp
}
