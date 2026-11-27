package com.personal.workhours.domain

import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.PeriodStats
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import com.personal.workhours.domain.model.WorkDurationSummary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object WorkDurationCalculator {

    private const val MINUTE_MS = 60_000L

    fun calculateDaySummary(
        record: WorkDayRecord?,
        schedule: DaySchedule?,
        nowEpochMs: Long,
        isDayOffOverride: Boolean = false
    ): WorkDurationSummary {
        val targetMinutes = if (isDayOffOverride || record?.isDayOff == true || schedule?.isWorkDay == false) {
            0
        } else {
            schedule?.targetWorkMinutes ?: 480
        }
        val targetMs = targetMinutes * MINUTE_MS

        if (record == null || record.clockInEpochMs == null) {
            return WorkDurationSummary(
                grossMs = 0L,
                manualBreakMs = 0L,
                effectiveBreakMs = 0L,
                activeBreakMs = 0L,
                netWorkMs = 0L,
                targetMs = targetMs,
                deltaMs = -targetMs
            )
        }

        val effectiveEndMs = max(record.clockInEpochMs, record.clockOutEpochMs ?: nowEpochMs)
        val grossMs = max(0L, effectiveEndMs - record.clockInEpochMs)

        var manualBreakMs = 0L
        var activeBreakMs = 0L
        for (brk in record.breaks) {
            val brkEnd = max(brk.startEpochMs, brk.endEpochMs ?: nowEpochMs)
            val duration = max(0L, brkEnd - brk.startEpochMs)
            manualBreakMs += duration
            if (brk.endEpochMs == null) {
                activeBreakMs += duration
            }
        }

        val effectiveBreakMs = if (manualBreakMs > 0L) {
            manualBreakMs
        } else if (record.clockOutEpochMs != null && record.autoDeductBreakMinutes > 0) {
            record.autoDeductBreakMinutes * MINUTE_MS
        } else {
            0L
        }

        val netWorkMs = max(0L, grossMs - effectiveBreakMs)
        val deltaMs = netWorkMs - targetMs

        return WorkDurationSummary(
            grossMs = grossMs,
            manualBreakMs = manualBreakMs,
            effectiveBreakMs = effectiveBreakMs,
            activeBreakMs = activeBreakMs,
            netWorkMs = netWorkMs,
            targetMs = targetMs,
            deltaMs = deltaMs
        )
    }

    fun aggregatePeriod(
        daySummaries: List<Pair<WorkDayStatus, WorkDurationSummary>>
    ): PeriodStats {
        var totalNetMs = 0L
        var totalTargetMs = 0L
        var totalBreakMs = 0L
        var workedDays = 0

        for ((status, summary) in daySummaries) {
            totalNetMs += summary.netWorkMs
            totalBreakMs += summary.effectiveBreakMs
            if (status != WorkDayStatus.DAY_OFF) {
                totalTargetMs += summary.targetMs
            }
            if (summary.netWorkMs > 0L || status == WorkDayStatus.CLOCKED_IN || status == WorkDayStatus.ON_BREAK || status == WorkDayStatus.COMPLETED) {
                workedDays++
            }
        }

        val avgMs = if (workedDays > 0) totalNetMs / workedDays else 0L
        return PeriodStats(
            totalNetMs = totalNetMs,
            totalTargetMs = totalTargetMs,
            totalBreakMs = totalBreakMs,
            workedDaysCount = workedDays,
            averageNetMsPerWorkedDay = avgMs,
            deltaMs = totalNetMs - totalTargetMs
        )
    }

    fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = max(0L, durationMs) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationHm(durationMs: Long): String {
        val totalMinutes = max(0L, durationMs) / MINUTE_MS
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    fun formatSignedDeltaHm(deltaMs: Long): String {
        val prefix = if (deltaMs >= 0L) "+" else "-"
        return prefix + formatDurationHm(abs(deltaMs))
    }

    fun formatHoursDecimal(durationMs: Long): String {
        val hours = max(0L, durationMs).toDouble() / 3_600_000.0
        return String.format(Locale.US, "%.1fh", hours)
    }
}
