package com.personal.workhours.ui.history

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personal.workhours.domain.EditWorkDayValidator
import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.EditValidationResult
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.ui.components.InsetGroup
import com.personal.workhours.ui.components.InsetRowDivider
import com.personal.workhours.ui.components.InsetValueRow
import com.personal.workhours.ui.components.PillToggle
import com.personal.workhours.ui.components.SectionOverline
import com.personal.workhours.ui.components.SheetDragHandle
import com.personal.workhours.ui.components.TactileButton
import com.personal.workhours.ui.components.WorkDatePickerDialog
import com.personal.workhours.ui.components.WorkTimePickerDialog
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import com.personal.workhours.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ActiveTimeField {
    CLOCK_IN,
    CLOCK_OUT,
    BREAK_START,
    BREAK_END
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkDaySheet(
    workDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    existingRecord: WorkDayRecord?,
    scheduleForDate: DaySchedule? = null,
    onDateChanged: (LocalDate) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (
        isDayOff: Boolean,
        clockInEpochMs: Long?,
        clockOutEpochMs: Long?,
        breaks: List<BreakRecord>,
        note: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { SheetDragHandle() }
    ) {
        EditWorkDayContent(
            workDate = workDate,
            today = today,
            existingRecord = existingRecord,
            scheduleForDate = scheduleForDate,
            onDateChanged = onDateChanged,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete
        )
    }
}

@Composable
internal fun EditWorkDayContent(
    workDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    existingRecord: WorkDayRecord?,
    scheduleForDate: DaySchedule? = null,
    onDateChanged: (LocalDate) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (
        isDayOff: Boolean,
        clockInEpochMs: Long?,
        clockOutEpochMs: Long?,
        breaks: List<BreakRecord>,
        note: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US) }

    val defaultStartMinute = scheduleForDate?.startMinuteOfDay ?: (8 * 60)
    val defaultEndMinute = scheduleForDate?.endMinuteOfDay ?: (17 * 60)

    var isDayOff by remember(workDate, existingRecord) {
        mutableStateOf(existingRecord?.isDayOff ?: false)
    }
    var clockInMs by remember(workDate, existingRecord) {
        mutableStateOf(
            existingRecord?.clockInEpochMs ?: ZonedDateTime.of(
                workDate,
                LocalTime.of(defaultStartMinute / 60, defaultStartMinute % 60),
                zoneId
            ).toInstant().toEpochMilli()
        )
    }
    var clockOutMs by remember(workDate, existingRecord) {
        mutableStateOf(
            if (existingRecord != null) {
                existingRecord.clockOutEpochMs
            } else if (workDate.isBefore(today)) {
                ZonedDateTime.of(
                    workDate,
                    LocalTime.of(defaultEndMinute / 60, defaultEndMinute % 60),
                    zoneId
                ).toInstant().toEpochMilli()
            } else {
                null
            }
        )
    }
    var note by remember(workDate, existingRecord) { mutableStateOf(existingRecord?.note ?: "") }
    val breaks = remember(workDate, existingRecord) {
        mutableStateListOf<BreakRecord>().apply {
            addAll(existingRecord?.breaks ?: emptyList())
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var pickerField by remember { mutableStateOf<ActiveTimeField?>(null) }
    var pickerBreakIndex by remember { mutableStateOf(-1) }

    val validation = EditWorkDayValidator.validate(
        isDayOff = isDayOff,
        clockInEpochMs = if (isDayOff) null else clockInMs,
        clockOutEpochMs = if (isDayOff) null else clockOutMs,
        breaks = if (isDayOff) emptyList() else breaks.toList(),
        note = note
    )

    val previewSummary = remember(isDayOff, clockInMs, clockOutMs, breaks.toList()) {
        if (isDayOff || clockOutMs == null) null
        else {
            val previewRecord = WorkDayRecord(
                workDate = workDate.toString(),
                clockInEpochMs = clockInMs,
                clockOutEpochMs = clockOutMs,
                breaks = breaks.toList()
            )
            WorkDurationCalculator.calculateDaySummary(
                record = previewRecord,
                schedule = scheduleForDate,
                nowEpochMs = clockOutMs ?: clockInMs,
                isDayOffOverride = false
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = LibbleMotion.DurationNormal,
                    easing = LibbleMotion.EaseOutExpo
                )
            )
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (existingRecord == null) "Log Time" else "Edit Time",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (previewSummary != null && validation is EditValidationResult.Valid) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${WorkDurationCalculator.formatDurationHm(previewSummary.netWorkMs)} net",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (existingRecord != null && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete day record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        InsetGroup {
            InsetValueRow(
                label = "Date",
                value = workDate.format(dateFormatter),
                onClick = { showDatePicker = true }
            )
            InsetRowDivider()
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                PillToggle(
                    options = listOf("Workday", "Day Off"),
                    selectedIndex = if (isDayOff) 1 else 0,
                    onSelect = { isDayOff = (it == 1) },
                    fillWidth = true
                )
            }
        }

        AnimatedContent(
            targetState = isDayOff,
            transitionSpec = {
                (fadeIn(tween(220, easing = LibbleMotion.EaseOutExpo)) togetherWith
                    fadeOut(tween(140))).using(
                    SizeTransform(clip = false) { _, _ ->
                        tween(durationMillis = LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo)
                    }
                )
            },
            label = "editDayMode"
        ) { dayOffMode ->
            if (dayOffMode) {
                InsetGroup {
                    InsetValueRow(
                        label = "Day off",
                        subtitle = "Excluded from daily target · Reminders paused",
                        showChevron = false
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    InsetGroup {
                        InsetValueRow(
                            label = "Time In",
                            value = Instant.ofEpochMilli(clockInMs).atZone(zoneId).toLocalTime().format(timeFormatter),
                            valueColor = MaterialTheme.colorScheme.primary,
                            onClick = { pickerField = ActiveTimeField.CLOCK_IN }
                        )
                        InsetRowDivider()
                        val outLabel = clockOutMs?.let {
                            Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)
                        } ?: "Still working"
                        InsetValueRow(
                            label = "Time Out",
                            value = outLabel,
                            valueColor = if (clockOutMs == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { pickerField = ActiveTimeField.CLOCK_OUT }
                        )
                        if (clockOutMs != null && !workDate.isBefore(today)) {
                            InsetRowDivider()
                            Box(
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { clockOutMs = null }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Keep shift open",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionOverline(
                            title = "Breaks",
                            trailing = {
                                TextButton(
                                    onClick = {
                                        val defaultBreakStartMin = scheduleForDate?.breakStartMinuteOfDay ?: (12 * 60)
                                        val defaultBreakDurationMin = scheduleForDate?.defaultBreakMinutes?.takeIf { it > 0 } ?: 60
                                        val bStart = ZonedDateTime.of(
                                            workDate,
                                            LocalTime.of(defaultBreakStartMin / 60, defaultBreakStartMin % 60),
                                            zoneId
                                        ).toInstant().toEpochMilli()
                                        val bEnd = bStart + defaultBreakDurationMin * 60_000L
                                        breaks.add(BreakRecord(startEpochMs = bStart, endEpochMs = bEnd))
                                    }
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Add")
                                }
                            }
                        )

                        InsetGroup {
                            if (breaks.isEmpty()) {
                                InsetValueRow(
                                    label = "No breaks logged",
                                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    showChevron = false
                                )
                            } else {
                                breaks.forEachIndexed { idx, brk ->
                                    val sText = Instant.ofEpochMilli(brk.startEpochMs).atZone(zoneId).toLocalTime().format(timeFormatter)
                                    val eText = brk.endEpochMs?.let {
                                        Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)
                                    } ?: "Active"

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TimePill(
                                                text = sText,
                                                onClick = {
                                                    pickerBreakIndex = idx
                                                    pickerField = ActiveTimeField.BREAK_START
                                                }
                                            )
                                            Text(
                                                text = "–",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            TimePill(
                                                text = eText,
                                                onClick = {
                                                    pickerBreakIndex = idx
                                                    pickerField = ActiveTimeField.BREAK_END
                                                }
                                            )
                                        }
                                        IconButton(
                                            onClick = { breaks.removeAt(idx) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove break",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (idx < breaks.lastIndex) {
                                        InsetRowDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionOverline(title = "Note")
            InsetGroup {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (note.isEmpty()) {
                        Text(
                            text = "Optional note...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = note,
                        onValueChange = { if (it.length <= 200) note = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (validation is EditValidationResult.Invalid) {
            Text(
                text = validation.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TactileButton(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            TactileButton(
                onClick = {
                    onSave(
                        isDayOff,
                        if (isDayOff) null else clockInMs,
                        if (isDayOff) null else clockOutMs,
                        if (isDayOff) emptyList() else breaks.toList(),
                        note
                    )
                },
                enabled = validation is EditValidationResult.Valid,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }

    if (showDatePicker) {
        WorkDatePickerDialog(
            initialDate = workDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                showDatePicker = false
                if (picked != workDate) {
                    onDateChanged(picked)
                }
            }
        )
    }

    val currentField = pickerField
    if (currentField != null) {
        val baseMs = when (currentField) {
            ActiveTimeField.CLOCK_IN -> clockInMs
            ActiveTimeField.CLOCK_OUT -> clockOutMs ?: ZonedDateTime.of(
                workDate,
                LocalTime.of(defaultEndMinute / 60, defaultEndMinute % 60),
                zoneId
            ).toInstant().toEpochMilli()
            ActiveTimeField.BREAK_START -> breaks.getOrNull(pickerBreakIndex)?.startEpochMs ?: clockInMs
            ActiveTimeField.BREAK_END -> breaks.getOrNull(pickerBreakIndex)?.endEpochMs ?: clockInMs
        }
        val initialTime = Instant.ofEpochMilli(baseMs).atZone(zoneId).toLocalTime()
        WorkTimePickerDialog(
            title = when (currentField) {
                ActiveTimeField.CLOCK_IN -> "Time In"
                ActiveTimeField.CLOCK_OUT -> "Time Out"
                ActiveTimeField.BREAK_START -> "Break Start"
                ActiveTimeField.BREAK_END -> "Break End"
            },
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            onDismiss = { pickerField = null },
            onConfirm = { h, m ->
                val updatedMs = ZonedDateTime.of(workDate, LocalTime.of(h, m), zoneId).toInstant().toEpochMilli()
                when (currentField) {
                    ActiveTimeField.CLOCK_IN -> clockInMs = updatedMs
                    ActiveTimeField.CLOCK_OUT -> clockOutMs = updatedMs
                    ActiveTimeField.BREAK_START -> if (pickerBreakIndex in breaks.indices) {
                        breaks[pickerBreakIndex] = breaks[pickerBreakIndex].copy(startEpochMs = updatedMs)
                    }
                    ActiveTimeField.BREAK_END -> if (pickerBreakIndex in breaks.indices) {
                        breaks[pickerBreakIndex] = breaks[pickerBreakIndex].copy(endEpochMs = updatedMs)
                    }
                }
                pickerField = null
            }
        )
    }
}

@Composable
private fun TimePill(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun EditWorkDaySheetPreview() {
    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface(color = MaterialTheme.colorScheme.background) {
            EditWorkDayContent(
                workDate = LocalDate.of(2026, 11, 25),
                existingRecord = null,
                onDismiss = {},
                onSave = { _, _, _, _, _ -> }
            )
        }
    }
}
