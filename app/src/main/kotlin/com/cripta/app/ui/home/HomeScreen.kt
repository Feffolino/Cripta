package com.cripta.app.ui.home

import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.ui.theme.accentTint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.formatDuration
import com.cripta.app.ui.vault.CoverThumb
import com.cripta.app.ui.vault.FolderMosaic
import com.cripta.app.ui.vault.TypeFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.pressScale

/**
 * Home: a summary of the vault, "Continua a guardare", a carousel of the latest additions, then
 * shelves (recent, favorites, folders). Every cover uses the vault's own renderer (tags, duration,
 * quality, watched progress) so thumbnails look the same everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenFile: (String) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenFavorites: () -> Unit = {},
    onLock: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDownload: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteCount by vm.favoriteCount.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderStats by vm.folderStats.collectAsStateWithLifecycle()
    val folderPreviews by vm.folderPreviews.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val savedFilters by vm.savedFilters.collectAsStateWithLifecycle()
    val homePeople by vm.homePeople.collectAsStateWithLifecycle()
    val peopleBlurred by vm.peopleBlurred.collectAsStateWithLifecycle()
    // Blurred faces stay blurred until the row is touched once (per visit).
    var peopleRevealed by remember { mutableStateOf(false) }
    // ...and blur again after 30 s: shown once in public, they do not stay on screen.
    androidx.compose.runtime.LaunchedEffect(peopleRevealed) {
        if (peopleRevealed) { kotlinx.coroutines.delay(30_000); peopleRevealed = false }
    }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val display by vm.display.collectAsStateWithLifecycle()
    val coverVersions by vm.coverVersions.collectAsStateWithLifecycle()
    val convertStatus by vm.convertStatus.collectAsStateWithLifecycle()
    val autoLockMinutes by vm.autoLockMinutes.collectAsStateWithLifecycle()
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun open(list: List<FileWithTags>, id: String) {
        vm.publishQueue(list.map { it.file.id })
        onOpenFile(id)
    }

    // Empty vault: "Importa file" opens the system picker right here, then shows Cartelle where
    // the import progress and the new files appear.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { vm.importFiles(uris); onOpenFolders() }
    }

    // Landscape: the top bar scrolls away (down hides, up reveals), like in Cartelle.
    val scrollBehavior = if (landscape) TopAppBarDefaults.enterAlwaysScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val homeList = androidx.compose.foundation.lazy.rememberLazyListState()
    val topLift = remember { com.cripta.app.ui.components.TopBarLift() }
    com.cripta.app.ui.components.TrackTopBarLift(topLift, homeList)
    // Faces on the shelves' covers follow "Persone in Home": none when off, blurred until the row is touched.
    val coverPeopleMap by vm.coverPeople.collectAsStateWithLifecycle()
    val coverPeople = remember(coverPeopleMap, peopleBlurred, peopleRevealed) {
        com.cripta.app.ui.vault.CoverPeople(coverPeopleMap, peopleBlurred && !peopleRevealed) { vm.faceCrop(it) }
    }
    androidx.compose.runtime.CompositionLocalProvider(com.cripta.app.ui.vault.LocalCoverPeople provides coverPeople) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                scrollBehavior = scrollBehavior,
                colors = com.cripta.app.ui.components.criptaTopBarColors(topLift.lifted),
                title = { com.cripta.app.ui.components.HeaderTitle(str(R.string.tab_home)) },
                actions = {
                    IconButton(onClick = onLock) { Icon(CriptaIcons.Lock, str(R.string.lock)) }
                },
            )
        },
    ) { pad ->
        // Loading -> empty / content: one calm crossfade instead of a hard cut after unlock.
        val phase = when {
            !loaded -> HomePhase.LOADING
            recents.isEmpty() && folders.isEmpty() -> HomePhase.EMPTY
            else -> HomePhase.CONTENT
        }
        androidx.compose.animation.Crossfade(
            targetState = phase,
            animationSpec = tween(Motion.MEDIUM, 0, Motion.EaseOutQuart),
            label = "homePhase",
        ) { p ->
        when (p) {
        HomePhase.LOADING -> HomeSkeleton(Modifier.fillMaxSize().padding(pad), landscape)
        HomePhase.EMPTY -> EmptyHome(
            Modifier.fillMaxSize().padding(pad),
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onDownload = onOpenDownload,
            onOpenFolders = onOpenFolders,
            onOpenSecurity = { vm.openSecurity(); onOpenSettings() },
            autoLockMinutes = autoLockMinutes,
        )
        HomePhase.CONTENT -> LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = homeList,
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // DS Home: the search pill first (the query opens in Cartelle).
            item {
                var q by remember { mutableStateOf("") }
                com.cripta.app.ui.components.CriptaTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text(str(R.string.search_vault)) },
                    leadingIcon = { Icon(CriptaIcons.Search, null) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        if (q.isNotBlank()) { vm.search(q.trim()); q = ""; onOpenFolders() }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                SummaryCard(
                    photos = summary.images, videos = summary.videos, others = summary.others, onlyNotes = summary.others == summary.notes,
                    onPhotos = { vm.showType(TypeFilter.IMAGE); onOpenFolders() },
                    onVideos = { vm.showType(TypeFilter.VIDEO); onOpenFolders() },
                    onNotes = { vm.showType(TypeFilter.OTHER); onOpenFolders() },
                    compact = landscape,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (convertStatus.active || convertStatus.lastResult != null || convertStatus.pendingOriginals.isNotEmpty()) {
                item {
                    com.cripta.app.ui.vault.ConvertBanner(convertStatus, onCancel = vm::cancelConversion,
                        onDismiss = vm::dismissConvertResult, onResolveOriginal = vm::resolveOriginal)
                }
            }
            if (continueWatching.isNotEmpty()) {
                item { ShelfHeader(str(R.string.continue_watching), onSeeAll = { vm.showType(TypeFilter.VIDEO); onOpenFolders() }) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.file.id }) { fwt ->
                            ContinueCard(fwt, display, coverVersions[fwt.file.id] ?: 0, vm::thumb) { open(continueWatching, fwt.file.id) }
                        }
                    }
                }
            }
            if (homePeople.isNotEmpty()) {
                item { ShelfHeader(str(R.string.people), onSeeAll = { vm.openPeople() }) }
                item {
                    val hidden = peopleBlurred && !peopleRevealed
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        items(homePeople.size, key = { homePeople[it].id }) { i ->
                            val p = homePeople[i]
                            Column(
                                Modifier.width(72.dp).clip(MaterialTheme.shapes.small)
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClickLabel = if (hidden) str(R.string.show_faces) else str(R.string.open_profile_of, p.title),
                                        onLongClickLabel = if (p.entity.favorite) str(R.string.unfavorite_short) else str(R.string.favorite_add),
                                        onClick = { if (hidden) peopleRevealed = true else com.cripta.app.ui.people.PeopleNav.openPerson(p.id) },
                                        onLongClick = { vm.setPersonFavorite(p.id, !p.entity.favorite) },
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    com.cripta.app.ui.settings.FaceAvatar(p.cover, { vm.faceCrop(it) }, 56.dp,
                                        // The blur keeps the face's round shape (its default edge is a rectangle).
                                        if (hidden) Modifier.blur(12.dp, androidx.compose.ui.draw.BlurredEdgeTreatment(CircleShape)) else Modifier)
                                    if (p.entity.favorite) com.cripta.app.ui.settings.FavoriteBadge(Modifier.align(Alignment.TopEnd))
                                }
                                Text(if (hidden) "•••" else p.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            if (savedFilters.isNotEmpty()) {
                item {
                    LazyRow(Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedFilters.size, key = { savedFilters[it].first.id }) { i ->
                            val (sf, count) = savedFilters[i]
                            // DS cr-chip: a saved filter is a one-tap chip.
                            com.cripta.app.ui.components.ActionChip(
                                onClick = { vm.applySavedFilter(sf.json); onOpenFolders() },
                                leadingIcon = { Icon(CriptaIcons.FilterList, null, modifier = Modifier.size(18.dp)) },
                                label = { Text("${sf.name} · $count", maxLines = 1) },
                            )
                        }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item { ShelfHeader(str(R.string.recently_added), onSeeAll = onOpenFolders) }
                item { CoverShelf(recents, display, coverVersions, vm::thumb) { id -> open(recents, id) } }
            }
            if (favorites.isNotEmpty()) {
                item { ShelfHeader(str(R.string.favorites), onSeeAll = onOpenFavorites) }
                item { CoverShelf(favorites, display, coverVersions, vm::thumb) { id -> open(favorites, id) } }
            }
            if (folders.isNotEmpty()) {
                item { ShelfHeader(str(R.string.home_folders), onSeeAll = onOpenFolders) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            val source = remember { MutableInteractionSource() }
                            FolderMosaic(
                                folder = folder,
                                stat = folderStats[folder.id],
                                previews = folderPreviews[folder.id].orEmpty(),
                                thumb = vm::thumb,
                                modifier = Modifier.width(128.dp).pressScale(source)
                                    .clickable(source, LocalIndication.current, role = Role.Button,
                                        onClickLabel = str(R.string.open_folder)) { vm.openFolder(folder.id); onOpenFolders() },
                            )
                        }
                    }
                }
            }
        }
        }
        }
    }
    }
}

private enum class HomePhase { LOADING, EMPTY, CONTENT }

/**
 * Home while the vault is read: the same rhythm as the content (search pill, the strip with its
 * figures as blocks, two shelves of covers), so the real thing replaces it without moving.
 */
@Composable
private fun HomeSkeleton(modifier: Modifier, landscape: Boolean) {
    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp), CircleShape)
        SummaryCard(0, 0, 0, onlyNotes = false, onPhotos = {}, onVideos = {}, onNotes = {}, compact = landscape,
            modifier = Modifier.padding(top = 12.dp), loading = true)
        repeat(if (landscape) 1 else 2) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                com.cripta.app.ui.components.Skeleton(Modifier.width(110.dp).height(14.dp))
            }
            LazyRow(userScrollEnabled = false, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(4) {
                    Column(Modifier.width(128.dp).padding(bottom = 6.dp)) {
                        com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), com.cripta.app.ui.theme.CoverShape)
                        com.cripta.app.ui.components.Skeleton(Modifier.padding(top = 8.dp, start = 2.dp).width(84.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * What the vault holds, as one quiet strip: videos, photos and space, each a tappable segment.
 * Numbers lead, labels follow; no per-type colours (the accent is kept for actions).
 */
@Composable
private fun SummaryCard(photos: Int, videos: Int, others: Int, onlyNotes: Boolean, onPhotos: () -> Unit, onVideos: () -> Unit, onNotes: () -> Unit,
                        modifier: Modifier = Modifier, compact: Boolean = false, loading: Boolean = false) {
    // DS cr-summary: one tonal object (surface-raised, radius-lg), equal centred columns.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = com.cripta.app.ui.theme.RadiusLg,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 4.dp)) {
            SummaryTile(CriptaIcons.Image, com.cripta.app.ui.components.formatCount(photos), str(R.string.photos), Modifier.weight(1f), onPhotos, compact, loading)
            SummaryTile(CriptaIcons.Movie, com.cripta.app.ui.components.formatCount(videos), str(R.string.videos), Modifier.weight(1f), onVideos, compact, loading)
            // The third tile opens the "Altro" filter (notes, PDFs, any other file): it counts all of
            // them, so the number matches what opens. Named "Note" while notes are all there is.
            SummaryTile(CriptaIcons.Description, com.cripta.app.ui.components.formatCount(others), if (onlyNotes) str(R.string.notes) else str(R.string.other),
                Modifier.weight(1f), onNotes, compact, loading)
        }
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, value: String, label: String, modifier: Modifier, onClick: () -> Unit, compact: Boolean = false,
                        loading: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier.heightIn(min = 48.dp).clip(com.cripta.app.ui.theme.RadiusLg)
            .clickable(source, LocalIndication.current, role = Role.Button, onClickLabel = str(R.string.show), onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (compact) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val figure = if (compact) MaterialTheme.typography.titleLarge else com.cripta.app.ui.theme.FigureStyle
        // Loading: the figure is a block as tall as its line, so the strip keeps its height.
        if (loading) com.cripta.app.ui.components.Skeleton(Modifier.padding(vertical = 3.dp).width(40.dp).height(with(androidx.compose.ui.platform.LocalDensity.current) { figure.lineHeight.toDp() } - 6.dp))
        else Text(value, style = figure, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Section title, its count and "Vedi tutti": the type carries the hierarchy, the accent marks the action. */
@Composable
private fun ShelfHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    // Whole row is the "Vedi tutti" button (48dp tall); the title is a heading for TalkBack.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onSeeAll != null) Modifier.clickable(role = Role.Button, onClickLabel = str(R.string.see_all), onClick = onSeeAll) else Modifier)
            // Space above the heading, little below: it belongs to the row under it.
            .padding(start = 16.dp, end = 16.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() })
        if (onSeeAll != null) {
            Text(str(R.string.see_all), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Horizontal shelf of vault-style covers with the name underneath. */
@Composable
private fun CoverShelf(
    items: List<FileWithTags>,
    display: DisplayPrefs,
    coverVersions: Map<String, Int>,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onOpen: (String) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.file.id }) { fwt ->
            val bmp by produceState<android.graphics.Bitmap?>(null, fwt.file.id, coverVersions[fwt.file.id] ?: 0) { value = thumb(fwt.file) }
            val source = remember { MutableInteractionSource() }
            Column(Modifier.width(128.dp).pressScale(source).clip(com.cripta.app.ui.theme.CoverShape)
                .clickable(source, LocalIndication.current, onClickLabel = str(R.string.open)) { onOpen(fwt.file.id) }
                .padding(bottom = 6.dp)) {
                CoverThumb(fwt.file, fwt.tags, display, bmp, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(fwt.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
            }
        }
    }
}

/** Wide card for a video in progress: cover, progress bar, time left. */
@Composable
private fun ContinueCard(
    fwt: FileWithTags,
    display: DisplayPrefs,
    coverVersion: Int,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    val f = fwt.file
    val bmp by produceState<android.graphics.Bitmap?>(null, f.id, coverVersion) { value = thumb(f) }
    val source = remember { MutableInteractionSource() }
    Column(Modifier.width(128.dp).pressScale(source).clip(com.cripta.app.ui.theme.CoverShape)
        .clickable(source, LocalIndication.current, onClickLabel = str(R.string.resume), onClick = onClick)
        .padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(com.cripta.app.ui.theme.CoverShape)) {
            CoverThumb(f, emptyList(), display.copy(resumePlayback = true, showDurationBadge = true), bmp, Modifier.fillMaxSize())
        }
        Text(f.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
        val left = (f.durationMs ?: 0L) - (f.playbackPosMs ?: 0L)
        formatDuration(left.takeIf { it > 0 })?.let {
            Text(str(R.string.time_left, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/** The latest additions as a swipeable 16:9 carousel, title/duration/tags over a gradient. */
@Composable
private fun HeroCarousel(
    items: List<FileWithTags>,
    display: DisplayPrefs,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    coverVersions: Map<String, Int>,
    onOpen: (String) -> Unit,
) {
    val pager = rememberPagerState { items.size }
    // No heading of its own: it takes the room a heading would.
    Column(Modifier.padding(top = 20.dp)) {
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { page ->
            val fwt = items[page]
            val f = fwt.file
            val bmp by produceState<android.graphics.Bitmap?>(null, f.id, coverVersions[f.id] ?: 0) { value = thumb(f) }
            val source = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).pressScale(source, pressed = 0.98f).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(source, LocalIndication.current, onClickLabel = str(R.string.open)) { onOpen(f.id) }) {
                // The title is already read from the overlay text below.
                bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                // A short shade only under the title: most of the picture stays clear.
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(80.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.originalName, color = Color.White, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        formatDuration(f.durationMs)?.let {
                            Text(" · $it", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                    if (fwt.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fwt.tags.take(3).forEach { t ->
                                com.cripta.app.ui.components.TagPill(com.cripta.app.ui.vault.badgeText(t, display),
                                    if (display.tagColors) com.cripta.app.ui.theme.tagColor(t) else MaterialTheme.colorScheme.primary,
                                    onImage = true, tagIcon = t.icon)
                            }
                            if (fwt.tags.size > 3) com.cripta.app.ui.components.MoreTagsPill(fwt.tags.size - 3, onImage = true)
                        }
                    }
                }
            }
        }
        if (items.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(items.size) { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

/**
 * Empty vault (in practice the first run): three short pages to swipe through instead of a bare
 * message, one step each (add, organise, lock). Every page acts on its own: import is the main
 * action, so nothing has to be "finished" before using the app. Dots and Indietro / Avanti below.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EmptyHome(
    modifier: Modifier,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenSecurity: () -> Unit,
    autoLockMinutes: Int,
) {
    val pager = rememberPagerState { 3 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun go(page: Int) = scope.launch { pager.animateScrollToPage(page, animationSpec = tween(Motion.LONG, 0, Motion.EaseOutQuart)) }
    val lock = when (autoLockMinutes) {
        -1 -> str(R.string.lock_never)
        0 -> str(R.string.lock_on_leave)
        else -> str(R.string.lock_after_minutes, autoLockMinutes)
    }
    Column(modifier) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> StartPage(CriptaIcons.PhotoLibrary, str(R.string.start_secure_title),
                    str(R.string.start_secure_text)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.fillMaxWidth(), onClick = onImport) {
                            Icon(CriptaIcons.Upload, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(str(R.string.import_files), maxLines = 2)
                        }
                        com.cripta.app.ui.components.FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = onDownload) {
                            Icon(CriptaIcons.Link, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(str(R.string.from_link), maxLines = 2)
                        }
                    }
                }
                1 -> StartPage(CriptaIcons.Folder, str(R.string.start_order_title),
                    str(R.string.start_order_text)) {
                    com.cripta.app.ui.components.FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenFolders) { Text(str(R.string.open_folders), maxLines = 2) }
                }
                else -> StartPage(CriptaIcons.Lock, str(R.string.start_lock_title),
                    str(R.string.start_lock_text, lock)) {
                    com.cripta.app.ui.components.FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenSecurity) { Text(str(R.string.open_security), maxLines = 2) }
                }
            }
        }
        // Indietro / dots / Avanti. Fixed slots so the dots never shift when a button hides.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (pager.currentPage > 0) com.cripta.app.ui.components.TextButton(onClick = { go(pager.currentPage - 1) }) { Text(str(R.string.back), maxLines = 1) }
            }
            PageDots(pager.currentPage, 3)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (pager.currentPage < 2) com.cripta.app.ui.components.TextButton(onClick = { go(pager.currentPage + 1) }) { Text(str(R.string.next), maxLines = 1) }
            }
        }
    }
}

/** One page of the start carousel: a mark, what to do, why, and its action(s). */
@Composable
private fun StartPage(icon: ImageVector, title: String, text: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                .widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // DS empty state: one object, so a tonal disc (accent-tint) with the step's glyph in accent.
            Surface(color = MaterialTheme.colorScheme.accentTint, shape = CircleShape, modifier = Modifier.size(120.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp).semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
            action()
        }
    }
}

/** Where the carousel is: the current dot is a wider accent pill, so it never relies on colour alone. */
@Composable
private fun PageDots(current: Int, count: Int) {
    Row(Modifier.semantics { contentDescription = str(R.string.step_of, current + 1, count) },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == current
            val w by androidx.compose.animation.core.animateDpAsState(if (on) 24.dp else 8.dp,
                tween(Motion.MEDIUM, 0, Motion.EaseOutQuart), label = "dot")
            Box(Modifier.size(w, 8.dp).clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
