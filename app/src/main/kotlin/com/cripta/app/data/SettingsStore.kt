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
/**
 * What happens to the original after a successful MP4 conversion. REPLACE: the MP4 takes the
 * original's place (same folder, tags, position, cover) and the original goes to the trash, so it
 * stays recoverable. ASK: previous behaviour (prompt). KEEP_BOTH: keep both, no prompt.
 */
enum class ConvertAfter { REPLACE, ASK, KEEP_BOTH }
/** How a tag is written on a cover badge: its short alias/emoji, or its full name. */
enum class CoverTagStyle { ALIAS, NAME }

/** Which optional details are drawn on file/folder cells. */
data class DisplayPrefs(
    val showFileInfo: Boolean = true,     // size + duration captions
    val showTagsOnCover: Boolean = true,  // tag badges over grid thumbnails
    val showDateHeaders: Boolean = true,  // "Oggi / Ieri / date" grouping
    val showFolderInfo: Boolean = true,   // per-folder item count + size
    val showNoteFab: Boolean = true,      // the "new note" floating button
    val showRandomFab: Boolean = true,    // the "random" floating button
    /** Rows of tag badges on a grid cover: 0 = automatic (by cover size), else 1..3. */
    val coverTagRows: Int = 0,
    val coverTagStyle: CoverTagStyle = CoverTagStyle.ALIAS,
    /** Give each tag its own stable colour (same tag = same colour everywhere). */
    val tagColors: Boolean = true,
    val showDurationBadge: Boolean = true, // "3:12" on video covers
    val showQualityBadge: Boolean = true,  // "4K / HD / SD" on covers
    val showStatsStrip: Boolean = true,    // the counts strip above the grid
    /** Resume videos where they were left, with a progress bar on their cover. */
    val resumePlayback: Boolean = true,
    /** Tag pickers: show a "Recenti" row with the last used tags. */
    val showRecentTags: Boolean = true,
    /** Viewer: quick-tag bar (pinned + recent tags, one tap to toggle). */
    val viewerQuickTags: Boolean = true,
)

/**
 * Remembered downloader destination, so it doesn't have to be picked every time. Only ids are
 * stored here (plain preferences); folder and tag names stay in the encrypted database.
 */
data class DownloadDefaults(
    val folderId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val height: Int? = null,
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
    /** Viewer: loop videos (default) or stop at the end. */
    val videoLoop: Boolean = true,
    /** Viewer: start videos muted. */
    val videoStartMuted: Boolean = false,
    /** Deleting moves files to a trash instead of shredding at once. Off = previous behaviour. */
    val trashEnabled: Boolean = false,
    /** Days a trashed file is kept before being crypto-shredded. */
    val trashDays: Int = 7,
    val downloadDefaults: DownloadDefaults = DownloadDefaults(),
    val convertAfter: ConvertAfter = ConvertAfter.REPLACE,
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
    private val coverTagRowsKey = intPreferencesKey("cover_tag_rows")
    private val coverTagStyleKey = intPreferencesKey("cover_tag_style")
    private val tagColorsKey = booleanPreferencesKey("tag_colors")
    private val durationBadgeKey = booleanPreferencesKey("duration_badge")
    private val qualityBadgeKey = booleanPreferencesKey("quality_badge")
    private val statsStripKey = booleanPreferencesKey("stats_strip")
    private val videoLoopKey = booleanPreferencesKey("video_loop")
    private val videoMutedKey = booleanPreferencesKey("video_start_muted")
    private val resumeKey = booleanPreferencesKey("resume_playback")
    private val trashKey = booleanPreferencesKey("trash_enabled")
    private val trashDaysKey = intPreferencesKey("trash_days")
    private val dlFolderKey = androidx.datastore.preferences.core.longPreferencesKey("dl_folder")
    private val dlTagsKey = androidx.datastore.preferences.core.stringPreferencesKey("dl_tag_ids")
    private val dlHeightKey = intPreferencesKey("dl_height")
    private val convertAfterKey = intPreferencesKey("convert_after")
    private val recentTagsKey = booleanPreferencesKey("recent_tags")
    private val quickTagsKey = booleanPreferencesKey("viewer_quick_tags")

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
                coverTagRows = (p[coverTagRowsKey] ?: 0).coerceIn(0, 3),
                coverTagStyle = CoverTagStyle.entries.getOrElse(p[coverTagStyleKey] ?: 0) { CoverTagStyle.ALIAS },
                tagColors = p[tagColorsKey] ?: true,
                showDurationBadge = p[durationBadgeKey] ?: true,
                showQualityBadge = p[qualityBadgeKey] ?: true,
                showStatsStrip = p[statsStripKey] ?: true,
                resumePlayback = p[resumeKey] ?: true,
                showRecentTags = p[recentTagsKey] ?: true,
                viewerQuickTags = p[quickTagsKey] ?: true,
            ),
            trashEnabled = p[trashKey] ?: false,
            trashDays = (p[trashDaysKey] ?: 7).coerceIn(1, 90),
            convertAfter = ConvertAfter.entries.getOrElse(p[convertAfterKey] ?: 0) { ConvertAfter.REPLACE },
            downloadDefaults = DownloadDefaults(
                folderId = p[dlFolderKey]?.takeIf { it >= 0 },
                tagIds = p[dlTagsKey].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() },
                height = p[dlHeightKey]?.takeIf { it > 0 },
            ),
            videoLoop = p[videoLoopKey] ?: true,
            videoStartMuted = p[videoMutedKey] ?: false,
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
    suspend fun setCoverTagRows(v: Int) { context.dataStore.edit { it[coverTagRowsKey] = v.coerceIn(0, 3) } }
    suspend fun setCoverTagStyle(v: CoverTagStyle) { context.dataStore.edit { it[coverTagStyleKey] = v.ordinal } }
    suspend fun setTagColors(v: Boolean) { context.dataStore.edit { it[tagColorsKey] = v } }
    suspend fun setShowDurationBadge(v: Boolean) { context.dataStore.edit { it[durationBadgeKey] = v } }
    suspend fun setShowQualityBadge(v: Boolean) { context.dataStore.edit { it[qualityBadgeKey] = v } }
    suspend fun setShowStatsStrip(v: Boolean) { context.dataStore.edit { it[statsStripKey] = v } }
    suspend fun setShowRecentTags(v: Boolean) { context.dataStore.edit { it[recentTagsKey] = v } }
    suspend fun setViewerQuickTags(v: Boolean) { context.dataStore.edit { it[quickTagsKey] = v } }
    suspend fun setConvertAfter(v: ConvertAfter) { context.dataStore.edit { it[convertAfterKey] = v.ordinal } }
    suspend fun setResumePlayback(v: Boolean) { context.dataStore.edit { it[resumeKey] = v } }
    suspend fun setTrashEnabled(v: Boolean) { context.dataStore.edit { it[trashKey] = v } }
    suspend fun setTrashDays(v: Int) { context.dataStore.edit { it[trashDaysKey] = v.coerceIn(1, 90) } }
    suspend fun setDownloadDefaults(d: DownloadDefaults) {
        context.dataStore.edit {
            it[dlFolderKey] = d.folderId ?: -1L
            it[dlTagsKey] = d.tagIds.joinToString(",")
            it[dlHeightKey] = d.height ?: 0
        }
    }
    suspend fun setVideoLoop(v: Boolean) { context.dataStore.edit { it[videoLoopKey] = v } }
    suspend fun setVideoStartMuted(v: Boolean) { context.dataStore.edit { it[videoMutedKey] = v } }

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
