package com.personal.workhours.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.workhours.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "workhours_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_DEDUCT_BREAK = booleanPreferencesKey("auto_deduct_break_enabled")
        val TRACKING_START_DATE = stringPreferencesKey("tracking_start_date")
        val ALARM_TIMING_V2_MIGRATED = booleanPreferencesKey("alarm_timing_v2_migrated")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val autoDeductBreakEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_DEDUCT_BREAK] ?: false
    }

    val trackingStartDate: Flow<LocalDate?> = context.dataStore.data.map { prefs ->
        prefs[Keys.TRACKING_START_DATE]?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }
    }

    suspend fun isAutoDeductBreakEnabled(): Boolean {
        return context.dataStore.data.first()[Keys.AUTO_DEDUCT_BREAK] ?: false
    }

    suspend fun ensureTrackingStartDate(date: LocalDate) {
        context.dataStore.edit { prefs ->
            if (!prefs.contains(Keys.TRACKING_START_DATE)) {
                prefs[Keys.TRACKING_START_DATE] = date.toString()
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setAutoDeductBreakEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DEDUCT_BREAK] = enabled }
    }

    suspend fun consumeAlarmTimingV2Migration(): Boolean {
        var shouldMigrate = false
        context.dataStore.edit { prefs ->
            if (prefs[Keys.ALARM_TIMING_V2_MIGRATED] != true) {
                prefs[Keys.ALARM_TIMING_V2_MIGRATED] = true
                shouldMigrate = true
            }
        }
        return shouldMigrate
    }
}
