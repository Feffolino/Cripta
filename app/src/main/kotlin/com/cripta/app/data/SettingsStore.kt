package com.cripta.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// A damaged settings file is replaced by defaults: thrown, it crashed every launch for good
// (the vault itself, in its own database, was fine but could not be reached).
private val Context.dataStore by preferencesDataStore(
    name = "cripta_settings",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

/** Choice for what to do with the original file after import. */
enum class DeleteOriginalPolicy { ASK, ALWAYS, NEVER }

enum class ViewMode { GRID, LIST }
enum class SortKey { DATE, NAME, SIZE, MANUAL }
/** Orders of the people page: the app's (starred, then most media), name, media count, newest media, when added. */
enum class PeopleSort { RELEVANCE, NAME, MEDIA, LATEST, ADDED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
/** Stored by ordinal: new orders go last. */
enum class TagSortMode { ALPHA, CUSTOM, USED }
/**
 * What happens to the original after a successful MP4 conversion. REPLACE: the MP4 takes the
 * original's place (same folder, tags, position, cover) and the original goes to the trash, so it
 * stays recoverable. ASK: previous behaviour (prompt). KEEP_BOTH: keep both, no prompt.
 */
enum class ConvertAfter { REPLACE, ASK, KEEP_BOTH }
/** How tags look on covers: round marks with an icon or initial (default), or full-colour pills with the alias. */
enum class CoverTagStyle { BUBBLE, PILL }

/** Shape of the covers in the player's filmstrip. */
enum class StripShape { RECT, SQUARE, CIRCLE }

/** Which optional details are drawn on file/folder cells. */
data class DisplayPrefs(
    val showFileInfo: Boolean = true,     // size + duration captions
    val showTagsOnCover: Boolean = true,  // tag badges over grid thumbnails
    val showDateHeaders: Boolean = true,  // "Oggi / Ieri / date" grouping
    val showFolderInfo: Boolean = true,   // per-folder item count + size
    val foldersHorizontal: Boolean = false, // folders in one row scrolling sideways instead of a grid
    val showNoteFab: Boolean = true,      // the "new note" floating button
    val showRandomFab: Boolean = true,    // the "random" floating button
    /** Rows of tag badges on a grid cover: 0 = automatic (by cover size), else 1..3. */
    val coverTagRows: Int = 0,
    val coverTagStyle: CoverTagStyle = CoverTagStyle.BUBBLE,
    /** Size of the tags on covers: 0 small, 1 medium, 2 large (marks, gaps and tray scale together). */
    val coverTagSize: Int = 1,
    /** Give each tag its own stable colour (same tag = same colour everywhere). */
    val tagColors: Boolean = true,
    /** Colour (ARGB) of every tag without its own colour; null = automatic, one per name. */
    val defaultTagColor: Int? = null,
    val showDurationBadge: Boolean = true, // "3:12" on video covers
    val showStatsStrip: Boolean = false,    // the counts strip above the grid (off in the DS look)
    /** Resume videos where they were left, with a progress bar on their cover. */
    val resumePlayback: Boolean = true,
    /** Tag pickers: show a "Recenti" row with the last used tags. */
    val showRecentTags: Boolean = true,
    /** How many recently used tags the "Recenti" row (and the viewer's quick tags) show. */
    val recentTagsCount: Int = 6,
    /** Viewer: quick-tag bar (pinned + recent tags, one tap to toggle). */
    val viewerQuickTags: Boolean = true,
    /** The faces of the people in a media, top-left on its cover. */
    val coverPeople: Boolean = true,
    /** Their size: 0 small, 1 medium, 2 large. */
    val coverPeopleSize: Int = 1,
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

/** A backup made on this device: when (epoch ms), how many files, the archive's size if known. */
data class LastBackup(val at: Long, val files: Int, val bytes: Long?)

data class Settings(
    val autoLockMinutes: Int = 1,
    val deleteOriginalPolicy: DeleteOriginalPolicy = DeleteOriginalPolicy.ASK,
    val viewMode: ViewMode = ViewMode.GRID,
    val gridColumns: Int = 3,
    /** Grid columns while the phone is in landscape (chosen separately from portrait). */
    val gridColumnsLandscape: Int = 5,
    val sortKey: SortKey = SortKey.DATE,
    val sortAscending: Boolean = false,
    /** Filter the current folder (root files at the root), rather than the whole vault. */
    val filterCurrentFolderOnly: Boolean = false,
    /** The folders group in Cartelle is folded down to its heading. */
    val foldersCollapsed: Boolean = false,
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
    /** Viewer: orientation kept by the player's rotation-lock button (an ActivityInfo value); -1 = free. */
    val viewerRotationLock: Int = -1,
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
    /** Portrait: the info panel shares the screen with the media, which keeps playing above it. */
    val splitDetails: Boolean = false,
    /** Player: hold on a side to play faster until release. */
    val holdForSpeed: Boolean = true,
    /** Speed while holding, in tenths (15 = 1.5x, 20 = 2x, 30 = 3x). */
    val holdSpeedX10: Int = 20,
    /** Seconds the player controls stay visible after a tap (2 / 4 / 8). */
    val controlsTimeoutSec: Int = 4,
    /** Intro/outro: analyse each new video's audio in the background. */
    val introAnalyzeNew: Boolean = true,
    /** People: analyse each new photo and video's faces in the background. */
    val facesAnalyzeNew: Boolean = true,
    /** People: marks on the video's progress bar where the people show up. */
    val faceMoments: Boolean = false,
    /** People on Home: "off", "blur" (blurred until touched) or "on". */
    val peopleOnHome: String = "blur",
    /**
     * Recognition: "all" asks about every face found (grouped in "Da sistemare"); "known" only offers
     * faces like a person the user gave at least two faces, and leaves the others in the details.
     */
    val facesMode: String = "all",
    /** Details panel: the whole tag library open ("Mostra tutte"), remembered across media. */
    val detailsAllTags: Boolean = false,
    /** Order of the people page: a [PeopleSort] name, and its direction. */
    val peopleSort: String = "RELEVANCE",
    val peopleSortAscending: Boolean = false,
    /** Intro/outro: fingerprint only the first and last minute of each video (much faster). */
    val introQuickScan: Boolean = true,
    /** Player: skip a known intro / outro by itself instead of offering the button. */
    val introAutoSkip: Boolean = false,
    val outroAutoSkip: Boolean = false,
    /** Deleting moves files to a trash instead of shredding at once. Off = previous behaviour. */
    val trashEnabled: Boolean = false,
    /** Import: fingerprint each new photo/video and ask when it looks like one in the vault. */
    // Off by default: a look-alike with another name is not a new version, so an import should not
    // stop to offer replacing it (Duplicati e cestino › Simili finds them when wanted).
    val similarOnImport: Boolean = false,
    /** Backup: deflate documents (notes, PDF, text) losslessly; media is always stored as is. */
    val backupCompressDocs: Boolean = false,
    /** Backup: what goes in it (remembered between backups). */
    val backupScope: BackupScope = BackupScope(),
    /** The last backup made on this device, null if none yet. */
    val lastBackup: LastBackup? = null,
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
    private val filterCurrentFolderOnlyKey = booleanPreferencesKey("filter_current_folder_only")
    private val foldersCollapsedKey = booleanPreferencesKey("folders_collapsed")
    private val themeKey = intPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val allowShotsKey = booleanPreferencesKey("allow_screenshots")
    private val showFileInfoKey = booleanPreferencesKey("show_file_info")
    private val showTagsCoverKey = booleanPreferencesKey("show_tags_cover")
    private val showDateHeadersKey = booleanPreferencesKey("show_date_headers")
    private val showFolderInfoKey = booleanPreferencesKey("show_folder_info")
    private val foldersHorizontalKey = booleanPreferencesKey("folders_horizontal")
    private val showNoteFabKey = booleanPreferencesKey("show_note_fab")
    private val showRandomFabKey = booleanPreferencesKey("show_random_fab")
    private val tagSortModeKey = intPreferencesKey("tag_sort_mode")
    private val coverTagRowsKey = intPreferencesKey("cover_tag_rows")
    private val coverTagStyleKey = intPreferencesKey("cover_tag_style")
    private val coverTagSizeKey = intPreferencesKey("cover_tag_size")
    private val tagColorsKey = booleanPreferencesKey("tag_colors")
    private val defaultTagColorKey = intPreferencesKey("default_tag_color")
    private val durationBadgeKey = booleanPreferencesKey("duration_badge")
    private val coverPeopleKey = booleanPreferencesKey("cover_people")
    private val coverPeopleSizeKey = intPreferencesKey("cover_people_size")
    // The 4K/HD/SD cover badge is gone; the key stays only so a reset clears the old value.
    private val qualityBadgeKey = booleanPreferencesKey("quality_badge")
    private val statsStripKey = booleanPreferencesKey("stats_strip")
    private val videoLoopKey = booleanPreferencesKey("video_loop")
    private val videoMutedKey = booleanPreferencesKey("video_start_muted")
    private val resumeKey = booleanPreferencesKey("resume_playback")
    private val trashKey = booleanPreferencesKey("trash_enabled")
    private val similarOnImportKey = booleanPreferencesKey("similar_on_import")
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
    private val rotationLockKey = intPreferencesKey("viewer_rotation_lock")
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
    private val splitDetailsKey = booleanPreferencesKey("split_details")
    private val holdSpeedOnKey = booleanPreferencesKey("hold_speed_on")
    private val holdSpeedKey = intPreferencesKey("hold_speed_x10")
    private val controlsTimeoutKey = intPreferencesKey("controls_timeout_sec")
    private val recentTagsKey = booleanPreferencesKey("recent_tags")
    private val recentTagsCountKey = intPreferencesKey("recent_tags_count")
    private val backupCompressKey = booleanPreferencesKey("backup_compress_docs")
    private val lastBackupAtKey = androidx.datastore.preferences.core.longPreferencesKey("last_backup_at")
    private val lastBackupFilesKey = intPreferencesKey("last_backup_files")
    private val lastBackupBytesKey = androidx.datastore.preferences.core.longPreferencesKey("last_backup_bytes")
    private val quickTagsKey = booleanPreferencesKey("viewer_quick_tags")
    private val introAnalyzeKey = booleanPreferencesKey("intro_analyze_new")
    private val facesAnalyzeKey = booleanPreferencesKey("faces_analyze_new")
    private val faceMomentsKey = booleanPreferencesKey("face_moments")
    private val peopleHomeKey = stringPreferencesKey("people_on_home")
    private val facesModeKey = stringPreferencesKey("faces_mode")
    private val detailsAllTagsKey = androidx.datastore.preferences.core.booleanPreferencesKey("details_all_tags")
    private val peopleSortKey = stringPreferencesKey("people_sort")
    private val peopleSortAscKey = booleanPreferencesKey("people_sort_asc")
    private val introQuickKey = booleanPreferencesKey("intro_quick_scan")
    private val introAutoKey = booleanPreferencesKey("intro_auto_skip")
    private val outroAutoKey = booleanPreferencesKey("outro_auto_skip")
    private val bkFilesKey = booleanPreferencesKey("backup_files")
    private val bkRootFilesKey = booleanPreferencesKey("backup_root_files")
    private val bkExcludedFoldersKey = stringPreferencesKey("backup_excluded_folders")
    private val bkTagsKey = booleanPreferencesKey("backup_tags")
    private val bkPeopleKey = booleanPreferencesKey("backup_people")
    private val bkAnalysisKey = booleanPreferencesKey("backup_analysis")
    private val bkSettingsKey = booleanPreferencesKey("backup_settings")
    private val bkCoversKey = booleanPreferencesKey("backup_covers")

    /** The stored preferences; a read error (storage trouble) gives the defaults instead of crashing whoever collects. */
    private val prefs: Flow<androidx.datastore.preferences.core.Preferences> = context.dataStore.data
        .catch { e -> if (e is java.io.IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }

    val settings: Flow<Settings> = prefs.map(::toSettings)

    /** The typed settings of the stored preferences [p] (a value of another type throws ClassCastException). */
    private fun toSettings(p: androidx.datastore.preferences.core.Preferences): Settings =
        Settings(
            autoLockMinutes = p[autoLock] ?: 1,
            deleteOriginalPolicy = DeleteOriginalPolicy.entries
                .getOrElse(p[delPolicy] ?: 0) { DeleteOriginalPolicy.ASK },
            viewMode = ViewMode.entries.getOrElse(p[viewModeKey] ?: 0) { ViewMode.GRID },
            gridColumns = (p[gridColsKey] ?: 3).coerceIn(2, 5),
            gridColumnsLandscape = (p[gridColsLandKey] ?: 5).coerceIn(3, 8),
            sortKey = SortKey.entries.getOrElse(p[sortKeyKey] ?: 0) { SortKey.DATE },
            sortAscending = p[sortAscKey] ?: false,
            filterCurrentFolderOnly = p[filterCurrentFolderOnlyKey] ?: false,
            foldersCollapsed = p[foldersCollapsedKey] ?: false,
            themeMode = ThemeMode.entries.getOrElse(p[themeKey] ?: ThemeMode.DARK.ordinal) { ThemeMode.DARK },
            dynamicColor = p[dynamicKey] ?: false,
            allowScreenshots = p[allowShotsKey] ?: false,
            tagSortMode = TagSortMode.entries.getOrElse(p[tagSortModeKey] ?: 0) { TagSortMode.ALPHA },
            display = DisplayPrefs(
                showFileInfo = p[showFileInfoKey] ?: true,
                showTagsOnCover = p[showTagsCoverKey] ?: true,
                showDateHeaders = p[showDateHeadersKey] ?: true,
                showFolderInfo = p[showFolderInfoKey] ?: true,
                foldersHorizontal = p[foldersHorizontalKey] ?: false,
                showNoteFab = p[showNoteFabKey] ?: true,
                showRandomFab = p[showRandomFabKey] ?: true,
                coverTagRows = (p[coverTagRowsKey] ?: 0).coerceIn(0, 3),
                coverTagStyle = CoverTagStyle.entries.getOrElse(p[coverTagStyleKey] ?: 0) { CoverTagStyle.BUBBLE },
                coverTagSize = (p[coverTagSizeKey] ?: 1).coerceIn(0, 2),
                tagColors = p[tagColorsKey] ?: true,
                // A preset of an earlier palette reads as its lighter successor.
                defaultTagColor = p[defaultTagColorKey]?.let { com.cripta.app.ui.theme.currentPresetColor(it) },
                showDurationBadge = p[durationBadgeKey] ?: true,
                coverPeople = p[coverPeopleKey] ?: true,
                coverPeopleSize = (p[coverPeopleSizeKey] ?: 1).coerceIn(0, 2),
                showStatsStrip = p[statsStripKey] ?: false,
                resumePlayback = p[resumeKey] ?: true,
                showRecentTags = p[recentTagsKey] ?: true,
                recentTagsCount = (p[recentTagsCountKey] ?: 6).coerceIn(1, 20),
                viewerQuickTags = p[quickTagsKey] ?: true,
            ),
            trashEnabled = p[trashKey] ?: false,
            similarOnImport = p[similarOnImportKey] ?: false,
            backupCompressDocs = p[backupCompressKey] ?: false,
            lastBackup = p[lastBackupAtKey]?.let { LastBackup(it, p[lastBackupFilesKey] ?: 0, p[lastBackupBytesKey]) },
            backupScope = BackupScope(
                files = p[bkFilesKey] ?: true,
                excludedFolders = BackupScope.parseIds(p[bkExcludedFoldersKey]),
                rootFiles = p[bkRootFilesKey] ?: true,
                tags = p[bkTagsKey] ?: true,
                people = p[bkPeopleKey] ?: true,
                analysis = p[bkAnalysisKey] ?: true,
                settings = p[bkSettingsKey] ?: true,
                covers = p[bkCoversKey] ?: true,
            ),
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
            viewerRotationLock = p[rotationLockKey] ?: -1,
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
            splitDetails = p[splitDetailsKey] ?: false,
            holdForSpeed = p[holdSpeedOnKey] ?: true,
            holdSpeedX10 = (p[holdSpeedKey] ?: 20).let { if (it in listOf(15, 20, 30)) it else 20 },
            controlsTimeoutSec = (p[controlsTimeoutKey] ?: 4).let { if (it in listOf(2, 4, 8)) it else 4 },
            introAnalyzeNew = p[introAnalyzeKey] ?: true,
            facesAnalyzeNew = p[facesAnalyzeKey] ?: true,
            faceMoments = p[faceMomentsKey] ?: false,
            peopleOnHome = (p[peopleHomeKey] ?: "blur").let { if (it in listOf("off", "blur", "on")) it else "blur" },
            facesMode = (p[facesModeKey] ?: "all").let { if (it in listOf("all", "known")) it else "all" },
            detailsAllTags = p[detailsAllTagsKey] ?: false,
            peopleSort = (p[peopleSortKey] ?: "RELEVANCE").let { k -> if (PeopleSort.entries.any { it.name == k }) k else "RELEVANCE" },
            peopleSortAscending = p[peopleSortAscKey] ?: false,
            introQuickScan = p[introQuickKey] ?: true,
            introAutoSkip = p[introAutoKey] ?: false,
            outroAutoSkip = p[outroAutoKey] ?: false,
        )

    suspend fun setAllowScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[allowShotsKey] = enabled }
    }

    suspend fun setShowFileInfo(v: Boolean) { context.dataStore.edit { it[showFileInfoKey] = v } }
    suspend fun setShowTagsOnCover(v: Boolean) { context.dataStore.edit { it[showTagsCoverKey] = v } }
    suspend fun setShowDateHeaders(v: Boolean) { context.dataStore.edit { it[showDateHeadersKey] = v } }
    suspend fun setShowFolderInfo(v: Boolean) { context.dataStore.edit { it[showFolderInfoKey] = v } }
    suspend fun setFoldersHorizontal(v: Boolean) { context.dataStore.edit { it[foldersHorizontalKey] = v } }
    suspend fun setShowNoteFab(v: Boolean) { context.dataStore.edit { it[showNoteFabKey] = v } }
    suspend fun setShowRandomFab(v: Boolean) { context.dataStore.edit { it[showRandomFabKey] = v } }
    suspend fun setCoverTagRows(v: Int) { context.dataStore.edit { it[coverTagRowsKey] = v.coerceIn(0, 3) } }
    suspend fun setCoverTagStyle(v: CoverTagStyle) { context.dataStore.edit { it[coverTagStyleKey] = v.ordinal } }
    suspend fun setCoverTagSize(v: Int) { context.dataStore.edit { it[coverTagSizeKey] = v.coerceIn(0, 2) } }
    suspend fun setTagColors(v: Boolean) { context.dataStore.edit { it[tagColorsKey] = v } }
    suspend fun setDefaultTagColor(argb: Int?) {
        context.dataStore.edit { if (argb == null) it.remove(defaultTagColorKey) else it[defaultTagColorKey] = argb }
    }
    suspend fun setShowDurationBadge(v: Boolean) { context.dataStore.edit { it[durationBadgeKey] = v } }
    suspend fun setCoverPeople(v: Boolean) { context.dataStore.edit { it[coverPeopleKey] = v } }
    suspend fun setCoverPeopleSize(v: Int) { context.dataStore.edit { it[coverPeopleSizeKey] = v.coerceIn(0, 2) } }
    suspend fun setShowStatsStrip(v: Boolean) { context.dataStore.edit { it[statsStripKey] = v } }
    suspend fun setSeekStep(v: Int) { context.dataStore.edit { it[seekStepKey] = v } }
    suspend fun setGestureControls(v: Boolean) { context.dataStore.edit { it[gestureKey] = v } }
    suspend fun setGestureVolume(v: Boolean) { context.dataStore.edit { it[gestureVolumeKey] = v } }
    suspend fun setAutoRotate(v: Boolean) { context.dataStore.edit { it[autoRotateKey] = v } }
    suspend fun setViewerRotationLock(orientation: Int) { context.dataStore.edit { it[rotationLockKey] = orientation } }
    suspend fun setPictureInPicture(v: Boolean) { context.dataStore.edit { it[pipKey] = v } }
    suspend fun setAutoNext(v: Boolean) { context.dataStore.edit { it[autoNextKey] = v } }
    suspend fun setAutoNextSec(v: Int) { context.dataStore.edit { it[autoNextSecKey] = v } }
    suspend fun setSwipeToClose(v: Boolean) { context.dataStore.edit { it[swipeCloseKey] = v } }
    suspend fun setSplitDetails(v: Boolean) { context.dataStore.edit { it[splitDetailsKey] = v } }
    suspend fun setHoldForSpeed(v: Boolean) { context.dataStore.edit { it[holdSpeedOnKey] = v } }
    suspend fun setHoldSpeed(x10: Int) { context.dataStore.edit { it[holdSpeedKey] = x10 } }
    suspend fun setControlsTimeout(sec: Int) { context.dataStore.edit { it[controlsTimeoutKey] = sec } }
    suspend fun setIntroAnalyzeNew(v: Boolean) { context.dataStore.edit { it[introAnalyzeKey] = v } }
    suspend fun setFacesAnalyzeNew(v: Boolean) { context.dataStore.edit { it[facesAnalyzeKey] = v } }
    suspend fun setFaceMoments(v: Boolean) { context.dataStore.edit { it[faceMomentsKey] = v } }
    suspend fun setPeopleOnHome(v: String) { context.dataStore.edit { it[peopleHomeKey] = v } }
    suspend fun setFacesMode(v: String) { context.dataStore.edit { it[facesModeKey] = v } }
    suspend fun setDetailsAllTags(v: Boolean) { context.dataStore.edit { it[detailsAllTagsKey] = v } }
    suspend fun setPeopleSort(key: PeopleSort, ascending: Boolean) {
        context.dataStore.edit { it[peopleSortKey] = key.name; it[peopleSortAscKey] = ascending }
    }
    suspend fun setIntroQuickScan(v: Boolean) { context.dataStore.edit { it[introQuickKey] = v } }
    suspend fun setIntroAutoSkip(v: Boolean) { context.dataStore.edit { it[introAutoKey] = v } }
    suspend fun setOutroAutoSkip(v: Boolean) { context.dataStore.edit { it[outroAutoKey] = v } }
    suspend fun setViewerFilmstrip(v: Boolean) { context.dataStore.edit { it[filmstripKey] = v } }
    suspend fun setPauseOnLeave(v: Boolean) { context.dataStore.edit { it[pauseOnLeaveKey] = v } }
    suspend fun setFilmstripShape(v: StripShape) { context.dataStore.edit { it[stripShapeKey] = v.ordinal } }
    suspend fun setFilmstripSpan(v: Int) { context.dataStore.edit { it[stripSpanKey] = v.coerceIn(1, 4) } }
    suspend fun setFilmstripSize(v: Int) { context.dataStore.edit { it[stripSizeKey] = v.coerceIn(0, 2) } }
    suspend fun setFilmstripTrueAspect(v: Boolean) { context.dataStore.edit { it[stripAspectKey] = v } }
    suspend fun setUpdatePrerelease(v: Boolean) { context.dataStore.edit { it[updatePreKey] = v } }
    suspend fun setShowRecentTags(v: Boolean) { context.dataStore.edit { it[recentTagsKey] = v } }
    suspend fun setBackupCompressDocs(v: Boolean) { context.dataStore.edit { it[backupCompressKey] = v } }
    suspend fun setLastBackup(v: LastBackup) {
        context.dataStore.edit {
            it[lastBackupAtKey] = v.at
            it[lastBackupFilesKey] = v.files
            if (v.bytes != null) it[lastBackupBytesKey] = v.bytes else it.remove(lastBackupBytesKey)
        }
    }
    suspend fun setBackupScope(v: BackupScope) {
        context.dataStore.edit {
            it[bkFilesKey] = v.files
            it[bkRootFilesKey] = v.rootFiles
            it[bkExcludedFoldersKey] = BackupScope.formatIds(v.excludedFolders)
            it[bkTagsKey] = v.tags
            it[bkPeopleKey] = v.people
            it[bkAnalysisKey] = v.analysis
            it[bkSettingsKey] = v.settings
            it[bkCoversKey] = v.covers
        }
    }

    /** Every stored preference for a backup ([BackupSettings] leaves out the device's own). */
    suspend fun exportForBackup(): ByteArray =
        BackupSettings.encode(context.dataStore.data.first().asMap().mapKeys { it.key.name })

    /**
     * Put back the preferences of a backup: each one in it replaces this device's value, the others
     * (and the excluded ones, see [BackupSettings]) stay as they are.
     */
    suspend fun importFromBackup(bytes: ByteArray) {
        val values = BackupSettings.decode(bytes)
        context.dataStore.edit { p ->
            val current = p.asMap().mapKeys { it.key.name }
            for ((k, v) in values) {
                // A preference this device keeps with another type is left alone: written, every
                // typed read of it (appearance, tags…) would throw.
                val here = current[k]
                if (here != null && !sameKind(here, v)) continue
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is Boolean -> p[booleanPreferencesKey(k)] = v
                    is Int -> p[intPreferencesKey(k)] = v
                    is Long -> p[androidx.datastore.preferences.core.longPreferencesKey(k)] = v
                    is Float -> p[androidx.datastore.preferences.core.floatPreferencesKey(k)] = v
                    is Double -> p[androidx.datastore.preferences.core.doublePreferencesKey(k)] = v
                    is String -> p[stringPreferencesKey(k)] = v
                    is Set<*> -> p[androidx.datastore.preferences.core.stringSetPreferencesKey(k)] = v as Set<String>
                }
            }
            // One not on this device yet, with a type its reads do not expect: checked before the
            // write, which a throw here cancels (written and undone after, the wrong value already
            // reached whoever reads the settings). The archive's settings are refused, rather than
            // an app that cannot read them.
            if (runCatching { toSettings(p) }.exceptionOrNull() is ClassCastException)
                throw IllegalArgumentException(com.cripta.app.str(com.cripta.app.R.string.settings_invalid))
        }
    }

    private fun sameKind(a: Any, b: Any): Boolean = when (a) {
        is Set<*> -> b is Set<*>
        else -> a::class == b::class
    }

    suspend fun setRecentTagsCount(v: Int) { context.dataStore.edit { it[recentTagsCountKey] = v.coerceIn(1, 20) } }
    suspend fun setViewerQuickTags(v: Boolean) { context.dataStore.edit { it[quickTagsKey] = v } }
    suspend fun setConvertAfter(v: ConvertAfter) { context.dataStore.edit { it[convertAfterKey] = v.ordinal; it[convertAfterChosenKey] = true } }
    suspend fun setResumePlayback(v: Boolean) { context.dataStore.edit { it[resumeKey] = v } }
    suspend fun setTrashEnabled(v: Boolean) { context.dataStore.edit { it[trashKey] = v } }
    suspend fun setSimilarOnImport(v: Boolean) { context.dataStore.edit { it[similarOnImportKey] = v } }
    suspend fun setTrashDays(v: Int) { context.dataStore.edit { it[trashDaysKey] = v.coerceIn(1, 90) } }
    /**
     * The vault was wiped (reset): what named its folders and tags by id goes with it. Kept, a new
     * folder or tag given the same id inherited the old one's exclusion from backups, or became
     * the downloads' destination, without the user choosing it.
     */
    suspend fun forgetVaultIds() {
        context.dataStore.edit {
            it.remove(dlFolderKey); it.remove(dlTagsKey); it.remove(bkExcludedFoldersKey)
        }
    }
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

    suspend fun setFilterCurrentFolderOnly(enabled: Boolean) {
        context.dataStore.edit { it[filterCurrentFolderOnlyKey] = enabled }
    }

    suspend fun setFoldersCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[foldersCollapsedKey] = collapsed }
    }

    /** Independent sort for screens without an open folder. The vault root keeps its legacy keys. */
    fun screenSort(screen: String): Flow<Pair<SortKey, Boolean>> = prefs.map { p ->
        SortKey.entries.getOrElse(p[intPreferencesKey("sort_${screen}_key")] ?: 0) { SortKey.DATE } to
            (p[booleanPreferencesKey("sort_${screen}_asc")] ?: false)
    }

    suspend fun setScreenSort(screen: String, key: SortKey, ascending: Boolean) {
        context.dataStore.edit {
            it[intPreferencesKey("sort_${screen}_key")] = key.ordinal
            it[booleanPreferencesKey("sort_${screen}_asc")] = ascending
        }
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
                showFolderInfoKey, foldersHorizontalKey, showNoteFabKey, showRandomFabKey, filterCurrentFolderOnlyKey, foldersCollapsedKey, durationBadgeKey, qualityBadgeKey,
                coverPeopleKey, coverPeopleSizeKey)
            "ETICHETTE" -> listOf(showTagsCoverKey, coverTagRowsKey, coverTagStyleKey, coverTagSizeKey, tagColorsKey, defaultTagColorKey,
                recentTagsKey, recentTagsCountKey, quickTagsKey, tagSortModeKey)
            "VISUALIZZATORE" -> listOf(resumeKey, videoLoopKey, videoMutedKey,
                seekStepKey, gestureKey, gestureVolumeKey, autoRotateKey, rotationLockKey, pipKey, filmstripKey, pauseOnLeaveKey, stripShapeKey, stripSpanKey, stripSizeKey, stripAspectKey, autoNextKey, autoNextSecKey,
                swipeCloseKey, splitDetailsKey, holdSpeedOnKey, holdSpeedKey, controlsTimeoutKey)
            "ANALISI" -> listOf(introAnalyzeKey, introQuickKey, introAutoKey, outroAutoKey, facesAnalyzeKey, faceMomentsKey, peopleHomeKey, facesModeKey)
            "SICUREZZA" -> listOf(autoLock, allowShotsKey)
            "IMPORT" -> listOf(delPolicy, convertAfterKey, convertAfterChosenKey)
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
