package com.lucabol.onelinejournal.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

data class JournalSettings(
    val remindersEnabled: Boolean = false,
    val reminderMinute: Int = 21 * 60,
    val suppressCompleted: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val appLock: Boolean = false,
)

class Settings(private val context: Context) {
    private val enabled = booleanPreferencesKey("reminders_enabled")
    private val minute = intPreferencesKey("reminder_minute")
    private val suppress = booleanPreferencesKey("suppress_completed")
    private val encryption = booleanPreferencesKey("backup_encryption")
    private val lock = booleanPreferencesKey("app_lock")

    val flow = context.settingsDataStore.data.map {
        JournalSettings(it[enabled] ?: false, it[minute] ?: 21 * 60, it[suppress] ?: false,
            it[encryption] ?: false, it[lock] ?: false)
    }

    suspend fun reminders(active: Boolean, minuteOfDay: Int, suppressCompleted: Boolean) {
        require(minuteOfDay in 0..1439)
        context.settingsDataStore.edit {
            it[enabled] = active
            it[minute] = minuteOfDay
            it[suppress] = suppressCompleted
        }
    }
    suspend fun encryption(active: Boolean) { context.settingsDataStore.edit { it[encryption] = active } }
    suspend fun appLock(active: Boolean) { context.settingsDataStore.edit { it[lock] = active } }
}
