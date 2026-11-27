package com.personal.workhours

import android.content.Context
import com.personal.workhours.alarm.AlarmNotificationManager
import com.personal.workhours.alarm.AlarmScheduler
import com.personal.workhours.data.local.WorkHoursDatabase
import com.personal.workhours.data.preferences.PreferencesRepository
import com.personal.workhours.data.repository.ScheduleRepository
import com.personal.workhours.data.repository.WorkLogRepository
import java.time.Clock

class AppContainer(context: Context) {

    val clock: Clock = Clock.systemDefaultZone()
    val database: WorkHoursDatabase = WorkHoursDatabase.build(context)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
    val notificationManager: AlarmNotificationManager = AlarmNotificationManager(context)
    val alarmScheduler: AlarmScheduler = AlarmScheduler(context, database, notificationManager, clock)

    val workLogRepository: WorkLogRepository = WorkLogRepository(
        db = database,
        alarmReconciler = alarmScheduler,
        clock = clock,
        autoDeductProvider = { preferencesRepository.isAutoDeductBreakEnabled() }
    )

    val scheduleRepository: ScheduleRepository = ScheduleRepository(
        db = database,
        alarmReconciler = alarmScheduler
    )
}
