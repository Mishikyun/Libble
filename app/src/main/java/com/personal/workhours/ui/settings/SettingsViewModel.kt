package com.personal.workhours.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.workhours.alarm.AlarmScheduler
import com.personal.workhours.data.preferences.PreferencesRepository
import com.personal.workhours.data.repository.ScheduleRepository
import com.personal.workhours.data.repository.WorkLogRepository
import com.personal.workhours.domain.export.BackupSerializer
import com.personal.workhours.domain.export.CsvExporter
import com.personal.workhours.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoDeductBreakEnabled: Boolean = false
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val workLogRepository: WorkLogRepository,
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.autoDeductBreakEnabled
    ) { theme, autoDeduct ->
        SettingsUiState(
            themeMode = theme,
            autoDeductBreakEnabled = autoDeduct
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setAutoDeductBreakEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoDeductBreakEnabled(enabled) }
    }

    fun triggerTestEscalation() {
        alarmScheduler.scheduleTestEscalation(delaySeconds = 5)
    }

    fun replayOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted(false) }
    }

    suspend fun buildAllCsv(): String {
        val nowMs = clock.millis()
        val today = Instant.ofEpochMilli(nowMs).atZone(clock.zone).toLocalDate()
        return CsvExporter.export(
            records = workLogRepository.getAllWorkDays(),
            schedulesByDay = scheduleRepository.getDaySchedules(),
            dayOffOverrides = scheduleRepository.getDayOffOverrides(),
            today = today,
            nowEpochMs = nowMs,
            zoneId = clock.zone
        )
    }

    suspend fun buildBackupJson(): String {
        val snapshot = workLogRepository.exportAllSnapshot()
        return BackupSerializer.encode(snapshot)
    }

    suspend fun restoreFromJson(rawJson: String): Result<Int> {
        val parsed = BackupSerializer.decodeAndValidate(rawJson)
        return parsed.mapCatching { payload ->
            workLogRepository.restoreSnapshot(payload)
            payload.workDays.size
        }
    }
}
