package com.habit1.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK", "AMOLED"
    val notificationsEnabled: Boolean = true,
    val firstRunCompleted: Boolean = false,
    val dailyReviewEnabled: Boolean = false,
    val dailyReviewTime: String = "21:00",
    val categoryGroupingEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val appLockTimeout: String = "IMMEDIATELY"
) {
    val eveningDigestEnabled: Boolean get() = dailyReviewEnabled
    val eveningDigestTime: String get() = dailyReviewTime
}

open class UserPreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val FIRST_RUN_COMPLETED = booleanPreferencesKey("first_run_completed")
        val DAILY_REVIEW_ENABLED = booleanPreferencesKey("daily_review_enabled")
        val DAILY_REVIEW_TIME = stringPreferencesKey("daily_review_time")
        val CATEGORY_GROUPING_ENABLED = booleanPreferencesKey("category_grouping_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT = stringPreferencesKey("app_lock_timeout")
    }

    open val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM",
                notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                firstRunCompleted = preferences[PreferencesKeys.FIRST_RUN_COMPLETED] ?: false,
                dailyReviewEnabled = preferences[PreferencesKeys.DAILY_REVIEW_ENABLED] ?: false,
                dailyReviewTime = preferences[PreferencesKeys.DAILY_REVIEW_TIME] ?: "21:00",
                categoryGroupingEnabled = preferences[PreferencesKeys.CATEGORY_GROUPING_ENABLED] ?: false,
                appLockEnabled = preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false,
                appLockTimeout = preferences[PreferencesKeys.APP_LOCK_TIMEOUT] ?: "IMMEDIATELY"
            )
        }

    open suspend fun updateThemeMode(themeMode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode
        }
    }

    open suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    open suspend fun setFirstRunCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_RUN_COMPLETED] = completed
        }
    }

    open suspend fun updateDailyReviewSettings(enabled: Boolean, time: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_REVIEW_ENABLED] = enabled
            preferences[PreferencesKeys.DAILY_REVIEW_TIME] = time
        }
    }

    open suspend fun updateEveningDigestSettings(enabled: Boolean, time: String) {
        updateDailyReviewSettings(enabled, time)
    }

    open suspend fun updateCategoryGroupingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CATEGORY_GROUPING_ENABLED] = enabled
        }
    }

    open suspend fun updateAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    open suspend fun updateAppLockTimeout(timeout: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT] = timeout
        }
    }

    open suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
