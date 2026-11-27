package com.personal.workhours.domain.export

import com.personal.workhours.domain.WorkDurationCalculator
import com.personal.workhours.domain.WorkDayStateResolver
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.WorkDayRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun export(
        records: List<WorkDayRecord>,
        schedulesByDay: Map<Int, DaySchedule>,
        dayOffOverrides: Set<String>,
        today: LocalDate,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Status,ClockIn,ClockOut,BreakMinutes,NetWorkedHours,TargetHours,DeltaHours,Note")

        val sorted = records.sortedByDescending { it.workDate }
        for (record in sorted) {
            val workDate = runCatching { LocalDate.parse(record.workDate) }.getOrNull() ?: continue
            val schedule = schedulesByDay[workDate.dayOfWeek.value]
            val isOverride = dayOffOverrides.contains(record.workDate)
            val status = WorkDayStateResolver.resolveStatus(workDate, today, record, schedule, isOverride)
            val summary = WorkDurationCalculator.calculateDaySummary(record, schedule, nowEpochMs, isOverride)

            val clockInStr = record.clockInEpochMs?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)
            }.orEmpty()
            val clockOutStr = record.clockOutEpochMs?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().format(timeFormatter)
            }.orEmpty()

            val breakMinutes = summary.effectiveBreakMs / 60_000L
            val netHours = String.format(Locale.US, "%.2f", summary.netWorkMs / 3_600_000.0)
            val targetHours = String.format(Locale.US, "%.2f", summary.targetMs / 3_600_000.0)
            val deltaHours = String.format(Locale.US, "%.2f", summary.deltaMs / 3_600_000.0)

            val row = listOf(
                escapeCsvCell(record.workDate, neutralizeSign = true),
                escapeCsvCell(status.name, neutralizeSign = true),
                escapeCsvCell(clockInStr, neutralizeSign = true),
                escapeCsvCell(clockOutStr, neutralizeSign = true),
                breakMinutes.toString(),
                netHours,
                targetHours,
                deltaHours,
                escapeCsvCell(record.note, neutralizeSign = true)
            ).joinToString(",")
            sb.appendLine(row)
        }

        return sb.toString()
    }

    internal fun escapeCsvCell(raw: String, neutralizeSign: Boolean): String {
        val trimmed = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val safe = if (neutralizeSign && trimmed.isNotEmpty() && trimmed[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$trimmed"
        } else {
            trimmed
        }
        val needsQuotes = safe.contains(',') || safe.contains('"') || safe.contains('\n') || safe.contains('\r')
        return if (needsQuotes) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
