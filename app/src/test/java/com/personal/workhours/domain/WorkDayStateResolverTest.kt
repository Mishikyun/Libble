package com.personal.workhours.domain

import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkDayStateResolverTest {

    private val workSchedule = DaySchedule(1, true, 480, 1020, 60, 480)
    private val weekendSchedule = DaySchedule(6, false, 480, 1020, 60, 0)
    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun `resolves all 6 daily statuses deterministically`() {
        assertEquals(
            WorkDayStatus.NOT_STARTED,
            WorkDayStateResolver.resolveStatus(today, today, null, workSchedule, false)
        )
        assertEquals(
            WorkDayStatus.MISSED,
            WorkDayStateResolver.resolveStatus(today.minusDays(1), today, null, workSchedule, false)
        )
        assertEquals(
            WorkDayStatus.DAY_OFF,
            WorkDayStateResolver.resolveStatus(today, today, null, weekendSchedule, false)
        )
        assertEquals(
            WorkDayStatus.DAY_OFF,
            WorkDayStateResolver.resolveStatus(today, today, null, workSchedule, true)
        )
        assertEquals(
            WorkDayStatus.CLOCKED_IN,
            WorkDayStateResolver.resolveStatus(
                today,
                today,
                WorkDayRecord(workDate = today.toString(), clockInEpochMs = 1000L),
                workSchedule,
                false
            )
        )
        assertEquals(
            WorkDayStatus.ON_BREAK,
            WorkDayStateResolver.resolveStatus(
                today,
                today,
                WorkDayRecord(
                    workDate = today.toString(),
                    clockInEpochMs = 1000L,
                    breaks = listOf(BreakRecord(startEpochMs = 2000L, endEpochMs = null))
                ),
                workSchedule,
                false
            )
        )
        assertEquals(
            WorkDayStatus.COMPLETED,
            WorkDayStateResolver.resolveStatus(
                today,
                today,
                WorkDayRecord(workDate = today.toString(), clockInEpochMs = 1000L, clockOutEpochMs = 5000L),
                workSchedule,
                false
            )
        )
    }

    @Test
    fun `findOvernightOpenShift detects unclosed yesterday shift and ignores today`() {
        val yesterdayShift = WorkDayRecord(workDate = "2026-11-24", clockInEpochMs = 1000L, clockOutEpochMs = null)
        val todayShift = WorkDayRecord(workDate = "2026-11-25", clockInEpochMs = 2000L, clockOutEpochMs = null)

        val found = WorkDayStateResolver.findOvernightOpenShift(listOf(todayShift, yesterdayShift), today)
        assertEquals("2026-11-24", found?.workDate)

        val none = WorkDayStateResolver.findOvernightOpenShift(listOf(todayShift), today)
        assertNull(none)
    }

    @Test
    fun `shouldIncludeInHistory hides blank pre-start days and keeps logged or post-start missed days`() {
        val start = LocalDate.of(2026, 11, 25)
        val preStart = LocalDate.of(2026, 11, 15)
        val postStartMissed = LocalDate.of(2026, 11, 26)
        val futureToday = LocalDate.of(2026, 11, 27)

        assertFalse(
            WorkDayStateResolver.shouldIncludeInHistory(
                workDate = preStart,
                today = futureToday,
                trackingStartDate = start,
                hasRecord = false,
                isOverride = false,
                status = WorkDayStatus.MISSED
            )
        )
        assertTrue(
            WorkDayStateResolver.shouldIncludeInHistory(
                workDate = preStart,
                today = futureToday,
                trackingStartDate = start,
                hasRecord = true,
                isOverride = false,
                status = WorkDayStatus.COMPLETED
            )
        )
        assertTrue(
            WorkDayStateResolver.shouldIncludeInHistory(
                workDate = postStartMissed,
                today = futureToday,
                trackingStartDate = start,
                hasRecord = false,
                isOverride = false,
                status = WorkDayStatus.MISSED
            )
        )
    }
}
