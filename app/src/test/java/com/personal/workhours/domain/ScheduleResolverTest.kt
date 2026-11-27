package com.personal.workhours.domain

import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.WorkDayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleResolverTest {

    private val zone = ZoneId.of("Asia/Manila")

    private fun defaultSchedules(): Map<Int, DaySchedule> = (1..7).associateWith { dow ->
        DaySchedule(
            dayOfWeek = dow,
            isWorkDay = dow in 1..5,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 17 * 60,
            defaultBreakMinutes = 60,
            targetWorkMinutes = 480
        )
    }

    private fun defaultReminders(): Map<ReminderEventType, ReminderConfig> = mapOf(
        ReminderEventType.TIME_IN to ReminderConfig(ReminderEventType.TIME_IN, enabled = true, stage1LeadMinutes = 0, stage2Enabled = true, stage2DelayMinutes = 10),
        ReminderEventType.BREAK_END to ReminderConfig(ReminderEventType.BREAK_END, enabled = true, stage1LeadMinutes = 5, stage2Enabled = true, stage2DelayMinutes = 5),
        ReminderEventType.TIME_OUT to ReminderConfig(ReminderEventType.TIME_OUT, enabled = true, stage1LeadMinutes = 0, stage2Enabled = true, stage2DelayMinutes = 10)
    )

    @Test
    fun `early clock in skips today TIME_IN and schedules tomorrow TIME_IN`() {
        val now = ZonedDateTime.of(2026, 11, 25, 7, 50, 0, 0, zone).toInstant() // Wednesday
        val resolver = ScheduleResolver(Clock.fixed(now, zone), zone)

        val records = mapOf(
            "2026-11-25" to WorkDayRecord(workDate = "2026-11-25", clockInEpochMs = now.toEpochMilli())
        )
        val specs = resolver.resolveUpcomingScheduledAlarms(
            schedulesByDay = defaultSchedules(),
            reminderConfigs = defaultReminders(),
            dayOffOverrides = emptySet(),
            recordsByDate = records
        )

        val timeInSpecs = specs.filter { it.eventType == ReminderEventType.TIME_IN }
        assertEquals(2, timeInSpecs.size)
        assertTrue(timeInSpecs.all { it.workDate == "2026-11-26" })
    }

    @Test
    fun `day off override skips holiday and picks next workday`() {
        val now = ZonedDateTime.of(2026, 11, 27, 7, 0, 0, 0, zone).toInstant() // Friday
        val resolver = ScheduleResolver(Clock.fixed(now, zone), zone)

        val specs = resolver.resolveUpcomingScheduledAlarms(
            schedulesByDay = defaultSchedules(),
            reminderConfigs = defaultReminders(),
            dayOffOverrides = setOf("2026-11-27"),
            recordsByDate = emptyMap()
        )

        val timeInSpecs = specs.filter { it.eventType == ReminderEventType.TIME_IN }
        assertTrue(timeInSpecs.all { it.workDate == "2026-11-30" }) // Skips Fri + Sat + Sun -> Monday
    }

    @Test
    fun `resolveBreakEndAlarms computes stage 1 and stage 2 around break duration`() {
        val breakStart = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant()
        val resolver = ScheduleResolver(Clock.fixed(breakStart, zone), zone)

        val specs = resolver.resolveBreakEndAlarms(
            workDate = LocalDate.of(2026, 11, 25),
            breakStartEpochMs = breakStart.toEpochMilli(),
            schedule = defaultSchedules()[3],
            config = defaultReminders()[ReminderEventType.BREAK_END]
        )

        assertEquals(2, specs.size)
        assertEquals(breakStart.toEpochMilli() + 55 * 60_000L, specs[0].triggerEpochMs)
        assertEquals(breakStart.toEpochMilli() + 65 * 60_000L, specs[1].triggerEpochMs)
    }

    @Test
    fun `isEventStillNeeded returns false once punched`() {
        val now = ZonedDateTime.of(2026, 11, 25, 8, 10, 0, 0, zone).toInstant()
        val resolver = ScheduleResolver(Clock.fixed(now, zone), zone)
        val today = LocalDate.of(2026, 11, 25)
        val clockedInRecord = WorkDayRecord(workDate = "2026-11-25", clockInEpochMs = now.toEpochMilli() - 60_000L)

        assertFalse(
            resolver.isEventStillNeeded(
                ReminderEventType.TIME_IN,
                today,
                today,
                clockedInRecord,
                defaultSchedules()[3],
                false
            )
        )
        assertTrue(
            resolver.isEventStillNeeded(
                ReminderEventType.TIME_OUT,
                today,
                today,
                clockedInRecord,
                defaultSchedules()[3],
                false
            )
        )
    }

    @Test
    fun `early stage 1 fires before target and on-time stage 2 fires at target without duplicate`() {
        val targetMs = ZonedDateTime.of(2026, 11, 25, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val cfgEarlyAndOnTime = mapOf(
            ReminderEventType.TIME_IN to ReminderConfig(
                eventType = ReminderEventType.TIME_IN,
                enabled = true,
                stage1LeadMinutes = 5,
                stage2Enabled = true,
                stage2DelayMinutes = 0
            )
        )

        // 10m before: Stage 1 at 7:55, Stage 2 at 8:00
        val at750 = ZonedDateTime.of(2026, 11, 25, 7, 50, 0, 0, zone).toInstant()
        val specs750 = ScheduleResolver(Clock.fixed(at750, zone), zone).resolveUpcomingScheduledAlarms(
            schedulesByDay = defaultSchedules(),
            reminderConfigs = cfgEarlyAndOnTime,
            dayOffOverrides = emptySet(),
            recordsByDate = emptyMap()
        )
        assertEquals(2, specs750.size)
        assertEquals(1, specs750[0].stage)
        assertEquals(targetMs - 5 * 60_000L, specs750[0].triggerEpochMs)
        assertEquals(2, specs750[1].stage)
        assertEquals(targetMs, specs750[1].triggerEpochMs)

        // 3m before: Stage 1 is past, Stage 2 still fires at 8:00 sharp
        val at757 = ZonedDateTime.of(2026, 11, 25, 7, 57, 0, 0, zone).toInstant()
        val specs757 = ScheduleResolver(Clock.fixed(at757, zone), zone).resolveUpcomingScheduledAlarms(
            schedulesByDay = defaultSchedules(),
            reminderConfigs = cfgEarlyAndOnTime,
            dayOffOverrides = emptySet(),
            recordsByDate = emptyMap()
        )
        assertEquals(1, specs757.size)
        assertEquals(2, specs757[0].stage)
        assertEquals(targetMs, specs757[0].triggerEpochMs)

        // Both lead=0 and delay=0: no redundant Stage 1 at the exact same timestamp as Stage 2
        val cfgNoLeadOnTime = mapOf(
            ReminderEventType.TIME_IN to ReminderConfig(
                eventType = ReminderEventType.TIME_IN,
                enabled = true,
                stage1LeadMinutes = 0,
                stage2Enabled = true,
                stage2DelayMinutes = 0
            )
        )
        val specsNoDup = ScheduleResolver(Clock.fixed(at750, zone), zone).resolveUpcomingScheduledAlarms(
            schedulesByDay = defaultSchedules(),
            reminderConfigs = cfgNoLeadOnTime,
            dayOffOverrides = emptySet(),
            recordsByDate = emptyMap()
        )
        assertEquals(1, specsNoDup.size)
        assertEquals(2, specsNoDup[0].stage)
        assertEquals(targetMs, specsNoDup[0].triggerEpochMs)
    }

    @Test
    fun `break start schedules early heads-up and on-time trigger when clocked in with no break`() {
        val configs = mapOf(
            ReminderEventType.BREAK_START to ReminderConfig(
                eventType = ReminderEventType.BREAK_START,
                enabled = true,
                stage1LeadMinutes = 5,
                stage2Enabled = true,
                stage2DelayMinutes = 0
            )
        )
        val at1150 = ZonedDateTime.of(2026, 11, 25, 11, 50, 0, 0, zone).toInstant()
        val noonMs = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

        val specs = ScheduleResolver(Clock.fixed(at1150, zone), zone).resolveUpcomingScheduledAlarms(
            schedulesByDay = defaultSchedules(),
            reminderConfigs = configs,
            dayOffOverrides = emptySet(),
            recordsByDate = mapOf(
                "2026-11-25" to WorkDayRecord(
                    workDate = "2026-11-25",
                    clockInEpochMs = ZonedDateTime.of(2026, 11, 25, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
                )
            )
        )
        assertEquals(2, specs.size)
        assertEquals(ReminderEventType.BREAK_START, specs[0].eventType)
        assertEquals(noonMs - 5 * 60_000L, specs[0].triggerEpochMs)
        assertEquals(noonMs, specs[1].triggerEpochMs)

        val today = LocalDate.of(2026, 11, 25)
        val clockedInOnly = WorkDayRecord(workDate = "2026-11-25", clockInEpochMs = noonMs - 4 * 3_600_000L)
        assertTrue(
            ScheduleResolver(Clock.fixed(at1150, zone), zone).isEventStillNeeded(
                ReminderEventType.BREAK_START,
                today,
                today,
                clockedInOnly,
                defaultSchedules()[3],
                false
            )
        )
        val withBreak = clockedInOnly.copy(
            breaks = listOf(com.personal.workhours.domain.model.BreakRecord(startEpochMs = noonMs))
        )
        assertFalse(
            ScheduleResolver(Clock.fixed(at1150, zone), zone).isEventStillNeeded(
                ReminderEventType.BREAK_START,
                today,
                today,
                withBreak,
                defaultSchedules()[3],
                false
            )
        )
    }
}
