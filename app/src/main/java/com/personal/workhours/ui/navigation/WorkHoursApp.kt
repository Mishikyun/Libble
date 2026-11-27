package com.personal.workhours.ui.navigation

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.workhours.AppContainer
import com.personal.workhours.domain.export.BackupSerializer
import com.personal.workhours.domain.model.EditValidationResult
import com.personal.workhours.domain.model.ThemeMode
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.ui.dashboard.DashboardScreen
import com.personal.workhours.ui.dashboard.DashboardViewModel
import com.personal.workhours.ui.history.EditWorkDaySheet
import com.personal.workhours.ui.history.HistoryScreen
import com.personal.workhours.ui.history.HistoryViewModel
import com.personal.workhours.ui.onboarding.OnboardingScreen
import com.personal.workhours.ui.schedule.ScheduleScreen
import com.personal.workhours.ui.schedule.ScheduleViewModel
import com.personal.workhours.ui.settings.SettingsScreen
import com.personal.workhours.ui.settings.SettingsViewModel
import com.personal.workhours.ui.theme.WorkHoursTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TopTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Rounded.Dashboard),
    HISTORY("history", "History", Icons.Rounded.History),
    SCHEDULE("schedule", "Schedule", Icons.Rounded.Alarm),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings)
}

@Composable
fun WorkHoursApp(
    container: AppContainer,
    openDashboardTrigger: Int = 0
) {
    val themeMode by container.preferencesRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val onboardingState by remember(container) {
        container.preferencesRepository.onboardingCompleted.map<Boolean, Boolean?> { it }
    }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    WorkHoursTheme(themeMode = themeMode) {
        when (onboardingState) {
            null -> {
                Surface(modifier = Modifier.fillMaxSize()) {}
                return@WorkHoursTheme
            }

            false -> {
                OnboardingScreen(
                    onCompleteOnboarding = { workDays, startMin, endMin, breakMin ->
                        scope.launch {
                            val gross = ((endMin - startMin) + 1440) % 1440
                            val target = (gross - breakMin).coerceAtLeast(60)
                            container.scheduleRepository.applyDefaultShiftToWorkdays(
                                workDaysOfWeek = workDays,
                                startMinuteOfDay = startMin,
                                endMinuteOfDay = endMin,
                                defaultBreakMinutes = breakMin,
                                targetWorkMinutes = target
                            )
                            container.preferencesRepository.ensureTrackingStartDate(LocalDate.now(container.clock))
                            container.preferencesRepository.setOnboardingCompleted(true)
                        }
                    },
                    onTriggerTestAlarm = {
                        container.alarmScheduler.scheduleTestEscalation(delaySeconds = 5)
                    }
                )
                return@WorkHoursTheme
            }

            true -> Unit
        }

        val dashboardVm: DashboardViewModel = viewModel(
            factory = DashboardViewModel.Factory(
                container.workLogRepository,
                container.scheduleRepository,
                container.preferencesRepository,
                container.clock
            )
        )
        val historyVm: HistoryViewModel = viewModel(
            factory = HistoryViewModel.Factory(
                container.workLogRepository,
                container.scheduleRepository,
                container.preferencesRepository,
                container.clock
            )
        )
        val scheduleVm: ScheduleViewModel = viewModel(
            factory = ScheduleViewModel.Factory(container.scheduleRepository)
        )
        val settingsVm: SettingsViewModel = viewModel(
            factory = SettingsViewModel.Factory(
                container.preferencesRepository,
                container.workLogRepository,
                container.scheduleRepository,
                container.alarmScheduler,
                container.clock
            )
        )

        val dashboardState by dashboardVm.uiState.collectAsStateWithLifecycle()
        val historyState by historyVm.uiState.collectAsStateWithLifecycle()
        val scheduleState by scheduleVm.uiState.collectAsStateWithLifecycle()
        val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current

        // Manual Edit & Delete confirmation state
        var editingDate by remember { mutableStateOf<LocalDate?>(null) }
        var editingExistingRecord by remember { mutableStateOf<WorkDayRecord?>(null) }
        var pendingDeleteTarget by remember { mutableStateOf<Pair<LocalDate, WorkDayRecord?>?>(null) }

        fun openEditorForDate(date: LocalDate) {
            scope.launch {
                val existing = withContext(Dispatchers.IO) {
                    container.workLogRepository.getWorkDay(date)
                }
                editingExistingRecord = existing
                editingDate = date
            }
        }

        LaunchedEffect(openDashboardTrigger) {
            if (openDashboardTrigger > 0) {
                editingDate = null
                navController.navigate(TopTab.DASHBOARD.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        fun deleteDayWithUndo(date: LocalDate, existingRecord: WorkDayRecord?) {
            historyVm.deleteDay(date)
            if (existingRecord != null) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val label = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
                    val res = snackbarHostState.showSnackbar(
                        message = "Deleted $label log",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        historyVm.restoreDay(existingRecord)
                    }
                }
            }
        }

        // Export / Backup / Restore SAF launchers
        var pendingCsvText by remember { mutableStateOf<String?>(null) }
        val csvCreateLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            val csv = pendingCsvText ?: return@rememberLauncherForActivityResult
            pendingCsvText = null
            if (uri != null) {
                scope.launch {
                    val ok = writeTextToUri(context, uri, csv)
                    snackbarHostState.showSnackbar(if (ok) "CSV exported." else "Failed to write CSV.")
                }
            }
        }

        var pendingBackupJson by remember { mutableStateOf<String?>(null) }
        val backupCreateLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val json = pendingBackupJson ?: return@rememberLauncherForActivityResult
            pendingBackupJson = null
            if (uri != null) {
                scope.launch {
                    val ok = writeTextToUri(context, uri, json)
                    snackbarHostState.showSnackbar(if (ok) "Backup saved." else "Failed to write backup.")
                }
            }
        }

        var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
        val restoreOpenLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val text = readTextFromUri(context, uri, BackupSerializer.MAX_BACKUP_BYTES)
                    if (text == null) {
                        snackbarHostState.showSnackbar("Could not read file or file exceeds 5 MB.")
                    } else {
                        pendingRestoreJson = text
                    }
                }
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        TopTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopTab.DASHBOARD.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(TopTab.DASHBOARD.route) {
                    DashboardScreen(
                        uiState = dashboardState,
                        onClockIn = dashboardVm::clockIn,
                        onStartBreak = dashboardVm::startBreak,
                        onResumeWork = dashboardVm::resumeWork,
                        onClockOut = dashboardVm::clockOut,
                        onToggleSkipToday = dashboardVm::toggleSkipToday,
                        onSelectPeriod = dashboardVm::selectPeriod,
                        onEditDay = ::openEditorForDate,
                        onLogCustomTime = { openEditorForDate(dashboardState.todayDate) },
                        onRefreshDate = dashboardVm::refreshDateIfNeeded
                    )
                }

                composable(TopTab.HISTORY.route) {
                    HistoryScreen(
                        uiState = historyState,
                        onPreviousMonth = historyVm::previousMonth,
                        onNextMonth = historyVm::nextMonth,
                        onExportCsv = {
                            scope.launch {
                                pendingCsvText = historyVm.buildCsvForSelectedMonth()
                                csvCreateLauncher.launch("Libble_${historyState.selectedMonth}.csv")
                            }
                        },
                        onEditDay = ::openEditorForDate,
                        onDeleteDay = { item -> pendingDeleteTarget = item.date to item.record }
                    )
                }

                composable(TopTab.SCHEDULE.route) {
                    ScheduleScreen(
                        uiState = scheduleState,
                        onSaveDaySchedule = scheduleVm::saveDaySchedule,
                        onApplyDefaultShift = scheduleVm::applyDefaultShiftToWorkdays,
                        onSaveReminderConfig = scheduleVm::saveReminderConfig,
                        onAddDayOff = scheduleVm::addDayOffOverride,
                        onRemoveDayOff = scheduleVm::removeDayOffOverride
                    )
                }

                composable(TopTab.SETTINGS.route) {
                    SettingsScreen(
                        uiState = settingsState,
                        onSelectTheme = settingsVm::setThemeMode,
                        onToggleAutoDeduct = settingsVm::setAutoDeductBreakEnabled,
                        onTriggerTestAlarm = settingsVm::triggerTestEscalation,
                        onExportCsv = {
                            scope.launch {
                                pendingCsvText = settingsVm.buildAllCsv()
                                csvCreateLauncher.launch("Libble_All.csv")
                            }
                        },
                        onBackupJson = {
                            scope.launch {
                                pendingBackupJson = settingsVm.buildBackupJson()
                                backupCreateLauncher.launch("Libble_Backup_${LocalDate.now()}.json")
                            }
                        },
                        onRestoreJson = {
                            restoreOpenLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onReplayOnboarding = settingsVm::replayOnboarding
                    )
                }
            }
        }

        val currentEditDate = editingDate
        if (currentEditDate != null) {
            EditWorkDaySheet(
                workDate = currentEditDate,
                today = dashboardState.todayDate,
                existingRecord = editingExistingRecord,
                scheduleForDate = scheduleState.schedulesByDay[currentEditDate.dayOfWeek.value],
                onDateChanged = ::openEditorForDate,
                onDismiss = { editingDate = null },
                onSave = { isDayOff, clockInMs, clockOutMs, breaks, note ->
                    dashboardVm.saveDayEdit(
                        workDate = currentEditDate,
                        isDayOff = isDayOff,
                        clockInEpochMs = clockInMs,
                        clockOutEpochMs = clockOutMs,
                        breaks = breaks,
                        note = note
                    ) { result ->
                        if (result is EditValidationResult.Valid) {
                            editingDate = null
                        } else if (result is EditValidationResult.Invalid) {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = {
                    pendingDeleteTarget = currentEditDate to editingExistingRecord
                }
            )
        }

        val deleteCandidate = pendingDeleteTarget
        if (deleteCandidate != null) {
            val (deleteDate, deleteRecord) = deleteCandidate
            val formattedDate = deleteDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
            AlertDialog(
                onDismissRequest = { pendingDeleteTarget = null },
                title = { Text("Delete log?") },
                text = { Text("Remove the work log for $formattedDate?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteTarget = null
                            if (editingDate == deleteDate) {
                                editingDate = null
                            }
                            deleteDayWithUndo(deleteDate, deleteRecord)
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteTarget = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        val restoreCandidate = pendingRestoreJson
        if (restoreCandidate != null) {
            AlertDialog(
                onDismissRequest = { pendingRestoreJson = null },
                title = { Text("Restore Backup?") },
                text = { Text("This will replace your current logs and schedules with the selected backup file.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val raw = restoreCandidate
                            pendingRestoreJson = null
                            scope.launch {
                                val res = settingsVm.restoreFromJson(raw)
                                val msg = res.fold(
                                    onSuccess = { count -> "Restored $count work day logs." },
                                    onFailure = { err -> err.message ?: "Invalid backup file." }
                                )
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    ) { Text("Restore") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestoreJson = null }) { Text("Cancel") }
                }
            )
        }
    }
}

private suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Null output stream")
            true
        }.getOrDefault(false)
    }
}

private suspend fun readTextFromUri(context: Context, uri: Uri, maxBytes: Int): String? {
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return@use null
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }
}
