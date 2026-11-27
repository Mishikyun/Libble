package com.personal.workhours.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class WorkHoursExtendedColors(
    val clockedIn: Color,
    val onBreak: Color,
    val clockedOut: Color,
    val alarm: Color,
    val chartTargetLine: Color,
    val chartBarMet: Color,
    val chartBarUnder: Color,
    val chartBarDayOff: Color
)

val LightExtendedColors = WorkHoursExtendedColors(
    clockedIn = ClockedInLight,
    onBreak = OnBreakLight,
    clockedOut = ClockedOutLight,
    alarm = AlarmErrorLight,
    chartTargetLine = OnSurfaceVariantLight.copy(alpha = 0.55f),
    chartBarMet = TealPrimaryLight,
    chartBarUnder = TealPrimaryLight.copy(alpha = 0.45f),
    chartBarDayOff = OutlineLight
)

val DarkExtendedColors = WorkHoursExtendedColors(
    clockedIn = ClockedInDark,
    onBreak = OnBreakDark,
    clockedOut = ClockedOutDark,
    alarm = AlarmErrorDark,
    chartTargetLine = OnSurfaceVariantDark.copy(alpha = 0.55f),
    chartBarMet = TealPrimaryDark,
    chartBarUnder = TealPrimaryDark.copy(alpha = 0.45f),
    chartBarDayOff = OutlineDark
)

val LocalWorkHoursColors = staticCompositionLocalOf { LightExtendedColors }
