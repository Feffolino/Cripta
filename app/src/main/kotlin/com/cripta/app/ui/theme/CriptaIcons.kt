package com.cripta.app.ui.theme

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.cripta.app.R

/**
 * Every UI icon of the app, in one place: Tabler Icons v3.49.0 (MIT, assets/licenses), converted to
 * vector drawables (res/drawable/tabler_*.xml, 24dp, stroke 2). Outline icons at rest; the filled ones
 * where the meaning is "on" (a favourite star, the current tab, a selected tick) or where Material drew
 * them solid (play, pause, skip). Properties keep the Material names they replace.
 *
 * Plain values, not composables, so they work where Material's did: enums, lists, gesture callbacks.
 * Built on first use from the app's resources ([init] runs in CriptaApp.onCreate). The tags' own
 * icons are not here: they are TagIcons (Fluent Emoji Flat).
 */
object CriptaIcons {
    private lateinit var res: Resources

    fun init(resources: Resources) { res = resources }

    private fun load(@DrawableRes id: Int): ImageVector = ImageVector.vectorResource(null, res, id)

    val Add: ImageVector by lazy { load(R.drawable.tabler_plus) }
    val ArrowBack: ImageVector by lazy { load(R.drawable.tabler_arrow_left) }
    val ArrowDownward: ImageVector by lazy { load(R.drawable.tabler_arrow_down) }
    val ArrowDropDown: ImageVector by lazy { load(R.drawable.tabler_caret_down_filled) }
    val ArrowUpward: ImageVector by lazy { load(R.drawable.tabler_arrow_up) }
    val AspectRatio: ImageVector by lazy { load(R.drawable.tabler_aspect_ratio) }
    val Autorenew: ImageVector by lazy { load(R.drawable.tabler_transform) }
    val Backspace: ImageVector by lazy { load(R.drawable.tabler_backspace) }
    val Backup: ImageVector by lazy { load(R.drawable.tabler_cloud_upload) }
    val Block: ImageVector by lazy { load(R.drawable.tabler_ban) }
    val BrightnessHigh: ImageVector by lazy { load(R.drawable.tabler_brightness_up) }
    val BrightnessLow: ImageVector by lazy { load(R.drawable.tabler_brightness_down) }
    val BrightnessMedium: ImageVector by lazy { load(R.drawable.tabler_brightness_half) }
    val Casino: ImageVector by lazy { load(R.drawable.tabler_dice_5) }
    val Check: ImageVector by lazy { load(R.drawable.tabler_check) }
    val CheckCircle: ImageVector by lazy { load(R.drawable.tabler_circle_check_filled) }
    val ChevronRight: ImageVector by lazy { load(R.drawable.tabler_chevron_right) }
    val Clear: ImageVector by lazy { load(R.drawable.tabler_x) }
    val Close: ImageVector by lazy { load(R.drawable.tabler_x) }
    val CloudDownload: ImageVector by lazy { load(R.drawable.tabler_cloud_download) }
    val ContentCopy: ImageVector by lazy { load(R.drawable.tabler_copy) }
    val ContentPaste: ImageVector by lazy { load(R.drawable.tabler_clipboard) }
    val CreateNewFolder: ImageVector by lazy { load(R.drawable.tabler_folder_plus) }
    val Delete: ImageVector by lazy { load(R.drawable.tabler_trash) }
    val DeleteSweep: ImageVector by lazy { load(R.drawable.tabler_trash_x) }
    val Merge: ImageVector by lazy { load(R.drawable.tabler_arrow_merge) }
    val Description: ImageVector by lazy { load(R.drawable.tabler_file_text) }
    val Download: ImageVector by lazy { load(R.drawable.tabler_download) }
    val DownloadFilled: ImageVector by lazy { load(R.drawable.tabler_download_filled) }
    val DragHandle: ImageVector by lazy { load(R.drawable.tabler_grip_horizontal) }
    val DriveFileMove: ImageVector by lazy { load(R.drawable.tabler_folder_symlink) }
    val DriveFileRenameOutline: ImageVector by lazy { load(R.drawable.tabler_forms) }
    val Edit: ImageVector by lazy { load(R.drawable.tabler_pencil) }
    val EnhancedEncryption: ImageVector by lazy { load(R.drawable.tabler_lock_plus) }
    val ErrorOutline: ImageVector by lazy { load(R.drawable.tabler_alert_circle) }
    val ExpandLess: ImageVector by lazy { load(R.drawable.tabler_chevron_up) }
    val ExpandMore: ImageVector by lazy { load(R.drawable.tabler_chevron_down) }
    val Face: ImageVector by lazy { load(R.drawable.tabler_mood_smile) }
    val FastForward: ImageVector by lazy { load(R.drawable.tabler_player_track_next_filled) }
    val FastRewind: ImageVector by lazy { load(R.drawable.tabler_player_track_prev_filled) }
    val FilterList: ImageVector by lazy { load(R.drawable.tabler_filter) }
    val Fingerprint: ImageVector by lazy { load(R.drawable.tabler_fingerprint) }
    val Folder: ImageVector by lazy { load(R.drawable.tabler_folder) }
    val FolderOpen: ImageVector by lazy { load(R.drawable.tabler_folder_open) }
    val FolderFilled: ImageVector by lazy { load(R.drawable.tabler_folder_filled) }
    val GraphicEq: ImageVector by lazy { load(R.drawable.tabler_wave_sine) }
    val GridView: ImageVector by lazy { load(R.drawable.tabler_layout_grid) }
    val HelpOutline: ImageVector by lazy { load(R.drawable.tabler_help_circle) }
    val History: ImageVector by lazy { load(R.drawable.tabler_history) }
    val Home: ImageVector by lazy { load(R.drawable.tabler_home) }
    val HomeFilled: ImageVector by lazy { load(R.drawable.tabler_home_filled) }
    val Image: ImageVector by lazy { load(R.drawable.tabler_photo) }
    val Info: ImageVector by lazy { load(R.drawable.tabler_info_circle) }
    val InsertDriveFile: ImageVector by lazy { load(R.drawable.tabler_file) }
    val KeyboardArrowDown: ImageVector by lazy { load(R.drawable.tabler_chevron_down) }
    val KeyboardArrowLeft: ImageVector by lazy { load(R.drawable.tabler_chevron_left) }
    val KeyboardArrowRight: ImageVector by lazy { load(R.drawable.tabler_chevron_right) }
    val Label: ImageVector by lazy { load(R.drawable.tabler_tag) }
    val LabelOff: ImageVector by lazy { load(R.drawable.tabler_tag_off) }
    val LightMode: ImageVector by lazy { load(R.drawable.tabler_sun) }
    val Link: ImageVector by lazy { load(R.drawable.tabler_link) }
    val Lock: ImageVector by lazy { load(R.drawable.tabler_lock) }
    val MoreVert: ImageVector by lazy { load(R.drawable.tabler_dots_vertical) }
    val Movie: ImageVector by lazy { load(R.drawable.tabler_movie) }
    val Palette: ImageVector by lazy { load(R.drawable.tabler_palette) }
    val Pause: ImageVector by lazy { load(R.drawable.tabler_player_pause_filled) }
    val Photo: ImageVector by lazy { load(R.drawable.tabler_photo) }
    val PhotoLibrary: ImageVector by lazy { load(R.drawable.tabler_library_photo) }
    val PictureAsPdf: ImageVector by lazy { load(R.drawable.tabler_file_type_pdf) }
    val PictureInPictureAlt: ImageVector by lazy { load(R.drawable.tabler_picture_in_picture) }
    val PlayArrow: ImageVector by lazy { load(R.drawable.tabler_player_play_filled) }
    val PlayCircle: ImageVector by lazy { load(R.drawable.tabler_circle_caret_right) }
    val PushPin: ImageVector by lazy { load(R.drawable.tabler_pin) }
    val Refresh: ImageVector by lazy { load(R.drawable.tabler_refresh) }
    val RemoveCircleOutline: ImageVector by lazy { load(R.drawable.tabler_circle_minus) }
    val Repeat: ImageVector by lazy { load(R.drawable.tabler_repeat) }
    val RestoreFromTrash: ImageVector by lazy { load(R.drawable.tabler_restore) }
    val Schedule: ImageVector by lazy { load(R.drawable.tabler_clock) }
    val ScreenLockRotation: ImageVector by lazy { load(R.drawable.tabler_lock) }
    val ScreenRotation: ImageVector by lazy { load(R.drawable.tabler_device_mobile_rotated) }
    val SdStorage: ImageVector by lazy { load(R.drawable.tabler_device_sd_card) }
    val Search: ImageVector by lazy { load(R.drawable.tabler_search) }
    val SelectAll: ImageVector by lazy { load(R.drawable.tabler_select_all) }
    val Settings: ImageVector by lazy { load(R.drawable.tabler_settings) }
    val SettingsFilled: ImageVector by lazy { load(R.drawable.tabler_settings_filled) }
    val Shuffle: ImageVector by lazy { load(R.drawable.tabler_arrows_shuffle) }
    val SkipNext: ImageVector by lazy { load(R.drawable.tabler_player_skip_forward_filled) }
    val Sort: ImageVector by lazy { load(R.drawable.tabler_arrows_sort) }
    val Speed: ImageVector by lazy { load(R.drawable.tabler_gauge) }
    val Star: ImageVector by lazy { load(R.drawable.tabler_star_filled) }
    val StarBorder: ImageVector by lazy { load(R.drawable.tabler_star) }
    val SwapHoriz: ImageVector by lazy { load(R.drawable.tabler_arrows_left_right) }
    val SystemUpdate: ImageVector by lazy { load(R.drawable.tabler_device_mobile_down) }
    val ThumbUp: ImageVector by lazy { load(R.drawable.tabler_thumb_up) }
    val Timeline: ImageVector by lazy { load(R.drawable.tabler_timeline) }
    val Timer: ImageVector by lazy { load(R.drawable.tabler_hourglass) }
    val Tune: ImageVector by lazy { load(R.drawable.tabler_adjustments_horizontal) }
    val Upload: ImageVector by lazy { load(R.drawable.tabler_upload) }
    val ViewCarousel: ImageVector by lazy { load(R.drawable.tabler_carousel_horizontal) }
    val ViewList: ImageVector by lazy { load(R.drawable.tabler_list) }
    val Visibility: ImageVector by lazy { load(R.drawable.tabler_eye) }
    val VisibilityOff: ImageVector by lazy { load(R.drawable.tabler_eye_off) }
    val VolumeDown: ImageVector by lazy { load(R.drawable.tabler_volume_2) }
    val VolumeOff: ImageVector by lazy { load(R.drawable.tabler_volume_off) }
    val VolumeUp: ImageVector by lazy { load(R.drawable.tabler_volume) }
    val ZoomIn: ImageVector by lazy { load(R.drawable.tabler_zoom_in) }

    // The note editor's formatting bar (Markdown).
    val FormatBold: ImageVector by lazy { load(R.drawable.tabler_bold) }
    val FormatItalic: ImageVector by lazy { load(R.drawable.tabler_italic) }
    val FormatHeading: ImageVector by lazy { load(R.drawable.tabler_heading) }
    val FormatChecklist: ImageVector by lazy { load(R.drawable.tabler_checkbox) }
    val FormatNumbered: ImageVector by lazy { load(R.drawable.tabler_list_numbers) }
}
