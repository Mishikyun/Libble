package com.personal.workhours.domain

import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkDurationCalculatorTest {

    private val weekdaySchedule = DaySchedule(
        dayOfWeek = 1,
        isWorkDay = true,
        startMinuteOfDay = 480,
        endMinuteOfDay = 1020,
        defaultBreakMinutes = 60,
        targetWorkMinutes = 480
    )

    @Test
    fun `calculateDaySummary subtracts manual breaks accurately`() {
        val t0 = 1_700_000_000_000L
        val record = WorkDayRecord(
            workDate = "2026-11-25",
            clockInEpochMs = t0,
            clockOutEpochMs = t0 + 9 * 3_600_000L, // 9h gross
            breaks = listOf(
                BreakRecord(startEpochMs = t0 + 4 * 3_600_000L, endEpochMs = t0 + 5 * 3_600_000L) // 1h break
            )
        )

        val summary = WorkDurationCalculator.calculateDaySummary(record, weekdaySchedule, t0)
        assertEquals(9 * 3_600_000L, summary.grossMs)
        assertEquals(3_600_000L, summary.effectiveBreakMs)
        assertEquals(8 * 3_600_000L, summary.netWorkMs)
        assertEquals(0L, summary.deltaMs)
    }

    @Test
    fun `calculateDaySummary uses autoDeductBreakMinutes when completed with zero manual breaks`() {
        val t0 = 1_700_000_000_000L
        val record = WorkDayRecord(
            workDate = "2026-11-25",
            clockInEpochMs = t0,
            clockOutEpochMs = t0 + 9 * 3_600_000L,
            autoDeductBreakMinutes = 60,
            breaks = emptyList()
        )

        val summary = WorkDurationCalculator.calculateDaySummary(record, weekdaySchedule, t0)
        assertEquals(3_600_000L, summary.effectiveBreakMs)
        assertEquals(8 * 3_600_000L, summary.netWorkMs)
    }

    @Test
    fun `calculateDaySummary clamps netWorkMs to zero if break exceeds gross`() {
        val t0 = 1_700_000_000_000L
        val record = WorkDayRecord(
            workDate = "2026-11-25",
            clockInEpochMs = t0,
            clockOutEpochMs = t0 + 30 * 60_000L,
            autoDeductBreakMinutes = 60
        )

        val summary = WorkDurationCalculator.calculateDaySummary(record, weekdaySchedule, t0)
        assertEquals(0L, summary.netWorkMs)
    }

    @Test
    fun `aggregatePeriod excludes day off target hours`() {
        val t0 = 1_700_000_000_000L
        val worked = WorkDurationCalculator.calculateDaySummary(
            WorkDayRecord(workDate = "2026-11-23", clockInEpochMs = t0, clockOutEpochMs = t0 + 8 * 3_600_000L),
            weekdaySchedule,
            t0
        )
        val dayOff = WorkDurationCalculator.calculateDaySummary(
            WorkDayRecord(workDate = "2026-11-24", isDayOff = true),
            weekdaySchedule,
            t0,
            isDayOffOverride = true
        )

        val stats = WorkDurationCalculator.aggregatePeriod(
            listOf(
                WorkDayStatus.COMPLETED to worked,
                WorkDayStatus.DAY_OFF to dayOff
            )
        )
        assertEquals(1, stats.workedDaysCount)
        assertEquals(8 * 3_600_000L, stats.totalNetMs)
        assertEquals(8 * 3_600_000L, stats.totalTargetMs)
        assertEquals(0L, stats.deltaMs)
    }
}
