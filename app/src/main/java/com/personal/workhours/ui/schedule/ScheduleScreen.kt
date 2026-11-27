package com.personal.workhours.ui.schedule

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personal.workhours.domain.model.DayOffOverride
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.EscalationMode
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.domain.model.VibrationPatternType
import com.personal.workhours.ui.components.InsetGroup
import com.personal.workhours.ui.components.InsetRowDivider
import com.personal.workhours.ui.components.InsetValueRow
import com.personal.workhours.ui.components.LibbleSwitch
import com.personal.workhours.ui.components.PillToggle
import com.personal.workhours.ui.components.SectionOverline
import com.personal.workhours.ui.components.SheetDragHandle
import com.personal.workhours.ui.components.WorkDatePickerDialog
import com.personal.workhours.ui.components.WorkTimePickerDialog
import com.personal.workhours.ui.theme.LibbleMotion
import com.personal.workhours.ui.theme.WorkHoursTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onSaveDaySchedule: (DaySchedule) -> Unit,
    onApplyDefaultShift: (Set<Int>, Int, Int, Int, Int, Int?) -> Unit,
    onSaveReminderConfig: (ReminderConfig) -> Unit,
    onAddDayOff: (LocalDate, String) -> Unit,
    onRemoveDayOff: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleWorkday = uiState.schedulesByDay.values.firstOrNull { it.isWorkDay }
        ?: DaySchedule(1, true, 480, 1020, 60, 480)

    var selectedDayOfWeek by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var editingShiftTimeTarget by remember { mutableStateOf<String?>(null) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var editingReminderEvent by remember { mutableStateOf<ReminderEventType?>(null) }

    var showHolidayDatePicker by remember { mutableStateOf(false) }
    var pendingHolidayDate by remember { mutableStateOf<LocalDate?>(null) }
    var holidayReason by remember { mutableStateOf("Holiday") }

    var ringtoneEvent by remember { mutableStateOf<ReminderEventType?>(null) }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val event = ringtoneEvent ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val currentCfg = uiState.reminderConfigs[event] ?: ReminderConfig(event)
            onSaveReminderConfig(currentCfg.copy(soundUri = uri?.toString()))
        }
        ringtoneEvent = null
    }

    val activeSchedule = uiState.schedulesByDay[selectedDayOfWeek] ?: sampleWorkday
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "Schedule",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Section 1: Work Hours
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionOverline(title = "Work hours")

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                (1..7).forEach { dow ->
                    val sched = uiState.schedulesByDay[dow]
                    val isSelected = selectedDayOfWeek == dow
                    val isWorkday = sched?.isWorkDay == true
                    val label = DayOfWeek.of(dow).getDisplayName(TextStyle.NARROW, Locale.US)
                    val fullLabel = DayOfWeek.of(dow).getDisplayName(TextStyle.FULL, Locale.US)

                    val bg by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isWorkday -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = tween(200, easing = LibbleMotion.EaseOutExpo),
                        label = "schedBg_$dow"
                    )
                    val fg by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isWorkday -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(200, easing = LibbleMotion.EaseOutExpo),
                        label = "schedFg_$dow"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.04f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "schedScale_$dow"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(bg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) bg else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable(onClickLabel = fullLabel) { selectedDayOfWeek = dow }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                    }
                }
            }

            InsetGroup {
                InsetValueRow(
                    label = DayOfWeek.of(selectedDayOfWeek).getDisplayName(TextStyle.FULL, Locale.US),
                    trailing = {
                        LibbleSwitch(
                            checked = activeSchedule.isWorkDay,
                            onCheckedChange = { checked ->
                                onSaveDaySchedule(activeSchedule.copy(isWorkDay = checked))
                            }
                        )
                    }
                )

                AnimatedVisibility(
                    visible = activeSchedule.isWorkDay,
                    enter = expandVertically(tween(LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo)) +
                        fadeIn(tween(200, easing = LibbleMotion.EaseOutExpo)),
                    exit = shrinkVertically(tween(220, easing = LibbleMotion.EaseOutExpo)) +
                        fadeOut(tween(140))
                ) {
                    Column {
                        InsetRowDivider()
                        InsetValueRow(
                            label = "Time In",
                            value = LocalTime.of(
                                activeSchedule.startMinuteOfDay / 60,
                                activeSchedule.startMinuteOfDay % 60
                            ).format(timeFormatter),
                            onClick = { editingShiftTimeTarget = "START" }
                        )

                        InsetRowDivider()
                        InsetValueRow(
                            label = "Time Out",
                            value = LocalTime.of(
                                activeSchedule.endMinuteOfDay / 60,
                                activeSchedule.endMinuteOfDay % 60
                            ).format(timeFormatter),
                            onClick = { editingShiftTimeTarget = "END" }
                        )

                        if (activeSchedule.defaultBreakMinutes > 0) {
                            val breakStartMins = activeSchedule.breakStartMinuteOfDay ?: (12 * 60)
                            InsetRowDivider()
                            InsetValueRow(
                                label = "Break Start",
                                value = LocalTime.of(breakStartMins / 60, breakStartMins % 60).format(timeFormatter),
                                onClick = { editingShiftTimeTarget = "BREAK_START" }
                            )
                        }

                        InsetRowDivider()
                        InsetValueRow(
                            label = "Break",
                            value = if (activeSchedule.defaultBreakMinutes == 0) "None" else "${activeSchedule.defaultBreakMinutes}m",
                            onClick = { showBreakDialog = true }
                        )

                        InsetRowDivider()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val activeDays = uiState.schedulesByDay.values
                                        .filter { it.isWorkDay }
                                        .map { it.dayOfWeek }
                                        .toSet()
                                        .ifEmpty { setOf(1, 2, 3, 4, 5) }
                                    onApplyDefaultShift(
                                        activeDays,
                                        activeSchedule.startMinuteOfDay,
                                        activeSchedule.endMinuteOfDay,
                                        activeSchedule.defaultBreakMinutes,
                                        activeSchedule.targetWorkMinutes,
                                        activeSchedule.breakStartMinuteOfDay
                                    )
                                }
                                .padding(vertical = 14.dp)
                        ) {
                            Text(
                                text = "Apply to all workdays",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Escalating Reminders (3 clean rows -> modal sheet)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(title = "Reminders")

            InsetGroup {
                val events = listOf(
                    ReminderEventType.TIME_IN,
                    ReminderEventType.BREAK_START,
                    ReminderEventType.BREAK_END,
                    ReminderEventType.TIME_OUT
                )
                events.forEachIndexed { idx, eventType ->
                    val cfg = uiState.reminderConfigs[eventType] ?: ReminderConfig(eventType)
                    val title = when (eventType) {
                        ReminderEventType.TIME_IN -> "Time In"
                        ReminderEventType.BREAK_START -> "Break Start"
                        ReminderEventType.BREAK_END -> "Break End"
                        ReminderEventType.TIME_OUT -> "Time Out"
                    }
                    val subtitle = if (!cfg.enabled) {
                        null
                    } else {
                        val lead = if (cfg.stage1LeadMinutes == 0) {
                            if (cfg.stage2Enabled && cfg.stage2DelayMinutes == 0) "No early alert" else "On time"
                        } else {
                            "${cfg.stage1LeadMinutes}m before"
                        }
                        val esc = if (!cfg.stage2Enabled) "Notification only" else {
                            val mode = when (cfg.stage2Mode) {
                                EscalationMode.VIBRATE_ONLY -> "Vibrate"
                                EscalationMode.SOUND_AND_VIBRATE -> "Sound"
                                EscalationMode.FULL_ALARM -> "Alarm"
                            }
                            val timing = if (cfg.stage2DelayMinutes == 0) "On time" else "+${cfg.stage2DelayMinutes}m"
                            "$timing $mode"
                        }
                        "$lead · $esc"
                    }

                    InsetValueRow(
                        label = title,
                        subtitle = subtitle,
                        value = if (cfg.enabled) "On" else "Off",
                        valueColor = if (cfg.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        showChevron = true,
                        onClick = { editingReminderEvent = eventType }
                    )
                    if (idx < events.lastIndex) {
                        InsetRowDivider()
                    }
                }
            }
        }

        // Section 3: Days Off / Holidays
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionOverline(
                title = "Days off",
                trailing = {
                    TextButton(onClick = { showHolidayDatePicker = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Add")
                    }
                }
            )

            InsetGroup {
                if (uiState.dayOffOverrides.isEmpty()) {
                    InsetValueRow(
                        label = "No planned days off",
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        showChevron = false
                    )
                } else {
                    uiState.dayOffOverrides.forEachIndexed { idx, item ->
                        InsetValueRow(
                            label = item.reason,
                            subtitle = item.workDate,
                            trailing = {
                                IconButton(onClick = { onRemoveDayOff(item.workDate) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove day off",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                        if (idx < uiState.dayOffOverrides.lastIndex) {
                            InsetRowDivider()
                        }
                    }
                }
            }
        }
    }

    // Time picker for Start / Break Start / End
    val target = editingShiftTimeTarget
    if (target != null) {
        val currentMins = when (target) {
            "START" -> activeSchedule.startMinuteOfDay
            "BREAK_START" -> activeSchedule.breakStartMinuteOfDay ?: (12 * 60)
            else -> activeSchedule.endMinuteOfDay
        }
        val pickerTitle = when (target) {
            "START" -> "Time In"
            "BREAK_START" -> "Break Start"
            else -> "Time Out"
        }
        WorkTimePickerDialog(
            title = pickerTitle,
            initialHour = currentMins / 60,
            initialMinute = currentMins % 60,
            onDismiss = { editingShiftTimeTarget = null },
            onConfirm = { h, m ->
                val newMins = h * 60 + m
                val updated = when (target) {
                    "START" -> {
                        val gross = ((activeSchedule.endMinuteOfDay - newMins) + 1440) % 1440
                        activeSchedule.copy(
                            startMinuteOfDay = newMins,
                            targetWorkMinutes = (gross - activeSchedule.defaultBreakMinutes).coerceAtLeast(60)
                        )
                    }
                    "BREAK_START" -> activeSchedule.copy(breakStartMinuteOfDay = newMins)
                    else -> {
                        val gross = ((newMins - activeSchedule.startMinuteOfDay) + 1440) % 1440
                        activeSchedule.copy(
                            endMinuteOfDay = newMins,
                            targetWorkMinutes = (gross - activeSchedule.defaultBreakMinutes).coerceAtLeast(60)
                        )
                    }
                }
                onSaveDaySchedule(updated)
                editingShiftTimeTarget = null
            }
        )
    }

    // Break Duration Picker Dialog
    if (showBreakDialog) {
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            title = { Text("Break duration") },
            text = {
                Column {
                    listOf(0, 30, 45, 60, 90).forEach { mins ->
                        InsetValueRow(
                            label = if (mins == 0) "No break" else "$mins minutes",
                            value = if (activeSchedule.defaultBreakMinutes == mins) "Selected" else null,
                            valueColor = MaterialTheme.colorScheme.primary,
                            showChevron = false,
                            onClick = {
                                val gross = ((activeSchedule.endMinuteOfDay - activeSchedule.startMinuteOfDay) + 1440) % 1440
                                val targetWork = (gross - mins).coerceAtLeast(60)
                                onSaveDaySchedule(
                                    activeSchedule.copy(
                                        defaultBreakMinutes = mins,
                                        targetWorkMinutes = targetWork
                                    )
                                )
                                showBreakDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBreakDialog = false }) { Text("Close") }
            }
        )
    }

    // Reminder Detail ModalBottomSheet
    val reminderEvent = editingReminderEvent
    if (reminderEvent != null) {
        val cfg = uiState.reminderConfigs[reminderEvent] ?: ReminderConfig(reminderEvent)
        ReminderConfigSheet(
            config = cfg,
            onUpdate = onSaveReminderConfig,
            onPickSound = {
                ringtoneEvent = reminderEvent
                val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    cfg.soundUri?.let {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                    }
                }
                ringtoneLauncher.launch(pickerIntent)
            },
            onDismiss = { editingReminderEvent = null }
        )
    }

    if (showHolidayDatePicker) {
        WorkDatePickerDialog(
            initialDate = LocalDate.now(),
            onDismiss = { showHolidayDatePicker = false },
            onConfirm = { date ->
                showHolidayDatePicker = false
                holidayReason = "Holiday"
                pendingHolidayDate = date
            }
        )
    }

    pendingHolidayDate?.let { pickedDate ->
        AlertDialog(
            onDismissRequest = { pendingHolidayDate = null },
            title = { Text("Add day off ($pickedDate)") },
            text = {
                OutlinedTextField(
                    value = holidayReason,
                    onValueChange = { if (it.length <= 60) holidayReason = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddDayOff(pickedDate, holidayReason)
                        pendingHolidayDate = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHolidayDate = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderConfigSheet(
    config: ReminderConfig,
    onUpdate: (ReminderConfig) -> Unit,
    onPickSound: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = when (config.eventType) {
        ReminderEventType.TIME_IN -> "Time In Reminder"
        ReminderEventType.BREAK_START -> "Break Start Reminder"
        ReminderEventType.BREAK_END -> "Break End Reminder"
        ReminderEventType.TIME_OUT -> "Time Out Reminder"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            InsetGroup {
                InsetValueRow(
                    label = "Enable reminder",
                    trailing = {
                        LibbleSwitch(
                            checked = config.enabled,
                            onCheckedChange = { onUpdate(config.copy(enabled = it)) }
                        )
                    }
                )
            }

            AnimatedVisibility(
                visible = config.enabled,
                enter = expandVertically(tween(LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo)) +
                    fadeIn(tween(200, easing = LibbleMotion.EaseOutExpo)),
                exit = shrinkVertically(tween(220, easing = LibbleMotion.EaseOutExpo)) +
                    fadeOut(tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionOverline(title = "Early heads-up")
                        InsetGroup {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Notify before",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val leadOptions = listOf(0, 5, 10, 15)
                                val leadIdx = leadOptions.indexOf(config.stage1LeadMinutes).coerceAtLeast(0)
                                val zeroLabel = if (config.stage2Enabled && config.stage2DelayMinutes == 0) "Off" else "On time"
                                PillToggle(
                                    options = listOf(zeroLabel, "5m", "10m", "15m"),
                                    selectedIndex = leadIdx,
                                    onSelect = { idx -> onUpdate(config.copy(stage1LeadMinutes = leadOptions[idx])) },
                                    fillWidth = true
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionOverline(title = "Alarm")
                        InsetGroup {
                            InsetValueRow(
                                label = "Ring alarm",
                                trailing = {
                                    LibbleSwitch(
                                        checked = config.stage2Enabled,
                                        onCheckedChange = { onUpdate(config.copy(stage2Enabled = it)) }
                                    )
                                }
                            )
                            AnimatedVisibility(
                                visible = config.stage2Enabled,
                                enter = expandVertically(tween(LibbleMotion.DurationNormal, easing = LibbleMotion.EaseOutExpo)) +
                                    fadeIn(tween(200, easing = LibbleMotion.EaseOutExpo)),
                                exit = shrinkVertically(tween(220, easing = LibbleMotion.EaseOutExpo)) +
                                    fadeOut(tween(140))
                            ) {
                                Column {
                                    InsetRowDivider()
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "Trigger",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val delayOptions = listOf(0, 5, 10, 15)
                                        val delayIdx = delayOptions.indexOf(config.stage2DelayMinutes).coerceAtLeast(0)
                                        PillToggle(
                                            options = listOf("On time", "+5m", "+10m", "+15m"),
                                            selectedIndex = delayIdx,
                                            onSelect = { idx -> onUpdate(config.copy(stage2DelayMinutes = delayOptions[idx])) },
                                            fillWidth = true
                                        )
                                    }

                                    InsetRowDivider()
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val modes = EscalationMode.entries
                                        PillToggle(
                                            options = listOf("Vibrate", "Sound", "Alarm"),
                                            selectedIndex = modes.indexOf(config.stage2Mode).coerceAtLeast(0),
                                            onSelect = { idx -> onUpdate(config.copy(stage2Mode = modes[idx])) },
                                            fillWidth = true
                                        )
                                    }

                                    InsetRowDivider()
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "Vibration",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val patterns = VibrationPatternType.entries
                                        PillToggle(
                                            options = listOf("Pulse", "Heartbeat", "Urgent"),
                                            selectedIndex = patterns.indexOf(config.vibrationPattern).coerceAtLeast(0),
                                            onSelect = { idx -> onUpdate(config.copy(vibrationPattern = patterns[idx])) },
                                            fillWidth = true
                                        )
                                    }

                                    if (config.stage2Mode != EscalationMode.VIBRATE_ONLY) {
                                        InsetRowDivider()
                                        InsetValueRow(
                                            label = "Ringtone",
                                            value = if (config.soundUri == null) "Default" else "Custom",
                                            onClick = onPickSound
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ScheduleScreenPreview() {
    val schedules = (1..7).associateWith { dow ->
        DaySchedule(dow, dow in 1..5, 480, 1020, 60, 480)
    }
    val configs = mapOf(
        ReminderEventType.TIME_IN to ReminderConfig(ReminderEventType.TIME_IN),
        ReminderEventType.BREAK_END to ReminderConfig(ReminderEventType.BREAK_END, stage1LeadMinutes = 5, stage2DelayMinutes = 5),
        ReminderEventType.TIME_OUT to ReminderConfig(ReminderEventType.TIME_OUT, stage2Mode = EscalationMode.SOUND_AND_VIBRATE)
    )
    WorkHoursTheme(ThemeMode.SYSTEM) {
        Surface {
            ScheduleScreen(
                uiState = ScheduleUiState(
                    schedulesByDay = schedules,
                    reminderConfigs = configs,
                    dayOffOverrides = listOf(DayOffOverride("2026-11-30", "Bonifacio Day"))
                ),
                onSaveDaySchedule = {},
                onApplyDefaultShift = { _, _, _, _, _, _ -> },
                onSaveReminderConfig = {},
                onAddDayOff = { _, _ -> },
                onRemoveDayOff = {}
            )
        }
    }
}
