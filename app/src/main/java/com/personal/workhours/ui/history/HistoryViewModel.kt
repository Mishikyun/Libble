package com.personal.workhours.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.workhours.data.preferences.PreferencesRepository
import com.personal.workhours.data.repository.ScheduleRepository
import com.personal.workhours.data.repository.WorkLogRepository
import com.personal.workhours.domain.WorkDayStateResolver
import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.export.CsvExporter
import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.EditValidationResult
import com.personal.workhours.domain.model.PeriodStats
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import com.personal.workhours.domain.model.WorkDurationSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class ActivityHeatLevel {
    FUTURE,
    EMPTY,
    MISSED,
    PARTIAL,
    COMPLETE
}

data class HistoryCalendarCell(
    val date: LocalDate,
    val isToday: Boolean,
    val heatLevel: ActivityHeatLevel
)

data class HistoryDayItem(
    val date: LocalDate,
    val record: WorkDayRecord?,
    val schedule: DaySchedule?,
    val status: WorkDayStatus,
    val summary: WorkDurationSummary
)

data class HistoryUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val items: List<HistoryDayItem> = emptyList(),
    val calendarCells: List<HistoryCalendarCell> = emptyList(),
    val monthStats: PeriodStats = PeriodStats(0L, 0L, 0L, 0, 0L, 0L),
    val schedulesByDay: Map<Int, DaySchedule> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val workLogRepository: WorkLogRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val today: LocalDate
        get() = Instant.ofEpochMilli(clock.millis()).atZone(clock.zone).toLocalDate()

    private val selectedMonth = MutableStateFlow(YearMonth.from(today))

    val uiState: StateFlow<HistoryUiState> = selectedMonth.flatMapLatest { ym ->
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        combine(
            workLogRepository.observeWorkDaysInRange(start, end),
            scheduleRepository.observeDaySchedules(),
            scheduleRepository.observeDayOffOverrides(),
            preferencesRepository.trackingStartDate
        ) { records, schedules, overrides, trackingStart ->
            val currentToday = today
            val nowMs = clock.millis()
            val recordsByDate = records.associateBy { it.workDate }
            val overrideSet = overrides.map { it.workDate }.toSet()

            val lastDayToShow = when {
                ym.isAfter(YearMonth.from(currentToday)) -> emptyList()
                ym == YearMonth.from(currentToday) -> (currentToday.dayOfMonth downTo 1).map { ym.atDay(it) }
                else -> (ym.lengthOfMonth() downTo 1).map { ym.atDay(it) }
            }

            val items = mutableListOf<HistoryDayItem>()
            val statPairs = mutableListOf<Pair<WorkDayStatus, WorkDurationSummary>>()

            for (date in lastDayToShow) {
                val dateStr = date.toString()
                val rec = recordsByDate[dateStr]
                val sched = schedules[date.dayOfWeek.value]
                val isOverride = overrideSet.contains(dateStr) || rec?.isDayOff == true
                val status = WorkDayStateResolver.resolveStatus(date, currentToday, rec, sched, isOverride)
                val summary = WorkDurationCalculator.calculateDaySummary(rec, sched, nowMs, isOverride)

                val include = WorkDayStateResolver.shouldIncludeInHistory(
                    workDate = date,
                    today = currentToday,
                    trackingStartDate = trackingStart,
                    hasRecord = rec != null,
                    isOverride = isOverride,
                    status = status
                )
                if (include) {
                    items.add(HistoryDayItem(date, rec, sched, status, summary))
                    statPairs.add(status to summary)
                }
            }

            val calendarCells = (1..ym.lengthOfMonth()).map { day ->
                val date = ym.atDay(day)
                val dateStr = date.toString()
                val rec = recordsByDate[dateStr]
                val sched = schedules[date.dayOfWeek.value]
                val isOverride = overrideSet.contains(dateStr) || rec?.isDayOff == true
                val heatLevel = if (date.isAfter(currentToday)) {
                    ActivityHeatLevel.FUTURE
                } else {
                    val status = WorkDayStateResolver.resolveStatus(date, currentToday, rec, sched, isOverride)
                    val summary = WorkDurationCalculator.calculateDaySummary(rec, sched, nowMs, isOverride)
                    when (status) {
                        WorkDayStatus.COMPLETED, WorkDayStatus.CLOCKED_IN, WorkDayStatus.ON_BREAK -> {
                            if (summary.targetMs > 0L) {
                                when {
                                    summary.netWorkMs >= (summary.targetMs * 0.90).toLong() -> ActivityHeatLevel.COMPLETE
                                    summary.netWorkMs > 0L || rec?.clockInEpochMs != null -> ActivityHeatLevel.PARTIAL
                                    else -> ActivityHeatLevel.EMPTY
                                }
                            } else {
                                if (status == WorkDayStatus.COMPLETED && summary.netWorkMs > 0L) {
                                    ActivityHeatLevel.COMPLETE
                                } else {
                                    ActivityHeatLevel.PARTIAL
                                }
                            }
                        }

                        WorkDayStatus.MISSED -> {
                            if (trackingStart == null || !date.isBefore(trackingStart)) {
                                ActivityHeatLevel.MISSED
                            } else {
                                ActivityHeatLevel.EMPTY
                            }
                        }

                        WorkDayStatus.DAY_OFF, WorkDayStatus.NOT_STARTED -> ActivityHeatLevel.EMPTY
                    }
                }
                HistoryCalendarCell(
                    date = date,
                    isToday = date == currentToday,
                    heatLevel = heatLevel
                )
            }

            HistoryUiState(
                selectedMonth = ym,
                items = items,
                calendarCells = calendarCells,
                monthStats = WorkDurationCalculator.aggregatePeriod(statPairs),
                schedulesByDay = schedules
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HistoryUiState(selectedMonth = YearMonth.from(today))
    )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun saveDayEdit(
        workDate: LocalDate,
        isDayOff: Boolean,
        clockInEpochMs: Long?,
        clockOutEpochMs: Long?,
        breaks: List<BreakRecord>,
        note: String,
        onResult: (EditValidationResult) -> Unit
    ) {
        viewModelScope.launch {
            val autoDeduct = if (preferencesRepository.isAutoDeductBreakEnabled()) {
                uiState.value.schedulesByDay[workDate.dayOfWeek.value]?.defaultBreakMinutes ?: 60
            } else 0
            val res = workLogRepository.saveManualEdit(
                workDate = workDate,
                isDayOff = isDayOff,
                clockInEpochMs = clockInEpochMs,
                clockOutEpochMs = clockOutEpochMs,
                breaks = breaks,
                autoDeductBreakMinutes = autoDeduct,
                note = note
            )
            onResult(res)
        }
    }

    fun deleteDay(workDate: LocalDate) {
        viewModelScope.launch { workLogRepository.deleteDay(workDate) }
    }

    fun restoreDay(record: WorkDayRecord) {
        viewModelScope.launch { workLogRepository.restoreWorkDay(record) }
    }

    suspend fun buildCsvForSelectedMonth(): String {
        val ym = selectedMonth.value
        val start = ym.atDay(1).toString()
        val end = ym.atEndOfMonth().toString()
        val records = workLogRepository.getAllWorkDays().filter { it.workDate in start..end }
        return CsvExporter.export(
            records = records,
            schedulesByDay = scheduleRepository.getDaySchedules(),
            dayOffOverrides = scheduleRepository.getDayOffOverrides(),
            today = today,
            nowEpochMs = clock.millis(),
            zoneId = clock.zone
        )
    }

    class Factory(
        private val workLogRepository: WorkLogRepository,
        private val scheduleRepository: ScheduleRepository,
        private val preferencesRepository: PreferencesRepository,
        private val clock: Clock = Clock.systemDefaultZone()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(workLogRepository, scheduleRepository, preferencesRepository, clock) as T
        }
    }
}
