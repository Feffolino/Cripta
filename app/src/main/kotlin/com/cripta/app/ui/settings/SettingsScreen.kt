package com.cripta.app.ui.settings

import com.cripta.app.plural

import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.width
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cripta.app.ui.theme.Motion
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import kotlinx.coroutines.launch
import com.cripta.app.data.SortKey
import com.cripta.app.data.ViewMode
import androidx.compose.material3.AlertDialog
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.vault.LabelEditorDialog
import com.cripta.app.ui.vault.tagAlias

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** Opens a file in the viewer (intro results). */
    onOpenFile: (String) -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val pkgInfo = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val updateState by vm.update.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    var pendingExport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> pendingExport = uri }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    var editTag by remember { mutableStateOf<TagEntity?>(null) }
    var deleteTag by remember { mutableStateOf<TagEntity?>(null) }
    var addTag by remember { mutableStateOf(false) }
    var confirmRandomColors by remember { mutableStateOf(false) }

    val dupMode by vm.dupMode.collectAsStateWithLifecycle()
    val dupScanning by vm.dupScanning.collectAsStateWithLifecycle()
    val dupProgress by vm.dupProgress.collectAsStateWithLifecycle()
    val dupScannedCount by vm.dupScannedCount.collectAsStateWithLifecycle()
    val exactGroups by vm.exactGroups.collectAsStateWithLifecycle()
    val dupGroups by vm.dupGroups.collectAsStateWithLifecycle()
    val dupNotice by vm.dupNotice.collectAsStateWithLifecycle()
    val dupWaiting by vm.dupResultWaiting.collectAsStateWithLifecycle()
    val dupLast by vm.dupLastResult.collectAsStateWithLifecycle()
    val dupFreed by vm.dupFreed.collectAsStateWithLifecycle()
    val dupApart by vm.dupApart.collectAsStateWithLifecycle()
    val dupUndo by vm.dupUndo.collectAsStateWithLifecycle()
    val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
    val backupProgress by vm.backupProgress.collectAsStateWithLifecycle()
    val backupEstimate by vm.backupEstimate.collectAsStateWithLifecycle()
    val backupFolders by vm.backupFolders.collectAsStateWithLifecycle()

    var showAllTags by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val trashedFolders by vm.trashedFolders.collectAsStateWithLifecycle()

    // Two-level navigation: the category list, or one category page. Back returns to the list.
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // Section a search result asked for (scrolled to once the page opens), and the one marked for a moment.
    var anchor by remember { mutableStateOf<String?>(null) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    /** "Mostra altre opzioni" state of the page being shown (opened by a search hit). */
    var confirmReset by remember { mutableStateOf<SettingsPage?>(null) }
    val current = page?.let { p -> SettingsPage.entries.firstOrNull { it.name == p } }
    androidx.activity.compose.BackHandler(enabled = current != null) { page = null }
    // Requests from elsewhere (Home, duplicate-scan notification).
    val requestedPage by vm.requestedPage.collectAsStateWithLifecycle()
    LaunchedEffect(requestedPage) {
        requestedPage?.let { page = it; vm.consumeRequestedPage() }
    }
    LaunchedEffect(Unit) { SettingsTabReselect.events.collect { page = null; anchor = null } }

    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Landscape: the top bar scrolls away (down hides, up reveals), like in Cartelle.
    val scrollBehavior = if (landscape) androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior()
        else androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    val maxW = if (landscape) 1400.dp else 720.dp
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                scrollBehavior = scrollBehavior,
                colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { if (current == null) com.cripta.app.ui.components.HeaderTitle(str(R.string.tab_settings)) else com.cripta.app.ui.components.PageTitle(current.label) },
                // Impostazioni is a tab root: the back arrow only appears inside a category page.
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(CriptaIcons.ArrowBack, str(R.string.back_to_settings))
                        }
                    }
                },
                actions = {
                    if (current != null && current.resettable) {
                        TextButton(onClick = { confirmReset = current }) { com.cripta.app.ui.components.ButtonText(str(R.string.defaults)) }
                    }
                    // Quick lock, always at hand.
                    IconButton(onClick = { vm.lockNow(); onBack() }) { Icon(CriptaIcons.Lock, str(R.string.lock_now)) }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { pad ->
      // List <-> category page: a short crossfade, so the swap reads as one step deeper/back.
      androidx.compose.animation.AnimatedContent(
          targetState = current,
          transitionSpec = {
              // Into a page: it slides in from the right; back to the list: the reverse.
              val dir = if (targetState != null) 1 else -1
              (androidx.compose.animation.fadeIn(tween(Motion.MEDIUM, Motion.SHORT / 2, Motion.EaseOutQuart)) +
                  androidx.compose.animation.slideInHorizontally(Motion.enter(Motion.LONG)) { w -> dir * w / 10 }) togetherWith
                  (androidx.compose.animation.fadeOut(tween(Motion.SHORT, 0, Motion.EaseOutQuart)) +
                      androidx.compose.animation.slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { w -> -dir * w / 14 })
          },
          label = "settingsPage",
      ) { current ->
        if (current == null) {
            MainSettingsList(
                modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()
                    .wrapContentWidth().widthIn(max = maxW).fillMaxWidth(),
                columns = if (landscape) 2 else 1,
                query = query,
                onQuery = { query = it },
                summaries = SettingsPage.entries.associateWith { pageSummary(it, s, tags.size, trashed.size + trashedFolders.size, dupWaiting?.groups?.size) },
                // From a search result: land on its section and keep the query for the way back.
                onOpen = { p, a -> page = p.name; anchor = a; if (a == null) query = "" },
                onLock = { vm.lockNow(); onBack() },
            )
        } else {
          Column(Modifier.fillMaxSize().padding(pad).wrapContentWidth().widthIn(max = maxW).fillMaxWidth()) {
            if (current == SettingsPage.ETICHETTE) {
                // The live preview stays pinned on top while the options scroll under it.
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom) {
                    com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(if (landscape) 96.dp else 132.dp))
                    com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(if (landscape) 72.dp else 92.dp))
                    Text(str(R.string.live_preview), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Each page is a list of named sections: a search result scrolls to its section and marks it.
            val sections: List<Pair<String, @Composable () -> Unit>> = when (current) {
                SettingsPage.ASPETTO -> listOf(
                    sec(str(R.string.sec_theme)) {
                        Section(str(R.string.sec_theme)) {
                            com.cripta.app.ui.components.ChipFlowRow {
                                com.cripta.app.data.ThemeMode.entries.forEach { m ->
                                    com.cripta.app.ui.components.ChoiceChip(
                                        selected = s.themeMode == m,
                                        onClick = { vm.setThemeMode(m) },
                                        label = {
                                            Text(when (m) {
                                                com.cripta.app.data.ThemeMode.SYSTEM -> str(R.string.theme_system)
                                                com.cripta.app.data.ThemeMode.LIGHT -> str(R.string.theme_light)
                                                com.cripta.app.data.ThemeMode.DARK -> str(R.string.theme_dark)
                                            })
                                        },
                                    )
                                }
                            }
                            ToggleRow(str(R.string.dynamic_colours), s.dynamicColor) { vm.setDynamicColor(it) }
                        }
                    },
                    sec(str(R.string.sec_language)) {
                        Section(str(R.string.sec_language), str(R.string.language_hint)) { LanguageRow() }
                    },
                    sec(str(R.string.tab_folders)) {
                        Section(str(R.string.tab_folders), str(R.string.folders_sec_hint)) {
                            ToggleRow(str(R.string.filters_current_folder), s.filterCurrentFolderOnly,
                                desc = str(R.string.filters_current_folder_desc)) {
                                vm.setFilterCurrentFolderOnly(it)
                            }
                            ToggleRow(str(R.string.summary_above_grid), s.display.showStatsStrip) { vm.setShowStatsStrip(it) }
                            ToggleRow(str(R.string.date_headers), s.display.showDateHeaders) { vm.setShowDateHeaders(it) }
                            ToggleRow(str(R.string.size_length_under), s.display.showFileInfo) { vm.setShowFileInfo(it) }
                            ToggleRow(str(R.string.count_under_folders), s.display.showFolderInfo) { vm.setShowFolderInfo(it) }
                            ToggleRow(str(R.string.length_on_video_covers), s.display.showDurationBadge,
                                desc = str(R.string.length_on_covers_desc)) { vm.setShowDurationBadge(it) }
                            ToggleRow(str(R.string.people_on_covers), s.display.coverPeople,
                                desc = str(R.string.people_on_covers_desc)) { vm.setCoverPeople(it) }
                            if (s.display.coverPeople) {
                                ChoiceRow(str(R.string.face_size), listOf(0 to str(R.string.small_pl), 1 to str(R.string.medium_pl), 2 to str(R.string.large_pl)),
                                    s.display.coverPeopleSize, indent = 0.dp) { vm.setCoverPeopleSize(it) }
                            }
                        }
                    },
                    sec(str(R.string.sec_buttons)) {
                        Section(str(R.string.sec_buttons)) {
                            ToggleRow(str(R.string.new_note_button), s.display.showNoteFab) { vm.setShowNoteFab(it) }
                            ToggleRow(str(R.string.random_button), s.display.showRandomFab) { vm.setShowRandomFab(it) }
                        }
                    },
                )
                SettingsPage.ETICHETTE -> listOf(
                    sec(str(R.string.sec_tag_library)) {
                        Section(str(R.string.sec_tag_library), str(R.string.tag_library_hint, tags.size)) {
                            if (tags.isEmpty()) {
                                Text(str(R.string.no_tags_dot), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val custom = s.tagSortMode == com.cripta.app.data.TagSortMode.CUSTOM
                                // Long libraries are collapsed so the page stays short.
                                val visible = if (showAllTags || tags.size <= 8) tags else tags.take(8)
                                visible.forEachIndexed { index, tag ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        // The tag as it looks everywhere else (the shared pill); tap it to change
                                        // name, alias, colour and pin.
                                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                            com.cripta.app.ui.components.TagPill(
                                                com.cripta.app.ui.components.tagText(tag),
                                                if (s.display.tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary,
                                                size = com.cripta.app.ui.components.TagPillSize.Medium,
                                                base = MaterialTheme.colorScheme.background,
                                                tagIcon = tag.icon,
                                                leadingIcon = if (tag.pinned) CriptaIcons.PushPin else null,
                                                onClick = { editTag = tag },
                                                onClickLabel = str(R.string.edit_name_alias_colour),
                                            )
                                        }
                                        if (custom) {
                                            IconButton(onClick = { vm.moveTag(tag.id, up = true) }, enabled = index > 0) {
                                                Icon(CriptaIcons.KeyboardArrowUp, str(R.string.up))
                                            }
                                            IconButton(onClick = { vm.moveTag(tag.id, up = false) }, enabled = index < tags.size - 1) {
                                                Icon(CriptaIcons.KeyboardArrowDown, str(R.string.down))
                                            }
                                        }
                                        IconButton(onClick = { editTag = tag }) { Icon(CriptaIcons.Edit, str(R.string.edit)) }
                                        IconButton(onClick = { deleteTag = tag }) { Icon(CriptaIcons.Delete, str(R.string.delete)) }
                                    }
                                }
                                if (tags.size > 8) {
                                    TextButton(onClick = { showAllTags = !showAllTags }) {
                                        com.cripta.app.ui.components.ButtonText(if (showAllTags) str(R.string.show_less) else str(R.string.show_all_n, tags.size))
                                    }
                                }
                            }
                            com.cripta.app.ui.components.FilledTonalButton(
                                onClick = { addTag = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Icon(CriptaIcons.Add, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(str(R.string.add_tag))
                            }
                        }
                    },
                    sec(str(R.string.sec_on_covers)) {
                        Section(str(R.string.sec_on_covers)) {
                            ToggleRow(str(R.string.show_tags), s.display.showTagsOnCover) { vm.setShowTagsOnCover(it) }
                            if (s.display.showTagsOnCover) {
                                ChoiceRow(str(R.string.tag_rows), listOf(0 to str(R.string.quality_auto), 1 to "1", 2 to "2", 3 to "3"), s.display.coverTagRows,
                                    indent = 0.dp, desc = str(R.string.tag_rows_desc)) { vm.setCoverTagRows(it) }
                            }
                        }
                    },
                    sec(str(R.string.sec_colours)) {
                        Section(str(R.string.sec_colours)) {
                            ToggleRow(str(R.string.coloured_tags), s.display.tagColors) { vm.setTagColors(it) }
                            if (s.display.tagColors) {
                                Text(str(R.string.default_colour), style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 4.dp))
                                com.cripta.app.ui.vault.TagColorPicker(
                                    selected = s.display.defaultTagColor,
                                    autoColor = com.cripta.app.ui.theme.autoTagColor("etichetta"),
                                    autoDescription = str(R.string.auto_colour_per_name),
                                    onPick = { vm.setDefaultTagColor(it) },
                                )
                                Text(
                                    if (s.display.defaultTagColor == null) str(R.string.default_colour_auto_desc)
                                    else str(R.string.default_colour_set_desc),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (tags.isNotEmpty()) {
                                    com.cripta.app.ui.components.FilledTonalButton(onClick = { confirmRandomColors = true },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp)) {
                                        Icon(CriptaIcons.Palette, null, Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                        Text(str(R.string.random_colours_all))
                                    }
                                }
                            }
                        }
                    },
                    sec(str(R.string.sec_quick_assign)) {
                        Section(str(R.string.sec_quick_assign), str(R.string.quick_assign_hint)) {
                            ToggleRow(str(R.string.recent_tags), s.display.showRecentTags,
                                desc = if (s.display.showRecentTags) str(R.string.recent_tags_on, s.display.recentTagsCount)
                                    else str(R.string.recent_tags_off)) {
                                vm.setShowRecentTags(it)
                            }
                            if (s.display.showRecentTags) {
                                // Also how many recent tags the viewer's bar adds after the pinned ones.
                                ChoiceRow(str(R.string.how_many_recent), listOf(3 to "3", 6 to "6", 9 to "9", 12 to "12"),
                                    s.display.recentTagsCount, indent = 0.dp) { vm.setRecentTagsCount(it) }
                            }
                            ToggleRow(str(R.string.viewer_tag_bar), s.display.viewerQuickTags,
                                desc = when {
                                    !s.display.viewerQuickTags -> str(R.string.viewer_tag_bar_off)
                                    s.display.showRecentTags -> str(R.string.viewer_tag_bar_recent, s.display.recentTagsCount)
                                    else -> str(R.string.viewer_tag_bar_all)
                                }) { vm.setViewerQuickTags(it) }
                        }
                    },
                    sec(str(R.string.sec_library_order)) {
                        Section(str(R.string.sec_library_order)) {
                            val custom = s.tagSortMode == com.cripta.app.data.TagSortMode.CUSTOM
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.cripta.app.ui.components.ChoiceChip(selected = !custom,
                                    onClick = { vm.setTagSortMode(com.cripta.app.data.TagSortMode.ALPHA) },
                                    label = { Text(str(R.string.alphabetical)) })
                                com.cripta.app.ui.components.ChoiceChip(selected = custom,
                                    onClick = { vm.setTagSortMode(com.cripta.app.data.TagSortMode.CUSTOM) },
                                    label = { Text(str(R.string.custom)) })
                            }
                            Text(str(R.string.custom_order_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                SettingsPage.VISUALIZZATORE -> listOf(
                    sec(str(R.string.sec_playback)) {
                        Section(str(R.string.sec_playback), str(R.string.playback_hint)) {
                            ToggleRow(str(R.string.resume_where_left), s.display.resumePlayback,
                                desc = str(R.string.resume_desc),
                                icon = CriptaIcons.History) { vm.setResumePlayback(it) }
                            ToggleRow(str(R.string.loop), s.videoLoop,
                                desc = str(R.string.loop_desc),
                                icon = CriptaIcons.Repeat) { vm.setVideoLoop(it) }
                            ToggleRow(str(R.string.start_muted), s.videoStartMuted,
                                desc = str(R.string.start_muted_desc),
                                icon = CriptaIcons.VolumeOff) { vm.setVideoStartMuted(it) }
                        }
                    },
                    sec(str(R.string.sec_video_end)) {
                        Section(str(R.string.sec_video_end)) {
                            ToggleRow(str(R.string.auto_next), s.autoNext,
                                desc = if (s.videoLoop) str(R.string.auto_next_loop_on)
                                    else str(R.string.auto_next_desc),
                                icon = CriptaIcons.SkipNext) { vm.setAutoNext(it) }
                            if (s.autoNext) {
                                ChoiceRow(str(R.string.show_next_in_last), listOf(5 to "5 s", 8 to "8 s", 15 to "15 s"),
                                    s.autoNextSec) { vm.setAutoNextSec(it) }
                            }
                        }
                    },
                    sec(str(R.string.sec_gestures)) {
                        Section(str(R.string.sec_gestures), str(R.string.gestures_hint)) {
                            ToggleRow(str(R.string.swipe_down_close), s.swipeToClose,
                                desc = str(R.string.swipe_down_close_desc),
                                icon = CriptaIcons.KeyboardArrowDown) { vm.setSwipeToClose(it) }
                            ToggleRow(str(R.string.brightness_left), s.gestureControls,
                                desc = str(R.string.brightness_left_desc),
                                icon = CriptaIcons.LightMode) { vm.setGestureControls(it) }
                            ToggleRow(str(R.string.volume_right), s.gestureVolume,
                                desc = str(R.string.volume_right_desc),
                                icon = CriptaIcons.VolumeUp) { vm.setGestureVolume(it) }
                            ToggleRow(str(R.string.hold_to_speed), s.holdForSpeed,
                                desc = str(R.string.hold_to_speed_desc),
                                icon = CriptaIcons.FastForward) { vm.setHoldForSpeed(it) }
                            if (s.holdForSpeed) {
                                ChoiceRow(str(R.string.speed_while_holding), listOf(15 to str(R.string.speed_1_5), 20 to "2×", 30 to "3×"),
                                    s.holdSpeedX10) { vm.setHoldSpeed(it) }
                            }
                            ChoiceRow(str(R.string.double_tap_skip), listOf(5 to "5 s", 10 to "10 s", 30 to "30 s"),
                                s.seekStepSec, icon = CriptaIcons.FastRewind) { vm.setSeekStep(it) }
                        }
                    },
                    sec(str(R.string.sec_screen)) {
                        Section(str(R.string.sec_screen)) {
                            ToggleRow(str(R.string.follow_video_orientation), s.autoRotate,
                                desc = if (s.autoRotate) str(R.string.follow_orientation_on)
                                    else str(R.string.follow_orientation_off),
                                icon = CriptaIcons.ScreenRotation) { vm.setAutoRotate(it) }
                            ChoiceRow(str(R.string.controls_visible_for), listOf(2 to "2 s", 4 to "4 s", 8 to "8 s"),
                                s.controlsTimeoutSec, icon = CriptaIcons.Timer) { vm.setControlsTimeout(it) }
                        }
                    },
                    sec(str(R.string.sec_nearby_previews)) {
                        Section(str(R.string.sec_nearby_previews), str(R.string.nearby_previews_hint)) {
                            ToggleRow(str(R.string.show_previews), s.viewerFilmstrip, icon = CriptaIcons.ViewCarousel) { vm.setViewerFilmstrip(it) }
                            if (s.viewerFilmstrip) {
                                ChoiceRow(str(R.string.shape), listOf(
                                    com.cripta.app.data.StripShape.RECT to str(R.string.rectangle),
                                    com.cripta.app.data.StripShape.SQUARE to str(R.string.square),
                                    com.cripta.app.data.StripShape.CIRCLE to str(R.string.circle),
                                ), s.filmstripShape) { vm.setFilmstripShape(it) }
                                // Stored per side; shown as the whole strip, current file included.
                                ChoiceRow(str(R.string.covers_at_most), listOf(1 to "3", 2 to "5", 3 to "7", 4 to "9"),
                                    s.filmstripSpan) { vm.setFilmstripSpan(it) }
                                ChoiceRow(str(R.string.size_label), listOf(0 to str(R.string.small_f), 1 to str(R.string.medium_f), 2 to str(R.string.large_f)),
                                    s.filmstripSize) { vm.setFilmstripSize(it) }
                                if (s.filmstripShape == com.cripta.app.data.StripShape.RECT) {
                                    ToggleRow(str(R.string.full_portrait_covers), s.filmstripTrueAspect,
                                        desc = str(R.string.full_portrait_covers_desc)) {
                                        vm.setFilmstripTrueAspect(it)
                                    }
                                }
                            }
                        }
                    },
                    sec(str(R.string.sec_details_panel)) {
                        Section(str(R.string.sec_details_panel), str(R.string.details_panel_hint)) {
                            ToggleRow(str(R.string.split_with_details), s.splitDetails,
                                desc = if (s.splitDetails) str(R.string.split_details_on)
                                    else str(R.string.split_details_off)) { vm.setSplitDetails(it) }
                        }
                    },
                    sec(str(R.string.sec_leaving_app)) {
                        Section(str(R.string.sec_leaving_app)) {
                            ToggleRow(str(R.string.pause_video), s.pauseOnLeave,
                                desc = str(R.string.pause_video_desc),
                                icon = CriptaIcons.Pause) { vm.setPauseOnLeave(it) }
                            ToggleRow(str(R.string.auto_pip), s.pictureInPicture,
                                desc = str(R.string.auto_pip_desc),
                                icon = CriptaIcons.PictureInPictureAlt) { vm.setPictureInPicture(it) }
                        }
                    },
                )
                SettingsPage.ANALISI -> listOf(
                    sec(str(R.string.people)) {
                        PeopleSettings(s, vm)
                    },
                    sec(str(R.string.intro_and_ending)) {
                        IntroSettings(s, vm, onOpenFile)
                    },
                )
                SettingsPage.SICUREZZA -> listOf(
                    sec(str(R.string.sec_unlock)) {
                        Section(str(R.string.sec_unlock), str(R.string.unlock_hint)) {
                            UnlockModeOptions(vm)
                        }
                    },
                    sec(str(R.string.sec_auto_lock)) {
                        Section(str(R.string.sec_auto_lock), str(R.string.auto_lock_hint)) {
                            com.cripta.app.ui.components.ChipFlowRow {
                                listOf(-1, 0, 1, 5, 15, 30).forEach { m ->
                                    com.cripta.app.ui.components.ChoiceChip(
                                        selected = s.autoLockMinutes == m,
                                        onClick = { vm.setAutoLock(m) },
                                        label = { Text(when (m) { -1 -> str(R.string.never); 0 -> str(R.string.immediately); else -> str(R.string.n_min, m) }) },
                                    )
                                }
                            }
                        }
                    },
                    sec(str(R.string.sec_privacy)) {
                        Section(str(R.string.sec_privacy)) {
                            ToggleRow(str(R.string.allow_screenshots), s.allowScreenshots,
                                desc = str(R.string.allow_screenshots_desc)) {
                                vm.setAllowScreenshots(it)
                            }
                        }
                    },
                    sec(str(R.string.sec_security_limits)) {
                        Section(str(R.string.sec_security_limits)) {
                            Text(
                                str(R.string.security_limits_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                SettingsPage.IMPORT -> listOf(
                    sec(str(R.string.sec_original_after_import)) {
                        Section(str(R.string.sec_original_after_import), str(R.string.original_after_import_hint)) {
                            com.cripta.app.ui.components.ChipFlowRow {
                                DeleteOriginalPolicy.entries.forEach { p ->
                                    com.cripta.app.ui.components.ChoiceChip(
                                        selected = s.deleteOriginalPolicy == p,
                                        onClick = { vm.setDeletePolicy(p) },
                                        label = {
                                            Text(when (p) {
                                                DeleteOriginalPolicy.ASK -> str(R.string.ask)
                                                DeleteOriginalPolicy.ALWAYS -> str(R.string.delete)
                                                DeleteOriginalPolicy.NEVER -> str(R.string.keep)
                                            })
                                        },
                                    )
                                }
                            }
                            // What the chosen option actually does; "Elimina" warns that it is not reversible.
                            androidx.compose.animation.AnimatedContent(
                                targetState = s.deleteOriginalPolicy,
                                transitionSpec = {
                                    androidx.compose.animation.fadeIn(Motion.enter()) togetherWith
                                        androidx.compose.animation.fadeOut(Motion.exit()) using
                                        androidx.compose.animation.SizeTransform(clip = false) { _, _ -> Motion.enter(Motion.MEDIUM) }
                                },
                                label = "deletePolicyDesc",
                            ) { p ->
                                Text(
                                    when (p) {
                                        DeleteOriginalPolicy.ASK -> str(R.string.original_ask_desc)
                                        DeleteOriginalPolicy.ALWAYS -> str(R.string.original_always_desc)
                                        DeleteOriginalPolicy.NEVER -> str(R.string.original_never_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p == DeleteOriginalPolicy.ALWAYS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(str(R.string.original_only_successful),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    sec(str(R.string.sec_mp4_conversion)) {
                        Section(str(R.string.sec_mp4_conversion), str(R.string.mp4_conversion_hint)) {
                            com.cripta.app.ui.components.ChipFlowRow {
                                listOf(
                                    com.cripta.app.data.ConvertAfter.REPLACE to str(R.string.convert_replace),
                                    com.cripta.app.data.ConvertAfter.ASK to str(R.string.ask),
                                    com.cripta.app.data.ConvertAfter.KEEP_BOTH to str(R.string.convert_keep_both),
                                ).forEach { (v, l) ->
                                    com.cripta.app.ui.components.ChoiceChip(selected = s.convertAfter == v, onClick = { vm.setConvertAfter(v) }, label = { Text(l) })
                                }
                            }
                            Text(
                                when (s.convertAfter) {
                                    com.cripta.app.data.ConvertAfter.REPLACE ->
                                        str(R.string.convert_replace_setting_desc, s.trashDays)
                                    com.cripta.app.data.ConvertAfter.ASK -> str(R.string.convert_ask_desc)
                                    com.cripta.app.data.ConvertAfter.KEEP_BOTH -> str(R.string.convert_keep_both_setting_desc)
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                SettingsPage.BACKUP -> listOf(
                    sec(str(R.string.sec_what_to_include)) {
                        Section(str(R.string.sec_what_to_include), str(R.string.what_to_include_hint)) {
                            BackupScopeChoice(s.backupScope, backupFolders) { vm.setBackupScope(it) }
                        }
                    },
                    sec(str(R.string.sec_encrypted_backup)) {
                        Section(str(R.string.sec_encrypted_backup), str(R.string.encrypted_backup_hint)) {
                            // Always visible: how big the archive would be if made now.
                            backupEstimate?.let { (n, bytes) ->
                                com.cripta.app.ui.components.CountRow(str(R.string.estimated_size),
                                    com.cripta.app.ui.components.formatBytes(bytes) + " · " +
                                        plural(R.plurals.files_n, n, com.cripta.app.ui.components.formatCount(n)))
                                Text(if (s.backupCompressDocs) str(R.string.backup_estimate_compressed)
                                    else str(R.string.backup_estimate_space),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ToggleRow(str(R.string.compress_documents), s.backupCompressDocs,
                                desc = str(R.string.compress_documents_desc)) {
                                vm.setBackupCompressDocs(it)
                            }
                            androidx.compose.animation.AnimatedContent(
                                targetState = backupBusy,
                                transitionSpec = { calmSwap() },
                                label = "backupBusy",
                            ) { busy ->
                                if (busy != null) {
                                    // Runs on its own; the vault stays usable, the result arrives as a message.
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                                        val p = backupProgress
                                        val eta = com.cripta.app.ui.components.rememberEta(p ?: 0f, p != null, key = busy)
                                        Text(if (p != null) "$busy ${(p * 100).toInt()}%" else busy, style = MaterialTheme.typography.bodyMedium)
                                        if (p != null) {
                                            val smooth by animateFloatAsState(p, tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "backupPct")
                                            androidx.compose.material3.LinearProgressIndicator(progress = { smooth }, modifier = Modifier.fillMaxWidth())
                                        } else {
                                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                        }
                                        Text(eta?.replaceFirstChar { it.uppercase() }?.let { str(R.string.eta_keep_open, it) }
                                                ?: str(R.string.may_take_minutes),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { exportLauncher.launch("cripta-backup-${backupStamp()}.criptabak") },
                                            enabled = !s.backupScope.isEmpty,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        ) { com.cripta.app.ui.components.ButtonText(str(R.string.create_backup)) }
                                        com.cripta.app.ui.components.FilledTonalButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                            com.cripta.app.ui.components.ButtonText(str(R.string.restore_from_backup))
                                        }
                                    }
                                }
                            }
                            Text(str(R.string.restore_adds_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                SettingsPage.SPAZIO -> listOf(
                    sec(str(R.string.sec_duplicate_files)) {
                        Section(str(R.string.sec_duplicate_files), str(R.string.duplicates_hint)) {
                            // How much of the vault the deep search already knows (it only reads the rest).
                            val prints by androidx.compose.runtime.produceState<Pair<Int, Int>?>(null, dupScanning, dupLast) { value = vm.printStats() }
                            androidx.compose.animation.AnimatedContent(
                                targetState = dupScanning,
                                transitionSpec = { calmSwap() },
                                label = "dupScan",
                            ) { scanning ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (scanning) {
                                val (done, total) = dupProgress
                                val scanEta = com.cripta.app.ui.components.rememberEta(
                                    if (total > 0) done.toFloat() / total else 0f, total > 0)
                                Text((if (total > 0) str(R.string.searching_n, done, total) else str(R.string.searching)) + (scanEta?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium)
                                if (total > 0) {
                                    val smooth by animateFloatAsState(
                                        (done.toFloat() / total).coerceIn(0f, 1f),
                                        tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "dupProgress",
                                    )
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { smooth }, modifier = Modifier.fillMaxWidth())
                                } else {
                                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                Text(str(R.string.search_continues),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                com.cripta.app.ui.components.FilledTonalButton(onClick = { vm.cancelScan() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    com.cripta.app.ui.components.ButtonText(str(R.string.stop_search))
                                }
                            } else {
                                dupLast?.let { r ->
                                    val deep = r.mode == com.cripta.app.data.dedup.DupScanStore.Mode.SIMILAR
                                    Text(str(if (deep) R.string.last_search_deep else R.string.last_search_quick,
                                        if (r.groups.isEmpty()) str(R.string.no_groups) else plural(R.plurals.groups_n, r.groups.size), r.scanned), style = MaterialTheme.typography.bodyMedium)
                                    if (r.groups.isNotEmpty()) Button(onClick = { vm.openScanResult() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                        com.cripta.app.ui.components.ButtonText(plural(R.plurals.open_groups_found, r.groups.size))
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(str(R.string.n_quick_search), style = MaterialTheme.typography.titleSmall)
                                    Text(str(R.string.quick_search_desc),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    com.cripta.app.ui.components.FilledTonalButton(onClick = { vm.scanExact() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        com.cripta.app.ui.components.ButtonText(str(R.string.n_quick_search))
                                    }
                                }
                                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(str(R.string.n_deep_search), style = MaterialTheme.typography.titleSmall)
                                    val known = prints
                                    Text(str(R.string.deep_search_desc) + when {
                                        known == null -> ""
                                        known.second == 0 -> str(R.string.no_photo_video)
                                        known.first >= known.second -> str(R.string.all_media_analysed, known.second)
                                        known.first == 0 -> str(R.string.first_time_all, known.second)
                                        else -> str(R.string.some_media_analysed, known.first, known.second, known.second - known.first)
                                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    com.cripta.app.ui.components.FilledTonalButton(onClick = { vm.scanSimilar() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        com.cripta.app.ui.components.ButtonText(str(R.string.n_deep_search))
                                    }
                                }
                                ToggleRow(str(R.string.check_similar_import), s.similarOnImport,
                                    desc = str(R.string.check_similar_import_desc)) {
                                    vm.setSimilarOnImport(it)
                                }
                            }
                            }
                            }
                        }
                    },
                    sec(str(R.string.sec_trash)) {
                        Section(str(R.string.sec_trash), str(R.string.trash_hint)) {
                            ToggleRow(str(R.string.use_trash), s.trashEnabled,
                                desc = str(R.string.use_trash_desc)) { vm.setTrashEnabled(it) }
                            if (s.trashEnabled) {
                                ChoiceRow(str(R.string.keep_for), listOf(3 to str(R.string.days_3), 7 to str(R.string.days_7), 30 to str(R.string.days_30)), s.trashDays,
                                    indent = 0.dp) { vm.setTrashDays(it) }
                            }
                            com.cripta.app.ui.components.FilledTonalButton(onClick = { showTrash = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp)) {
                                com.cripta.app.ui.components.ButtonText(if (trashed.isEmpty() && trashedFolders.isEmpty()) str(R.string.trash_empty)
                                    else str(R.string.open_trash_n, trashed.size + trashedFolders.size))
                            }
                        }
                    },
                )
                SettingsPage.INFO -> listOf(
                    sec(str(R.string.sec_updates)) {
                        Section(str(R.string.sec_updates), str(R.string.updates_hint)) {
                            // Phases swap in place (keyed by phase, so progress ticks don't re-animate).
                            androidx.compose.animation.AnimatedContent(
                                targetState = updateState,
                                contentKey = { it::class },
                                transitionSpec = { calmSwap() },
                                label = "updateState",
                            ) { u ->
                            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (u) {
                                SettingsViewModel.UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Text(str(R.string.checking), style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 12.dp))
                                }
                                SettingsViewModel.UpdateState.UpToDate ->
                                    Text(str(R.string.up_to_date), style = MaterialTheme.typography.bodyMedium)
                                is SettingsViewModel.UpdateState.Available -> {
                                    Text(str(R.string.update_available, u.release.versionName, com.cripta.app.ui.components.formatBytes(u.release.sizeBytes)),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = { vm.downloadUpdate(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                        com.cripta.app.ui.components.ButtonText(str(R.string.install_update))
                                    }
                                }
                                is SettingsViewModel.UpdateState.Downloading -> {
                                    // Read the live state (not the frozen target) so the bar keeps moving.
                                    val pct = (updateState as? SettingsViewModel.UpdateState.Downloading)?.pct ?: u.pct
                                    val smooth by animateFloatAsState(pct / 100f, tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "updatePct")
                                    val updEta = com.cripta.app.ui.components.rememberEta(pct / 100f)
                                    Text(str(R.string.update_downloading_pct, pct) + (updEta?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { smooth }, modifier = Modifier.fillMaxWidth())
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(str(R.string.update_continues),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { vm.cancelUpdateDownload(ctx) }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
                                    }
                                }
                                is SettingsViewModel.UpdateState.ReadyToInstall -> {
                                    Text(str(R.string.update_ready_reopen, u.release.versionName),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = { vm.installUpdate(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                        com.cripta.app.ui.components.ButtonText(str(R.string.install_update))
                                    }
                                }
                                is SettingsViewModel.UpdateState.Error ->
                                    Text(u.message, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error)
                                SettingsViewModel.UpdateState.Idle -> {}
                            }
                            }
                            }
                            ToggleRow(str(R.string.include_prerelease), s.updatePrerelease) { vm.setUpdatePrerelease(it) }
                            if (updateState !is SettingsViewModel.UpdateState.Downloading) {
                                com.cripta.app.ui.components.FilledTonalButton(
                                    onClick = { vm.checkUpdate(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(str(R.string.check_updates))
                                }
                            }
                        }
                    },
                    sec(str(R.string.sec_about)) {
                        Section(str(R.string.sec_about)) {
                            InfoRow(str(R.string.version), pkgInfo?.versionName ?: "—")
                            InfoRow("Build", pkgInfo?.longVersionCode?.toString() ?: "—")
                            InfoRow(str(R.string.package_), ctx.packageName)
                            InfoRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                        }
                    },
                    sec(str(R.string.sec_last_crash)) {
                        var crash by remember { mutableStateOf(com.cripta.app.CrashLog.read(ctx)) }
                        Section(str(R.string.sec_last_crash), str(R.string.last_crash_hint)) {
                            val c = crash
                            if (c == null) {
                                Text(str(R.string.no_crash), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val lines = c.lines()
                                Text(lines.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelLarge)
                                Text(lines.firstOrNull { it.contains("Exception") || it.contains("Error") }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 4,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    com.cripta.app.ui.components.FilledTonalButton(
                                        onClick = {
                                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(c))
                                            android.widget.Toast.makeText(ctx, str(R.string.details_copied), android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { com.cripta.app.ui.components.ButtonText(str(R.string.copy_details)) }
                                    com.cripta.app.ui.components.FilledTonalButton(
                                        onClick = { com.cripta.app.CrashLog.clear(ctx); crash = null },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { com.cripta.app.ui.components.ButtonText(str(R.string.delete)) }
                                }
                            }
                        }
                    },
                )
            }
            val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
            LaunchedEffect(current, anchor) {
                val a = anchor ?: return@LaunchedEffect
                val index = sections.indexOfFirst { it.first == a }
                if (index >= 0) { gridState.scrollToItem(index); highlighted = a }
                anchor = null
            }
            LaunchedEffect(highlighted) {
                if (highlighted != null) { kotlinx.coroutines.delay(1800); highlighted = null }
            }
            // Portrait: one column. Landscape: sections flow in two columns, so the width is used
            // instead of stretching every row across the screen. No frames: headings and space group.
            androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(if (landscape) 2 else 1),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
                verticalItemSpacing = 32.dp,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                staggeredItems(sections, key = { it.first }) { (key, content) ->
                    SectionMark(key == highlighted) { content() }
                }
            }
          }
        }
      }
    }

    confirmReset?.let { p ->
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(str(R.string.restore_defaults_q)) },
            text = { Text(str(R.string.restore_defaults_text, p.label)) },
            confirmButton = { TextButton(onClick = { vm.resetPage(p.name); confirmReset = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }

    if (showTrash) {
        TrashDialog(
            items = trashed.map { it.file },
            folders = trashedFolders,
            days = s.trashDays,
            thumb = { vm.thumbOf(it) },
            onRestore = { vm.restore(it) },
            onDelete = { vm.deleteForever(it) },
            onRestoreFolder = { vm.restoreFolder(it) },
            onDeleteFolder = { vm.deleteFolderForever(it) },
            onEmpty = { vm.emptyTrash() },
            onRestoreMany = { f, d -> vm.restoreMany(f, d) },
            onDeleteMany = { f, d -> vm.deleteManyForever(f, d) },
            onDismiss = { showTrash = false },
        )
    }
    if (addTag) {
        var newColor by remember { mutableStateOf<Int?>(null) }
        var newIcon by remember { mutableStateOf<String?>(null) }
        LabelEditorDialog(
            title = str(R.string.new_tag),
            onColor = { newColor = it },
            onIcon = { newIcon = it },
            onConfirm = { name, alias -> vm.createTag(name, alias, newColor, newIcon); addTag = false },
            onDismiss = { addTag = false },
        )
    }
    if (pendingExport != null || pendingImport != null) {
        PassphraseDialog(
            title = if (pendingExport != null) str(R.string.backup_passphrase) else str(R.string.restore_passphrase),
            creating = pendingExport != null,
            onConfirm = { pass ->
                pendingExport?.let { vm.exportBackup(it, pass) }
                pendingImport?.let { vm.importBackup(it, pass) }
                pendingExport = null; pendingImport = null
            },
            onDismiss = { pendingExport = null; pendingImport = null },
        )
    }
    editTag?.let { tag ->
        LabelEditorDialog(
            title = str(R.string.edit_tag),
            initialName = tag.name,
            initialAlias = tag.alias ?: "",
            initialColor = tag.color,
            onColor = { vm.setTagColor(tag.id, it) },
            initialPinned = tag.pinned,
            onPinned = { vm.setTagPinned(tag.name, it) },
            initialIcon = tag.icon,
            onIcon = { vm.setTagIcon(tag.name, it) },
            onConfirm = { name, alias -> vm.editTag(tag.id, name, alias); editTag = null },
            onDismiss = { editTag = null },
        )
    }
    when (dupMode) {
        SettingsViewModel.DupMode.EXACT -> DuplicatesCompare(
            title = str(R.string.exact_duplicates),
            groups = dupGroups,
            summary = buildString {
                append(str(R.string.scanned_n, dupScannedCount))
                val waste = exactGroups.sumOf { it.sizeBytes * (it.files.size - 1) }
                if (waste > 0) append(str(R.string.reclaim_extra, com.cripta.app.ui.components.formatBytes(waste)))
                append(str(R.string.tags_pass_to_kept))
            },
            emptyText = str(R.string.scanned_no_dups, dupScannedCount),
            notice = dupNotice,
            thumb = { vm.thumb(it) },
            channelFor = { vm.channelFor(it) },
            imageBytes = { vm.imageBytes(it) },
            onKeepOnly = { vm.keepOnly(it) },
            onDelete = { vm.deleteDuplicate(it) },
            onClearNotice = { vm.clearDupNotice() },
            onDismiss = { vm.closeDuplicates() },
            trashDays = if (s.trashEnabled) s.trashDays else null,
            canUndo = dupUndo.isNotEmpty(),
            onUndo = { vm.undoDuplicates() },
        )
        SettingsViewModel.DupMode.SIMILAR -> SimilarPager(
            title = str(R.string.similar_media),
            groups = dupGroups,
            emptyText = str(R.string.analysed_no_similar, dupScannedCount),
            notice = dupNotice,
            thumb = { vm.thumb(it) },
            imageBytes = { vm.imageBytes(it) },
            onKeep = { vm.keepCopies(it) },
            onNotSimilar = { vm.notSimilar(it) },
            // As a face's frame: the media full screen in the viewer, swiping through the group.
            onOpen = { ids, id -> vm.openPersonMedia(ids, id, null); onOpenFile(id) },
            onClearNotice = { vm.clearDupNotice() },
            onDismiss = { vm.closeDuplicates() },
            freed = dupFreed,
            apart = dupApart,
            initialPage = vm.lastSimilarPage,
            onPage = { vm.lastSimilarPage = it },
            trashDays = if (s.trashEnabled) s.trashDays else null,
            canUndo = dupUndo.isNotEmpty(),
            onUndo = { vm.undoDuplicates() },
        )
        SettingsViewModel.DupMode.NONE -> Unit
    }
    if (confirmRandomColors) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmRandomColors = false },
            title = { Text(str(R.string.random_colours_q)) },
            text = {
                Text(str(R.string.random_colours_text, tags.size))
            },
            confirmButton = { TextButton(onClick = { vm.randomizeTagColors(); confirmRandomColors = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.assign)) } },
            dismissButton = { TextButton(onClick = { confirmRandomColors = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }

    deleteTag?.let { tag ->
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { deleteTag = null },
            title = { Text(str(R.string.delete_tag_q)) },
            text = { Text(str(R.string.delete_tag_text, tag.name)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteTag(tag.id); deleteTag = null }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTag = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
    }
}

/** Restoring: what older versions accepted. */
private const val MIN_PASSPHRASE = 6
/**
 * Creating: a backup copied off the phone can be attacked offline with no limit on attempts,
 * where only the passphrase's length stands in the way (6 characters fell quickly).
 */
private const val MIN_NEW_PASSPHRASE = 10

/**
 * Passphrase entry. Creating a backup asks for it twice (a typo would make the archive
 * unrecoverable); restoring asks once and explains that files are added, not replaced.
 */
@Composable
private fun PassphraseDialog(title: String, creating: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val minLen = if (creating) MIN_NEW_PASSPHRASE else MIN_PASSPHRASE
    val tooShort = pass.length < minLen
    val mismatch = creating && confirm.isNotEmpty() && confirm != pass
    val valid = !tooShort && (!creating || confirm == pass)
    val transformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) CriptaIcons.VisibilityOff else CriptaIcons.Visibility,
                if (visible) str(R.string.hide_passphrase) else str(R.string.show_passphrase),
            )
        }
    }
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (creating) str(R.string.passphrase_create_hint)
                    else str(R.string.passphrase_restore_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.cripta.app.ui.components.CriptaTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(str(R.string.passphrase)) },
                    singleLine = true,
                    visualTransformation = transformation,
                    trailingIcon = toggle,
                    supportingText = {
                        Text(if (tooShort) str(R.string.passphrase_too_short, minLen, pass.length, minLen) else str(R.string.length_ok))
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = if (creating) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(pass) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (creating) {
                    com.cripta.app.ui.components.CriptaTextField(
                        value = confirm, onValueChange = { confirm = it },
                        label = { Text(str(R.string.repeat_passphrase)) },
                        singleLine = true,
                        visualTransformation = transformation,
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(str(R.string.passphrase_mismatch)) }) else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(pass) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = valid) { com.cripta.app.ui.components.ButtonText(if (creating) str(R.string.create_backup) else str(R.string.restore)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

enum class SettingsGroup(private val labelRes: Int) {
    PREFERENCES(R.string.group_preferences), DATA(R.string.group_data), APP(R.string.group_app);

    val label: String get() = str(labelRes)
}

/** Settings pages, in list order, grouped by what the user wants to do. */
enum class SettingsPage(
    private val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val group: SettingsGroup,
    val resettable: Boolean = true,
) {
    ASPETTO(R.string.page_appearance, CriptaIcons.Palette, SettingsGroup.PREFERENCES),
    ETICHETTE(R.string.tags, CriptaIcons.Label, SettingsGroup.PREFERENCES),
    VISUALIZZATORE(R.string.page_viewer, CriptaIcons.PlayCircle, SettingsGroup.PREFERENCES),
    ANALISI(R.string.page_analysis, CriptaIcons.Face, SettingsGroup.PREFERENCES),
    SICUREZZA(R.string.page_security, CriptaIcons.Lock, SettingsGroup.DATA),
    IMPORT(R.string.page_import, CriptaIcons.Download, SettingsGroup.DATA),
    BACKUP(R.string.kind_backup, CriptaIcons.Backup, SettingsGroup.DATA, resettable = false),
    SPAZIO(R.string.page_space, CriptaIcons.DeleteSweep, SettingsGroup.DATA, resettable = false),
    INFO(R.string.page_info, CriptaIcons.SystemUpdate, SettingsGroup.APP, resettable = false),
    ;

    val label: String get() = str(labelRes)
}

/** "1 elemento" / "N elementi": what the trash counts. Files and folders alike are elements. */
private fun elementsLabel(n: Int): String = plural(R.plurals.items_n, n)

/** One-line summary of a page's current state, shown under its name in the list. */
private fun pageSummary(p: SettingsPage, s: com.cripta.app.data.Settings, tagCount: Int, trashCount: Int, dupGroups: Int?): String = when (p) {
    SettingsPage.ASPETTO -> str(when (s.themeMode) {
        com.cripta.app.data.ThemeMode.SYSTEM -> R.string.sum_theme_system
        com.cripta.app.data.ThemeMode.LIGHT -> R.string.sum_theme_light
        com.cripta.app.data.ThemeMode.DARK -> R.string.sum_theme_dark
    }) + (if (s.dynamicColor) str(R.string.sum_dynamic) else "") + (if (s.filterCurrentFolderOnly) str(R.string.sum_filters_folder) else "")
    SettingsPage.ETICHETTE -> plural(R.plurals.tags_n, tagCount) + (if (s.display.showTagsOnCover) str(R.string.sum_on_covers) else "") +
        if (s.display.showRecentTags) str(R.string.sum_recent) else ""
    SettingsPage.VISUALIZZATORE -> listOfNotNull(
        if (s.display.resumePlayback) str(R.string.sum_resume) else null,
        if (s.videoLoop) str(R.string.sum_loop) else null,
        if (s.autoNext) str(R.string.sum_next_file) else null,
        if (s.splitDetails) str(R.string.sum_split) else null,
    ).joinToString(" · ").ifEmpty { str(R.string.sum_viewer_default) }
    SettingsPage.ANALISI -> str(R.string.sum_analysis,
        str(if (s.facesAnalyzeNew) R.string.sum_auto_analysis else R.string.sum_manual_analysis),
        str(if (s.introAnalyzeNew) R.string.sum_auto else R.string.sum_manual))
    SettingsPage.SICUREZZA -> when (s.autoLockMinutes) {
        -1 -> str(R.string.sum_lock_never); 0 -> str(R.string.sum_lock_now); else -> str(R.string.sum_lock_after, s.autoLockMinutes)
    } + str(if (s.allowScreenshots) R.string.sum_shots_allowed else R.string.sum_shots_blocked)
    SettingsPage.IMPORT -> str(R.string.sum_import,
        str(when (s.deleteOriginalPolicy) {
            DeleteOriginalPolicy.ASK -> R.string.sum_ask; DeleteOriginalPolicy.ALWAYS -> R.string.sum_delete; DeleteOriginalPolicy.NEVER -> R.string.sum_keep
        }),
        str(when (s.convertAfter) {
            com.cripta.app.data.ConvertAfter.REPLACE -> R.string.sum_replaces
            com.cripta.app.data.ConvertAfter.ASK -> R.string.sum_ask
            com.cripta.app.data.ConvertAfter.KEEP_BOTH -> R.string.sum_keep_both
        }))
    SettingsPage.BACKUP -> str(R.string.sum_backup)
    SettingsPage.SPAZIO -> listOfNotNull(
        str(R.string.kind_duplicates),
        if (trashCount > 0) str(R.string.sum_trash_n, elementsLabel(trashCount)) else str(if (s.trashEnabled) R.string.sum_trash_empty else R.string.sum_trash_off),
        dupGroups?.let { str(R.string.sum_results_ready, it) },
    ).joinToString(" · ")
    SettingsPage.INFO -> str(R.string.sum_info)
}

/** A searchable option: what it is called, extra words people may type, where it lives (page + section). */
private class SearchEntry(val label: String, val keywords: String, val page: SettingsPage, val section: String)

// A getter: the texts are read in the current language each time the search runs.
private val SEARCH_INDEX: List<SearchEntry> get() = listOf(
    SearchEntry(str(R.string.si_00), str(R.string.si_00_kw), SettingsPage.ASPETTO, str(R.string.sec_theme)),
    SearchEntry(str(R.string.si_01), str(R.string.si_01_kw), SettingsPage.ASPETTO, str(R.string.sec_theme)),
    SearchEntry(str(R.string.si_42), str(R.string.si_42_kw), SettingsPage.ASPETTO, str(R.string.sec_language)),
    SearchEntry(str(R.string.si_02), str(R.string.si_02_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_03), str(R.string.si_03_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_04), str(R.string.si_04_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_05), str(R.string.si_05_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_06), str(R.string.si_06_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_07), str(R.string.si_07_kw), SettingsPage.ASPETTO, str(R.string.tab_folders)),
    SearchEntry(str(R.string.si_08), str(R.string.si_08_kw), SettingsPage.ASPETTO, str(R.string.sec_buttons)),
    SearchEntry(str(R.string.si_09), str(R.string.si_09_kw), SettingsPage.ETICHETTE, str(R.string.sec_tag_library)),
    SearchEntry(str(R.string.si_10), str(R.string.si_10_kw), SettingsPage.ETICHETTE, str(R.string.sec_on_covers)),
    SearchEntry(str(R.string.si_11), str(R.string.si_11_kw), SettingsPage.ETICHETTE, str(R.string.sec_colours)),
    SearchEntry(str(R.string.si_12), str(R.string.si_12_kw), SettingsPage.ETICHETTE, str(R.string.sec_quick_assign)),
    SearchEntry(str(R.string.si_13), str(R.string.si_13_kw), SettingsPage.ETICHETTE, str(R.string.sec_quick_assign)),
    SearchEntry(str(R.string.si_14), str(R.string.si_14_kw), SettingsPage.ETICHETTE, str(R.string.sec_library_order)),
    SearchEntry(str(R.string.si_15), str(R.string.si_15_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_playback)),
    SearchEntry(str(R.string.si_16), str(R.string.si_16_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_playback)),
    SearchEntry(str(R.string.si_17), str(R.string.si_17_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_playback)),
    SearchEntry(str(R.string.si_18), str(R.string.si_18_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_video_end)),
    SearchEntry(str(R.string.si_19), str(R.string.si_19_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_20), str(R.string.si_20_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_21), str(R.string.si_21_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_22), str(R.string.si_22_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_23), str(R.string.si_23_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_gestures)),
    SearchEntry(str(R.string.si_24), str(R.string.si_24_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_screen)),
    SearchEntry(str(R.string.si_25), str(R.string.si_25_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_screen)),
    SearchEntry(str(R.string.si_26), str(R.string.si_26_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_nearby_previews)),
    SearchEntry(str(R.string.si_27), str(R.string.si_27_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_details_panel)),
    SearchEntry(str(R.string.si_28), str(R.string.si_28_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_leaving_app)),
    SearchEntry(str(R.string.si_29), str(R.string.si_29_kw), SettingsPage.VISUALIZZATORE, str(R.string.sec_leaving_app)),
    SearchEntry(str(R.string.si_30), str(R.string.si_30_kw), SettingsPage.ANALISI, str(R.string.people)),
    SearchEntry(str(R.string.si_31), str(R.string.si_31_kw), SettingsPage.ANALISI, str(R.string.intro_and_ending)),
    SearchEntry(str(R.string.si_32), str(R.string.si_32_kw), SettingsPage.SICUREZZA, str(R.string.sec_unlock)),
    SearchEntry(str(R.string.si_33), str(R.string.si_33_kw), SettingsPage.SICUREZZA, str(R.string.sec_auto_lock)),
    SearchEntry(str(R.string.si_34), str(R.string.si_34_kw), SettingsPage.SICUREZZA, str(R.string.sec_privacy)),
    SearchEntry(str(R.string.si_35), str(R.string.si_35_kw), SettingsPage.IMPORT, str(R.string.sec_original_after_import)),
    SearchEntry(str(R.string.si_36), str(R.string.si_36_kw), SettingsPage.IMPORT, str(R.string.sec_mp4_conversion)),
    SearchEntry(str(R.string.si_37), str(R.string.si_37_kw), SettingsPage.BACKUP, str(R.string.sec_encrypted_backup)),
    SearchEntry(str(R.string.si_38), str(R.string.si_38_kw), SettingsPage.BACKUP, str(R.string.sec_what_to_include)),
    SearchEntry(str(R.string.si_39), str(R.string.si_39_kw), SettingsPage.SPAZIO, str(R.string.sec_duplicate_files)),
    SearchEntry(str(R.string.si_40), str(R.string.si_40_kw), SettingsPage.SPAZIO, str(R.string.sec_trash)),
    SearchEntry(str(R.string.si_41), str(R.string.si_41_kw), SettingsPage.INFO, str(R.string.sec_updates)),
)

/** First level: search + preference pages + tools, each with a live summary. */
@Composable
private fun MainSettingsList(
    modifier: Modifier,
    columns: Int,
    query: String,
    onQuery: (String) -> Unit,
    summaries: Map<SettingsPage, String>,
    /** Page, plus the section to land on when coming from a search result. */
    onOpen: (SettingsPage, String?) -> Unit,
    onLock: () -> Unit,
) {
    // Two columns of page rows in landscape; search, group labels and the lock button span the width.
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
        { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = full) {
            // The app's single-line field: the same filled pill as every other search.
            com.cripta.app.ui.components.CriptaTextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text(str(R.string.search_setting)) },
                leadingIcon = { Icon(CriptaIcons.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(CriptaIcons.Close, str(R.string.clear_short)) } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            val hits = SEARCH_INDEX.filter { e -> e.label.lowercase().contains(q) || e.keywords.contains(q) }
            if (hits.isEmpty()) {
                item(span = full) { Text(str(R.string.no_setting_found), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)) }
            }
            items(hits.size) { i ->
                val e = hits[i]
                PageRow(e.page, e.label, "${e.page.label} › ${e.section}") { onOpen(e.page, e.section) }
            }
        } else {
            SettingsGroup.entries.forEach { g ->
                item(span = full) { GroupLabel(g.label) }
                val pages = SettingsPage.entries.filter { it.group == g }
                items(pages.size) { i ->
                    val p = pages[i]
                    PageRow(p, p.label, summaries[p].orEmpty()) { onOpen(p, null) }
                }
                // DS Impostazioni: the space used, as a counted row, closes "I tuoi dati".
                if (g == SettingsGroup.DATA) item(span = full) { SpaceUsedRow() }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
        // DS: 32dp between groups, 10 from a heading to its rows.
        modifier = Modifier.padding(top = 32.dp, bottom = 10.dp).semantics { heading() })
}

/** Calm swap between two states of a panel: crossfade while the height follows. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<*>.calmSwap(): androidx.compose.animation.ContentTransform =
    (androidx.compose.animation.fadeIn(Motion.enter()) togetherWith androidx.compose.animation.fadeOut(Motion.exit())).using(
        androidx.compose.animation.SizeTransform { _, _ -> Motion.enter(Motion.MEDIUM) })

/** Date/time stamp for the backup file name, so successive backups don't overwrite each other. */
private fun backupStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(java.util.Date())

/** A page entry: plain row with its icon, name and current-state summary. No frame around it. */
@Composable
private fun PageRow(p: SettingsPage, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(p.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

/** "Spazio usato: 18,4 GB di 64 GB": what the vault takes on this phone, against the phone's storage. */
@Composable
private fun SpaceUsedRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val space by androidx.compose.runtime.produceState<Pair<Long, Long>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val dir = ctx.filesDir
                val used = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
                    (ctx.getDatabasePath("x").parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)
                used to android.os.StatFs(dir.path).totalBytes
            }.getOrNull()
        }
    }
    val s = space ?: return
    com.cripta.app.ui.components.CountRow(
        str(R.string.space_used_short), com.cripta.app.ui.components.formatBytes(s.first),
        of = com.cripta.app.ui.components.formatBytes(s.second),
        progress = if (s.second > 0) s.first.toFloat() / s.second else 0f,
    )
}

/**
 * A group of options: heading, optional explanation, the options. No frame or card around it:
 * the heading and the space before the next group do the grouping.
 */
@Composable
internal fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    // The explanation sits close under its heading (it belongs to it), the options a step further.
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/**
 * The opening of a page that is about one feature (Persone, Intro e finale): what it does in a
 * sentence or two, no heading (the top bar already names the page), then its state and main action.
 */
@Composable
internal fun PageIntro(text: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Named section of a page, so a search result can find it. */
private fun sec(key: String, content: @Composable () -> Unit): Pair<String, @Composable () -> Unit> = key to content

/** Briefly marks the section a search result landed on: a soft tint behind it that fades out. */
@Composable
private fun SectionMark(on: Boolean, content: @Composable () -> Unit) {
    val tint by androidx.compose.animation.animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        tween(if (on) Motion.MEDIUM else Motion.LONG * 3, 0, Motion.EaseOutQuart), label = "sectionMark",
    )
    val shape = MaterialTheme.shapes.medium
    Box(Modifier.drawBehind {
        val pad = 10.dp.toPx()
        val outline = shape.createOutline(
            androidx.compose.ui.geometry.Size(size.width + 2 * pad, size.height + 2 * pad), layoutDirection, this)
        translate(-pad, -pad) { drawOutline(outline, tint) }
    }) { content() }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    desc: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    // The whole row is one switch (label included) for touch and TalkBack; the Switch only draws.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp).size(24.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            // Title in medium weight, its caption close under it: the pair reads as one item.
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(checked = checked, onCheckedChange = null, colors = com.cripta.app.ui.components.criptaSwitchColors())
    }
}

/**
 * What the next backup carries: one switch per group, and for the files the top-level folders
 * (each with everything under it) and the files in no folder, folded until asked for.
 */
@Composable
private fun BackupScopeChoice(
    scope: com.cripta.app.data.BackupScope,
    folders: List<com.cripta.app.data.db.FolderEntity>,
    onChange: (com.cripta.app.data.BackupScope) -> Unit,
) {
    var showFolders by remember { mutableStateOf(false) }
    ToggleRow(str(R.string.files_cap), scope.files, desc = str(R.string.backup_files_desc)) {
        onChange(scope.copy(files = it))
    }
    if (scope.files && folders.isNotEmpty()) {
        val ids = folders.map { it.id }.toSet()
        val chosen = ids.count { it !in scope.excludedFolders }
        val all = chosen == ids.size && scope.rootFiles
        TextButton(onClick = { showFolders = !showFolders }) {
            com.cripta.app.ui.components.ButtonText(
                when {
                    showFolders -> str(R.string.hide_folders)
                    all -> str(R.string.choose_folders_all)
                    else -> str(R.string.choose_folders_n, chosen, ids.size)
                }
            )
        }
        if (showFolders) {
            Column(Modifier.padding(start = 16.dp)) {
                folders.forEach { f ->
                    ToggleRow(f.name, f.id !in scope.excludedFolders) { on ->
                        // Ids of folders gone since are dropped on the way.
                        val ex = scope.excludedFolders.filter { it in ids }.toSet()
                        onChange(scope.copy(excludedFolders = if (on) ex - f.id else ex + f.id))
                    }
                }
                ToggleRow(str(R.string.files_outside_folders), scope.rootFiles) { onChange(scope.copy(rootFiles = it)) }
                Text(str(R.string.folder_with_subfolders),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (scope.files) {
        ToggleRow(str(R.string.covers_by_hand), scope.covers, desc = str(R.string.covers_by_hand_desc)) {
            onChange(scope.copy(covers = it))
        }
    }
    ToggleRow(str(R.string.tags_saved_filters), scope.tags, desc = str(R.string.tags_saved_filters_desc)) {
        onChange(scope.copy(tags = it))
    }
    ToggleRow(str(R.string.people_and_faces), scope.people, desc = str(R.string.people_and_faces_desc)) {
        onChange(scope.copy(people = it))
    }
    ToggleRow(str(R.string.analysis_backup), scope.analysis, desc = str(R.string.analysis_backup_desc)) {
        onChange(scope.copy(analysis = it))
    }
    ToggleRow(str(R.string.tab_settings), scope.settings,
        desc = str(R.string.settings_backup_desc)) {
        onChange(scope.copy(settings = it))
    }
    Text(str(R.string.trash_never_backup),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (scope.isEmpty) {
        Text(str(R.string.choose_one_thing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** A labelled single choice shown as chips, aligned with the toggle rows (icon column). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** Sub-option of the toggle above: 36dp lines it up under a toggle with an icon, 0dp in line with one without. */
    indent: androidx.compose.ui.unit.Dp = 36.dp,
    /** What the choice does, under the chips (aligned with them). */
    desc: String? = null,
    onPick: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp, top = 2.dp).size(24.dp))
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.width(indent))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            com.cripta.app.ui.components.ChipFlowRow {
                options.forEach { (v, l) -> com.cripta.app.ui.components.ChoiceChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(l) }) }
            }
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * The language Cripta is in. On Android 13+ the row opens the system's per-app language page;
 * before that the app follows the phone's language and the row only says so.
 */
@Composable
private fun LanguageRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val locale = com.cripta.app.Strings.locale
    val current = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    val perApp = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .then(if (perApp) Modifier.clickable(onClickLabel = str(R.string.language_pick)) {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                        android.net.Uri.fromParts("package", ctx.packageName, null)))
                }
            } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(current, style = MaterialTheme.typography.bodyLarge)
            Text(str(if (perApp) R.string.language_pick else R.string.language_follows_system),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (perApp) {
            Icon(CriptaIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Selection mark over a trash cell: a filled tick when selected, an empty ring while selecting. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TrashSelectionMark(selecting: Boolean, selected: Boolean) {
    if (!selecting) return
    if (selected) {
        // DS cr-cover.is-selected: a 3dp accent ring on the cover's edge.
        Box(Modifier.matchParentSize().clip(com.cripta.app.ui.theme.CoverShape)
            .border(3.dp, MaterialTheme.colorScheme.primary, com.cripta.app.ui.theme.CoverShape))
    }
    Box(
        Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .then(if (selected) Modifier else Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(CriptaIcons.Check, str(R.string.selected_m), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * Trash as a full screen browsable like the vault: folder mosaics and file covers in a grid.
 * Deleted folders open to show what they held, so a single file can be restored, and it reappears
 * at its original path. A tap on a file (or a folder's ⋮) opens its details with "Ripristina" and
 * "Elimina definitivamente"; a long-press starts selecting several, to restore or destroy just
 * those; "Svuota" empties everything.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrashDialog(
    items: List<com.cripta.app.data.db.FileEntity>,
    folders: List<com.cripta.app.data.db.FolderEntity>,
    days: Int,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRestoreFolder: (Long) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onEmpty: () -> Unit,
    onRestoreMany: (List<String>, List<Long>) -> Unit,
    onDeleteMany: (List<String>, List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    // Selection (long-press to start, then taps add / remove), within the level being shown.
    var selFiles by remember { mutableStateOf(emptySet<String>()) }
    var selFolders by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDeleteSel by remember { mutableStateOf(false) }
    var confirmFolder by remember { mutableStateOf<com.cripta.app.data.db.FolderEntity?>(null) }
    var confirmFile by remember { mutableStateOf<com.cripta.app.data.db.FileEntity?>(null) }
    var fileSheet by remember { mutableStateOf<com.cripta.app.data.db.FileEntity?>(null) }
    var folderSheet by remember { mutableStateOf<com.cripta.app.data.db.FolderEntity?>(null) }
    // Path of opened trashed folders (empty = top level of the trash).
    val path = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val fmt = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    val trashedIds = remember(folders) { folders.map { it.id }.toSet() }
    val byId = remember(folders) { folders.associateBy { it.id } }
    // A restored/destroyed folder disappears: drop it (and what's below) from the path.
    val openId = path.lastOrNull()?.takeIf { it in trashedIds }
    if (path.isNotEmpty() && openId == null) { androidx.compose.runtime.SideEffect { path.clear() } }
    // Top level: trashed folders/files whose parent is not itself in the trash. Inside: direct content.
    val shownFolders = folders.filter { if (openId == null) it.parentId == null || it.parentId !in trashedIds else it.parentId == openId }
    val shownFiles = items.filter { if (openId == null) it.folderId == null || it.folderId !in trashedIds else it.folderId == openId }
    fun left(at: Long?) = at?.let { days - ((System.currentTimeMillis() - it) / 86_400_000L).toInt() } ?: days
    fun leftText(at: Long?): String { val l = left(at); return if (l <= 1) str(R.string.destroyed_today) else str(R.string.days_left, l) }
    fun subtree(id: Long): Set<Long> {
        val ids = HashSet<Long>(); val todo = ArrayDeque(listOf(id))
        while (todo.isNotEmpty()) { val x = todo.removeFirst(); if (ids.add(x)) folders.filter { it.parentId == x }.forEach { todo.add(it.id) } }
        return ids
    }
    fun filesUnder(id: Long): List<com.cripta.app.data.db.FileEntity> = subtree(id).let { ids -> items.filter { it.folderId in ids } }
    /** Elements inside a trashed folder: its files and its subfolders, at every depth. */
    fun elementsUnder(id: Long): Int = subtree(id).let { ids -> items.count { it.folderId in ids } + ids.size - 1 }
    // Only what is still here and shown counts (restored / destroyed meanwhile, or another level).
    val shownFileIds = shownFiles.map { it.id }.toSet()
    val shownFolderIds = shownFolders.map { it.id }.toSet()
    val selF = selFiles intersect shownFileIds
    val selD = selFolders intersect shownFolderIds
    val inSel = selF.isNotEmpty() || selD.isNotEmpty()
    fun clearSel() { selFiles = emptySet(); selFolders = emptySet() }
    // Opening or leaving a folder starts over.
    LaunchedEffect(openId) { clearSel() }
    /** Elements a selection destroys: the selected files, the selected folders and all they hold. */
    val selElements = selF.size + selD.sumOf { elementsUnder(it) + 1 }

    com.cripta.app.ui.components.CriptaDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.activity.compose.BackHandler {
            when {
                inSel -> clearSel()
                path.isNotEmpty() -> path.removeAt(path.lastIndex)
                else -> onDismiss()
            }
        }
        // Its own full-screen window: the camera cutout (a side one in landscape) is not kept clear
        // by the app's usual insets here, and covered the back arrow, the title and the first
        // covers. safeDrawing = system bars + cutout (+ keyboard).
        val safe = androidx.compose.foundation.layout.WindowInsets.safeDrawing
        androidx.compose.material3.Scaffold(
            contentWindowInsets = safe,
            topBar = {
                androidx.compose.material3.TopAppBar(
                    // DS: while selecting, the bar lifts one tier (surface-raised).
                    colors = if (inSel) androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer) else com.cripta.app.ui.components.criptaTopBarColors(),
                    windowInsets = safe.only(
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                            androidx.compose.foundation.layout.WindowInsetsSides.Top
                    ),
                    navigationIcon = {
                        if (inSel) {
                            IconButton(onClick = { clearSel() }) { Icon(CriptaIcons.Close, str(R.string.exit_selection)) }
                        } else {
                            IconButton(onClick = { if (path.isNotEmpty()) path.removeAt(path.lastIndex) else onDismiss() }) {
                                Icon(CriptaIcons.ArrowBack, if (path.isNotEmpty()) str(R.string.back) else str(R.string.close_trash))
                            }
                        }
                    },
                    title = {
                        if (inSel) {
                            val n = selF.size + selD.size
                            Text(plural(R.plurals.n_selected_m, n), maxLines = 1)
                        } else Column {
                            Text(if (openId == null) str(R.string.sec_trash) else byId[openId]?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // The top level says its count and rule in the page (DS); a folder shows where it was.
                            if (openId != null) Text((listOf(str(R.string.sec_trash)) + path.dropLast(1).mapNotNull { byId[it]?.name }).joinToString(" › "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        if (inSel) {
                            val all = selF.size == shownFileIds.size && selD.size == shownFolderIds.size
                            IconButton(onClick = {
                                if (all) clearSel() else { selFiles = shownFileIds; selFolders = shownFolderIds }
                            }) {
                                Icon(CriptaIcons.SelectAll, if (all) str(R.string.deselect_all) else str(R.string.select_all),
                                    tint = if (all) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current)
                            }
                        } else if (openId != null) {
                            TextButton(onClick = { onRestoreFolder(openId) }) { com.cripta.app.ui.components.ButtonText(str(R.string.restore_all)) }
                        }
                    },
                )
            },
            bottomBar = {
                // The trash's actions sit at the bottom, always at the same height (the main buttons'
                // 56dp, as in the faces' pages): "Svuota il cestino", or those of the selection.
                val top = openId == null && (items.isNotEmpty() || folders.isNotEmpty())
                if (inSel || top) {
                    // DS cr-bottombar: surface-raised, both buttons 56dp, the destructive one tonal in danger.
                    androidx.compose.material3.BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        windowInsets = safe.only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                                androidx.compose.foundation.layout.WindowInsetsSides.Bottom
                        ),
                    ) {
                        if (!inSel) com.cripta.app.ui.components.DestructiveButton(str(R.string.empty_trash), onClick = { confirmEmpty = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), icon = CriptaIcons.Delete)
                        else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { confirmDeleteSel = true },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp), icon = CriptaIcons.Delete)
                            com.cripta.app.ui.components.FilledTonalButton(onClick = { onRestoreMany(selF.toList(), selD.toList()); clearSel() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                Icon(CriptaIcons.RestoreFromTrash, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                com.cripta.app.ui.components.ButtonText(str(R.string.restore))
                            }
                        }
                    }
                }
            },
        ) { pad ->
            if (shownFolders.isEmpty() && shownFiles.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(CriptaIcons.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Text(if (openId == null) str(R.string.trash_is_empty) else str(R.string.nothing_else_folder),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                }
            } else {
                val phone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = if (phone) androidx.compose.foundation.lazy.grid.GridCells.Fixed(3)
                        else androidx.compose.foundation.lazy.grid.GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (openId == null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        // DS Cestino: the rule in one line, then what the trash holds.
                        Column {
                            Text(str(R.string.trash_days_hint, days),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            com.cripta.app.ui.components.CountRow(str(R.string.in_trash), com.cripta.app.ui.components.formatCount(items.size + folders.size),
                                after = (if (items.size + folders.size == 1) " elemento" else " elementi") + " · " +
                                    com.cripta.app.ui.components.formatBytes(items.sumOf { it.sizeBytes }),
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    items(shownFolders.size, key = { "d" + shownFolders[it].id }) { i ->
                        val fo = shownFolders[i]
                        val under = filesUnder(fo.id)
                        val selected = fo.id in selD
                        fun toggle() { selFolders = if (selected) selFolders - fo.id else selFolders + fo.id }
                        Column {
                            Box {
                                com.cripta.app.ui.vault.FolderMosaic(
                                    folder = fo,
                                    stat = com.cripta.app.data.FolderStat(elementsUnder(fo.id), under.sumOf { it.sizeBytes }),
                                    previews = under.sortedByDescending { it.importedAt }.take(4),
                                    thumb = thumb,
                                    // Tap: its details and actions (restore, delete, open), like a file here.
                                    modifier = Modifier.combinedClickable(
                                        onClickLabel = if (inSel) (if (selected) str(R.string.deselect) else str(R.string.select)) else str(R.string.details),
                                        onLongClickLabel = str(R.string.select),
                                        onClick = { if (inSel) toggle() else folderSheet = fo }, onLongClick = { toggle() },
                                    ),
                                )
                                TrashSelectionMark(inSel, selected)
                            }
                            Text(leftText(fo.deletedAt), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                    items(shownFiles.size, key = { "f" + shownFiles[it].id }) { i ->
                        val f = shownFiles[i]
                        // The rounded clip bounds the touch ripple; the bottom padding keeps the two text
                        // lines out of its lower corners, which used to shave off their first/last letters.
                        val selected = f.id in selF
                        fun toggle() { selFiles = if (selected) selFiles - f.id else selFiles + f.id }
                        Column(Modifier.clip(com.cripta.app.ui.theme.CoverShape)
                            .combinedClickable(
                                onClickLabel = if (inSel) (if (selected) str(R.string.deselect) else str(R.string.select)) else str(R.string.details),
                                onLongClickLabel = str(R.string.select),
                                onClick = { if (inSel) toggle() else fileSheet = f }, onLongClick = { toggle() },
                            )
                            .padding(bottom = 8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(com.cripta.app.ui.theme.CoverShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                                val b = bmp
                                if (b != null) {
                                    androidx.compose.foundation.Image(b.asImageBitmap(), f.originalName,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(if (com.cripta.app.data.VaultRepository.isVideo(f.mimeType)) CriptaIcons.Movie
                                        else CriptaIcons.InsertDriveFile, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                }
                                if (!inSel) com.cripta.app.ui.components.formatDuration(f.durationMs)?.let {
                                    com.cripta.app.ui.components.CoverBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp))
                                }
                                TrashSelectionMark(inSel, selected)
                            }
                            Text(f.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                            Text("${com.cripta.app.ui.components.formatBytes(f.sizeBytes)} · ${leftText(f.deletedAt)}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        }

        // ---- details + actions of a file
        fileSheet?.let { f ->
            com.cripta.app.ui.components.CriptaSheet(onDismissRequest = { fileSheet = null }) {
                val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val where = f.folderId?.let { id ->
                        // Original path: trashed folders by name (live ones are not in this list).
                        generateSequence(byId[id]) { it.parentId?.let(byId::get) }.map { it.name }.toList().reversed()
                            .joinToString(" › ").ifEmpty { null }
                    } ?: str(R.string.vault)
                    listOfNotNull(
                        str(R.string.size_label) to com.cripta.app.ui.components.formatBytes(f.sizeBytes),
                        com.cripta.app.ui.components.formatDuration(f.durationMs)?.let { str(R.string.duration) to it },
                        if (f.width != null && f.height != null) str(R.string.resolution) to "${f.width}×${f.height}" else null,
                        str(R.string.location) to where,
                        str(R.string.deleted_on_m) to (f.deletedAt?.let { fmt.format(java.util.Date(it)) } ?: "—"),
                        str(R.string.destruction) to leftText(f.deletedAt).replaceFirstChar { it.uppercase() },
                    ).forEach { (k, v) -> InfoRow(k, v) }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Asked first, as for folders and several files: this destroys it for good.
                        com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { afterSheet { confirmFile = f } },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp))
                        Button(onClick = { afterSheet { onRestore(f.id) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.restore)) }
                    }
                    if (f.folderId != null && f.folderId in trashedIds) {
                        Text(str(R.string.restore_to_folder_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // ---- details + actions of a folder
        folderSheet?.let { fo ->
            val under = filesUnder(fo.id)
            com.cripta.app.ui.components.CriptaSheet(onDismissRequest = { folderSheet = null }) {
                val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fo.name, style = MaterialTheme.typography.titleMedium)
                    InfoRow(str(R.string.contents), elementsLabel(elementsUnder(fo.id)) +
                        " · " + com.cripta.app.ui.components.formatBytes(under.sumOf { it.sizeBytes }))
                    InfoRow(str(R.string.deleted_on_f), fo.deletedAt?.let { fmt.format(java.util.Date(it)) } ?: "—")
                    InfoRow(str(R.string.destruction), leftText(fo.deletedAt).replaceFirstChar { it.uppercase() })
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.cripta.app.ui.components.DestructiveButton(str(R.string.delete), onClick = { afterSheet { confirmFolder = fo } },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp))
                        Button(onClick = { afterSheet { onRestoreFolder(fo.id) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.restore)) }
                    }
                    TextButton(onClick = { afterSheet { path.add(fo.id) } }, modifier = Modifier.fillMaxWidth()) { com.cripta.app.ui.components.ButtonText(str(R.string.open_choose_items)) }
                }
            }
        }
        confirmFile?.let { cf ->
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmFile = null },
                title = { Text(str(R.string.delete_forever_q)) },
                text = { Text(str(R.string.file_destroy_secure, cf.originalName)) },
                confirmButton = {
                    TextButton(onClick = { onDelete(cf.id); confirmFile = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmFile = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        confirmFolder?.let { fo ->
            val n = elementsUnder(fo.id)
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmFolder = null },
                title = { Text(str(R.string.delete_forever_q)) },
                text = {
                    Text(if (n == 0) str(R.string.folder_destroy_secure, fo.name)
                        else str(R.string.folder_items_destroy, fo.name, elementsLabel(n)))
                },
                confirmButton = {
                    TextButton(onClick = { onDeleteFolder(fo.id); confirmFolder = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmFolder = null }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        if (confirmDeleteSel && inSel) {
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmDeleteSel = false },
                title = { Text(str(R.string.delete_forever_q)) },
                text = {
                    Text(plural(R.plurals.items_destroyed, selElements, elementsLabel(selElements)))
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteMany(selF.toList(), selD.toList()); clearSel(); confirmDeleteSel = false
                    }) { Text(str(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDeleteSel = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
        if (confirmEmpty) {
            com.cripta.app.ui.components.CriptaAlertDialog(
                onDismissRequest = { confirmEmpty = false },
                title = { Text(str(R.string.empty_trash_q)) },
                text = {
                    val total = items.size + folders.size
                    Text(plural(R.plurals.items_destroyed, total, elementsLabel(total)))
                },
                confirmButton = {
                    TextButton(onClick = { onEmpty(); confirmEmpty = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.empty_short), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmEmpty = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
            )
        }
    }
}

/** DS radio row: the ring (accent with its dot when chosen), the title, its caption under it. The whole row is the choice. */
@Composable
internal fun RadioRow(title: String, caption: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}
