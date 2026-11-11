package com.cripta.app.ui.settings

import com.cripta.app.plural
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cripta.app.ui.theme.Motion
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DeleteOriginalPolicy
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.vault.LabelEditorDialog

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
    // The update's installer opens only while this screen is the one in front (see the view model).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.setSettingsForeground(true)
            else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) vm.setSettingsForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        vm.setSettingsForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); vm.setSettingsForeground(false) }
    }
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
    var mergingTags by remember { mutableStateOf(false) }
    var confirmDeleteUnused by remember { mutableStateOf(false) }

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
    val backupFolderStats by vm.backupFolderStats.collectAsStateWithLifecycle()
    val backupRootStat by vm.backupRootStat.collectAsStateWithLifecycle()

    var showAllTags by remember { mutableStateOf(false) }

    var tagQuery by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val space by vm.space.collectAsStateWithLifecycle()
    val tagCount by vm.tagCount.collectAsStateWithLifecycle()
    val tagUsage by vm.tagUsage.collectAsStateWithLifecycle()
    val cleanupFiles by vm.allFiles.collectAsStateWithLifecycle()
    var showCleanup by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshSpace() }
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
    // Set by the list or page on screen, so a page opening at a search result's section (or the
    // list coming back scrolled) shows the right bar at once.
    val topLift = remember { com.cripta.app.ui.components.TopBarLift() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                scrollBehavior = scrollBehavior,
                colors = com.cripta.app.ui.components.criptaTopBarColors(topLift.lifted),
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
            val mainState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            com.cripta.app.ui.components.TrackTopBarLift(topLift, mainState)
            MainSettingsList(
                state = mainState,
                modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()
                    .wrapContentWidth().widthIn(max = maxW).fillMaxWidth(),
                columns = if (landscape) 2 else 1,
                query = query,
                onQuery = { query = it },
                summaries = SettingsPage.entries.associateWith { pageSummary(it, s, tagCount, trashed.size + trashedFolders.size, dupWaiting?.groups?.size) },
                space = space,
                onOpenSpace = { page = SettingsPage.SPAZIO.name; anchor = str(R.string.sec_space_detail) },
                // From a search result: land on its section and keep the query for the way back.
                onOpen = { p, a -> page = p.name; anchor = a; if (a == null) query = "" },
                onLock = { vm.lockNow(); onBack() },
            )
        } else {
          Column(Modifier.fillMaxSize().padding(pad).wrapContentWidth().widthIn(max = maxW).fillMaxWidth()) {
            if (current == SettingsPage.ETICHETTE) {
                // The live preview stays pinned on top while the options scroll under it.
                // Same side margin as the sections under it; the caption reads as one with the covers.
                // It is one header with the top bar: it lifts with it when the sections scroll under.
                com.cripta.app.ui.components.LiftedHeader(topLift.lifted) {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        // Sizes on both sides of the automatic rows' steps (100 and 150 dp): 3 + 1 rows
                        // upright, 2 + 1 sideways, as covers of those sizes get them.
                        com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(if (landscape) 104.dp else 152.dp), tags)
                        com.cripta.app.ui.vault.CoverPreview(s.display, Modifier.size(if (landscape) 72.dp else 92.dp), tags)
                        Text(str(R.string.live_preview), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                            ToggleRow(str(R.string.folders_in_a_row), s.display.foldersHorizontal,
                                desc = str(R.string.folders_in_a_row_desc)) { vm.setFoldersHorizontal(it) }
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
                                // The pickers' search field (name or acronym), always above the list.
                                com.cripta.app.ui.vault.TagSearchField(tagQuery, Modifier.padding(bottom = 8.dp)) { tagQuery = it }
                                val searching = tagQuery.isNotBlank()
                                val found = if (searching) tags.filter { com.cripta.app.ui.vault.tagMatches(it, tagQuery) } else tags
                                // Long libraries are collapsed so the page stays short; opening or
                                // closing the rest grows the list in place (ease-out, no bounce).
                                // Not in the custom order: a row could not be dragged past the last one shown.
                                val visible = if (showAllTags || custom || searching || found.size <= 8) found else found.take(8)
                                if (searching && found.isEmpty()) Text(str(R.string.tag_no_match, tagQuery.trim()),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TagLibraryList(
                                    tags = visible,
                                    // Moving within search results would renumber the whole library from a part of it.
                                    reorderable = custom && !searching,
                                    colorOf = { tag -> if (s.display.tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary },
                                    countOf = { tag -> tagUsage[tag.id]?.files },
                                    onEdit = { editTag = it },
                                    onDelete = { deleteTag = it },
                                    onMoveStep = { tag, up -> vm.moveTag(tag.id, up = up) },
                                    onMoveTo = { tag, index -> vm.moveTagTo(tag.id, index) },
                                    modifier = Modifier.animateContentSize(Motion.enter(Motion.MEDIUM)),
                                )
                                if (tags.size > 8 && !custom && !searching) {
                                    TextButton(onClick = { showAllTags = !showAllTags }) {
                                        com.cripta.app.ui.components.ButtonText(if (showAllTags) str(R.string.show_less) else str(R.string.show_all_n, tags.size))
                                    }
                                }
                            }
                            com.cripta.app.ui.components.FilledTonalButton(
                                onClick = { addTag = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                Icon(CriptaIcons.Add, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                com.cripta.app.ui.components.ButtonText(str(R.string.add_tag))
                            }
                        }
                    },
                    // The order sits right under the library it sorts (its drag handles appear there).
                    sec(str(R.string.sec_library_order)) {
                        val mode = s.tagSortMode
                        Section(str(R.string.sec_library_order), when (mode) {
                            com.cripta.app.data.TagSortMode.CUSTOM -> str(R.string.custom_order_hint)
                            com.cripta.app.data.TagSortMode.USED -> str(R.string.used_order_hint)
                            com.cripta.app.data.TagSortMode.ALPHA -> str(R.string.alpha_order_hint)
                        }) {
                            com.cripta.app.ui.components.ChipFlowRow {
                                listOf(
                                    com.cripta.app.data.TagSortMode.ALPHA to str(R.string.alphabetical),
                                    com.cripta.app.data.TagSortMode.USED to str(R.string.most_used),
                                    com.cripta.app.data.TagSortMode.CUSTOM to str(R.string.custom),
                                ).forEach { (m, label) ->
                                    com.cripta.app.ui.components.ChoiceChip(selected = mode == m,
                                        onClick = { vm.setTagSortMode(m) }, label = { Text(label) })
                                }
                            }
                        }
                    },
                    sec(str(R.string.sec_on_covers)) {
                        Section(str(R.string.sec_on_covers)) {
                            ToggleRow(str(R.string.show_tags), s.display.showTagsOnCover) { vm.setShowTagsOnCover(it) }
                            if (s.display.showTagsOnCover) {
                                ChoiceRow(str(R.string.tag_style), listOf(com.cripta.app.data.CoverTagStyle.BUBBLE to str(R.string.tag_style_bubble),
                                    com.cripta.app.data.CoverTagStyle.PILL to str(R.string.tag_style_pill)), s.display.coverTagStyle,
                                    indent = 0.dp, desc = str(R.string.tag_style_desc)) { vm.setCoverTagStyle(it) }
                                ChoiceRow(str(R.string.tag_rows), listOf(0 to str(R.string.quality_auto), 1 to "1", 2 to "2", 3 to "3"), s.display.coverTagRows,
                                    indent = 0.dp, desc = str(R.string.tag_rows_desc)) { vm.setCoverTagRows(it) }
                            }
                        }
                    },
                    sec(str(R.string.sec_colours)) {
                        Section(str(R.string.sec_colours)) {
                            ToggleRow(str(R.string.coloured_tags), s.display.tagColors) { vm.setTagColors(it) }
                            if (s.display.tagColors) {
                                // A plain settings item: title, its caption 2dp under it, then the choice.
                                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text(str(R.string.default_colour), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (s.display.defaultTagColor == null) str(R.string.default_colour_auto_desc)
                                        else str(R.string.default_colour_set_desc),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Box(Modifier.padding(top = 10.dp)) {
                                        com.cripta.app.ui.vault.TagColorPicker(
                                            selected = s.display.defaultTagColor,
                                            autoColor = com.cripta.app.ui.theme.autoTagColor("etichetta"),
                                            autoDescription = str(R.string.auto_colour_per_name),
                                            onPick = { vm.setDefaultTagColor(it) },
                                        )
                                    }
                                }
                                if (tags.isNotEmpty()) {
                                    com.cripta.app.ui.components.FilledTonalButton(onClick = { confirmRandomColors = true },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                                        Icon(CriptaIcons.Palette, null, Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                        com.cripta.app.ui.components.ButtonText(str(R.string.random_colours_all))
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
                    // Rare actions on the library, last; the destructive ones in the error colour.
                    sec(str(R.string.sec_tag_maintenance)) {
                        Section(str(R.string.sec_tag_maintenance), str(R.string.tag_maintenance_hint)) {
                            com.cripta.app.ui.components.FilledTonalButton(
                                onClick = { mergingTags = true }, enabled = tags.size >= 2,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                Icon(CriptaIcons.Merge, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                com.cripta.app.ui.components.ButtonText(str(R.string.merge_tags))
                            }
                            Text(str(R.string.merge_tags_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            // On no file at all, the trash included (a restored file keeps its tags).
                            val unused = tags.filter { tagUsage[it.id]?.links == 0 }
                            Text(
                                if (unused.isEmpty()) str(R.string.no_unused_tags)
                                else str(R.string.unused_tags_line, unused.joinToString(", ") { it.name }),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            if (unused.isNotEmpty()) {
                                com.cripta.app.ui.components.DestructiveButton(
                                    str(R.string.delete_unused_n, unused.size), onClick = { confirmDeleteUnused = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), icon = CriptaIcons.Delete,
                                )
                            }
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
                    // One-feature page: what it does, its state (last and next backup), the main action.
                    sec(str(R.string.sec_encrypted_backup)) {
                        PageIntro(str(R.string.encrypted_backup_hint)) {
                            LastBackupRow(s.lastBackup)
                            // Always visible: how big the archive would be if made now.
                            backupEstimate?.let { (n, bytes) ->
                                com.cripta.app.ui.components.CountRow(str(R.string.next_backup),
                                    com.cripta.app.ui.components.formatBytes(bytes),
                                    after = " · " + plural(R.plurals.files_n, n, com.cripta.app.ui.components.formatCount(n)))
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
                                        if (p != null) {
                                            val smooth by animateFloatAsState(p, tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "backupPct")
                                            com.cripta.app.ui.components.CountRow(busy, "${(p * 100).toInt()}%", progress = smooth)
                                        } else {
                                            Text(busy, style = MaterialTheme.typography.bodyMedium)
                                            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                        }
                                        Text(eta?.replaceFirstChar { it.uppercase() }?.let { str(R.string.eta_keep_open, it) }
                                                ?: str(R.string.may_take_minutes),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { exportLauncher.launch("cripta-backup-${backupStamp()}.criptabak") },
                                            enabled = !s.backupScope.isEmpty,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        ) {
                                            Icon(CriptaIcons.Backup, null, Modifier.size(20.dp))
                                            com.cripta.app.ui.components.ButtonText(str(R.string.create_backup), modifier = Modifier.padding(start = 8.dp))
                                        }
                                        backupEstimate?.let { (_, bytes) ->
                                            Text(if (s.backupCompressDocs) str(R.string.backup_estimate_compressed)
                                                else str(R.string.backup_space_needed, com.cripta.app.ui.components.formatBytes(bytes)),
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    },
                    sec(str(R.string.sec_what_to_include)) {
                        Section(str(R.string.sec_what_to_include), str(R.string.what_to_include_hint)) {
                            BackupScopeChoice(s.backupScope, backupFolders, backupFolderStats, backupRootStat) { vm.setBackupScope(it) }
                        }
                    },
                    sec(str(R.string.sec_backup_options)) {
                        Section(str(R.string.sec_backup_options)) {
                            ToggleRow(str(R.string.compress_documents), s.backupCompressDocs,
                                desc = str(R.string.compress_documents_desc), icon = CriptaIcons.Description) {
                                vm.setBackupCompressDocs(it)
                            }
                        }
                    },
                    sec(str(R.string.sec_backup_restore)) {
                        Section(str(R.string.sec_backup_restore), str(R.string.restore_adds_hint)) {
                            com.cripta.app.ui.components.FilledTonalButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                enabled = backupBusy == null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Icon(CriptaIcons.Download, null, Modifier.size(20.dp))
                                com.cripta.app.ui.components.ButtonText(str(R.string.restore_from_backup), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    },
                )
                SettingsPage.SPAZIO -> listOf(
                    sec(str(R.string.sec_space_detail)) {
                        Section(str(R.string.sec_space_detail), str(R.string.space_detail_hint)) {
                            SpaceDetails(space, trashed.size + trashedFolders.size,
                                onCleanup = { showCleanup = true },
                                onClearCache = { vm.clearCache() },
                                onEmptyTrash = { confirmEmptyTrash = true })
                        }
                    },
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
            com.cripta.app.ui.components.TrackTopBarLift(topLift, gridState)
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

    if (showCleanup) {
        VaultCleanupDialog(cleanupFiles, { vm.thumbOf(it) }, s.trashEnabled, s.trashDays,
            onDelete = { vm.deleteFiles(it) }, onDismiss = { showCleanup = false })
    }
    if (confirmEmptyTrash) {
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text(str(R.string.empty_trash_q)) },
            text = { Text(str(R.string.cleanup_delete_text, com.cripta.app.ui.components.formatBytes(space?.byKind?.get(com.cripta.app.data.SpaceKind.TRASH) ?: 0L))) },
            confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirmEmptyTrash = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.empty_trash)) } },
            dismissButton = { TextButton(onClick = { confirmEmptyTrash = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
        )
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
        LabelEditorDialog(
            title = str(R.string.new_tag),
            showColor = true, showIcon = true, showPinned = true, showOnCovers = true,
            takenNames = tags.map { it.name },
            onConfirm = { draft -> vm.createTag(draft); addTag = false },
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
            showColor = true, initialColor = tag.color,
            showPinned = true, initialPinned = tag.pinned,
            showIcon = true, initialIcon = tag.icon,
            showOnCovers = true, initialOnCovers = tag.onCovers,
            usage = tagUsage[tag.id],
            onDelete = { vm.deleteTag(tag.id); editTag = null },
            takenNames = tags.filter { it.id != tag.id }.map { it.name },
            onConfirm = { draft -> vm.saveTag(tag.id, draft); editTag = null },
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

    if (mergingTags) {
        MergeTagsDialog(
            tags = tags,
            colorOf = { tag -> if (s.display.tagColors) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary },
            onMerge = { from, into -> vm.mergeTags(from.id, into.id); mergingTags = false },
            onDismiss = { mergingTags = false },
        )
    }
    if (confirmDeleteUnused) {
        val unused = tags.filter { tagUsage[it.id]?.links == 0 }
        com.cripta.app.ui.components.CriptaAlertDialog(
            onDismissRequest = { confirmDeleteUnused = false },
            title = { Text(str(R.string.delete_unused_q)) },
            text = { Text(str(R.string.delete_unused_text, unused.joinToString(", ") { it.name })) },
            confirmButton = {
                TextButton(enabled = unused.isNotEmpty(), onClick = { vm.deleteUnusedTags(unused.map { it.id }); confirmDeleteUnused = false }) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteUnused = false }) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
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

/** Calm swap between two states of a panel: crossfade while the height follows. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<*>.calmSwap(): androidx.compose.animation.ContentTransform =
    (androidx.compose.animation.fadeIn(Motion.enter()) togetherWith androidx.compose.animation.fadeOut(Motion.exit())).using(
        androidx.compose.animation.SizeTransform { _, _ -> Motion.enter(Motion.MEDIUM) })
