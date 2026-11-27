package com.personal.workhours

import android.app.Application
import com.personal.workhours.domain.model.EscalationMode
import com.personal.workhours.domain.model.ReminderEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class WorkHoursApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationManager.ensureChannels()
        appScope.launch {
            container.preferencesRepository.ensureTrackingStartDate(LocalDate.now(container.clock))
            container.database.ensureDefaultSeedData()
            if (container.preferencesRepository.consumeAlarmTimingV2Migration()) {
                val currentConfigs = container.scheduleRepository.getReminderConfigs()
                ReminderEventType.entries.forEach { eventType ->
                    val existing = currentConfigs[eventType]
                    if (existing != null) {
                        container.scheduleRepository.saveReminderConfig(
                            existing.copy(
                                stage1LeadMinutes = if (existing.stage1LeadMinutes == 0) 5 else existing.stage1LeadMinutes,
                                stage2Enabled = true,
                                stage2DelayMinutes = 0,
                                stage2Mode = EscalationMode.FULL_ALARM
                            )
                        )
                    }
                }
            }
            container.alarmScheduler.reconcileAll()
        }
    }
}
