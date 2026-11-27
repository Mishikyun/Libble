package com.personal.workhours.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.workhours.data.repository.ScheduleRepository
import com.personal.workhours.domain.model.DayOffOverride
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScheduleUiState(
    val schedulesByDay: Map<Int, DaySchedule> = emptyMap(),
    val reminderConfigs: Map<ReminderEventType, ReminderConfig> = emptyMap(),
    val dayOffOverrides: List<DayOffOverride> = emptyList()
)

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.observeDaySchedules(),
        scheduleRepository.observeReminderConfigs(),
        scheduleRepository.observeDayOffOverrides()
    ) { schedules, configs, overrides ->
        ScheduleUiState(
            schedulesByDay = schedules,
            reminderConfigs = configs,
            dayOffOverrides = overrides
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScheduleUiState()
    )

    fun saveDaySchedule(schedule: DaySchedule) {
        viewModelScope.launch { scheduleRepository.saveDaySchedule(schedule) }
    }

    fun applyDefaultShiftToWorkdays(
        workDaysOfWeek: Set<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        defaultBreakMinutes: Int,
        targetWorkMinutes: Int,
        breakStartMinuteOfDay: Int? = 12 * 60
    ) {
        viewModelScope.launch {
            scheduleRepository.applyDefaultShiftToWorkdays(
                workDaysOfWeek = workDaysOfWeek,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                defaultBreakMinutes = defaultBreakMinutes,
                targetWorkMinutes = targetWorkMinutes,
                breakStartMinuteOfDay = breakStartMinuteOfDay
            )
        }
    }

    fun saveReminderConfig(config: ReminderConfig) {
        viewModelScope.launch { scheduleRepository.saveReminderConfig(config) }
    }

    fun addDayOffOverride(date: LocalDate, reason: String) {
        viewModelScope.launch { scheduleRepository.addDayOffOverride(date, reason) }
    }

    fun removeDayOffOverride(workDate: String) {
        viewModelScope.launch { scheduleRepository.removeDayOffOverride(workDate) }
    }

    class Factory(
        private val scheduleRepository: ScheduleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScheduleViewModel(scheduleRepository) as T
        }
    }
}
