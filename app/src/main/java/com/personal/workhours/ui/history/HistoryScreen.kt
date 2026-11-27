package com.personal.workhours.ui.history

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.PeriodStats
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import com.personal.workhours.domain.model.WorkDurationSummary
import com.personal.workhours.ui.components.InsetGroup
import com.personal.workhours.ui.components.InsetRowDivider
import com.personal.workhours.ui.components.SectionOverline
import com.personal.workhours.ui.components.StatusBadge
import com.personal.workhours.ui.components.WorkDatePickerDialog
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.WorkHoursThemeExtras
import com.personal.workhours.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExportCsv: () -> Unit,
    onEditDay: (LocalDate) -> Unit,
    onDeleteDay: (HistoryDayItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }
    val zoneId = remember { ZoneId.systemDefault() }
    var showAddDatePicker by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row {
                    IconButton(onClick = { showAddDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add or fix day",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onExportCsv) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Export CSV",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            InsetGroup {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(
                        text = uiState.selectedMonth.format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    MonthMetric(
                        label = "Total",
                        value = WorkDurationCalculator.formatHoursDecimal(uiState.monthStats.totalNetMs),
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.height(28.dp)
                    )
                    MonthMetric(
                        label = "Days",
                        value = uiState.monthStats.workedDaysCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.height(28.dp)
                    )
                    MonthMetric(
                        label = "Avg/Day",
                        value = WorkDurationCalculator.formatHoursDecimal(uiState.monthStats.averageNetMsPerWorkedDay),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionOverline(
                    title = "Activity",
                    trailing = { ActivityLegend() }
                )
                InsetGroup {
                    MonthlyActivityCalendar(
                        selectedMonth = uiState.selectedMonth,
                        cells = uiState.calendarCells,
                        onSelectDate = onEditDay
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionOverline(title = "Logs")

                InsetGroup {
                    if (uiState.items.isEmpty()) {
                        Text(
                            text = "No time logs for this month.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    } else {
                        uiState.items.forEachIndexed { idx, item ->
                            if (item.record != null) {
                                key(item.date, item.record.updatedAtEpochMs) {
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        positionalThreshold = { totalDistance -> totalDistance * 0.42f }
                                    )
                                    val haptics = LocalHapticFeedback.current
                                    LaunchedEffect(dismissState.targetValue) {
                                        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    LaunchedEffect(dismissState.currentValue) {
                                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                            onDeleteDay(item)
                                            dismissState.reset()
                                        }
                                    }

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        enableDismissFromEndToStart = true,
                                        backgroundContent = {
                                            val armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                                            val bgColor by animateColorAsState(
                                                targetValue = if (armed) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                },
                                                label = "swipeDeleteBg"
                                            )
                                            val fgColor = if (armed) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                            Box(
                                                contentAlignment = Alignment.CenterEnd,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(bgColor)
                                                    .padding(horizontal = 20.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = fgColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "Delete",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = fgColor
                                                    )
                                                }
                                            }
                                        },
                                        content = {
                                            HistoryDayRowContent(
                                                item = item,
                                                dayFormatter = dayFormatter,
                                                timeFormatter = timeFormatter,
                                                zoneId = zoneId,
                                                onClick = { onEditDay(item.date) }
                                            )
                                        }
                                    )
                                }
                            } else {
                                HistoryDayRowContent(
                                    item = item,
                                    dayFormatter = dayFormatter,
                                    timeFormatter = timeFormatter,
                                    zoneId = zoneId,
                                    onClick = { onEditDay(item.date) }
                                )
                            }
                            if (idx < uiState.items.lastIndex) {
                                InsetRowDivider()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddDatePicker) {
        WorkDatePickerDialog(
            initialDate = LocalDate.now(),
            onDismiss = { showAddDatePicker = false },
            onConfirm = { picked ->
                showAddDatePicker = false
                onEditDay(picked)
            }
        )
    }
}

@Composable
private fun HistoryDayRowContent(
    item: HistoryDayItem,
    dayFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    zoneId: ZoneId,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.date.format(dayFormatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val details = when (item.status) {
                WorkDayStatus.DAY_OFF -> item.record?.note?.ifBlank { "Scheduled day off" } ?: "Scheduled day off"
                WorkDayStatus.MISSED -> "Tap to add missed punches"
                WorkDayStatus.NOT_STARTED -> "Not started"
                else -> {
                    val inText = item.record?.clockInEpochMs?.let {
                        Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)
                    } ?: "--"
                    val outText = item.record?.clockOutEpochMs?.let {
                        Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)
                    } ?: "Active"
                    if (item.summary.effectiveBreakMs > 0L) {
                        "$inText – $outText · Break ${formatCompactBreak(item.summary.effectiveBreakMs)}"
                    } else {
                        "$inText – $outText"
                    }
                }
            }
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.summary.netWorkMs > 0L) {
                Text(
                    text = formatPaddedHm(item.summary.netWorkMs),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            StatusBadge(status = item.status)
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun formatPaddedHm(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val h = totalMinutes / 60L
    val m = totalMinutes % 60L
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

private fun formatCompactBreak(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val h = totalMinutes / 60L
    val m = totalMinutes % 60L
    return when {
        h > 0L && m == 0L -> "${h}h"
        h > 0L -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

@Composable
private fun ActivityLegend() {
    val extras = WorkHoursThemeExtras.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LegendSwatch(color = extras.clockedIn, label = "Full")
        LegendSwatch(color = extras.clockedIn.copy(alpha = 0.35f), label = "Partial")
        LegendSwatch(color = extras.alarm.copy(alpha = 0.45f), label = "Missed")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthlyActivityCalendar(
    selectedMonth: YearMonth,
    cells: List<HistoryCalendarCell>,
    onSelectDate: (LocalDate) -> Unit
) {
    val extras = WorkHoursThemeExtras.colors
    val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
    val resolvedCells = if (cells.isNotEmpty()) {
        cells
    } else {
        (1..selectedMonth.lengthOfMonth()).map { day ->
            HistoryCalendarCell(
                date = selectedMonth.atDay(day),
                isToday = false,
                heatLevel = ActivityHeatLevel.EMPTY
            )
        }
    }
    val leadingBlanks = selectedMonth.atDay(1).dayOfWeek.value - 1
    val paddedCells: List<HistoryCalendarCell?> = List(leadingBlanks) { null } + resolvedCells
    val weeks = paddedCells.chunked(7)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            dayHeaders.forEach { dayLetter ->
                Text(
                    text = dayLetter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        weeks.forEach { week ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (col in 0 until 7) {
                    val cell = week.getOrNull(col)
                    if (cell == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        )
                    } else {
                        val bgColor: Color
                        val fgColor: Color
                        val textWeight: FontWeight
                        val tileBorderColor: Color?

                        when (cell.heatLevel) {
                            ActivityHeatLevel.COMPLETE -> {
                                bgColor = extras.clockedIn
                                fgColor = MaterialTheme.colorScheme.onPrimary
                                textWeight = FontWeight.SemiBold
                                tileBorderColor = null
                            }

                            ActivityHeatLevel.PARTIAL -> {
                                bgColor = extras.clockedIn.copy(alpha = 0.28f)
                                fgColor = extras.clockedIn
                                textWeight = FontWeight.Medium
                                tileBorderColor = extras.clockedIn.copy(alpha = 0.45f)
                            }

                            ActivityHeatLevel.MISSED -> {
                                bgColor = extras.alarm.copy(alpha = 0.16f)
                                fgColor = extras.alarm
                                textWeight = FontWeight.Medium
                                tileBorderColor = extras.alarm.copy(alpha = 0.35f)
                            }

                            ActivityHeatLevel.EMPTY -> {
                                bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                fgColor = MaterialTheme.colorScheme.onSurfaceVariant
                                textWeight = FontWeight.Normal
                                tileBorderColor = null
                            }

                            ActivityHeatLevel.FUTURE -> {
                                bgColor = Color.Transparent
                                fgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                textWeight = FontWeight.Normal
                                tileBorderColor = null
                            }
                        }

                        val shape = RoundedCornerShape(8.dp)
                        val borderModifier = if (cell.isToday) {
                            val ringColor = if (cell.heatLevel == ActivityHeatLevel.COMPLETE) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            Modifier.border(1.5.dp, ringColor, shape)
                        } else if (tileBorderColor != null) {
                            Modifier.border(1.dp, tileBorderColor, shape)
                        } else {
                            Modifier
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(shape)
                                .background(bgColor)
                                .then(borderModifier)
                                .then(
                                    if (cell.heatLevel != ActivityHeatLevel.FUTURE) {
                                        Modifier.clickable { onSelectDate(cell.date) }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = cell.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                fontWeight = textWeight,
                                color = fgColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    val t0 = System.currentTimeMillis() - 10 * 3_600_000L
    val sampleCells = (1..30).map { day ->
        val date = LocalDate.of(2026, 11, day)
        val level = when {
            day > 25 -> ActivityHeatLevel.FUTURE
            day == 25 || day in listOf(4, 5, 6, 11, 12, 13, 18, 19, 20) -> ActivityHeatLevel.COMPLETE
            day == 24 -> ActivityHeatLevel.MISSED
            day == 23 || day == 17 -> ActivityHeatLevel.PARTIAL
            else -> ActivityHeatLevel.EMPTY
        }
        HistoryCalendarCell(date = date, isToday = day == 25, heatLevel = level)
    }
    val sample = HistoryUiState(
        selectedMonth = YearMonth.of(2026, 11),
        calendarCells = sampleCells,
        items = listOf(
            HistoryDayItem(
                date = LocalDate.of(2026, 11, 25),
                record = WorkDayRecord(workDate = "2026-11-25", clockInEpochMs = t0, clockOutEpochMs = t0 + 9 * 3_600_000L, autoDeductBreakMinutes = 60),
                schedule = DaySchedule(3, true, 480, 1020, 60, 480),
                status = WorkDayStatus.COMPLETED,
                summary = WorkDurationSummary(9 * 3_600_000L, 0L, 3_600_000L, 0L, 8 * 3_600_000L, 8 * 3_600_000L, 0L)
            ),
            HistoryDayItem(
                date = LocalDate.of(2026, 11, 24),
                record = null,
                schedule = DaySchedule(2, true, 480, 1020, 60, 480),
                status = WorkDayStatus.MISSED,
                summary = WorkDurationSummary(0L, 0L, 0L, 0L, 0L, 8 * 3_600_000L, -8 * 3_600_000L)
            )
        ),
        monthStats = PeriodStats(8 * 3_600_000L, 16 * 3_600_000L, 3_600_000L, 1, 8 * 3_600_000L, -8 * 3_600_000L)
    )

    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface {
            HistoryScreen(
                uiState = sample,
                onPreviousMonth = {},
                onNextMonth = {},
                onExportCsv = {},
                onEditDay = {},
                onDeleteDay = {}
            )
        }
    }
}
