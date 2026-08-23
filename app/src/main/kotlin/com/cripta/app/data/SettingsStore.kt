package com.cripta.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "cripta_settings")

/** Choice for what to do with the original file after import. */
enum class DeleteOriginalPolicy { ASK, ALWAYS, NEVER }

enum class ViewMode { GRID, LIST }

data class Settings(
    val autoLockMinutes: Int = 1,
    val deleteOriginalPolicy: DeleteOriginalPolicy = DeleteOriginalPolicy.ASK,
    val viewMode: ViewMode = ViewMode.GRID,
    val gridColumns: Int = 3,
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val autoLock = intPreferencesKey("auto_lock_minutes")
    private val delPolicy = intPreferencesKey("delete_original_policy")
    private val viewModeKey = intPreferencesKey("view_mode")
    private val gridColsKey = intPreferencesKey("grid_columns")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            autoLockMinutes = p[autoLock] ?: 1,
            deleteOriginalPolicy = DeleteOriginalPolicy.entries
                .getOrElse(p[delPolicy] ?: 0) { DeleteOriginalPolicy.ASK },
            viewMode = ViewMode.entries.getOrElse(p[viewModeKey] ?: 0) { ViewMode.GRID },
            gridColumns = (p[gridColsKey] ?: 3).coerceIn(2, 5),
        )
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[viewModeKey] = mode.ordinal }
    }

    suspend fun setGridColumns(cols: Int) {
        context.dataStore.edit { it[gridColsKey] = cols.coerceIn(2, 5) }
    }

    suspend fun settingsOnce(): Settings = settings.first()

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[autoLock] = minutes }
    }

    suspend fun setDeleteOriginalPolicy(policy: DeleteOriginalPolicy) {
        context.dataStore.edit { it[delPolicy] = policy.ordinal }
    }
}
