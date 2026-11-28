package com.personal.workhours.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.workhours.MainActivity
import com.personal.workhours.data.local.WorkHoursDatabase
import com.personal.workhours.data.local.toDomain
import com.personal.workhours.domain.ScheduleResolver
import com.personal.workhours.domain.model.AlarmTriggerSpec
import com.personal.workhours.domain.model.ReminderEventType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class AlarmScheduler(
    private val context: Context,
    private val db: WorkHoursDatabase,
    private val notificationManager: AlarmNotificationManager,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun reconcileAll() {
        db.ensureDefaultSeedData()
        val schedules = db.scheduleDao().getDaySchedules().associate { it.dayOfWeek to it.toDomain() }
        val configs = db.scheduleDao().getReminderConfigs().map { it.toDomain() }.associateBy { it.eventType }
        val overrides = db.scheduleDao().getDayOffOverrides().map { it.workDate }.toSet()

        val nowMs = clock.millis()
        val today = Instant.ofEpochMilli(nowMs).atZone(clock.zone).toLocalDate()
        val rangeRecords = db.workLogDao()
            .getWorkDaysInRange(today.toString(), today.plusDays(7).toString())
            .map { it.toDomain() }
            .associateBy { it.workDate }

        cancelEvent(ReminderEventType.TIME_IN)
        cancelEvent(ReminderEventType.BREAK_START)
        cancelEvent(ReminderEventType.TIME_OUT)

        val resolver = ScheduleResolver(clock, clock.zone)
        val specs = resolver.resolveUpcomingScheduledAlarms(
            schedulesByDay = schedules,
            reminderConfigs = configs,
            dayOffOverrides = overrides,
            recordsByDate = rangeRecords
        )
        specs.forEach { scheduleSpec(it) }

        // If user is currently on break (e.g., after reboot), re-arm BREAK_END
        val todayRec = rangeRecords[today.toString()]
        val openBreak = todayRec?.breaks?.lastOrNull { it.endEpochMs == null }
        if (openBreak != null) {
            scheduleBreakEnd(openBreak.startEpochMs)
        } else {
            cancelBreakEnd()
        }
    }

    suspend fun scheduleBreakEnd(breakStartEpochMs: Long) {
        cancelBreakEnd()
        val today = Instant.ofEpochMilli(clock.millis()).atZone(clock.zone).toLocalDate()
        val schedule = db.scheduleDao().getScheduleForDay(today.dayOfWeek.value)?.toDomain()
        val config = db.scheduleDao().getReminderConfig(ReminderEventType.BREAK_END.name)?.toDomain()
        val specs = ScheduleResolver(clock, clock.zone).resolveBreakEndAlarms(
            workDate = today,
            breakStartEpochMs = breakStartEpochMs,
            schedule = schedule,
            config = config
        )
        specs.forEach { scheduleSpec(it) }
    }

    fun cancelBreakEnd() {
        cancelEvent(ReminderEventType.BREAK_END)
    }

    fun cancelEvent(eventType: ReminderEventType) {
        cancelRequestCode(AlarmContracts.requestCodeFor(eventType, 1))
        cancelRequestCode(AlarmContracts.requestCodeFor(eventType, 2))
        cancelRequestCode(AlarmContracts.REQUEST_CODE_SNOOZE)
        notificationManager.cancelNotificationForEvent(eventType)
    }

    fun scheduleSnooze(
        eventType: ReminderEventType,
        workDate: String,
        snoozeMinutes: Int,
        isTest: Boolean
    ) {
        val triggerMs = clock.millis() + snoozeMinutes.coerceAtLeast(1) * 60_000L
        val pi = buildBroadcastPendingIntent(
            requestCode = AlarmContracts.REQUEST_CODE_SNOOZE,
            eventType = eventType,
            stage = 2,
            workDate = workDate,
            targetEpochMs = triggerMs,
            isTest = isTest
        )
        scheduleAlarmClock(triggerMs, pi)
    }

    fun scheduleTestEscalation(delaySeconds: Int = 5) {
        val nowMs = clock.millis()
        val triggerMs = nowMs + delaySeconds.coerceAtLeast(2) * 1000L
        val todayStr = Instant.ofEpochMilli(nowMs).atZone(clock.zone).toLocalDate().toString()
        val pi = buildBroadcastPendingIntent(
            requestCode = AlarmContracts.REQUEST_CODE_TEST,
            eventType = ReminderEventType.TIME_IN,
            stage = 2,
            workDate = todayStr,
            targetEpochMs = triggerMs,
            isTest = true
        )
        scheduleAlarmClock(triggerMs, pi)
    }

    private fun scheduleSpec(spec: AlarmTriggerSpec) {
        val pi = buildBroadcastPendingIntent(
            requestCode = AlarmContracts.requestCodeFor(spec.eventType, spec.stage),
            eventType = spec.eventType,
            stage = spec.stage,
            workDate = spec.workDate,
            targetEpochMs = spec.scheduledTargetEpochMs,
            isTest = false
        )
        if (spec.stage == 1) {
            scheduleExactOrFallback(spec.triggerEpochMs, pi)
        } else {
            scheduleAlarmClock(spec.triggerEpochMs, pi)
        }
    }

    private fun scheduleExactOrFallback(triggerMs: Long, pi: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun scheduleAlarmClock(triggerMs: Long, pi: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            val showPi = PendingIntent.getActivity(
                context,
                50,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showPi), pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun buildBroadcastPendingIntent(
        requestCode: Int,
        eventType: ReminderEventType,
        stage: Int,
        workDate: String,
        targetEpochMs: Long,
        isTest: Boolean
    ): PendingIntent {
        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmContracts.ACTION_FIRE_REMINDER
            putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
            putExtra(AlarmContracts.EXTRA_STAGE, stage)
            putExtra(AlarmContracts.EXTRA_WORK_DATE, workDate)
            putExtra(AlarmContracts.EXTRA_TARGET_EPOCH_MS, targetEpochMs)
            putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun cancelRequestCode(requestCode: Int) {
        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmContracts.ACTION_FIRE_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }
}
