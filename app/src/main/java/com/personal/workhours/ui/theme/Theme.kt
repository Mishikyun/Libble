package com.personal.workhours.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.personal.workhours.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = OnTealPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = NeutralSecondaryLight,
    onSecondary = OnNeutralSecondaryLight,
    secondaryContainer = NeutralContainerLight,
    onSecondaryContainer = OnNeutralContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F9),
    surfaceContainer = Color(0xFFEEF1F5),
    surfaceContainerHigh = Color(0xFFE6EAF0),
    surfaceContainerHighest = Color(0xFFDDE2EA),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = AlarmErrorLight,
    onError = OnTealPrimaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = NeutralSecondaryDark,
    onSecondary = OnNeutralSecondaryDark,
    secondaryContainer = NeutralContainerDark,
    onSecondaryContainer = OnNeutralContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = Color(0xFF0E1219),
    surfaceContainer = Color(0xFF12161E),
    surfaceContainerHigh = Color(0xFF1A202B),
    surfaceContainerHighest = Color(0xFF242C3A),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = AlarmErrorDark,
    onError = OnTealPrimaryDark
)

@Composable
fun WorkHoursTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWorkHoursColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WorkHoursTypography,
            content = content
        )
    }
}

object WorkHoursThemeExtras {
    val colors: WorkHoursExtendedColors
        @Composable get() = LocalWorkHoursColors.current
}
