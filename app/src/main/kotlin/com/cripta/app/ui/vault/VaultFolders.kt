package com.cripta.app.ui.vault

import androidx.compose.material3.minimumInteractiveComponentSize
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun CoverPreview(display: DisplayPrefs, modifier: Modifier = Modifier) {
    val sample = remember {
        listOf("Mare" to "🌊", "Famiglia" to "FA", "Estate" to "ES", "Viaggi" to "✈️", "Amici" to "AM",
            "Festa" to "🎉", "Montagna" to "MO", "2024" to "24", "Preferiti" to "PR", "Cane" to "🐶")
            .mapIndexed { i, (n, a) -> TagEntity(id = -1L - i, name = n, alias = a) }
    }
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
) {
    val accent = folder.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val lift by animateFloatAsState(if (highlighted) 1.05f else 1f, Motion.enter(Motion.SHORT), label = "folderLift")
    val hl by animateFloatAsState(if (highlighted) 1f else 0f, Motion.enter(Motion.SHORT), label = "folderHl")
    val primary = MaterialTheme.colorScheme.primary
    val shape = com.cripta.app.ui.theme.CoverShape
    val tile = MaterialTheme.colorScheme.surfaceContainer
    // Sheets without a picture of their own (folders with 1-2 files) take the folder's colour.
    val farSheet = remember(accent, tile) { accent.copy(alpha = 0.30f).compositeOver(tile) }
    val nearSheet = remember(accent, tile) { accent.copy(alpha = 0.55f).compositeOver(tile) }
    @Composable
    fun sheetPicture(f: com.cripta.app.data.db.FileEntity?, dim: Float) {
        if (f == null) return
        val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, alpha = dim, modifier = Modifier.fillMaxSize()) }
    }
    // Scale outermost, so the caller's clip/click area scales with the card.
    // DS FolderStack: the cover in front (4:3), the next two peeking above it, dimmed.
    Column(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }.then(modifier)) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Box(Modifier.offset(y = (-12).dp).padding(horizontal = 24.dp).fillMaxWidth().height(40.dp).clip(shape)
                .background(farSheet)) {
                sheetPicture(previews.getOrNull(2), 0.3f)
            }
            Box(Modifier.offset(y = (-6).dp).padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).clip(shape).background(nearSheet)) {
                sheetPicture(previews.getOrNull(1), 0.45f)
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape)
                    .background(accent.copy(alpha = 0.16f).compositeOver(tile))
                    .then(if (hl > 0f) Modifier.border(3.dp, primary.copy(alpha = hl), shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val f = previews.firstOrNull()
                if (f == null) {
                    com.cripta.app.ui.components.FolderGlyph(folder.color, folder.icon, folder.emoji, 44.dp)
                } else {
                    val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                if (hl > 0f) Box(Modifier.matchParentSize().background(primary.copy(alpha = 0.18f * hl)))
            }
        }
        // DS: the folder's colour as a small square (or its own icon), the name in `title`, the star
        // after it when favourite, the count as a caption under.
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (folder.icon.isNullOrBlank() && folder.emoji.isNullOrBlank()) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            } else {
                com.cripta.app.ui.components.FolderGlyph(folder.color, folder.icon, folder.emoji, 20.dp)
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
            val n = stat?.count ?: 0
            Text(if (n == 0) str(R.string.empty_f) else plural(R.plurals.files_n, n, com.cripta.app.ui.components.formatCount(n)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
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
    load: suspend () -> List<com.cripta.app.data.db.FileEntity>,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val files by produceState<List<com.cripta.app.data.db.FileEntity>?>(null, folder.id) { value = load() }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        val afterSheet = com.cripta.app.ui.components.rememberSheetAction()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(str(R.string.cover_of, folder.name), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(str(R.string.cover_tap_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            com.cripta.app.ui.components.ChoiceChip(
                selected = folder.coverFileId == null,
                onClick = { afterSheet { onPick(null) } },
                label = { Text(str(R.string.cover_auto_latest)) },
            )
            val list = files
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
                        val chosen = f.id == folder.coverFileId
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
