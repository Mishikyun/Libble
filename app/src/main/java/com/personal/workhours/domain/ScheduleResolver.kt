package com.personal.workhours.domain

import com.personal.workhours.domain.model.AlarmTriggerSpec
import com.personal.workhours.domain.model.DayOffOverride
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.WorkDayRecord
import com.personal.workhours.domain.model.WorkDayStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleResolver(
    private val clock: Clock,
    private val zoneId: ZoneId = clock.zone
) {

    fun resolveUpcomingScheduledAlarms(
        schedulesByDay: Map<Int, DaySchedule>,
        reminderConfigs: Map<ReminderEventType, ReminderConfig>,
        dayOffOverrides: Set<String>,
        recordsByDate: Map<String, WorkDayRecord>,
        lookaheadDays: Int = 7
    ): List<AlarmTriggerSpec> {
        val nowMs = clock.millis()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val overrideSet = dayOffOverrides + recordsByDate.filterValues { it.isDayOff }.keys
        val specs = mutableListOf<AlarmTriggerSpec>()

        findNextTriggerForEvent(
            eventType = ReminderEventType.TIME_IN,
            nowMs = nowMs,
            today = today,
            lookaheadDays = lookaheadDays,
            schedulesByDay = schedulesByDay,
            config = reminderConfigs[ReminderEventType.TIME_IN],
            overrideDates = overrideSet,
            recordsByDate = recordsByDate
        )?.let { specs.addAll(it) }

        findNextTriggerForEvent(
            eventType = ReminderEventType.BREAK_START,
            nowMs = nowMs,
            today = today,
            lookaheadDays = lookaheadDays,
            schedulesByDay = schedulesByDay,
            config = reminderConfigs[ReminderEventType.BREAK_START],
            overrideDates = overrideSet,
            recordsByDate = recordsByDate
        )?.let { specs.addAll(it) }

        findNextTriggerForEvent(
            eventType = ReminderEventType.TIME_OUT,
            nowMs = nowMs,
            today = today,
            lookaheadDays = lookaheadDays,
            schedulesByDay = schedulesByDay,
            config = reminderConfigs[ReminderEventType.TIME_OUT],
            overrideDates = overrideSet,
            recordsByDate = recordsByDate
        )?.let { specs.addAll(it) }

        return specs
    }

    fun resolveBreakEndAlarms(
        workDate: LocalDate,
        breakStartEpochMs: Long,
        schedule: DaySchedule?,
        config: ReminderConfig?
    ): List<AlarmTriggerSpec> {
        if (config == null || !config.enabled) return emptyList()
        val defaultBreakMinutes = (schedule?.defaultBreakMinutes ?: 60).coerceAtLeast(1)
        val targetEndMs = breakStartEpochMs + defaultBreakMinutes * 60_000L
        val nowMs = clock.millis()
        val dateStr = workDate.toString()

        val result = mutableListOf<AlarmTriggerSpec>()
        val stage1Ms = targetEndMs - config.stage1LeadMinutes.coerceAtLeast(0) * 60_000L
        val stage2Ms = targetEndMs + config.stage2DelayMinutes.coerceAtLeast(0) * 60_000L
        val skipRedundantStage1 = config.stage2Enabled && stage1Ms == stage2Ms
        if (!skipRedundantStage1 && stage1Ms > nowMs) {
            result.add(
                AlarmTriggerSpec(
                    eventType = ReminderEventType.BREAK_END,
                    stage = 1,
                    workDate = dateStr,
                    scheduledTargetEpochMs = targetEndMs,
                    triggerEpochMs = stage1Ms
                )
            )
        }
        if (config.stage2Enabled && stage2Ms > nowMs) {
            result.add(
                AlarmTriggerSpec(
                    eventType = ReminderEventType.BREAK_END,
                    stage = 2,
                    workDate = dateStr,
                    scheduledTargetEpochMs = targetEndMs,
                    triggerEpochMs = stage2Ms
                )
            )
        }
        return result
    }

    fun isEventStillNeeded(
        eventType: ReminderEventType,
        workDate: LocalDate,
        today: LocalDate,
        record: WorkDayRecord?,
        schedule: DaySchedule?,
        isDayOffOverride: Boolean
    ): Boolean {
        val status = WorkDayStateResolver.resolveStatus(
            workDate = workDate,
            today = today,
            record = record,
            schedule = schedule,
            isDayOffOverride = isDayOffOverride
        )
        return when (eventType) {
            ReminderEventType.TIME_IN -> status == WorkDayStatus.NOT_STARTED
            ReminderEventType.BREAK_START -> status == WorkDayStatus.CLOCKED_IN && record?.breaks.isNullOrEmpty()
            ReminderEventType.BREAK_END -> status == WorkDayStatus.ON_BREAK
            ReminderEventType.TIME_OUT -> status == WorkDayStatus.CLOCKED_IN || status == WorkDayStatus.ON_BREAK
        }
    }

    private fun findNextTriggerForEvent(
        eventType: ReminderEventType,
        nowMs: Long,
        today: LocalDate,
        lookaheadDays: Int,
        schedulesByDay: Map<Int, DaySchedule>,
        config: ReminderConfig?,
        overrideDates: Set<String>,
        recordsByDate: Map<String, WorkDayRecord>
    ): List<AlarmTriggerSpec>? {
        if (config == null || !config.enabled) return null

        for (offset in 0..lookaheadDays) {
            val candidateDate = today.plusDays(offset.toLong())
            val dateStr = candidateDate.toString()
            if (overrideDates.contains(dateStr)) continue

            val schedule = schedulesByDay[candidateDate.dayOfWeek.value] ?: continue
            if (!schedule.isWorkDay) continue

            val record = recordsByDate[dateStr]
            if (eventType == ReminderEventType.TIME_IN && record?.clockInEpochMs != null) {
                continue
            }
            if (eventType == ReminderEventType.BREAK_START) {
                if (schedule.defaultBreakMinutes <= 0 || schedule.breakStartMinuteOfDay == null) continue
                if (record?.clockOutEpochMs != null || !record?.breaks.isNullOrEmpty()) continue
            }
            if (eventType == ReminderEventType.TIME_OUT && record?.clockOutEpochMs != null) {
                continue
            }

            val minuteOfDay = when (eventType) {
                ReminderEventType.TIME_IN -> schedule.startMinuteOfDay
                ReminderEventType.BREAK_START -> schedule.breakStartMinuteOfDay ?: continue
                ReminderEventType.TIME_OUT -> schedule.endMinuteOfDay
                ReminderEventType.BREAK_END -> continue
            }
            val targetTime = LocalTime.of(
                (minuteOfDay / 60).coerceIn(0, 23),
                (minuteOfDay % 60).coerceIn(0, 59)
            )
            val targetMs = ZonedDateTime.of(candidateDate, targetTime, zoneId).toInstant().toEpochMilli()
            val stage1Ms = targetMs - config.stage1LeadMinutes.coerceAtLeast(0) * 60_000L
            val stage2Ms = targetMs + config.stage2DelayMinutes.coerceAtLeast(0) * 60_000L
            val skipRedundantStage1 = config.stage2Enabled && stage1Ms == stage2Ms

            val list = mutableListOf<AlarmTriggerSpec>()
            if (!skipRedundantStage1 && stage1Ms > nowMs) {
                list.add(
                    AlarmTriggerSpec(
                        eventType = eventType,
                        stage = 1,
                        workDate = dateStr,
                        scheduledTargetEpochMs = targetMs,
                        triggerEpochMs = stage1Ms
                    )
                )
            }
            if (config.stage2Enabled && stage2Ms > nowMs) {
                list.add(
                    AlarmTriggerSpec(
                        eventType = eventType,
                        stage = 2,
                        workDate = dateStr,
                        scheduledTargetEpochMs = targetMs,
                        triggerEpochMs = stage2Ms
                    )
                )
            }
            if (list.isNotEmpty()) {
                return list
            }
        }
        return null
    }
}
