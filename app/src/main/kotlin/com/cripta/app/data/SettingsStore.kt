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

/** Shape of the covers in the player's filmstrip. */
enum class StripShape { RECT, SQUARE, CIRCLE }

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
    /** Colour (ARGB) of every tag without its own colour; null = automatic, one per name. */
    val defaultTagColor: Int? = null,
    val showDurationBadge: Boolean = true, // "3:12" on video covers
    val showQualityBadge: Boolean = true,  // "4K / HD / SD" on covers
    val showStatsStrip: Boolean = true,    // the counts strip above the grid
    /** Resume videos where they were left, with a progress bar on their cover. */
    val resumePlayback: Boolean = true,
    /** Tag pickers: show a "Recenti" row with the last used tags. */
    val showRecentTags: Boolean = true,
    /** How many recently used tags the "Recenti" row (and the viewer's quick tags) show. */
    val recentTagsCount: Int = 6,
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
    /** Grid columns while the phone is in landscape (chosen separately from portrait). */
    val gridColumnsLandscape: Int = 5,
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
    /** Viewer: seconds jumped by a double tap on the sides (5 / 10 / 30). */
    val seekStepSec: Int = 10,
    /** Viewer: vertical drag on the left/right side sets brightness/volume. */
    val gestureControls: Boolean = true,
    /** Viewer: vertical drag on the right side changes the media volume (left side = brightness). */
    val gestureVolume: Boolean = true,
    /** Viewer: rotate to landscape for landscape videos, portrait for portrait ones. */
    val autoRotate: Boolean = true,
    /** Viewer: continue a video in a small window when leaving the app (off: privacy). */
    val pictureInPicture: Boolean = false,
    /** Updates: also offer pre-releases (every CI build is published as one). */
    val updatePrerelease: Boolean = true,
    /** Viewer: strip of the neighbouring files' covers while the controls show. */
    val viewerFilmstrip: Boolean = true,
    /** Viewer: pause a video when leaving the app (not in Picture-in-Picture or split screen). */
    val pauseOnLeave: Boolean = true,
    /** Filmstrip: cover shape, covers on each side of the current one (1..4), size (0 S, 1 M, 2 L). */
    val filmstripShape: StripShape = StripShape.RECT,
    val filmstripSpan: Int = 3,
    val filmstripSize: Int = 1,
    /** Filmstrip: portrait videos and photos keep their vertical shape instead of a 16:9 crop. */
    val filmstripTrueAspect: Boolean = true,
    /** Viewer: near the end of a video (loop off) show "Prossimo" and move on to the next file. */
    val autoNext: Boolean = true,
    /** Seconds before the end at which the "Prossimo" card appears (5 / 8 / 15). */
    val autoNextSec: Int = 8,
    /** Viewer: vertical swipe down closes it. */
    val swipeToClose: Boolean = true,
    /** Viewer: vertical swipe up opens tags & details. */
    /** Player: hold on a side to play faster until release. */
    val holdForSpeed: Boolean = true,
    /** Speed while holding, in tenths (15 = 1.5x, 20 = 2x, 30 = 3x). */
    val holdSpeedX10: Int = 20,
    /** Seconds the player controls stay visible after a tap (2 / 4 / 8). */
    val controlsTimeoutSec: Int = 4,
    /** Deleting moves files to a trash instead of shredding at once. Off = previous behaviour. */
    val trashEnabled: Boolean = false,
    /** Backup: deflate documents (notes, PDF, text) losslessly; media is always stored as is. */
    val backupCompressDocs: Boolean = false,
    /** Days a trashed file is kept before being crypto-shredded. */
    val trashDays: Int = 7,
    val downloadDefaults: DownloadDefaults = DownloadDefaults(),
    val convertAfter: ConvertAfter = ConvertAfter.REPLACE,
    /** False until the user picked (and chose to remember) what happens after a conversion. */
    val convertAfterChosen: Boolean = false,
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val autoLock = intPreferencesKey("auto_lock_minutes")
    private val delPolicy = intPreferencesKey("delete_original_policy")
    private val viewModeKey = intPreferencesKey("view_mode")
    private val gridColsKey = intPreferencesKey("grid_columns")
    private val gridColsLandKey = intPreferencesKey("grid_columns_landscape")
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
    private val defaultTagColorKey = intPreferencesKey("default_tag_color")
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
    private val convertAfterChosenKey = booleanPreferencesKey("convert_after_chosen")
    private val seekStepKey = intPreferencesKey("seek_step_sec")
    private val gestureKey = booleanPreferencesKey("gesture_controls")
    private val gestureVolumeKey = booleanPreferencesKey("gesture_volume")
    private val autoRotateKey = booleanPreferencesKey("auto_rotate")
    private val pipKey = booleanPreferencesKey("picture_in_picture")
    private val updatePreKey = booleanPreferencesKey("update_prerelease")
    private val filmstripKey = booleanPreferencesKey("viewer_filmstrip")
    private val pauseOnLeaveKey = booleanPreferencesKey("pause_on_leave")
    private val stripShapeKey = intPreferencesKey("filmstrip_shape")
    private val stripSpanKey = intPreferencesKey("filmstrip_span")
    private val stripSizeKey = intPreferencesKey("filmstrip_size")
    private val stripAspectKey = booleanPreferencesKey("filmstrip_true_aspect")
    private val autoNextKey = booleanPreferencesKey("auto_next")
    private val autoNextSecKey = intPreferencesKey("auto_next_sec")
    private val swipeCloseKey = booleanPreferencesKey("swipe_close")
    private val holdSpeedOnKey = booleanPreferencesKey("hold_speed_on")
    private val holdSpeedKey = intPreferencesKey("hold_speed_x10")
    private val controlsTimeoutKey = intPreferencesKey("controls_timeout_sec")
    private val recentTagsKey = booleanPreferencesKey("recent_tags")
    private val recentTagsCountKey = intPreferencesKey("recent_tags_count")
    private val backupCompressKey = booleanPreferencesKey("backup_compress_docs")
    private val quickTagsKey = booleanPreferencesKey("viewer_quick_tags")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            autoLockMinutes = p[autoLock] ?: 1,
            deleteOriginalPolicy = DeleteOriginalPolicy.entries
                .getOrElse(p[delPolicy] ?: 0) { DeleteOriginalPolicy.ASK },
            viewMode = ViewMode.entries.getOrElse(p[viewModeKey] ?: 0) { ViewMode.GRID },
            gridColumns = (p[gridColsKey] ?: 3).coerceIn(2, 5),
            gridColumnsLandscape = (p[gridColsLandKey] ?: 5).coerceIn(3, 8),
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
                defaultTagColor = p[defaultTagColorKey],
                showDurationBadge = p[durationBadgeKey] ?: true,
                showQualityBadge = p[qualityBadgeKey] ?: true,
                showStatsStrip = p[statsStripKey] ?: true,
                resumePlayback = p[resumeKey] ?: true,
                showRecentTags = p[recentTagsKey] ?: true,
                recentTagsCount = (p[recentTagsCountKey] ?: 6).coerceIn(1, 20),
                viewerQuickTags = p[quickTagsKey] ?: true,
            ),
            trashEnabled = p[trashKey] ?: false,
            backupCompressDocs = p[backupCompressKey] ?: false,
            trashDays = (p[trashDaysKey] ?: 7).coerceIn(1, 90),
            convertAfter = ConvertAfter.entries.getOrElse(p[convertAfterKey] ?: 0) { ConvertAfter.REPLACE },
            convertAfterChosen = p[convertAfterChosenKey] ?: false,
            downloadDefaults = DownloadDefaults(
                folderId = p[dlFolderKey]?.takeIf { it >= 0 },
                tagIds = p[dlTagsKey].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() },
                height = p[dlHeightKey]?.takeIf { it > 0 },
            ),
            videoLoop = p[videoLoopKey] ?: true,
            videoStartMuted = p[videoMutedKey] ?: false,
            seekStepSec = (p[seekStepKey] ?: 10).let { if (it in listOf(5, 10, 30)) it else 10 },
            gestureControls = p[gestureKey] ?: true,
            // Until set on its own, volume follows the old combined brightness+volume switch.
            gestureVolume = p[gestureVolumeKey] ?: p[gestureKey] ?: true,
            autoRotate = p[autoRotateKey] ?: true,
            pictureInPicture = p[pipKey] ?: false,
            updatePrerelease = p[updatePreKey] ?: true,
            viewerFilmstrip = p[filmstripKey] ?: true,
            pauseOnLeave = p[pauseOnLeaveKey] ?: true,
            filmstripShape = StripShape.entries.getOrElse(p[stripShapeKey] ?: 0) { StripShape.RECT },
            filmstripSpan = (p[stripSpanKey] ?: 3).coerceIn(1, 4),
            filmstripSize = (p[stripSizeKey] ?: 1).coerceIn(0, 2),
            filmstripTrueAspect = p[stripAspectKey] ?: true,
            autoNext = p[autoNextKey] ?: true,
            autoNextSec = (p[autoNextSecKey] ?: 8).let { if (it in listOf(5, 8, 15)) it else 8 },
            swipeToClose = p[swipeCloseKey] ?: true,
            holdForSpeed = p[holdSpeedOnKey] ?: true,
            holdSpeedX10 = (p[holdSpeedKey] ?: 20).let { if (it in listOf(15, 20, 30)) it else 20 },
            controlsTimeoutSec = (p[controlsTimeoutKey] ?: 4).let { if (it in listOf(2, 4, 8)) it else 4 },
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
    suspend fun setDefaultTagColor(argb: Int?) {
        context.dataStore.edit { if (argb == null) it.remove(defaultTagColorKey) else it[defaultTagColorKey] = argb }
    }
    suspend fun setShowDurationBadge(v: Boolean) { context.dataStore.edit { it[durationBadgeKey] = v } }
    suspend fun setShowQualityBadge(v: Boolean) { context.dataStore.edit { it[qualityBadgeKey] = v } }
    suspend fun setShowStatsStrip(v: Boolean) { context.dataStore.edit { it[statsStripKey] = v } }
    suspend fun setSeekStep(v: Int) { context.dataStore.edit { it[seekStepKey] = v } }
    suspend fun setGestureControls(v: Boolean) { context.dataStore.edit { it[gestureKey] = v } }
    suspend fun setGestureVolume(v: Boolean) { context.dataStore.edit { it[gestureVolumeKey] = v } }
    suspend fun setAutoRotate(v: Boolean) { context.dataStore.edit { it[autoRotateKey] = v } }
    suspend fun setPictureInPicture(v: Boolean) { context.dataStore.edit { it[pipKey] = v } }
    suspend fun setAutoNext(v: Boolean) { context.dataStore.edit { it[autoNextKey] = v } }
    suspend fun setAutoNextSec(v: Int) { context.dataStore.edit { it[autoNextSecKey] = v } }
    suspend fun setSwipeToClose(v: Boolean) { context.dataStore.edit { it[swipeCloseKey] = v } }
    suspend fun setHoldForSpeed(v: Boolean) { context.dataStore.edit { it[holdSpeedOnKey] = v } }
    suspend fun setHoldSpeed(x10: Int) { context.dataStore.edit { it[holdSpeedKey] = x10 } }
    suspend fun setControlsTimeout(sec: Int) { context.dataStore.edit { it[controlsTimeoutKey] = sec } }
    suspend fun setViewerFilmstrip(v: Boolean) { context.dataStore.edit { it[filmstripKey] = v } }
    suspend fun setPauseOnLeave(v: Boolean) { context.dataStore.edit { it[pauseOnLeaveKey] = v } }
    suspend fun setFilmstripShape(v: StripShape) { context.dataStore.edit { it[stripShapeKey] = v.ordinal } }
    suspend fun setFilmstripSpan(v: Int) { context.dataStore.edit { it[stripSpanKey] = v.coerceIn(1, 4) } }
    suspend fun setFilmstripSize(v: Int) { context.dataStore.edit { it[stripSizeKey] = v.coerceIn(0, 2) } }
    suspend fun setFilmstripTrueAspect(v: Boolean) { context.dataStore.edit { it[stripAspectKey] = v } }
    suspend fun setUpdatePrerelease(v: Boolean) { context.dataStore.edit { it[updatePreKey] = v } }
    suspend fun setShowRecentTags(v: Boolean) { context.dataStore.edit { it[recentTagsKey] = v } }
    suspend fun setBackupCompressDocs(v: Boolean) { context.dataStore.edit { it[backupCompressKey] = v } }
    suspend fun setRecentTagsCount(v: Int) { context.dataStore.edit { it[recentTagsCountKey] = v.coerceIn(1, 20) } }
    suspend fun setViewerQuickTags(v: Boolean) { context.dataStore.edit { it[quickTagsKey] = v } }
    suspend fun setConvertAfter(v: ConvertAfter) { context.dataStore.edit { it[convertAfterKey] = v.ordinal; it[convertAfterChosenKey] = true } }
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

    suspend fun setGridColumns(cols: Int, landscape: Boolean = false) {
        context.dataStore.edit {
            if (landscape) it[gridColsLandKey] = cols.coerceIn(3, 8) else it[gridColsKey] = cols.coerceIn(2, 5)
        }
    }

    suspend fun settingsOnce(): Settings = settings.first()

    /**
     * Reset the preferences shown on one Settings page (by page name) to their defaults by
     * removing their keys. Only preferences: vault content is never affected.
     */
    suspend fun resetPage(page: String) {
        val keys: List<androidx.datastore.preferences.core.Preferences.Key<*>> = when (page) {
            "ASPETTO" -> listOf(themeKey, dynamicKey, statsStripKey, showDateHeadersKey, showFileInfoKey,
                showFolderInfoKey, showNoteFabKey, showRandomFabKey)
            "COPERTINE" -> listOf(showTagsCoverKey, coverTagRowsKey, coverTagStyleKey, tagColorsKey, defaultTagColorKey, durationBadgeKey, qualityBadgeKey)
            "VIDEO" -> listOf(resumeKey, videoLoopKey, videoMutedKey, convertAfterKey, convertAfterChosenKey,
                seekStepKey, gestureKey, gestureVolumeKey, autoRotateKey, pipKey, filmstripKey, pauseOnLeaveKey, stripShapeKey, stripSpanKey, stripSizeKey, stripAspectKey, autoNextKey, autoNextSecKey,
                swipeCloseKey, holdSpeedOnKey, holdSpeedKey, controlsTimeoutKey)
            "ETICHETTE" -> listOf(recentTagsKey, recentTagsCountKey, quickTagsKey, tagSortModeKey)
            "SICUREZZA" -> listOf(autoLock, allowShotsKey)
            "IMPORT" -> listOf(delPolicy)
            else -> emptyList()
        }
        if (keys.isNotEmpty()) context.dataStore.edit { p -> keys.forEach { p.remove(it) } }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[autoLock] = minutes }
    }

    suspend fun setDeleteOriginalPolicy(policy: DeleteOriginalPolicy) {
        context.dataStore.edit { it[delPolicy] = policy.ordinal }
    }
}
