package com.personal.workhours.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkLogDao {

    @Transaction
    @Query("SELECT * FROM work_days WHERE workDate = :workDate LIMIT 1")
    fun observeWorkDay(workDate: String): Flow<WorkDayWithBreaks?>

    @Transaction
    @Query("SELECT * FROM work_days WHERE workDate = :workDate LIMIT 1")
    suspend fun getWorkDay(workDate: String): WorkDayWithBreaks?

    @Transaction
    @Query("SELECT * FROM work_days WHERE clockOutEpochMs IS NULL AND isDayOff = 0 ORDER BY workDate DESC")
    fun observeOpenShifts(): Flow<List<WorkDayWithBreaks>>

    @Transaction
    @Query("SELECT * FROM work_days WHERE clockOutEpochMs IS NULL AND isDayOff = 0 ORDER BY workDate DESC")
    suspend fun getOpenShifts(): List<WorkDayWithBreaks>

    @Transaction
    @Query("SELECT * FROM work_days WHERE workDate BETWEEN :startDate AND :endDate ORDER BY workDate DESC")
    fun observeWorkDaysInRange(startDate: String, endDate: String): Flow<List<WorkDayWithBreaks>>

    @Transaction
    @Query("SELECT * FROM work_days WHERE workDate BETWEEN :startDate AND :endDate ORDER BY workDate DESC")
    suspend fun getWorkDaysInRange(startDate: String, endDate: String): List<WorkDayWithBreaks>

    @Transaction
    @Query("SELECT * FROM work_days ORDER BY workDate DESC")
    suspend fun getAllWorkDays(): List<WorkDayWithBreaks>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkDay(entity: WorkDayEntity): Long

    @Update
    suspend fun updateWorkDay(entity: WorkDayEntity)

    @Query("DELETE FROM work_days WHERE workDate = :workDate")
    suspend fun deleteWorkDayByDate(workDate: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBreak(entity: BreakSessionEntity): Long

    @Update
    suspend fun updateBreak(entity: BreakSessionEntity)

    @Query("SELECT * FROM break_sessions WHERE workDayId = :workDayId AND endEpochMs IS NULL LIMIT 1")
    suspend fun getOpenBreak(workDayId: Long): BreakSessionEntity?

    @Query("UPDATE break_sessions SET endEpochMs = :endEpochMs WHERE workDayId = :workDayId AND endEpochMs IS NULL")
    suspend fun closeOpenBreaks(workDayId: Long, endEpochMs: Long)

    @Query("DELETE FROM break_sessions WHERE workDayId = :workDayId")
    suspend fun deleteBreaksForWorkDay(workDayId: Long)

    @Query("DELETE FROM work_days")
    suspend fun clearAllWorkDays()
}

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM day_schedules ORDER BY dayOfWeek ASC")
    fun observeDaySchedules(): Flow<List<DayScheduleEntity>>

    @Query("SELECT * FROM day_schedules ORDER BY dayOfWeek ASC")
    suspend fun getDaySchedules(): List<DayScheduleEntity>

    @Query("SELECT * FROM day_schedules WHERE dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getScheduleForDay(dayOfWeek: Int): DayScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaySchedules(schedules: List<DayScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaySchedule(schedule: DayScheduleEntity)

    @Query("SELECT * FROM reminder_configs")
    fun observeReminderConfigs(): Flow<List<ReminderConfigEntity>>

    @Query("SELECT * FROM reminder_configs")
    suspend fun getReminderConfigs(): List<ReminderConfigEntity>

    @Query("SELECT * FROM reminder_configs WHERE eventType = :eventType LIMIT 1")
    suspend fun getReminderConfig(eventType: String): ReminderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderConfigs(configs: List<ReminderConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderConfig(config: ReminderConfigEntity)

    @Query("SELECT * FROM day_off_overrides ORDER BY workDate ASC")
    fun observeDayOffOverrides(): Flow<List<DayOffOverrideEntity>>

    @Query("SELECT * FROM day_off_overrides ORDER BY workDate ASC")
    suspend fun getDayOffOverrides(): List<DayOffOverrideEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM day_off_overrides WHERE workDate = :workDate)")
    suspend fun isDayOffOverride(workDate: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayOffOverride(override: DayOffOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayOffOverrides(overrides: List<DayOffOverrideEntity>)

    @Query("DELETE FROM day_off_overrides WHERE workDate = :workDate")
    suspend fun deleteDayOffOverride(workDate: String)

    @Query("DELETE FROM day_schedules")
    suspend fun clearDaySchedules()

    @Query("DELETE FROM reminder_configs")
    suspend fun clearReminderConfigs()

    @Query("DELETE FROM day_off_overrides")
    suspend fun clearDayOffOverrides()
}
