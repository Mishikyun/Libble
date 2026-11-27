package com.personal.workhours.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.model.AlarmTriggerSpec
import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DailyChartPoint
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.PeriodStats
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import com.personal.workhours.ui.components.InsetGroup
import com.personal.workhours.ui.components.LibbleLogo
import com.personal.workhours.ui.components.PillToggle
import com.personal.workhours.ui.components.SectionOverline
import com.personal.workhours.ui.components.StatusBadge
import com.personal.workhours.ui.components.TactileButton
import com.personal.workhours.ui.components.TactileOutlinedButton
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.WorkHoursThemeExtras
import com.personal.workhours.ui.theme.tabular
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onClockIn: () -> Unit,
    onStartBreak: () -> Unit,
    onResumeWork: () -> Unit,
    onClockOut: () -> Unit,
    onToggleSkipToday: () -> Unit,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onEditDay: (LocalDate) -> Unit,
    onLogCustomTime: () -> Unit,
    onRefreshDate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val nowEpochMs = rememberLiveTicker(
        active = uiState.todayStatus == WorkDayStatus.CLOCKED_IN || uiState.todayStatus == WorkDayStatus.ON_BREAK
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshDate()
    }
    LaunchedEffect(nowEpochMs / 60_000L) {
        onRefreshDate()
    }

    val summary = remember(uiState.todayRecord, uiState.todaySchedule, uiState.isDayOffToday, nowEpochMs) {
        uiState.computeTodaySummary(nowEpochMs)
    }
    val liveWeekNetMs = remember(uiState.weeklyProgress, uiState.todayRecord, uiState.todaySchedule, uiState.isDayOffToday, nowEpochMs) {
        uiState.computeLiveWeekNetMs(nowEpochMs)
    }
    val liveMonthNetMs = remember(uiState.baseMonthNetMsWithoutToday, uiState.todayRecord, uiState.todaySchedule, uiState.isDayOffToday, nowEpochMs) {
        uiState.computeLiveMonthNetMs(nowEpochMs)
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US) }
    val extras = WorkHoursThemeExtras.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // Single Clean Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibbleLogo(size = 28.dp)
                Column {
                    Text(
                        text = uiState.todayDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (uiState.todayStatus == WorkDayStatus.NOT_STARTED || uiState.todayStatus == WorkDayStatus.DAY_OFF) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (uiState.isDayOffToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(onClick = onToggleSkipToday)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (uiState.isDayOffToday) "Day Off" else "Skip Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.isDayOffToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Overnight Unclosed Shift Warning (if applicable)
        uiState.overnightOpenShift?.let { unclosed ->
            InsetGroup(containerColor = extras.alarm.copy(alpha = 0.08f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = extras.alarm,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Unclosed shift · ${unclosed.workDate}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Close yesterday's shift first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            runCatching { LocalDate.parse(unclosed.workDate) }.getOrNull()?.let(onEditDay)
                        }
                    ) {
                        Text("Fix")
                    }
                }
            }
        }

        // Hero Punch Card
        InsetGroup {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusBadge(status = uiState.todayStatus)
                    val clockInLabel = uiState.todayRecord?.clockInEpochMs?.let {
                        "In ${Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)}"
                    }
                    if (clockInLabel != null) {
                        Text(
                            text = clockInLabel,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEditDay(uiState.todayDate) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val primaryTimer = when (uiState.todayStatus) {
                        WorkDayStatus.ON_BREAK -> WorkDurationCalculator.formatDurationHms(summary.activeBreakMs)
                        WorkDayStatus.NOT_STARTED, WorkDayStatus.DAY_OFF, WorkDayStatus.MISSED -> "00:00:00"
                        else -> WorkDurationCalculator.formatDurationHms(summary.netWorkMs)
                    }
                    Text(
                        text = primaryTimer,
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        color = if (uiState.todayStatus == WorkDayStatus.ON_BREAK) extras.onBreak else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val targetText = WorkDurationCalculator.formatDurationHm(summary.targetMs)
                    val breakText = WorkDurationCalculator.formatDurationHm(summary.effectiveBreakMs)
                    val subLabel = when (uiState.todayStatus) {
                        WorkDayStatus.ON_BREAK -> "Break timer · Worked ${WorkDurationCalculator.formatDurationHm(summary.netWorkMs)}"
                        WorkDayStatus.DAY_OFF -> "Reminders paused for today"
                        WorkDayStatus.COMPLETED -> "Break $breakText · Target $targetText"
                        else -> "Break $breakText · Target $targetText"
                    }
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedContent(
                    targetState = uiState.todayStatus,
                    transitionSpec = {
                        fadeIn(tween(LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo)) togetherWith
                            fadeOut(tween(150))
                    },
                    label = "punchButtons"
                ) { status ->
                    when (status) {
                        WorkDayStatus.NOT_STARTED, WorkDayStatus.MISSED -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TactileButton(
                                    onClick = onClockIn,
                                    enabled = uiState.overnightOpenShift == null,
                                    contentPadding = PaddingValues(vertical = 15.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clock In", style = MaterialTheme.typography.labelLarge)
                                }
                                TextButton(
                                    onClick = onLogCustomTime,
                                    enabled = uiState.overnightOpenShift == null
                                ) {
                                    Text("Log custom date & time", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        WorkDayStatus.CLOCKED_IN -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TactileButton(
                                    onClick = onStartBreak,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Break")
                                }
                                TactileButton(
                                    onClick = onClockOut,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clock Out")
                                }
                            }
                        }

                        WorkDayStatus.ON_BREAK -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TactileButton(
                                    onClick = onResumeWork,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Resume")
                                }
                                TactileOutlinedButton(
                                    onClick = onClockOut,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clock Out")
                                }
                            }
                        }

                        WorkDayStatus.COMPLETED -> {
                            TactileOutlinedButton(
                                onClick = { onEditDay(uiState.todayDate) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Punches")
                            }
                        }

                        WorkDayStatus.DAY_OFF -> {
                            TactileOutlinedButton(
                                onClick = onToggleSkipToday,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Work Today Anyway")
                            }
                        }
                    }
                }
            }
        }

        // Quiet Next Alert Line
        uiState.nextAlert?.let { alert ->
            val alertTime = Instant.ofEpochMilli(alert.triggerEpochMs).atZone(zoneId).toLocalTime().format(timeFormatter)
            val eventLabel = when (alert.eventType) {
                ReminderEventType.TIME_IN -> "Time In"
                ReminderEventType.BREAK_START -> "Break Start"
                ReminderEventType.BREAK_END -> "Break End"
                ReminderEventType.TIME_OUT -> "Time Out"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Next · $eventLabel at $alertTime (${uiState.nextAlertModeLabel})",
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Weekly Total Progress Section
        WeeklyProgressSection(
            weeklyProgress = uiState.weeklyProgress,
            liveWeekNetMs = liveWeekNetMs
        )

        // Hours Overview Section
        val activePoints = if (uiState.selectedPeriod == ChartPeriod.WEEK) uiState.weekPoints else uiState.monthPoints
        val activeStats = if (uiState.selectedPeriod == ChartPeriod.WEEK) uiState.weekStats else uiState.monthStats
        val livePeriodNetMs = if (uiState.selectedPeriod == ChartPeriod.WEEK) liveWeekNetMs else liveMonthNetMs
        val liveAvgMs = if (activeStats.workedDaysCount > 0) livePeriodNetMs / activeStats.workedDaysCount else 0L

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(
                title = "Activity",
                trailing = {
                    PillToggle(
                        options = listOf("Week", "Month"),
                        selectedIndex = if (uiState.selectedPeriod == ChartPeriod.WEEK) 0 else 1,
                        onSelect = { idx ->
                            onSelectPeriod(if (idx == 0) ChartPeriod.WEEK else ChartPeriod.MONTH)
                        }
                    )
                }
            )

            InsetGroup {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Clean Borderless Inline KPI Strip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineMetric(
                            label = if (uiState.selectedPeriod == ChartPeriod.WEEK) "Week Total" else "Month Total",
                            value = WorkDurationCalculator.formatHoursDecimal(livePeriodNetMs),
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.height(32.dp)
                        )
                        InlineMetric(
                            label = "Daily Avg",
                            value = WorkDurationCalculator.formatHoursDecimal(liveAvgMs),
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.height(32.dp)
                        )
                        InlineMetric(
                            label = "Breaks",
                            value = WorkDurationCalculator.formatDurationHm(activeStats.totalBreakMs),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    HoursBarChart(
                        points = activePoints,
                        isMonthView = uiState.selectedPeriod == ChartPeriod.MONTH,
                        onEditDay = { dateStr ->
                            runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let(onEditDay)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyProgressSection(
    weeklyProgress: WeeklyProgressSummary,
    liveWeekNetMs: Long
) {
    val extras = WorkHoursThemeExtras.colors
    val fullTargetMs = weeklyProgress.fullWeekTargetMs
    val goalMet = fullTargetMs > 0L && liveWeekNetMs >= fullTargetMs
    val rawFraction = if (fullTargetMs > 0L) (liveWeekNetMs.toFloat() / fullTargetMs.toFloat()) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = tween(LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo),
        label = "weekProgress"
    )
    val percent = if (fullTargetMs > 0L) {
        ((liveWeekNetMs.toDouble() / fullTargetMs.toDouble()) * 100.0).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val weekRangeLabel = remember(weeklyProgress.weekStart, weeklyProgress.weekEnd) {
        val monthDayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        if (weeklyProgress.weekStart.month == weeklyProgress.weekEnd.month) {
            "${weeklyProgress.weekStart.format(monthDayFmt)} – ${weeklyProgress.weekEnd.dayOfMonth}"
        } else {
            "${weeklyProgress.weekStart.format(monthDayFmt)} – ${weeklyProgress.weekEnd.format(monthDayFmt)}"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionOverline(
            title = "This week",
            trailing = {
                Text(
                    text = weekRangeLabel,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        InsetGroup {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = WorkDurationCalculator.formatDurationHm(liveWeekNetMs),
                            style = MaterialTheme.typography.headlineMedium.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (fullTargetMs > 0L) {
                            Text(
                                text = "/ ${formatCompactHours(fullTargetMs)}",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    if (fullTargetMs > 0L) {
                        Surface(
                            color = if (goalMet) extras.clockedIn.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${percent}%",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = if (goalMet) extras.clockedIn else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (animatedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(if (goalMet) extras.clockedIn else MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isOvertime = fullTargetMs > 0L && liveWeekNetMs > fullTargetMs
                    val firstLabel = if (isOvertime) "Overtime" else "Remaining"
                    val firstValue = if (isOvertime) {
                        "+${WorkDurationCalculator.formatDurationHm(liveWeekNetMs - fullTargetMs)}"
                    } else {
                        WorkDurationCalculator.formatDurationHm((fullTargetMs - liveWeekNetMs).coerceAtLeast(0L))
                    }
                    InlineMetric(
                        label = firstLabel,
                        value = firstValue,
                        valueColor = if (isOvertime) extras.clockedIn else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.height(32.dp)
                    )
                    InlineMetric(
                        label = "Days",
                        value = "${weeklyProgress.workedDaysCount} / ${weeklyProgress.scheduledWorkdaysCount}",
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.height(32.dp)
                    )
                    val paceDelta = liveWeekNetMs - weeklyProgress.paceTargetToDateMs
                    InlineMetric(
                        label = "Pace",
                        value = WorkDurationCalculator.formatSignedDeltaHm(paceDelta),
                        valueColor = if (paceDelta >= 0L) extras.clockedIn else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun formatCompactHours(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val h = totalMinutes / 60L
    val m = totalMinutes % 60L
    return if (m == 0L) "${h}h" else "${h}h ${m.toString().padStart(2, '0')}m"
}

@Composable
private fun InlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun rememberLiveTicker(active: Boolean): Long {
    val nowMs = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        nowMs.longValue = System.currentTimeMillis()
    }
    LaunchedEffect(active) {
        nowMs.longValue = System.currentTimeMillis()
        while (active) {
            delay(1_000L)
            nowMs.longValue = System.currentTimeMillis()
        }
    }
    return nowMs.longValue
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    val now = System.currentTimeMillis()
    val sampleState = DashboardUiState(
        todayDate = LocalDate.of(2026, 11, 25),
        todayStatus = WorkDayStatus.CLOCKED_IN,
        todayRecord = WorkDayRecord(
            workDate = "2026-11-25",
            clockInEpochMs = now - 4 * 3_600_000L - 18 * 60_000L,
            breaks = listOf(BreakRecord(startEpochMs = now - 2 * 3_600_000L, endEpochMs = now - 3_600_000L))
        ),
        todaySchedule = DaySchedule(3, true, 480, 1020, 60, 480),
        nextAlert = AlarmTriggerSpec(ReminderEventType.TIME_OUT, 1, "2026-11-25", now + 3_600_000L, now + 3_600_000L),
        nextAlertModeLabel = "Gentle notif",
        weeklyProgress = WeeklyProgressSummary(
            weekStart = LocalDate.of(2026, 11, 23),
            weekEnd = LocalDate.of(2026, 11, 29),
            baseWeekNetMsWithoutToday = 16 * 3_600_000L + 42 * 60_000L,
            fullWeekTargetMs = 40 * 3_600_000L,
            paceTargetToDateMs = 24 * 3_600_000L,
            scheduledWorkdaysCount = 5,
            workedDaysCount = 3
        ),
        baseMonthNetMsWithoutToday = 16 * 3_600_000L + 42 * 60_000L,
        weekPoints = listOf(
            DailyChartPoint("2026-11-23", "M", 8.2f, 8f, 60, WorkDayStatus.COMPLETED),
            DailyChartPoint("2026-11-24", "T", 8.5f, 8f, 60, WorkDayStatus.COMPLETED),
            DailyChartPoint("2026-11-25", "W", 4.3f, 8f, 60, WorkDayStatus.CLOCKED_IN),
            DailyChartPoint("2026-11-26", "T", 0f, 8f, 0, WorkDayStatus.NOT_STARTED),
            DailyChartPoint("2026-11-27", "F", 0f, 8f, 0, WorkDayStatus.NOT_STARTED),
            DailyChartPoint("2026-11-28", "S", 0f, 0f, 0, WorkDayStatus.DAY_OFF),
            DailyChartPoint("2026-11-29", "S", 0f, 0f, 0, WorkDayStatus.DAY_OFF)
        ),
        weekStats = PeriodStats(21 * 3_600_000L, 24 * 3_600_000L, 3 * 3_600_000L, 3, 7 * 3_600_000L, -3 * 3_600_000L)
    )

    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface {
            DashboardScreen(
                uiState = sampleState,
                onClockIn = {},
                onStartBreak = {},
                onResumeWork = {},
                onClockOut = {},
                onToggleSkipToday = {},
                onSelectPeriod = {},
                onEditDay = {},
                onLogCustomTime = {}
            )
        }
    }
}
