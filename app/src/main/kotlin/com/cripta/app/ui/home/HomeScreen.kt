package com.cripta.app.ui.home

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.semantics
import com.cripta.app.ui.theme.Motion
import com.cripta.app.ui.theme.pressScale

/**
 * Home: a summary of the vault, "Continua a guardare", a carousel of the latest additions, then
 * shelves (recent, favorites, folders). Every cover uses the vault's own renderer (tags, duration,
 * quality, watched progress) so thumbnails look the same everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val recents by vm.recents.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val favoriteCount by vm.favoriteCount.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val folders by vm.folders.collectAsState()
    val folderStats by vm.folderStats.collectAsState()
    val folderPreviews by vm.folderPreviews.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val savedFilters by vm.savedFilters.collectAsState()
    val summary by vm.summary.collectAsState()
    val display by vm.display.collectAsState()
    val coverVersions by vm.coverVersions.collectAsState()
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
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                title = { com.cripta.app.ui.components.HeaderTitle("Cripta") },
                actions = {
                    // Tools (duplicates, trash, backup) one tap away from Home.
                    IconButton(onClick = { vm.openTools(); onOpenSettings() }) { Icon(Icons.Filled.Build, "Strumenti") }
                    IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Blocca") }
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
        HomePhase.LOADING -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        HomePhase.EMPTY -> EmptyHome(
            Modifier.fillMaxSize().padding(pad),
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onDownload = onOpenDownload,
        )
        HomePhase.CONTENT -> LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SummaryCard(
                    videos = summary.videos, photos = summary.images, bytes = summary.bytes,
                    onVideos = { vm.showType(TypeFilter.VIDEO); onOpenFolders() },
                    onPhotos = { vm.showType(TypeFilter.IMAGE); onOpenFolders() },
                    onAll = onOpenFolders,
                    compact = landscape,
                )
            }
            if (savedFilters.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedFilters.size) { i ->
                            val (sf, count) = savedFilters[i]
                            val c = com.cripta.app.ui.theme.TagPalette[i % com.cripta.app.ui.theme.TagPalette.size]
                            Surface(color = c.copy(alpha = 0.18f), shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.extraLarge)
                                    .clickable(role = Role.Button, onClickLabel = "Applica filtro") { vm.applySavedFilter(sf.json); onOpenFolders() }) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.FilterList, null, tint = c, modifier = Modifier.size(18.dp))
                                    Text(sf.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                                    Surface(color = c, shape = CircleShape, modifier = Modifier.padding(start = 8.dp)) {
                                        Text("$count", color = Color.White, style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (continueWatching.isNotEmpty()) {
                item { ShelfHeader("Continua a guardare", Icons.Filled.History, Color(0xFFEC4899), continueWatching.size) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueWatching, key = { it.file.id }) { fwt ->
                            ContinueCard(fwt, display, coverVersions[fwt.file.id] ?: 0, vm::thumb) { open(continueWatching, fwt.file.id) }
                        }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                if (!landscape) {
                    // Carousel of the latest additions (a 16:9 hero would fill a landscape screen).
                    item { HeroCarousel(recents.take(5), vm::thumb, coverVersions) { id -> open(recents, id) } }
                }
                item {
                    ShelfHeader("Aggiunti di recente", Icons.Filled.NewReleases, Color(0xFF3B82F6), summary.total,
                        onSeeAll = onOpenFolders)
                }
                item { CoverShelf(if (landscape) recents else recents.drop(minOf(5, recents.size)).ifEmpty { recents }, display, coverVersions, vm::thumb) { id -> open(recents, id) } }
            }
            if (favorites.isNotEmpty()) {
                item { ShelfHeader("Preferiti", Icons.Filled.Star, com.cripta.app.ui.theme.Favorite, favoriteCount, onSeeAll = onOpenFavorites) }
                item { CoverShelf(favorites, display, coverVersions, vm::thumb) { id -> open(favorites, id) } }
            }
            if (folders.isNotEmpty()) {
                item { ShelfHeader("Cartelle", Icons.Filled.Folder, Color(0xFF10B981), folders.size, onSeeAll = onOpenFolders) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            val source = remember { MutableInteractionSource() }
                            FolderMosaic(
                                folder = folder,
                                stat = folderStats[folder.id],
                                previews = folderPreviews[folder.id].orEmpty(),
                                thumb = vm::thumb,
                                modifier = Modifier.width(128.dp).pressScale(source).clip(MaterialTheme.shapes.medium)
                                    .clickable(source, LocalIndication.current, role = Role.Button,
                                        onClickLabel = "Apri cartella") { vm.openFolder(folder.id); onOpenFolders() },
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

private enum class HomePhase { LOADING, EMPTY, CONTENT }

/** Stats-style header: videos, photos and space, each tappable. */
@Composable
private fun SummaryCard(videos: Int, photos: Int, bytes: Long, onVideos: () -> Unit, onPhotos: () -> Unit, onAll: () -> Unit, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile(Icons.Filled.Movie, Color(0xFF3B82F6), "$videos", if (videos == 1) "video" else "video", Modifier.weight(1f), onVideos, compact)
        SummaryTile(Icons.Filled.Image, Color(0xFF22C55E), "$photos", if (photos == 1) "foto" else "foto", Modifier.weight(1f), onPhotos, compact)
        SummaryTile(Icons.Filled.SdStorage, Color(0xFF8B5CF6), formatBytes(bytes), "occupati", Modifier.weight(1f), onAll, compact)
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier, onClick: () -> Unit, compact: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    Surface(color = tint.copy(alpha = 0.12f), shape = MaterialTheme.shapes.large,
        modifier = modifier.pressScale(source).clip(MaterialTheme.shapes.large)
            .clickable(source, LocalIndication.current, role = Role.Button, onClick = onClick)) {
        if (compact) {
            // Landscape: icon beside the numbers, half the height of the stacked tile.
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = tint, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
                Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp))
            }
            return@Surface
        }
        Column(Modifier.padding(12.dp)) {
            Surface(color = tint, shape = CircleShape, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Section title with a coloured icon, a count and "Vedi tutti". */
@Composable
private fun ShelfHeader(title: String, icon: ImageVector, tint: Color, count: Int, onSeeAll: (() -> Unit)? = null) {
    // Whole row is the "Vedi tutti" button (48dp tall); the title is a heading for TalkBack.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onSeeAll != null) Modifier.clickable(role = Role.Button, onClickLabel = "Vedi tutti", onClick = onSeeAll) else Modifier)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = tint.copy(alpha = 0.16f), shape = CircleShape, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 10.dp).semantics { heading() })
        Text("$count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (onSeeAll != null) {
            Text("Vedi tutti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
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
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.file.id }) { fwt ->
            val bmp by produceState<android.graphics.Bitmap?>(null, fwt.file.id, coverVersions[fwt.file.id] ?: 0) { value = thumb(fwt.file) }
            val source = remember { MutableInteractionSource() }
            Column(Modifier.width(124.dp).pressScale(source).clip(MaterialTheme.shapes.medium)
                .clickable(source, LocalIndication.current, onClickLabel = "Apri") { onOpen(fwt.file.id) }) {
                CoverThumb(fwt.file, fwt.tags, display, bmp, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(fwt.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
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
    Column(Modifier.width(210.dp).pressScale(source).clip(MaterialTheme.shapes.medium)
        .clickable(source, LocalIndication.current, onClickLabel = "Riprendi", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
            CoverThumb(f, emptyList(), display.copy(resumePlayback = true), bmp, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Riprendi", tint = Color.White)
            }
        }
        Text(f.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        val left = (f.durationMs ?: 0L) - (f.playbackPosMs ?: 0L)
        formatDuration(left.takeIf { it > 0 })?.let {
            Text("Mancano $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/** The latest additions as a swipeable 16:9 carousel, title/duration/tags over a gradient. */
@Composable
private fun HeroCarousel(
    items: List<FileWithTags>,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    coverVersions: Map<String, Int>,
    onOpen: (String) -> Unit,
) {
    val pager = rememberPagerState { items.size }
    Column {
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { page ->
            val fwt = items[page]
            val f = fwt.file
            val bmp by produceState<android.graphics.Bitmap?>(null, f.id, coverVersions[f.id] ?: 0) { value = thumb(f) }
            val source = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).pressScale(source, pressed = 0.98f).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(source, LocalIndication.current, onClickLabel = "Apri") { onOpen(f.id) }) {
                // The title is already read from the overlay text below.
                bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(96.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(f.originalName, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOfNotNull(
                            formatDuration(f.durationMs),
                            if (VaultRepository.isVideo(f.mimeType)) com.cripta.app.ui.vault.qualityLabel(f.width, f.height) else null,
                            formatBytes(f.sizeBytes),
                        ).joinToString(" · ").let {
                            Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        }
                        fwt.tags.take(3).forEach { t ->
                            Surface(color = com.cripta.app.ui.theme.tagColor(t), shape = MaterialTheme.shapes.extraSmall) {
                                Text(t.alias?.takeIf { it.isNotBlank() } ?: t.name, color = Color.White,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
        if (items.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(items.size) { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
    }
}

/** Empty vault: an accent circle and quick actions instead of a bare message. */
@Composable
private fun EmptyHome(modifier: Modifier, onImport: () -> Unit, onDownload: () -> Unit) {
    // Landscape: smaller badge and padding + scroll, so both buttons stay reachable.
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(
        if (landscape) modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp) else modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(if (landscape) 56.dp else 96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (landscape) 28.dp else 44.dp))
            }
        }
        Text("Vault vuoto", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() })
        Text("Importa foto e video o scaricali da un link: restano cifrati e visibili solo qui.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Icons.Filled.EnhancedEncryption, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Importa file")
        }
        OutlinedButton(onClick = onDownload, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Scarica da un link")
        }
    }
}
