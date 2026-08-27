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
enum class SortKey { DATE, NAME, SIZE, MANUAL }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TagSortMode { ALPHA, CUSTOM }

/** Which optional details are drawn on file/folder cells. */
data class DisplayPrefs(
    val showFileInfo: Boolean = true,     // size + duration captions
    val showTagsOnCover: Boolean = true,  // tag badges over grid thumbnails
    val showDateHeaders: Boolean = true,  // "Oggi / Ieri / date" grouping
    val showFolderInfo: Boolean = true,   // per-folder item count + size
    val showNoteFab: Boolean = true,      // the "new note" floating button
    val showRandomFab: Boolean = true,    // the "random" floating button
)

data class Settings(
    val autoLockMinutes: Int = 1,
    val deleteOriginalPolicy: DeleteOriginalPolicy = DeleteOriginalPolicy.ASK,
    val viewMode: ViewMode = ViewMode.GRID,
    val gridColumns: Int = 3,
    val sortKey: SortKey = SortKey.DATE,
    val sortAscending: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    /** When false (default) the window keeps FLAG_SECURE: screenshots blocked, hidden in recents. */
    val allowScreenshots: Boolean = false,
    val tagSortMode: TagSortMode = TagSortMode.ALPHA,
    val display: DisplayPrefs = DisplayPrefs(),
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val autoLock = intPreferencesKey("auto_lock_minutes")
    private val delPolicy = intPreferencesKey("delete_original_policy")
    private val viewModeKey = intPreferencesKey("view_mode")
    private val gridColsKey = intPreferencesKey("grid_columns")
    private val sortKeyKey = intPreferencesKey("sort_key")
    private val sortAscKey = booleanPreferencesKey("sort_asc")
    private val themeKey = intPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val allowShotsKey = booleanPreferencesKey("allow_screenshots")
    private val showFileInfoKey = booleanPreferencesKey("show_file_info")
    private val showTagsCoverKey = booleanPreferencesKey("show_tags_cover")
    private val showDateHeadersKey = booleanPreferencesKey("show_date_headers")
    private val showFolderInfoKey = booleanPreferencesKey("show_folder_info")
    private val showNoteFabKey = booleanPreferencesKey("show_note_fab")
    private val showRandomFabKey = booleanPreferencesKey("show_random_fab")
    private val tagSortModeKey = intPreferencesKey("tag_sort_mode")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            autoLockMinutes = p[autoLock] ?: 1,
            deleteOriginalPolicy = DeleteOriginalPolicy.entries
                .getOrElse(p[delPolicy] ?: 0) { DeleteOriginalPolicy.ASK },
            viewMode = ViewMode.entries.getOrElse(p[viewModeKey] ?: 0) { ViewMode.GRID },
            gridColumns = (p[gridColsKey] ?: 3).coerceIn(2, 5),
            sortKey = SortKey.entries.getOrElse(p[sortKeyKey] ?: 0) { SortKey.DATE },
            sortAscending = p[sortAscKey] ?: false,
            themeMode = ThemeMode.entries.getOrElse(p[themeKey] ?: ThemeMode.DARK.ordinal) { ThemeMode.DARK },
            dynamicColor = p[dynamicKey] ?: false,
            allowScreenshots = p[allowShotsKey] ?: false,
            tagSortMode = TagSortMode.entries.getOrElse(p[tagSortModeKey] ?: 0) { TagSortMode.ALPHA },
            display = DisplayPrefs(
                showFileInfo = p[showFileInfoKey] ?: true,
                showTagsOnCover = p[showTagsCoverKey] ?: true,
                showDateHeaders = p[showDateHeadersKey] ?: true,
                showFolderInfo = p[showFolderInfoKey] ?: true,
                showNoteFab = p[showNoteFabKey] ?: true,
                showRandomFab = p[showRandomFabKey] ?: true,
            ),
        )
    }

    suspend fun setAllowScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[allowShotsKey] = enabled }
    }

    suspend fun setShowFileInfo(v: Boolean) { context.dataStore.edit { it[showFileInfoKey] = v } }
    suspend fun setShowTagsOnCover(v: Boolean) { context.dataStore.edit { it[showTagsCoverKey] = v } }
    suspend fun setShowDateHeaders(v: Boolean) { context.dataStore.edit { it[showDateHeadersKey] = v } }
    suspend fun setShowFolderInfo(v: Boolean) { context.dataStore.edit { it[showFolderInfoKey] = v } }
    suspend fun setShowNoteFab(v: Boolean) { context.dataStore.edit { it[showNoteFabKey] = v } }
    suspend fun setShowRandomFab(v: Boolean) { context.dataStore.edit { it[showRandomFabKey] = v } }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.ordinal }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = enabled }
    }

    suspend fun setSort(key: SortKey, ascending: Boolean) {
        context.dataStore.edit { it[sortKeyKey] = key.ordinal; it[sortAscKey] = ascending }
    }

    suspend fun setTagSortMode(mode: TagSortMode) {
        context.dataStore.edit { it[tagSortModeKey] = mode.ordinal }
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
