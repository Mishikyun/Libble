package com.personal.workhours.domain

import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.EditValidationResult
import org.junit.Assert.assertTrue
import org.junit.Test

class EditWorkDayValidatorTest {

    @Test
    fun `rejects clock out before or equal clock in`() {
        val res = EditWorkDayValidator.validate(
            isDayOff = false,
            clockInEpochMs = 10_000L,
            clockOutEpochMs = 10_000L,
            breaks = emptyList(),
            note = ""
        )
        assertTrue(res is EditValidationResult.Invalid)
    }

    @Test
    fun `rejects overlapping break intervals`() {
        val res = EditWorkDayValidator.validate(
            isDayOff = false,
            clockInEpochMs = 10_000L,
            clockOutEpochMs = 100_000L,
            breaks = listOf(
                BreakRecord(startEpochMs = 20_000L, endEpochMs = 40_000L),
                BreakRecord(startEpochMs = 35_000L, endEpochMs = 50_000L)
            ),
            note = ""
        )
        assertTrue(res is EditValidationResult.Invalid)
    }

    @Test
    fun `rejects break extending beyond clock out`() {
        val res = EditWorkDayValidator.validate(
            isDayOff = false,
            clockInEpochMs = 10_000L,
            clockOutEpochMs = 50_000L,
            breaks = listOf(BreakRecord(startEpochMs = 20_000L, endEpochMs = 60_000L)),
            note = ""
        )
        assertTrue(res is EditValidationResult.Invalid)
    }

    @Test
    fun `accepts valid shift with multiple non-overlapping breaks`() {
        val res = EditWorkDayValidator.validate(
            isDayOff = false,
            clockInEpochMs = 10_000L,
            clockOutEpochMs = 100_000L,
            breaks = listOf(
                BreakRecord(startEpochMs = 20_000L, endEpochMs = 30_000L),
                BreakRecord(startEpochMs = 40_000L, endEpochMs = 50_000L)
            ),
            note = "Normal day"
        )
        assertTrue(res is EditValidationResult.Valid)
    }
}
