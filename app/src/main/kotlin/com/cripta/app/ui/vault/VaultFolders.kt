package com.cripta.app.ui.vault

import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.cripta.app.ui.components.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.FolderStat
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.theme.Motion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

internal fun folderSubtitle(stat: FolderStat?): String {
    if (stat == null || stat.count == 0) return str(R.string.empty_f)
    val n = stat.count
    val items = plural(R.plurals.items_n, n)
    return "$items · ${formatBytes(stat.bytes)}"
}

/** Clickable path "Radice › A › B": tap a level to jump straight back to it. */
@Composable
internal fun Breadcrumb(names: List<String>, onGo: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(names.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val all = listOf(str(R.string.root)) + names
        all.forEachIndexed { i, n ->
            val last = i == all.lastIndex
            Text(
                n,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.minimumInteractiveComponentSize().clip(MaterialTheme.shapes.small)
                    .then(if (last) Modifier else Modifier.clickable(onClickLabel = str(R.string.go_to, n), role = Role.Button) { onGo(i) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            )
            if (!last) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/**
 * The vault's cover (tags on up to 3 rows, length badge, watched progress, favorite),
 * for other screens such as Home, so every thumbnail in the app looks and reads the same.
 */
@Composable
fun CoverThumb(
    file: com.cripta.app.data.db.FileEntity,
    tags: List<TagEntity>,
    display: DisplayPrefs,
    bmp: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
) {
    val isVideo = VaultRepository.isVideo(file.mimeType)
    val icon = when {
        VaultRepository.isImage(file.mimeType) -> CriptaIcons.Image
        isVideo -> CriptaIcons.Movie
        else -> CriptaIcons.InsertDriveFile
    }
    // Decorative: every caller shows the file name as text next to the cover.
    ThumbBox(bmp, icon, isVideo, null, selected = false, favorite = file.isFavorite,
        tags = if (display.showTagsOnCover) tags else emptyList(), modifier = modifier, file = file, display = display)
}

/**
 * Live sample cover for the settings screen, drawn with the real cover renderer so every cover
 * option (tag rows, style, colours, duration/quality badges) can be judged before leaving.
 */
@Composable
fun CoverPreview(display: DisplayPrefs, modifier: Modifier = Modifier, tags: List<TagEntity> = emptyList()) {
    val fallback = remember {
        listOf("Mare" to "🌊", "Famiglia" to "FA", "Estate" to "ES", "Viaggi" to "✈️", "Amici" to "AM",
            "Festa" to "🎉", "Montagna" to "MO", "2024" to "24", "Preferiti" to "PR", "Cane" to "🐶")
            .mapIndexed { i, (n, a) -> TagEntity(id = -1L - i, name = n, alias = a) }
    }
    // The user's own tags (acronyms, icons, colours) when there are some: the sample only stands in.
    val sample = remember(tags) { tags.take(10).ifEmpty { fallback } }
    val file = remember {
        com.cripta.app.data.db.FileEntity(
            id = "preview", originalName = "Esempio.mp4", mimeType = "video/mp4", sizeBytes = 48_000_000,
            createdAt = 0, importedAt = 0, wrappedKeyset = ByteArray(0),
            durationMs = 192_000, width = 1920, height = 1080,
        )
    }
    ThumbBox(null, CriptaIcons.Movie, isVideo = true, name = str(R.string.preview), selected = false, favorite = true,
        tags = if (display.showTagsOnCover) sample else emptyList(), modifier = modifier, file = file, display = display)
}

/**
 * Folder card drawn as a stack: its cover in front (the chosen one, else the newest), the next two
 * pictures peeking out above it, like an album. Nothing is drawn over the pictures: the folder's
 * glyph (its colour and emoji), its star and its count sit with the name underneath, which keeps
 * the whole width (the actions are on long-press).
 */
@Composable
fun FolderMosaic(
    folder: FolderEntity,
    stat: FolderStat?,
    previews: List<com.cripta.app.data.db.FileEntity>,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
    /** Item count under the name (the size is in the folder's menu). */
    showInfo: Boolean = true,
    /** Drop target under a drag-selection: the card lifts, tints and gets a primary border. */
    highlighted: Boolean = false,
    /** A star action on the Favorites screen; regular cards keep their actions menu. */
    onRemoveFavorite: (() -> Unit)? = null,
    /** Bumped (incremented) when dropped files land on the card: it gives a short squeeze. */
    bump: Int = 0,
    /** While dropped files land, the count shown is at least this (it ticks up per file). */
    countAtLeast: Int? = null,
    /** The count in the accent colour (files just landed). */
    countFlash: Boolean = false,
    /** Per-file cover version: a cover regenerated while the card is on screen is redrawn. */
    coverVersions: Map<String, Int> = emptyMap(),
) {
    /**
     * A picture of the stack, null until loaded. Keyed by the file and its cover version, and only
     * shown for the file it was loaded for: on a change of file the old picture is not left showing.
     */
    @Composable
    fun picture(f: com.cripta.app.data.db.FileEntity): Pair<Boolean, android.graphics.Bitmap?> {
        val loaded by produceState<Pair<String, android.graphics.Bitmap?>?>(null, f.id, coverVersions[f.id] ?: 0) { value = f.id to thumb(f) }
        val mine = loaded?.takeIf { it.first == f.id }
        return (mine != null) to mine?.second
    }
    // The tones of the folder's colour (the accent's quiet tones without one): the container
    // grounds the empty cover and veils the pictures behind, the mark is its dot.
    val tones = com.cripta.app.ui.theme.rememberTagTones(folder.color?.let { Color(it) }, colored = true)
    val lift by animateFloatAsState(if (highlighted) 1.08f else 1f, Motion.enter(Motion.MEDIUM), label = "folderLift")
    val hl by animateFloatAsState(if (highlighted) 1f else 0f, Motion.enter(Motion.SHORT), label = "folderHl")
    // Landing squeeze: 1 -> 0.93 -> 1, ease-out both ways (no overshoot).
    val squeeze = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(bump) {
        if (bump == 0) return@LaunchedEffect
        squeeze.animateTo(0.93f, Motion.enter(Motion.SHORT))
        squeeze.animateTo(1f, Motion.enter(Motion.MEDIUM))
    }
    val primary = MaterialTheme.colorScheme.primary
    val shape = com.cripta.app.ui.theme.CoverShape
    val tile = MaterialTheme.colorScheme.surfaceContainer
    // The two sheets behind are the folder's container tone, the farther a step softer so the stack
    // keeps its depth; their pictures show through it, veiled by the container (60% far, 45% near).
    val container = tones.container
    val farSheet = remember(container, tile) { container.copy(alpha = 0.65f).compositeOver(tile) }
    val nearSheet = remember(container, tile) { container.copy(alpha = 0.85f).compositeOver(tile) }
    @Composable
    fun sheetPicture(f: com.cripta.app.data.db.FileEntity?, veil: Float) {
        if (f == null) return
        val bmp = picture(f).second
        bmp?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(container.copy(alpha = veil)))
        }
    }
    // Scale outermost, so the caller's clip/click area scales with the card.
    // DS FolderStack: the cover in front (4:3), the next two peeking above it, dimmed.
    Column(Modifier.graphicsLayer { scaleX = lift * squeeze.value; scaleY = lift * squeeze.value }.then(modifier)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Box(Modifier.offset(y = (-12).dp).padding(horizontal = 24.dp).fillMaxWidth().height(40.dp).clip(shape)
                .background(farSheet)) {
                sheetPicture(previews.getOrNull(2), 0.60f)
            }
            Box(Modifier.offset(y = (-6).dp).padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).clip(shape).background(nearSheet)) {
                sheetPicture(previews.getOrNull(1), 0.45f)
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape)
                    .background(container)
                    .then(if (hl > 0f) Modifier.border(3.dp, primary.copy(alpha = hl), shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val f = previews.firstOrNull()
                if (f == null) {
                    // An empty folder: its container with the emoji (or the folder symbol) alone, 48dp.
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.icon, folder.emoji, 48.dp, open = highlighted)
                } else {
                    val (done, bmp) = picture(f)
                    if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    // No picture to show (none made, or unreadable): the file's type, as the cover picker shows it.
                    else if (done) com.cripta.app.ui.components.CoverPlaceholder(com.cripta.app.ui.components.typeIconFor(f.mimeType), 32.dp)
                }
                if (hl > 0f) Box(Modifier.matchParentSize().background(primary.copy(alpha = 0.18f * hl)))
                // Over a cover, the open folder says "drop here" (the glyph of an empty card opens itself).
                if (hl > 0f && f != null) {
                    Box(Modifier.size(44.dp).graphicsLayer { alpha = hl }.clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.cripta.app.ui.components.OnImageGlass), contentAlignment = Alignment.Center) {
                        Icon(CriptaIcons.FolderOpen, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
        // DS: the folder's emoji alone (20dp, no disc), else a 10dp dot in its mark tone; the name in
        // `title`, the star after it when favourite, the count as a caption under.
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (folder.icon.isNullOrBlank() && folder.emoji.isNullOrBlank()) {
                Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tones.mark))
            } else {
                com.cripta.app.ui.components.FolderGlyph(folder.color, folder.icon, folder.emoji, 20.dp, open = highlighted)
            }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // DS: the name takes the row, the star sits at its end.
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    if (folder.isFavorite && onRemoveFavorite == null) {
                        Icon(CriptaIcons.Star, str(R.string.folder_favorite), tint = com.cripta.app.ui.theme.Favorite,
                            modifier = Modifier.padding(start = 4.dp).size(18.dp))
                    }
                }
            }
            if (onRemoveFavorite != null) {
                IconButton(onClick = onRemoveFavorite, modifier = Modifier.minimumInteractiveComponentSize().size(40.dp)) {
                    Icon(CriptaIcons.Star, str(R.string.unfavorite_folder), tint = com.cripta.app.ui.theme.Favorite,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
        if (showInfo) {
            val n = maxOf(stat?.count ?: 0, countAtLeast ?: 0)
            val countColor by androidx.compose.animation.animateColorAsState(
                if (countFlash) primary else MaterialTheme.colorScheme.onSurfaceVariant, Motion.enter(Motion.SHORT), label = "folderCount")
            Text(if (n == 0) str(R.string.empty_f) else plural(R.plurals.files_n, n, com.cripta.app.ui.components.formatCount(n)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = countColor, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * Pick the picture in front of a folder's stack: "Automatica" (the newest file) or any of the
 * folder's own files. The current choice carries the selection ring and tick.
 */
@Composable
internal fun FolderCoverSheet(
    folder: FolderEntity,
    /** The newest files up to a limit, and whether there are more ("Altro" raises the limit). */
    load: suspend (Int) -> com.cripta.app.data.VaultRepository.CoverChoices,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var limit by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(COVER_PAGE) }
    // The previous page stays while the next one loads (no flash of the skeleton).
    var choices by remember { androidx.compose.runtime.mutableStateOf<com.cripta.app.data.VaultRepository.CoverChoices?>(null) }
    LaunchedEffect(folder.id, limit) { choices = load(limit) }
    val files = choices?.files
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(str(R.string.cover_of, folder.name), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(str(R.string.cover_tap_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            // A cover no longer valid (moved, deleted) is not in the list: then "Automatica" is what shows.
            val coverInUse = folder.coverFileId?.takeIf { id -> files?.any { it.id == id } != false }
            com.cripta.app.ui.components.ChoiceChip(
                selected = coverInUse == null,
                onClick = { afterSheet { onPick(null) } },
                label = { Text(str(R.string.cover_auto_latest)) },
            )
            val list = files
            // The picker lists the newest files first: said, with a way to list older ones too.
            if (choices?.more == true) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.cover_picker_limit, limit), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                com.cripta.app.ui.components.TextButton(onClick = { limit += COVER_PAGE }) { com.cripta.app.ui.components.ButtonText(str(R.string.more)) }
            }
            when {
                list == null -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp), userScrollEnabled = false,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(12) {
                        com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), com.cripta.app.ui.theme.CoverShape)
                    }
                }
                list.isEmpty() -> Text(str(R.string.folder_no_files), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.size, key = { list[it].id }) { i ->
                        val f = list[i]
                        val chosen = f.id == coverInUse
                        com.cripta.app.ui.components.MediaThumb(
                            f, thumb,
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(com.cripta.app.ui.theme.CoverShape)
                                .clickable(onClickLabel = str(R.string.use_as_cover)) { afterSheet { onPick(f.id) } }
                                .semantics { contentDescription = f.originalName; selected = chosen },
                            selected = chosen,
                            showFavoriteBadge = false,
                        )
                    }
                }
            }
        }
    }
}

/** Files the cover picker lists at a time. */
private const val COVER_PAGE = 500
