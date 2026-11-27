package com.personal.workhours.domain

import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import java.time.LocalDate

object WorkDayStateResolver {

    fun resolveStatus(
        workDate: LocalDate,
        today: LocalDate,
        record: WorkDayRecord?,
        schedule: DaySchedule?,
        isDayOffOverride: Boolean
    ): WorkDayStatus {
        if (record != null) {
            if (record.isDayOff || isDayOffOverride) {
                return WorkDayStatus.DAY_OFF
            }
            if (record.clockInEpochMs != null) {
                return when {
                    record.clockOutEpochMs != null -> WorkDayStatus.COMPLETED
                    record.breaks.any { it.endEpochMs == null } -> WorkDayStatus.ON_BREAK
                    else -> WorkDayStatus.CLOCKED_IN
                }
            }
        }

        if (isDayOffOverride || schedule?.isWorkDay == false) {
            return WorkDayStatus.DAY_OFF
        }

        return if (workDate.isBefore(today)) {
            WorkDayStatus.MISSED
        } else {
            WorkDayStatus.NOT_STARTED
        }
    }

    fun shouldIncludeInHistory(
        workDate: LocalDate,
        today: LocalDate,
        trackingStartDate: LocalDate?,
        hasRecord: Boolean,
        isOverride: Boolean,
        status: WorkDayStatus
    ): Boolean {
        if (hasRecord || isOverride) return true
        val effectiveStart = trackingStartDate ?: today
        if (workDate.isBefore(effectiveStart)) return false
        return status == WorkDayStatus.MISSED || status == WorkDayStatus.NOT_STARTED
    }

    fun findOvernightOpenShift(
        openShifts: List<WorkDayRecord>,
        today: LocalDate
    ): WorkDayRecord? {
        return openShifts
            .filter { shift ->
                shift.clockInEpochMs != null &&
                    shift.clockOutEpochMs == null &&
                    !shift.isDayOff &&
                    runCatching { LocalDate.parse(shift.workDate).isBefore(today) }.getOrDefault(false)
            }
            .maxByOrNull { it.workDate }
    }
}
