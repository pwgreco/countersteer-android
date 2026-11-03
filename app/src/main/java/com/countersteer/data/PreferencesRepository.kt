package com.countersteer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val CRASH_DETECTION_ENABLED = booleanPreferencesKey("crash_detection_enabled")
        val CRASH_COUNTDOWN_SECONDS = intPreferencesKey("crash_countdown_seconds")
        val SPEED_ALERT_ENABLED = booleanPreferencesKey("speed_alert_enabled")
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val crashDetectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CRASH_DETECTION_ENABLED] ?: true }

    val crashCountdownSeconds: Flow<Int> =
        context.dataStore.data.map { it[Keys.CRASH_COUNTDOWN_SECONDS] ?: 30 }

    val speedAlertEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SPEED_ALERT_ENABLED] ?: true }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = value }
    }

    suspend fun setCrashDetectionEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.CRASH_DETECTION_ENABLED] = value }
    }

    suspend fun setCrashCountdownSeconds(value: Int) {
        context.dataStore.edit { it[Keys.CRASH_COUNTDOWN_SECONDS] = value }
    }

    suspend fun setSpeedAlertEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SPEED_ALERT_ENABLED] = value }
    }
}
