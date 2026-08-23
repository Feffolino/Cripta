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

data class Settings(
    val autoLockMinutes: Int = 1,
    val deleteOriginalPolicy: DeleteOriginalPolicy = DeleteOriginalPolicy.ASK,
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val autoLock = intPreferencesKey("auto_lock_minutes")
    private val delPolicy = intPreferencesKey("delete_original_policy")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            autoLockMinutes = p[autoLock] ?: 1,
            deleteOriginalPolicy = DeleteOriginalPolicy.entries
                .getOrElse(p[delPolicy] ?: 0) { DeleteOriginalPolicy.ASK },
        )
    }

    suspend fun settingsOnce(): Settings = settings.first()

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[autoLock] = minutes }
    }

    suspend fun setDeleteOriginalPolicy(policy: DeleteOriginalPolicy) {
        context.dataStore.edit { it[delPolicy] = policy.ordinal }
    }
}
