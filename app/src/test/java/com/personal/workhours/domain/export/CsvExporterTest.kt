package com.personal.workhours.domain.export

import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.WorkDayRecord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CsvExporterTest {

    private val zone = ZoneId.of("Asia/Manila")

    @Test
    fun `neutralizes formula injection and escapes commas and quotes`() {
        val inMs = ZonedDateTime.of(2026, 11, 25, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val outMs = ZonedDateTime.of(2026, 11, 25, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
        val records = listOf(
            WorkDayRecord(
                workDate = "2026-11-25",
                clockInEpochMs = inMs,
                clockOutEpochMs = outMs,
                autoDeductBreakMinutes = 60,
                note = "=CMD(\"calc\"), \"test\""
            )
        )
        val schedules = mapOf(
            3 to DaySchedule(3, true, 480, 1020, 60, 480)
        )

        val csv = CsvExporter.export(
            records = records,
            schedulesByDay = schedules,
            dayOffOverrides = emptySet(),
            today = LocalDate.of(2026, 11, 25),
            nowEpochMs = outMs,
            zoneId = zone
        )

        assertTrue(csv.contains("Date,Status,ClockIn,ClockOut,BreakMinutes,NetWorkedHours,TargetHours,DeltaHours,Note"))
        assertTrue(csv.contains("\"'=CMD(\"\"calc\"\"), \"\"test\"\"\""))
    }
}
