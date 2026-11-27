package com.personal.workhours.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.workhours.data.preferences.PreferencesRepository
import com.personal.workhours.data.repository.ScheduleRepository
import com.personal.workhours.data.repository.WorkLogRepository
import com.personal.workhours.domain.ScheduleResolver
import com.personal.workhours.domain.WorkDayStateResolver
import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.model.AlarmTriggerSpec
import com.personal.workhours.domain.model.DailyChartPoint
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.PeriodStats
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class ChartPeriod {
    WEEK,
    MONTH
}

data class WeeklyProgressSummary(
    val weekStart: LocalDate = LocalDate.now(),
    val weekEnd: LocalDate = LocalDate.now(),
    val baseWeekNetMsWithoutToday: Long = 0L,
    val fullWeekTargetMs: Long = 0L,
    val paceTargetToDateMs: Long = 0L,
    val scheduledWorkdaysCount: Int = 0,
    val workedDaysCount: Int = 0
)

data class DashboardUiState(
    val todayDate: LocalDate = LocalDate.now(),
    val todayStatus: WorkDayStatus = WorkDayStatus.NOT_STARTED,
    val todayRecord: WorkDayRecord? = null,
    val todaySchedule: DaySchedule? = null,
    val overnightOpenShift: WorkDayRecord? = null,
    val nextAlert: AlarmTriggerSpec? = null,
    val nextAlertModeLabel: String = "",
    val selectedPeriod: ChartPeriod = ChartPeriod.WEEK,
    val weeklyProgress: WeeklyProgressSummary = WeeklyProgressSummary(),
    val baseMonthNetMsWithoutToday: Long = 0L,
    val weekPoints: List<DailyChartPoint> = emptyList(),
    val weekStats: PeriodStats = PeriodStats(0L, 0L, 0L, 0, 0L, 0L),
    val monthPoints: List<DailyChartPoint> = emptyList(),
    val monthStats: PeriodStats = PeriodStats(0L, 0L, 0L, 0, 0L, 0L),
    val allSchedules: Map<Int, DaySchedule> = emptyMap(),
    val allRecordsByDate: Map<String, WorkDayRecord> = emptyMap(),
    val isDayOffToday: Boolean = false
) {
    fun computeTodaySummary(nowEpochMs: Long): WorkDurationSummary {
        return WorkDurationCalculator.calculateDaySummary(
            record = todayRecord,
            schedule = todaySchedule,
            nowEpochMs = nowEpochMs,
            isDayOffOverride = isDayOffToday
        )
    }

    fun computeLiveWeekNetMs(nowEpochMs: Long): Long {
        return weeklyProgress.baseWeekNetMsWithoutToday + computeTodaySummary(nowEpochMs).netWorkMs
    }

    fun computeLiveMonthNetMs(nowEpochMs: Long): Long {
        return baseMonthNetMsWithoutToday + computeTodaySummary(nowEpochMs).netWorkMs
    }
}

class DashboardViewModel(
    private val workLogRepository: WorkLogRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val today: LocalDate
        get() = Instant.ofEpochMilli(clock.millis()).atZone(clock.zone).toLocalDate()

    private val selectedPeriod = MutableStateFlow(ChartPeriod.WEEK)
    private val activeDate = MutableStateFlow(today)

    fun refreshDateIfNeeded() {
        val current = today
        if (activeDate.value != current) {
            activeDate.value = current
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = activeDate.flatMapLatest { currentToday ->
        val mStart = currentToday.withDayOfMonth(1)
        val mEnd = currentToday.withDayOfMonth(currentToday.lengthOfMonth())
        val wStart = currentToday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val wEnd = currentToday.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val qStart = if (wStart.isBefore(mStart)) wStart else mStart
        val qEnd = if (wEnd.isAfter(mEnd)) wEnd else mEnd

        combine(
            workLogRepository.observeWorkDay(currentToday),
            workLogRepository.observeOpenShifts(),
            workLogRepository.observeWorkDaysInRange(qStart, qEnd),
            scheduleRepository.observeDaySchedules(),
            scheduleRepository.observeReminderConfigs(),
            scheduleRepository.observeDayOffOverrides(),
            combine(selectedPeriod, preferencesRepository.trackingStartDate) { period, start -> period to start }
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val todayRec = values[0] as WorkDayRecord?
            @Suppress("UNCHECKED_CAST")
            val openShifts = values[1] as List<WorkDayRecord>
            @Suppress("UNCHECKED_CAST")
            val rangeRecords = values[2] as List<WorkDayRecord>
            @Suppress("UNCHECKED_CAST")
            val schedules = values[3] as Map<Int, DaySchedule>
            @Suppress("UNCHECKED_CAST")
            val reminders = values[4] as Map<ReminderEventType, ReminderConfig>
            @Suppress("UNCHECKED_CAST")
            val overrides = values[5] as List<com.personal.workhours.domain.model.DayOffOverride>
            @Suppress("UNCHECKED_CAST")
            val periodAndStart = values[6] as Pair<ChartPeriod, LocalDate?>
            val period = periodAndStart.first
            val trackingStart = periodAndStart.second

            val nowMs = clock.millis()
            val overrideSet = overrides.map { it.workDate }.toSet()
            val recordsMap = rangeRecords.associateBy { it.workDate }
            val todaySchedule = schedules[currentToday.dayOfWeek.value]
            val isDayOffToday = overrideSet.contains(currentToday.toString()) || todayRec?.isDayOff == true

            val todayStatus = WorkDayStateResolver.resolveStatus(
                workDate = currentToday,
                today = currentToday,
                record = todayRec,
                schedule = todaySchedule,
                isDayOffOverride = isDayOffToday
            )

            val overnight = WorkDayStateResolver.findOvernightOpenShift(openShifts, currentToday)

            val resolver = ScheduleResolver(clock, clock.zone)
            val upcoming = if (todayStatus == WorkDayStatus.ON_BREAK && todayRec != null) {
                val openBreak = todayRec.breaks.lastOrNull { it.endEpochMs == null }
                if (openBreak != null) {
                    resolver.resolveBreakEndAlarms(
                        workDate = currentToday,
                        breakStartEpochMs = openBreak.startEpochMs,
                        schedule = todaySchedule,
                        config = reminders[ReminderEventType.BREAK_END]
                    )
                } else emptyList()
            } else {
                resolver.resolveUpcomingScheduledAlarms(
                    schedulesByDay = schedules,
                    reminderConfigs = reminders,
                    dayOffOverrides = overrideSet,
                    recordsByDate = recordsMap
                )
            }
            val nextTrigger = upcoming.minByOrNull { it.triggerEpochMs }
            val nextLabel = nextTrigger?.let { trigger ->
                val cfg = reminders[trigger.eventType]
                if (trigger.stage == 1) "Gentle notif" else when (cfg?.stage2Mode) {
                    com.personal.workhours.domain.model.EscalationMode.VIBRATE_ONLY -> "Vibration alert"
                    com.personal.workhours.domain.model.EscalationMode.SOUND_AND_VIBRATE -> "Sound + Vibrate"
                    com.personal.workhours.domain.model.EscalationMode.FULL_ALARM -> "Full Alarm"
                    null -> "Alert"
                }
            }.orEmpty()

            val weekStart = currentToday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekDates = (0L..6L).map { weekStart.plusDays(it) }
            val (weekPoints, weekStats) = buildChartSeries(weekDates, recordsMap, schedules, overrideSet, currentToday, trackingStart, nowMs, isMonth = false)

            val monthDates = (0L until currentToday.lengthOfMonth().toLong()).map {
                currentToday.withDayOfMonth(1).plusDays(it)
            }
            val (monthPoints, monthStats) = buildChartSeries(monthDates, recordsMap, schedules, overrideSet, currentToday, trackingStart, nowMs, isMonth = true)

            val weeklyProgress = computeWeeklyProgress(
                weekStart = weekStart,
                weekDates = weekDates,
                weekStats = weekStats,
                recordsMap = recordsMap,
                schedules = schedules,
                overrides = overrideSet,
                currentToday = currentToday,
                nowMs = nowMs
            )
            val baseMonthNetMsWithoutToday = computeBaseNetWithoutToday(
                dates = monthDates,
                recordsMap = recordsMap,
                schedules = schedules,
                overrides = overrideSet,
                currentToday = currentToday,
                nowMs = nowMs
            )

            DashboardUiState(
                todayDate = currentToday,
                todayStatus = todayStatus,
                todayRecord = todayRec,
                todaySchedule = todaySchedule,
                overnightOpenShift = overnight,
                nextAlert = nextTrigger,
                nextAlertModeLabel = nextLabel,
                selectedPeriod = period,
                weeklyProgress = weeklyProgress,
                baseMonthNetMsWithoutToday = baseMonthNetMsWithoutToday,
                weekPoints = weekPoints,
                weekStats = weekStats,
                monthPoints = monthPoints,
                monthStats = monthStats,
                allSchedules = schedules,
                allRecordsByDate = recordsMap,
                isDayOffToday = isDayOffToday
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(todayDate = today)
    )

    private fun buildChartSeries(
        dates: List<LocalDate>,
        recordsMap: Map<String, WorkDayRecord>,
        schedules: Map<Int, DaySchedule>,
        overrides: Set<String>,
        currentToday: LocalDate,
        trackingStart: LocalDate?,
        nowMs: Long,
        isMonth: Boolean
    ): Pair<List<DailyChartPoint>, PeriodStats> {
        val points = mutableListOf<DailyChartPoint>()
        val summaryPairs = mutableListOf<Pair<WorkDayStatus, WorkDurationSummary>>()
        val effectiveStart = trackingStart ?: currentToday

        for (date in dates) {
            val dateStr = date.toString()
            val rec = recordsMap[dateStr]
            val sched = schedules[date.dayOfWeek.value]
            val isOverride = overrides.contains(dateStr) || rec?.isDayOff == true
            val rawStatus = WorkDayStateResolver.resolveStatus(date, currentToday, rec, sched, isOverride)
            val status = if (rec == null && !isOverride && date.isBefore(effectiveStart) && rawStatus == WorkDayStatus.MISSED) {
                WorkDayStatus.NOT_STARTED
            } else {
                rawStatus
            }
            val summary = WorkDurationCalculator.calculateDaySummary(rec, sched, nowMs, isOverride)

            val label = if (isMonth) {
                date.dayOfMonth.toString()
            } else {
                date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US)
            }
            points.add(
                DailyChartPoint(
                    workDate = dateStr,
                    shortLabel = label,
                    netHours = (summary.netWorkMs / 3_600_000f),
                    targetHours = (summary.targetMs / 3_600_000f),
                    breakMinutes = summary.effectiveBreakMs / 60_000L,
                    status = status
                )
            )
            val countInStats = rec != null || isOverride || (!date.isAfter(currentToday) && !date.isBefore(effectiveStart))
            if (countInStats) {
                summaryPairs.add(status to summary)
            }
        }

        return points to WorkDurationCalculator.aggregatePeriod(summaryPairs)
    }

    private fun computeWeeklyProgress(
        weekStart: LocalDate,
        weekDates: List<LocalDate>,
        weekStats: PeriodStats,
        recordsMap: Map<String, WorkDayRecord>,
        schedules: Map<Int, DaySchedule>,
        overrides: Set<String>,
        currentToday: LocalDate,
        nowMs: Long
    ): WeeklyProgressSummary {
        var baseWeekNetMsWithoutToday = 0L
        var fullWeekTargetMs = 0L
        var scheduledWorkdaysCount = 0
        var workedDaysCount = 0

        for (date in weekDates) {
            val dateStr = date.toString()
            val rec = recordsMap[dateStr]
            val sched = schedules[date.dayOfWeek.value]
            val isOverride = overrides.contains(dateStr) || rec?.isDayOff == true
            val status = WorkDayStateResolver.resolveStatus(date, currentToday, rec, sched, isOverride)
            val summary = WorkDurationCalculator.calculateDaySummary(rec, sched, nowMs, isOverride)

            if (date != currentToday) {
                baseWeekNetMsWithoutToday += summary.netWorkMs
            }
            if (sched?.isWorkDay == true && !isOverride) {
                scheduledWorkdaysCount++
                fullWeekTargetMs += sched.targetWorkMinutes * 60_000L
            }
            if (status == WorkDayStatus.COMPLETED ||
                status == WorkDayStatus.CLOCKED_IN ||
                status == WorkDayStatus.ON_BREAK ||
                summary.netWorkMs > 0L
            ) {
                workedDaysCount++
            }
        }

        return WeeklyProgressSummary(
            weekStart = weekStart,
            weekEnd = weekStart.plusDays(6),
            baseWeekNetMsWithoutToday = baseWeekNetMsWithoutToday,
            fullWeekTargetMs = fullWeekTargetMs,
            paceTargetToDateMs = weekStats.totalTargetMs,
            scheduledWorkdaysCount = scheduledWorkdaysCount,
            workedDaysCount = workedDaysCount
        )
    }

    private fun computeBaseNetWithoutToday(
        dates: List<LocalDate>,
        recordsMap: Map<String, WorkDayRecord>,
        schedules: Map<Int, DaySchedule>,
        overrides: Set<String>,
        currentToday: LocalDate,
        nowMs: Long
    ): Long {
        var total = 0L
        for (date in dates) {
            if (date == currentToday) continue
            val dateStr = date.toString()
            val rec = recordsMap[dateStr]
            val sched = schedules[date.dayOfWeek.value]
            val isOverride = overrides.contains(dateStr) || rec?.isDayOff == true
            val summary = WorkDurationCalculator.calculateDaySummary(rec, sched, nowMs, isOverride)
            total += summary.netWorkMs
        }
        return total
    }

    fun selectPeriod(period: ChartPeriod) {
        selectedPeriod.value = period
    }

    fun clockIn() {
        viewModelScope.launch { workLogRepository.clockIn(today) }
    }

    fun startBreak() {
        viewModelScope.launch { workLogRepository.startBreak(today) }
    }

    fun resumeWork() {
        viewModelScope.launch { workLogRepository.resumeWork(today) }
    }

    fun clockOut() {
        viewModelScope.launch { workLogRepository.clockOut(today) }
    }

    fun toggleSkipToday() {
        viewModelScope.launch {
            if (uiState.value.isDayOffToday) {
                workLogRepository.unskipToday(today)
            } else {
                workLogRepository.skipToday(today)
            }
        }
    }

    fun saveDayEdit(
        workDate: LocalDate,
        isDayOff: Boolean,
        clockInEpochMs: Long?,
        clockOutEpochMs: Long?,
        breaks: List<com.personal.workhours.domain.model.BreakRecord>,
        note: String,
        onResult: (com.personal.workhours.domain.model.EditValidationResult) -> Unit
    ) {
        viewModelScope.launch {
            val autoDeduct = if (preferencesRepository.isAutoDeductBreakEnabled()) {
                uiState.value.allSchedules[workDate.dayOfWeek.value]?.defaultBreakMinutes ?: 60
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

    class Factory(
        private val workLogRepository: WorkLogRepository,
        private val scheduleRepository: ScheduleRepository,
        private val preferencesRepository: PreferencesRepository,
        private val clock: Clock = Clock.systemDefaultZone()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(workLogRepository, scheduleRepository, preferencesRepository, clock) as T
        }
    }
}
