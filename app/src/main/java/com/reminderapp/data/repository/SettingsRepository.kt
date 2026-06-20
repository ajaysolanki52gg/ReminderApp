package com.reminderapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reminderapp.domain.model.NotificationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val defaultNotificationMode: NotificationMode = NotificationMode.NOTIFICATION,
    val defaultSnoozeDuration: Int = 10 // minutes
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DEFAULT_MODE = stringPreferencesKey("default_notification_mode")
    private val DEFAULT_SNOOZE = intPreferencesKey("default_snooze_duration")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultNotificationMode = NotificationMode.valueOf(
                prefs[DEFAULT_MODE] ?: NotificationMode.NOTIFICATION.name
            ),
            defaultSnoozeDuration = prefs[DEFAULT_SNOOZE] ?: 10
        )
    }

    suspend fun setDefaultNotificationMode(mode: NotificationMode) {
        context.dataStore.edit { it[DEFAULT_MODE] = mode.name }
    }

    suspend fun setDefaultSnoozeDuration(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_SNOOZE] = minutes }
    }
}
