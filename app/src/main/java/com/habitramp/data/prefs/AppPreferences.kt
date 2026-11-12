package com.habitramp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "habitramp_prefs")

class AppPreferences(private val context: Context) {
    val lastSelectedHabitId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_HABIT_ID]
    }

    suspend fun setLastSelectedHabitId(id: Long?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(LAST_HABIT_ID) else prefs[LAST_HABIT_ID] = id
        }
    }

    companion object {
        private val LAST_HABIT_ID = longPreferencesKey("last_habit_id")
    }
}
