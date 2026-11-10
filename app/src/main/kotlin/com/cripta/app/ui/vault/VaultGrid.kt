package com.cripta.app.ui.vault

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import com.cripta.app.ui.theme.popIn
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import com.cripta.app.data.DisplayPrefs
import com.cripta.app.data.ViewMode
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.TagEntity
import com.cripta.app.ui.components.fileMeta
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.CornerSelectionCheck
import com.cripta.app.ui.components.SelectableThumbFrame
import com.cripta.app.ui.components.ThumbCrossfade
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun FileCell(
    item: FileWithTags,
    viewMode: ViewMode,
    selected: Boolean,
    selectionMode: Boolean,
    display: DisplayPrefs,
    modifier: Modifier,
    coverOverride: android.graphics.Bitmap?,
    coverVersion: Int,
    thumb: suspend () -> android.graphics.Bitmap?,
) {
    val mime = item.file.mimeType
    val isVideo = VaultRepository.isVideo(mime)
    val fallbackIcon = when {
        VaultRepository.isImage(mime) -> CriptaIcons.Image
        isVideo -> CriptaIcons.Movie
        else -> CriptaIcons.InsertDriveFile
    }
    val loaded by produceState<android.graphics.Bitmap?>(initialValue = null, item.file.id, coverVersion) { value = thumb() }
    // A freshly regenerated cover (override) wins over the cached/loaded one so it shows at once.
    val bmp = coverOverride ?: loaded

    // Tap/long-press are handled by the grid container (unified gesture), not per cell.
    if (viewMode == ViewMode.LIST) {
        // A plain row on the page: no card around it, the cover and the text carry it.
        Row(modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // List mode: no tag badges on the cover — every tag is shown as a pill beside it.
            ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
                emptyList(), Modifier.size(76.dp), file = item.file, display = display, compact = true,
                selectionMode = selectionMode)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge)
                if (display.showFileInfo) {
                    Text(listMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
                if (item.tags.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.forEach { TagChip(it, display) }
                    }
                }
            }
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
            if (display.showTagsOnCover) item.tags else emptyList(),
            Modifier.fillMaxWidth().aspectRatio(1f), file = item.file, display = display, selectionMode = selectionMode)
        Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
        if (display.showFileInfo) {
            // DS cr-cover-item__meta: caption, muted, 2dp under the name.
            Text(fileMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp))
        }
    }
}

/** The grid / list while the first level is read: covers and name lines of the same size, same gaps. */
@Composable
internal fun VaultSkeleton(columns: Int, viewMode: ViewMode, showInfo: Boolean, modifier: Modifier = Modifier) {
    val list = viewMode == ViewMode.LIST
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns), modifier, userScrollEnabled = false,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (list) 4.dp else 14.dp),
    ) {
        items(if (list) 10 else columns * 6) {
            if (list) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.cripta.app.ui.components.Skeleton(Modifier.size(76.dp), com.cripta.app.ui.theme.CoverShape)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) { com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth(0.6f).height(14.dp)) }
                    if (showInfo) Box(Modifier.padding(top = 2.dp).height(16.dp), contentAlignment = Alignment.CenterStart) { com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth(0.4f).height(10.dp)) }
                }
            } else Column {
                com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), com.cripta.app.ui.theme.CoverShape)
                Box(Modifier.padding(top = 8.dp, start = 2.dp).height(20.dp), contentAlignment = Alignment.CenterStart) { com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth(0.7f).height(12.dp)) }
                if (showInfo) Box(Modifier.padding(start = 2.dp, top = 2.dp).height(16.dp), contentAlignment = Alignment.CenterStart) { com.cripta.app.ui.components.Skeleton(Modifier.fillMaxWidth(0.45f).height(10.dp)) }
            }
        }
    }
}

/** Size · date (the length is on the cover itself in list mode, unless the badge is off). */
private fun listMeta(f: com.cripta.app.data.db.FileEntity, withDuration: Boolean): String {
    val parts = mutableListOf<String>()
    if (withDuration) com.cripta.app.ui.components.formatDuration(f.durationMs)?.let { parts += it }
    parts += formatBytes(f.sizeBytes)
    parts += java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale).format(java.util.Date(f.importedAt))
    return parts.joinToString(" · ")
}

/**
 * Text a tag shows on a cover badge, per the user's style choice. Short style with an icon and no
 * alias: the icon alone (it replaces the two letters).
 */
// On a cover a tag shows only its symbol or its acronym, never the full name.
internal fun badgeText(tag: TagEntity, @Suppress("UNUSED_PARAMETER") display: DisplayPrefs?): String = when {
    tag.icon != null && tag.alias.isNullOrBlank() -> ""
    else -> tagAlias(tag)
}

/** A tag's colour on screen: its own, or the app accent for every tag when tag colours are off. */
@Composable
private fun tagHue(tag: TagEntity, display: DisplayPrefs?): Color =
    if (display?.tagColors != false) com.cripta.app.ui.theme.tagColor(tag) else MaterialTheme.colorScheme.primary

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThumbBox(
    bmp: android.graphics.Bitmap?,
    fallbackIcon: ImageVector,
    isVideo: Boolean,
    /** Image description; null inside cells, where the file name is already shown as text. */
    name: String?,
    selected: Boolean,
    favorite: Boolean,
    tags: List<TagEntity>,
    modifier: Modifier,
    file: com.cripta.app.data.db.FileEntity? = null,
    display: DisplayPrefs? = null,
    /** Small list-mode thumbnail: only the duration, bottom-right. */
    compact: Boolean = false,
    /** Selection mode: unselected covers show the empty ring where the check lands. */
    selectionMode: Boolean = false,
) {
    val duration = if (isVideo && display?.showDurationBadge != false)
        com.cripta.app.ui.components.formatDuration(file?.durationMs) else null
    SelectableThumbFrame(
        selected = selected,
        shape = if (compact) com.cripta.app.ui.theme.CoverShapeSmall else com.cripta.app.ui.theme.CoverShape,
        modifier = modifier,
        selectedScale = if (compact) 0.9f else 0.92f,
        overlay = { CornerSelectionCheck(selected, selectionMode, size = if (compact) 20.dp else 24.dp) },
    ) {
        // Without a cover: the type's icon on a neutral tile (the shape tells the type; colour stays for state).
        com.cripta.app.ui.components.CoverPlaceholderBackground()
        ThumbCrossfade(bmp, contentDescription = name, modifier = Modifier.fillMaxSize()) {
            com.cripta.app.ui.components.CoverPlaceholder(fallbackIcon, if (compact) 26.dp else 32.dp)
        }
        // Play marker only when no length badge already says "this is a video".
        if (isVideo && duration == null) {
            com.cripta.app.ui.components.CoverPlayMark(Modifier.align(Alignment.Center), if (compact) 28.dp else 34.dp)
        }
        // "Resume" progress: how much of the video has already been watched.
        val watched = if (isVideo && display?.resumePlayback != false) file?.let { f ->
            val p = f.playbackPosMs; val d = f.durationMs
            if (p != null && d != null && d > 0) (p.toFloat() / d).coerceIn(0f, 1f) else null
        } else null
        val shownWatched = watched?.takeIf { it > 0.01f }?.let { w ->
            // A new position (back from the player with the cover on screen) slides to its width.
            androidx.compose.animation.core.animateFloatAsState(w, com.cripta.app.ui.theme.Motion.enter(com.cripta.app.ui.theme.Motion.LONG), label = "watched").value
        }
        if (tags.isNotEmpty()) {
            TagBadges(
                tags = tags,
                display = display,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(bottom = if (shownWatched != null) 4.dp else 0.dp),
            )
        }
        if (shownWatched != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(com.cripta.app.ui.theme.Glass)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(shownWatched).background(MaterialTheme.colorScheme.primary))
            }
        }
        val people = if (!compact && display?.coverPeople != false && file != null) LocalCoverPeople.current else null
        val faces = people?.byFile?.get(file?.id).orEmpty()
        if (favorite || faces.isNotEmpty()) {
            Row(Modifier.align(Alignment.TopStart).padding(if (compact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.cripta.app.ui.components.AnimatedCoverStar(favorite, size = if (compact) 20.dp else 28.dp)
                if (people != null && faces.isNotEmpty()) CoverFaces(faces, display?.coverPeopleSize ?: 1, people.blurred, people.crop)
            }
        }
        if (compact) {
            if (duration != null) {
                com.cripta.app.ui.components.CoverBadge(duration, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        } else if (!selectionMode && !selected && duration != null) {
            // Top-right (the selection check's corner in selection mode), so the bottom stays free for tags.
            com.cripta.app.ui.components.CoverBadge(duration, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** Up to three faces of the people in a media, each on the dark glass, then "+N". */
@Composable
private fun CoverFaces(ids: List<Long>, sizeStep: Int, blurred: Boolean, crop: suspend (Long) -> android.graphics.Bitmap?) {
    val size = when (sizeStep) { 0 -> 20.dp; 2 -> 34.dp; else -> 26.dp }
    ids.take(3).forEach { id ->
        val bmp by produceState<android.graphics.Bitmap?>(null, id) { value = crop(id) }
        Box(Modifier.size(size).clip(CircleShape).background(com.cripta.app.ui.components.OnImageGlass).padding(1.5.dp)
            .clip(CircleShape).then(if (blurred) Modifier.blur(size / 4, androidx.compose.ui.draw.BlurredEdgeTreatment(CircleShape)) else Modifier)) {
            bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
    if (ids.size > 3) com.cripta.app.ui.components.CoverBadge("+${ids.size - 3}", Modifier)
}

/** A tag on the list row: the shared pill, small. */
@Composable
private fun TagChip(tag: TagEntity, display: DisplayPrefs) {
    com.cripta.app.ui.components.TagPill(
        com.cripta.app.ui.components.tagText(tag), tagHue(tag, display), tagIcon = tag.icon,
        size = com.cripta.app.ui.components.TagPillSize.Small,
        base = MaterialTheme.colorScheme.background,
    )
}

/**
 * Tags over the bottom of a cover, in the style picked in Impostazioni › Sulle copertine:
 * - [CoverTagStyle.BUBBLE] (default): round 22dp marks of one size, the tag's icon or its initial,
 *   so a row of them reads as a tidy strip and hides little of the picture;
 * - [CoverTagStyle.PILL]: pills in the tag's full colour with its alias (and icon), no see-through.
 * Up to 3 rows (automatic: by cover width, or fixed by the user), filled from the bottom; a "+N"
 * mark appears only once every row is full. A tag assigned while the cover is on screen pops in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBadges(tags: List<TagEntity>, display: DisplayPrefs?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val bubbles = (display?.coverTagStyle ?: com.cripta.app.data.CoverTagStyle.BUBBLE) == com.cripta.app.data.CoverTagStyle.BUBBLE
    // Tags the cover had when it appeared: only ones added after that animate in.
    val knownTags = remember { tags.mapTo(mutableSetOf()) { it.id } }

    BoxWithConstraints(modifier) {
        // DS cr-cover__bl: 8dp from the edges, 4dp between marks.
        val outerPad = 8.dp
        val availDp = maxWidth - outerPad * 2
        val availPx = with(density) { availDp.toPx() }
        val spacingPx = with(density) { 4.dp.toPx() }
        val bubbleDp = if (maxWidth < 100.dp) 18.dp else 22.dp
        val rowsWanted = when (val r = display?.coverTagRows ?: 0) {
            0 -> when {
                maxWidth >= 150.dp -> 3
                maxWidth >= 100.dp -> 2
                else -> 1
            }
            else -> r
        }
        val texts = remember(tags, bubbles) { tags.map { if (bubbles) bubbleText(it) else badgeText(it, display) } }
        // On-screen width of each mark, capped to a full row: a bubble is a fixed circle; a pill is
        // its measured text plus the icon and 8dp of padding on each side.
        val hPadPx = with(density) { 16.dp.toPx() }
        val iconPx = with(density) { 12.dp.toPx() }
        val iconGapPx = with(density) { 4.dp.toPx() }
        val bubblePx = with(density) { bubbleDp.toPx() }
        val widths = remember(texts, availPx, tags, bubbles, bubblePx) {
            texts.mapIndexed { i, t ->
                if (bubbles) bubblePx
                else {
                    val text = if (t.isEmpty()) 0f else measurer.measure(t, style).size.width.toFloat()
                    val icon = if (tags[i].icon == null) 0f else iconPx + if (t.isEmpty()) 0f else iconGapPx
                    minOf(text + icon + hPadPx, availPx)
                }
            }
        }

        // Greedy row packing: returns the tag indices of each row.
        fun pack(reserveLastPx: Float): List<List<Int>> {
            val rows = mutableListOf<MutableList<Int>>()
            var i = 0
            while (i < widths.size && rows.size < rowsWanted) {
                val row = mutableListOf<Int>()
                val last = rows.size == rowsWanted - 1
                val budget = if (last) availPx - reserveLastPx else availPx
                var used = 0f
                while (i < widths.size) {
                    val add = widths[i] + if (row.isNotEmpty()) spacingPx else 0f
                    if (used + add <= budget || row.isEmpty() && budget >= widths[i]) { row += i; used += add; i++ } else break
                }
                if (row.isEmpty()) break
                rows += row
            }
            return rows
        }

        var rows = pack(0f)
        var shown = rows.sumOf { it.size }
        if (shown < tags.size) {
            val overflowPx = maxOf(measurer.measure("+${tags.size}", style).size.width + hPadPx, if (bubbles) bubblePx else 0f) + spacingPx
            rows = pack(overflowPx)
            shown = rows.sumOf { it.size }
        }
        val extra = tags.size - shown

        Column(
            Modifier.padding(outerPad),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Filled from the bottom: the full rows sit on the cover's edge and the shorter
            // last row (plus the "+N" mark) goes on top, so it hides less of the image.
            rows.asReversed().forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { i ->
                        androidx.compose.runtime.key(tags[i].id) {
                            val fresh = remember { tags[i].id !in knownTags }
                            androidx.compose.runtime.SideEffect { knownTags += tags[i].id }
                            val mod = Modifier.popIn(fresh)
                            if (bubbles) CoverTagBubble(tags[i], texts[i], tagHue(tags[i], display), bubbleDp, mod)
                            else CoverTagChip(tags[i], texts[i], tagHue(tags[i], display), availDp, mod)
                        }
                    }
                    if (r == 0 && extra > 0) {
                        if (bubbles) Box(
                            Modifier.heightIn(min = bubbleDp).widthIn(min = bubbleDp).clip(CircleShape)
                                .background(com.cripta.app.ui.components.OnImageGlass).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("+$extra", style = style, color = Color.White, maxLines = 1) }
                        else com.cripta.app.ui.components.MoreTagsPill(extra, onImage = true)
                    }
                }
            }
        }
    }
}

/** A bubble's content when the tag has no icon: its alias (two characters at most), else its initial. */
private fun bubbleText(tag: TagEntity): String =
    tag.alias?.takeIf { it.isNotBlank() }?.take(2) ?: tag.name.take(1).uppercase(com.cripta.app.Strings.locale)

/** Ink on a tag's full colour: black, or white on a darker colour of the user's own (as TagPill's On). */
private fun inkOn(bg: Color): Color = if (bg.luminance() >= 0.175f) Color.Black else Color.White

/** D: one round mark, the tag's colour at 90% on the dark glass, its icon or initial in the middle. */
@Composable
private fun CoverTagBubble(tag: TagEntity, text: String, color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val bg = color.copy(alpha = 0.9f).compositeOver(com.cripta.app.ui.components.OnImageGlass)
    val fg = inkOn(bg)
    val drawn = com.cripta.app.ui.tagicons.rememberTagIcon(tag.icon)
    Box(
        modifier.size(size).clip(CircleShape).background(bg).semantics { contentDescription = tag.name },
        contentAlignment = Alignment.Center,
    ) {
        when {
            drawn != null -> Icon(drawn, null, tint = fg, modifier = Modifier.size(size * 0.58f))
            // Its room is kept while the icon loads, so nothing shifts.
            tag.icon != null -> Unit
            else -> Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (size < 22.dp) 9.sp else 11.sp),
                color = fg, maxLines = 1)
        }
    }
}

/** C: a pill in the tag's full colour (no see-through, no rim), its icon and alias in black or white. */
@Composable
private fun CoverTagChip(tag: TagEntity, text: String, color: Color, maxWidth: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val fg = inkOn(color)
    val drawn = com.cripta.app.ui.tagicons.rememberTagIcon(tag.icon)
    Row(
        modifier.widthIn(max = maxWidth).heightIn(min = 22.dp).clip(CircleShape).background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp).semantics { contentDescription = tag.name },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tag.icon != null) Box(Modifier.size(12.dp)) { if (drawn != null) Icon(drawn, null, tint = fg, modifier = Modifier.matchParentSize()) }
        if (tag.icon != null && text.isNotEmpty()) Spacer(Modifier.width(4.dp))
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Drag-to-reorder used by the Manual sort in BOTH grid and list. Reorder starts only after a
 * long press (so a stray tap never changes the order), then the item lifts, follows the finger and
 * the target slot is found by hit-testing the grid's layout info (works for any cell size / column
 * count). Holding it near the top/bottom edge scrolls the grid. [header] (the folders row) stays
 * above the items. TalkBack users get "Sposta prima / dopo" actions instead of the drag.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReorderableFileGrid(
    items: List<FileWithTags>,
    columns: Int,
    asList: Boolean,
    selection: Set<String>,
    display: DisplayPrefs,
    coverOverrides: Map<String, android.graphics.Bitmap>,
    coverVersions: Map<String, Int>,
    thumb: suspend (com.cripta.app.data.db.FileEntity) -> android.graphics.Bitmap?,
    onOpen: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    header: (@Composable () -> Unit)? = null,
    /** Selection mode: [onOpen] toggles instead of opening (only changes the spoken label here). */
    inSelection: Boolean = false,
    /** False while filtering/searching: items are shown but can't be dragged. */
    reorderEnabled: Boolean = true,
) {
    // The per-item tap handler is keyed on the file id only: read the latest callback, or a tap
    // would keep doing what it did when the cell first appeared (e.g. open while selecting).
    val currentOnOpen by rememberUpdatedState(onOpen)
    val list = remember { mutableStateListOf<FileWithTags>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) { list.clear(); list.addAll(items) } }

    val gridState = rememberLazyGridState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }    // finger position in the grid viewport
    var pressLocal by remember { mutableStateOf(Offset.Zero) } // grab point inside the cell
    val density = LocalDensity.current
    val itemShape = MaterialTheme.shapes.medium

    /** Move the dragged item to the slot under the finger (file cells only, never the header). */
    fun retarget() {
        val id = draggedId ?: return
        val from = list.indexOfFirst { it.file.id == id }
        val hitKey = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != FOLDERS_KEY &&
                pointer.x >= info.offset.x && pointer.x <= info.offset.x + info.size.width &&
                pointer.y >= info.offset.y && pointer.y <= info.offset.y + info.size.height
        }?.key as? String ?: return
        val target = list.indexOfFirst { it.file.id == hitKey }
        if (from >= 0 && target >= 0 && target != from) list.add(target, list.removeAt(from))
    }

    /** Accessibility reorder: one step earlier (-1) or later (+1). */
    fun moveBy(id: String, delta: Int) {
        val from = list.indexOfFirst { it.file.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        onReorder(list.map { it.file.id })
    }

    // Auto-scroll while the item is held near the top or bottom edge (faster the closer it is).
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (draggedId != null) {
            val h = gridState.layoutInfo.viewportSize.height.toFloat()
            val y = pointer.y
            val step = when {
                h <= 0f -> 0f
                y < edge -> -maxStep * ((edge - y) / edge).coerceIn(0f, 1f)
                y > h - edge -> maxStep * ((y - (h - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                gridState.scrollBy(step)
                retarget()
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(if (reorderEnabled) str(R.string.manual_order_hint)
            else str(R.string.manual_order_blocked),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (header != null) {
                item(key = FOLDERS_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            itemsIndexed(list, key = { _, it -> it.file.id }) { _, fwt ->
                val isDragged = fwt.file.id == draggedId
                val loaded by produceState<android.graphics.Bitmap?>(initialValue = null, fwt.file.id, coverVersions[fwt.file.id] ?: 0) { value = thumb(fwt.file) }
                val bmp = coverOverrides[fwt.file.id] ?: loaded
                // The held item lifts: slightly larger, with a shadow.
                val lift by animateFloatAsState(if (isDragged) 1.05f else 1f, Motion.enter(Motion.SHORT), label = "lift")
                ReorderCell(
                    item = fwt,
                    bmp = bmp,
                    selected = fwt.file.id in selection,
                    dragged = isDragged,
                    asList = asList,
                    display = display,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .then(if (!isDragged) Modifier.animateItem() else Modifier)
                        .graphicsLayer {
                            scaleX = lift; scaleY = lift
                            if (isDragged) {
                                shadowElevation = 8.dp.toPx()
                                shape = itemShape
                                clip = false
                                val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId }
                                if (info != null) {
                                    translationX = pointer.x - pressLocal.x - info.offset.x
                                    translationY = pointer.y - pressLocal.y - info.offset.y
                                }
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            val sel = fwt.file.id in selection
                            if (inSelection) this.selected = sel
                            onClick(label = if (inSelection) (if (sel) str(R.string.deselect) else str(R.string.select)) else str(R.string.open)) {
                                currentOnOpen(fwt.file.id); true
                            }
                            if (reorderEnabled) {
                                this.customActions = listOf(
                                    CustomAccessibilityAction(str(R.string.move_earlier)) { moveBy(fwt.file.id, -1); true },
                                    CustomAccessibilityAction(str(R.string.move_later)) { moveBy(fwt.file.id, 1); true },
                                )
                            }
                        }
                        .pointerInput(fwt.file.id) { detectTapGestures(onTap = { currentOnOpen(fwt.file.id) }) }
                        .then(if (!reorderEnabled) Modifier else Modifier.pointerInput(fwt.file.id) {
                            try {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        draggedId = fwt.file.id
                                        dragging = true
                                        pressLocal = local
                                        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == fwt.file.id }
                                        pointer = Offset((info?.offset?.x ?: 0) + local.x, (info?.offset?.y ?: 0) + local.y)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        pointer += amount
                                        retarget()
                                    },
                                    onDragEnd = { dragging = false; draggedId = null; onReorder(list.map { it.file.id }) },
                                    onDragCancel = { dragging = false; draggedId = null; onReorder(list.map { it.file.id }) },
                                )
                            } finally {
                                // Handler removed mid-drag (e.g. a filter turned reordering off): unfreeze
                                // the list without persisting a half-done order.
                                if (draggedId == fwt.file.id) { dragging = false; draggedId = null }
                            }
                        }),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReorderCell(
    item: FileWithTags,
    bmp: android.graphics.Bitmap?,
    selected: Boolean,
    dragged: Boolean,
    asList: Boolean,
    display: DisplayPrefs,
    modifier: Modifier,
) {
    val mime = item.file.mimeType
    val isVideo = VaultRepository.isVideo(mime)
    val fallbackIcon = when {
        VaultRepository.isImage(mime) -> CriptaIcons.Image
        isVideo -> CriptaIcons.Movie
        else -> CriptaIcons.InsertDriveFile
    }
    if (asList) {
        // A plain row like the regular list; only the row being dragged lifts onto a tonal slab.
        Surface(
            color = if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 0.dp,
            // Min height, not fixed: large fonts grow the row instead of clipping it.
            modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite, emptyList(), Modifier.size(56.dp),
                    file = item.file, display = display, compact = true)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge)
                    if (display.showFileInfo) {
                        Text(fileMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Icon(CriptaIcons.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).size(22.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            Box {
                ThumbBox(bmp, fallbackIcon, isVideo, null, selected, item.file.isFavorite,
                    if (display.showTagsOnCover) item.tags else emptyList(),
                    Modifier.fillMaxWidth().aspectRatio(1f), file = item.file, display = display)
                if (dragged) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(com.cripta.app.ui.components.OnImageGlass), contentAlignment = Alignment.Center) {
                        Icon(CriptaIcons.DragHandle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(item.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            if (display.showFileInfo) {
                Text(fileMeta(item.file, withDuration = !(isVideo && display.showDurationBadge)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, top = 1.dp))
            }
        }
    }
}
