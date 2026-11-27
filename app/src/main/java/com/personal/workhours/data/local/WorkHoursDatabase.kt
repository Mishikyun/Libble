package com.personal.workhours.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.workhours.domain.model.EscalationMode
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.VibrationPatternType

@Database(
    entities = [
        WorkDayEntity::class,
        BreakSessionEntity::class,
        DayScheduleEntity::class,
        ReminderConfigEntity::class,
        DayOffOverrideEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class WorkHoursDatabase : RoomDatabase() {
    abstract fun workLogDao(): WorkLogDao
    abstract fun scheduleDao(): ScheduleDao

    suspend fun ensureDefaultSeedData() {
        val sDao = scheduleDao()
        withTransaction {
            if (sDao.getDaySchedules().isEmpty()) {
                val defaults = (1..7).map { dow ->
                    DayScheduleEntity(
                        dayOfWeek = dow,
                        isWorkDay = dow in 1..5,
                        startMinuteOfDay = 8 * 60,
                        endMinuteOfDay = 17 * 60,
                        defaultBreakMinutes = 60,
                        targetWorkMinutes = 8 * 60,
                        breakStartMinuteOfDay = 12 * 60
                    )
                }
                sDao.upsertDaySchedules(defaults)
            }
            val defaultConfigs = ReminderEventType.entries.map { type ->
                ReminderConfigEntity(
                    eventType = type.name,
                    enabled = true,
                    stage1LeadMinutes = 5,
                    stage2Enabled = true,
                    stage2DelayMinutes = 0,
                    stage2Mode = EscalationMode.FULL_ALARM.name,
                    vibrationPattern = if (type == ReminderEventType.BREAK_END) {
                        VibrationPatternType.HEARTBEAT.name
                    } else {
                        VibrationPatternType.PULSE.name
                    },
                    soundUri = null,
                    snoozeMinutes = 5
                )
            }
            val existingTypes = sDao.getReminderConfigs().map { it.eventType }.toSet()
            val missing = defaultConfigs.filter { it.eventType !in existingTypes }
            if (missing.isNotEmpty()) {
                sDao.upsertReminderConfigs(missing)
            }
        }
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_schedules ADD COLUMN breakStartMinuteOfDay INTEGER DEFAULT 720")
            }
        }

        fun build(context: Context): WorkHoursDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WorkHoursDatabase::class.java,
                "workhours.db"
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
