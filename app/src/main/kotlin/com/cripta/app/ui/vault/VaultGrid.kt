package com.cripta.app.ui.vault

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.drawscope.withTransform
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

/** A tag's colour: its own, or the blue preset for every tag when tag colours are off (pills then take the accent's tones). */
private fun tagHue(tag: TagEntity, display: DisplayPrefs?): Color =
    if (display?.tagColors != false) com.cripta.app.ui.theme.tagColor(tag) else com.cripta.app.ui.theme.TagOffPastel

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
            // Over the whole cover, so the tray's frosting lines up with the picture under it.
            TagBadges(
                tagsIn = tags,
                display = display,
                modifier = Modifier.matchParentSize(),
                backdrop = bmp,
                bottomLift = if (shownWatched != null) 4.dp else 0.dp,
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
 * Tags over the bottom of a cover, in the style picked in Impostazioni › Etichette › Sulle copertine:
 * - [CoverTagStyle.BUBBLE] (default): round marks of one size, the tag's emoji alone or its initial
 *   on its pastel, so a row of them reads as a tidy strip and hides little of the picture;
 * - [CoverTagStyle.PILL]: pills with the tag's alias (and icon) on its pastel.
 * Every mark sits on one dark tray ([CoverTray]): the cover's own picture under it, frosted, then
 * [TrayInk]; it follows the rows as one stepped block, nothing empty beside a shorter row.
 * Up to 3 rows (automatic: by cover width, or fixed by the user), filled from the bottom; a white
 * "+N" ends the top row only once every row is full. "Dimensione" scales marks, gaps and tray
 * together ([coverTagScale]), so smaller marks fit more to a row. A tag assigned while the cover is
 * on screen pops in.
 *
 * [backdrop] is the cover's picture when [modifier] makes this fill the whole cover (the frosting
 * lines up with it); [bottomLift] raises the marks over the cover's progress bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagBadges(
    tagsIn: List<TagEntity>,
    display: DisplayPrefs?,
    modifier: Modifier,
    outerPad: androidx.compose.ui.unit.Dp = 8.dp,
    backdrop: android.graphics.Bitmap? = null,
    bottomLift: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // In the library's order (pinned first): when room runs out, the tags put first are the ones kept.
    val order = LocalTagOrder.current
    // Tags kept off the covers (Modifica etichetta › "Sulle copertine") are left out here.
    val tags = remember(tagsIn, order) { libraryOrder(tagsIn.filter { it.onCovers }, order) }
    if (tags.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bubbles = (display?.coverTagStyle ?: com.cripta.app.data.CoverTagStyle.BUBBLE) == com.cripta.app.data.CoverTagStyle.BUBBLE
    val scale = coverTagScale(display?.coverTagSize ?: 1)
    val base = MaterialTheme.typography.labelSmall
    val style = remember(base, scale) { scaledLabel(base, scale) }
    // Tags the cover had when it appeared: only ones added after that animate in.
    val knownTags = remember { tags.mapTo(mutableSetOf()) { it.id } }

    BoxWithConstraints(modifier) {
        val m = remember(maxWidth, scale, density, style, bubbles) {
            CoverMarkMetrics.of(density, measurer, style, scale, small = maxWidth < 100.dp, bubbles = bubbles)
        }
        // DS cr-cover__bl: 8dp from the edges.
        val outerPx = with(density) { outerPad.roundToPx() }
        val availPx = (constraints.maxWidth - 2 * outerPx).coerceAtLeast(0).toFloat()
        val rowsWanted = when (val r = display?.coverTagRows ?: 0) {
            0 -> when {
                maxWidth >= 150.dp -> 3
                maxWidth >= 100.dp -> 2
                else -> 1
            }
            else -> r
        }
        val texts = remember(tags, bubbles) { tags.map { if (bubbles) bubbleText(it) else badgeText(it, display) } }
        val widths = remember(texts, tags, m, availPx) { texts.mapIndexed { i, t -> minOf(m.markWidth(t, tags[i].icon != null), availPx) } }
        val layout = remember(widths, availPx, rowsWanted, m) {
            packCoverRows(widths, availPx, m.gap.toFloat(), rowsWanted) { n -> m.overflowWidth(n) }
        }
        val rowWidths = remember(layout, m) { layout.rowWidths(m.gap.toFloat()) }
        val n = layout.rows.size
        val contentH = n * m.rowHeight + (n - 1).coerceAtLeast(0) * m.gap
        val tray = remember(rowWidths, m) {
            trayComposePath(trayPath(rowWidths, m.rowHeight.toFloat(), m.gap.toFloat(), m.pad.toFloat(), m.radius.toFloat()))
        }
        val trayW = kotlin.math.ceil((rowWidths.maxOrNull() ?: 0f) + 2 * m.pad).toInt()
        val trayH = contentH + 2 * m.pad
        val liftPx = with(density) { bottomLift.roundToPx() }
        // Where the tray sits on the cover, for the frosting to line up with the picture under it.
        val cover = if (backdrop != null && constraints.hasBoundedHeight)
            androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight) else null
        // Right to left the marks start at the cover's right edge, and the tray with them.
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        val at = androidx.compose.ui.unit.IntOffset(
            if (rtl && cover != null) cover.width - outerPx + m.pad - trayW else outerPx - m.pad,
            (cover?.height ?: 0) - liftPx - outerPx - contentH - m.pad)
        val gapDp = with(density) { m.gap.toDp() }

        Box(Modifier.align(Alignment.BottomStart).padding(start = outerPad, bottom = outerPad + bottomLift)) {
            CoverTray(tray, trayW, trayH, m.pad, if (cover != null) backdrop else null, at, cover)
            Column(verticalArrangement = Arrangement.spacedBy(gapDp)) {
                // Filled from the bottom: the full rows sit on the cover's edge and the shorter
                // last row (plus the "+N") goes on top, so it hides less of the image.
                layout.rows.indices.reversed().forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gapDp), verticalAlignment = Alignment.CenterVertically) {
                        layout.rows[r].forEachIndexed { k, i ->
                            androidx.compose.runtime.key(tags[i].id) {
                                val fresh = remember { tags[i].id !in knownTags }
                                androidx.compose.runtime.SideEffect { knownTags += tags[i].id }
                                val mod = Modifier.popIn(fresh)
                                // Its packed width, as for pills: beside "+N" the top row's bubbles may be narrowed.
                                if (bubbles) CoverTagBubble(tags[i], texts[i], tagHue(tags[i], display), with(density) { layout.widths[r][k].toDp() }, mod)
                                else CoverTagChip(tags[i], texts[i], tagHue(tags[i], display), m, with(density) { layout.widths[r][k].toDp() }, mod)
                            }
                        }
                        if (r == layout.rows.lastIndex && layout.extra > 0) {
                            Box(Modifier.size(with(density) { layout.overflowWidth.toDp() }, with(density) { m.rowHeight.toDp() }),
                                contentAlignment = Alignment.Center) {
                                Text("+${layout.extra}", style = style, color = Color.White, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sizes of the cover marks in whole pixels (as the layout rounds them), for one cover width and
 * "Dimensione" [scale]: 20dp emoji in a 22dp slot (18dp on covers under 100dp), 4dp apart, on a
 * tray of 3dp padding and 14dp radius; pills 22dp tall with 8dp sides.
 */
internal class CoverMarkMetrics private constructor(
    val slot: Int,
    val rowHeight: Int,
    val gap: Int,
    val pad: Int,
    val radius: Int,
    val pillSide: Int,
    val icon: Int,
    val iconGap: Int,
    val bubbles: Boolean,
    /** Text of pills and of "+N": labelSmall, scaled. */
    val style: androidx.compose.ui.text.TextStyle,
    private val measure: (String) -> Int,
) {
    /** On-screen width of a mark: a bubble is its slot; a pill its text plus icon and sides. */
    fun markWidth(text: String, hasIcon: Boolean): Float =
        if (bubbles) slot.toFloat()
        else {
            val t = if (text.isEmpty()) 0 else measure(text)
            val i = if (!hasIcon) 0 else icon + if (text.isEmpty()) 0 else iconGap
            (t + i + 2 * pillSide).toFloat()
        }

    /** Width of the white "+[n]" that ends a full top row: its text with a little room, at least a slot. */
    fun overflowWidth(n: Int): Float = maxOf(measure("+$n") + 2 * iconGap, rowHeight).toFloat()

    companion object {
        fun of(
            density: androidx.compose.ui.unit.Density,
            measurer: androidx.compose.ui.text.TextMeasurer,
            style: androidx.compose.ui.text.TextStyle,
            scale: Float,
            small: Boolean,
            bubbles: Boolean,
        ): CoverMarkMetrics = with(density) {
            val measure = { s: String -> measurer.measure(s, style, maxLines = 1, softWrap = false).size.width }
            val slot = ((if (small) 18.dp else 22.dp) * scale).roundToPx()
            val pillH = maxOf((22.dp * scale).roundToPx(),
                measurer.measure("Ag", style, maxLines = 1).size.height + (4.dp * scale).roundToPx())
            CoverMarkMetrics(
                slot = slot,
                rowHeight = if (bubbles) slot else pillH,
                gap = (4.dp * scale).roundToPx(),
                pad = (3.dp * scale).roundToPx(),
                radius = (14.dp * scale).roundToPx(),
                pillSide = if (bubbles) 0 else (8.dp * scale).roundToPx(),
                icon = (14.dp * scale).roundToPx(),
                iconGap = (4.dp * scale).roundToPx(),
                bubbles = bubbles,
                style = style,
                measure = measure,
            )
        }
    }
}

/** [base] (labelSmall) at [scale]: the text of pills and "+N" for the "Dimensione" setting. */
private fun scaledLabel(base: androidx.compose.ui.text.TextStyle, scale: Float): androidx.compose.ui.text.TextStyle =
    base.copy(
        fontSize = if (base.fontSize.isSpecified) base.fontSize * scale else base.fontSize,
        lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else base.lineHeight,
    )

/** DS `cover-tray`: the dark of the tray behind the cover marks (#0A0C10 at 30%). */
internal val TrayInk = Color(0x4D0A0C10)

/** Frosting of the tray: the picture under it, blurred this much. */
private val TrayFrost = 8.dp

/** [ops] as a Compose path. */
internal fun trayComposePath(ops: List<TrayPathOp>): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        ops.forEach { op ->
            when (op) {
                is TrayPathOp.MoveTo -> moveTo(op.x, op.y)
                is TrayPathOp.LineTo -> lineTo(op.x, op.y)
                is TrayPathOp.CubicTo -> cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                TrayPathOp.Close -> close()
            }
        }
    }

/**
 * The dark tray behind a cover's marks, [w]×[h] px, drawn [pad] up and out from the marks' start
 * corner and taking no room itself (right to left: from the right, its outline mirrored). Inside [path]: the cover's [backdrop] (sized to [cover], the tray at
 * [at] on it) blurred by [TrayFrost], then [TrayInk]; without a picture, the ink alone. Masked
 * with the path (not clipped), so its curved edges are smooth.
 */
@Composable
private fun CoverTray(
    path: androidx.compose.ui.graphics.Path,
    w: Int,
    h: Int,
    pad: Int,
    backdrop: android.graphics.Bitmap?,
    at: androidx.compose.ui.unit.IntOffset,
    cover: androidx.compose.ui.unit.IntSize?,
) {
    Box(
        Modifier
            .layout { measurable, _ ->
                val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                // Relative: right to left the tray reaches back from the marks' right edge.
                layout(0, 0) { p.placeRelative(-pad, -pad) }
            }
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // The outline is drawn for marks aligned left: right to left they align right.
                val flip = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                withTransform({ if (flip) scale(-1f, 1f, pivot = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f)) }) {
                    drawPath(path, TrayInk)
                    drawPath(path, Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                }
            },
    ) {
        if (backdrop != null && cover != null) {
            val image = remember(backdrop) { backdrop.asImageBitmap() }
            Box(Modifier.fillMaxSize().blur(TrayFrost, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle)) {
                // The same picture as the cover (same crop and centre), shifted so it lines up.
                Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.layout { measurable, _ ->
                    val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(cover.width, cover.height))
                    layout(w, h) { p.place(-at.x, -at.y) }
                })
            }
        }
    }
}

/** "Bollini" or "Pillole" and their "Dimensione" (Impostazioni › Etichette), for marks drawn away from a cover (the tag editor). */
object CoverTagStyleNow {
    var value by mutableStateOf(com.cripta.app.data.CoverTagStyle.BUBBLE)
    var size by mutableStateOf(1)
}

/**
 * The mark [tag] gets on a cover, in the style and size the covers use, on its own small tray (the
 * tray of a one-mark row): the tag editor's preview. Its picture is a plain tile, so the tray has
 * no frosting there, only its dark.
 */
@Composable
internal fun CoverMarkSample(tag: TagEntity, color: Color) {
    val scale = coverTagScale(CoverTagStyleNow.size)
    val bubbles = CoverTagStyleNow.value == com.cripta.app.data.CoverTagStyle.BUBBLE
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.labelSmall
    val style = remember(base, scale) { scaledLabel(base, scale) }
    val m = remember(density, style, scale, bubbles) { CoverMarkMetrics.of(density, measurer, style, scale, small = false, bubbles = bubbles) }
    val pad = with(density) { m.pad.toDp() }
    Box(Modifier.background(TrayInk, androidx.compose.foundation.shape.RoundedCornerShape(with(density) { m.radius.toDp() })).padding(pad)) {
        if (bubbles) CoverTagBubble(tag, bubbleText(tag), color, with(density) { m.slot.toDp() }, Modifier)
        else {
            val text = badgeText(tag, null)
            val w = minOf(m.markWidth(text, tag.icon != null), with(density) { 160.dp.toPx() })
            CoverTagChip(tag, text, color, m, with(density) { w.toDp() }, Modifier)
        }
    }
}

/** Position of each tag id in the library (Impostazioni › Etichette); empty = not known here. */
internal val LocalTagOrder = androidx.compose.runtime.compositionLocalOf<Map<Long, Int>> { emptyMap() }

/** [tags] pinned first, then in the library's order ([order]: id to position). */
internal fun libraryOrder(tags: List<TagEntity>, order: Map<Long, Int>): List<TagEntity> =
    tags.sortedWith(compareBy<TagEntity>({ !it.pinned }, { order[it.id] ?: Int.MAX_VALUE }))

/** A bubble's content when the tag has no icon: its alias (two characters at most), else its initial. */
private fun bubbleText(tag: TagEntity): String =
    tag.alias?.takeIf { it.isNotBlank() }?.let { takeGraphemes(it.trim(), 2) }
        ?: takeGraphemes(tag.name.trim(), 1).uppercase(com.cripta.app.Strings.locale)

/** A cover mark's fill: the tag's pastel ([com.cripta.app.ui.theme.tagPastel]), opaque: covers are the one place it is drawn. */
internal fun coverMarkFill(color: Color): Color = com.cripta.app.ui.theme.tagPastel(color.copy(alpha = 1f))

/** Text on a cover mark: black or white, whichever reads at 4.5:1 on [fill] (black on every pastel). */
internal fun coverMarkInk(fill: Color): Color = if (fill.luminance() >= 0.175f) Color.Black else Color.White

/** True when [text] is an emoji (or a few), not letters or digits: an acronym like "🌊" is drawn as an emoji. */
internal fun isEmojiText(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    var symbol = false
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        if (Character.isLetterOrDigit(cp)) return false
        if (Character.getType(cp) == Character.OTHER_SYMBOL.toInt() || cp >= 0x1F000) symbol = true
        i += Character.charCount(cp)
    }
    return symbol
}

/** DS `cover-mark-rim`: a thin dark rim (rgba(10,12,16,0.45)) that holds a pastel mark on any picture. */
private val CoverMarkRim = Color(0x730A0C10)
/** DS cover mark shadow (0 1px 3px rgba(0,0,0,0.35)). */
private val CoverMarkShadow = Color(0x59000000)

/** The ground of a pastel cover mark in [shape]: a soft shadow, the [fill], a thin dark rim. */
private fun Modifier.coverMark(fill: Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.shadow(2.dp, shape, clip = false, ambientColor = CoverMarkShadow, spotColor = CoverMarkShadow)
        .clip(shape).background(fill).border(1.5.dp, CoverMarkRim, shape)

/**
 * A tag's emoji alone on a cover, in colour, no disc, rim or shadow behind it: the tray under the
 * marks ([TagBadges]) is what holds it on any picture.
 */
@Composable
private fun CoverEmoji(vector: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Icon(vector, null, tint = Color.Unspecified, modifier = Modifier.size(size))
}

/**
 * One cover mark of a tag, [size] square. With an emoji (its icon, or an acronym that is an emoji)
 * the emoji alone, 20/22 of the slot ([CoverEmoji]): never on a disc. Otherwise a round mark in the
 * tag's pastel ([coverMarkFill], rim and shadow), its acronym or initial in [coverMarkInk].
 */
@Composable
private fun CoverTagBubble(tag: TagEntity, text: String, color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val icon = com.cripta.app.ui.tagicons.rememberResolvedTagIcon(tag.icon)
    val drawn = com.cripta.app.ui.tagicons.rememberTagIcon(icon)
    val label = text.ifEmpty { tagAlias(tag) }
    val emojiAlias = icon == null && isEmojiText(label)
    if (icon != null || emojiAlias) {
        val emoji = size * (20f / 22f)
        Box(modifier.size(size).semantics { contentDescription = tag.name }, contentAlignment = Alignment.Center) {
            when {
                drawn != null -> CoverEmoji(drawn, emoji)
                // Its room is kept while the icon loads, so nothing shifts.
                icon != null -> Unit
                else -> {
                    val sp = with(LocalDensity.current) { (emoji * 0.82f).toSp() }
                    Text(label, maxLines = 1, softWrap = false,
                        style = androidx.compose.ui.text.TextStyle(fontSize = sp, lineHeight = sp))
                }
            }
        }
        return
    }
    val bg = coverMarkFill(color)
    val fg = coverMarkInk(bg)
    Box(
        modifier.size(size).coverMark(bg, CircleShape).semantics { contentDescription = tag.name },
        contentAlignment = Alignment.Center,
    ) {
        // 11sp in the 22dp slot (half of it, as in the DS), scaled with the slot.
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = (11f * size.value / 22f).sp,
            lineHeight = (14f * size.value / 22f).sp), color = fg, maxLines = 1, softWrap = false)
    }
}

/**
 * A pill in the tag's pastel ([coverMarkFill], rim and shadow), its emoji and acronym in
 * [coverMarkInk], exactly [width] wide and [m]'s row tall so the tray behind it fits.
 */
@Composable
private fun CoverTagChip(tag: TagEntity, text: String, color: Color, m: CoverMarkMetrics, width: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val bg = coverMarkFill(color)
    val fg = coverMarkInk(bg)
    val icon = com.cripta.app.ui.tagicons.rememberResolvedTagIcon(tag.icon)
    val drawn = com.cripta.app.ui.tagicons.rememberTagIcon(icon)
    val shownText = if (icon == null) text.ifEmpty { tagAlias(tag) } else text
    val density = LocalDensity.current
    Row(
        modifier.width(width).height(with(density) { m.rowHeight.toDp() }).coverMark(bg, CircleShape)
            .padding(horizontal = with(density) { m.pillSide.toDp() }).semantics { contentDescription = tag.name },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Box(Modifier.size(with(density) { m.icon.toDp() })) {
            if (drawn != null) Icon(drawn, null, tint = Color.Unspecified, modifier = Modifier.matchParentSize())
        }
        if (icon != null && text.isNotEmpty()) Spacer(Modifier.width(with(density) { m.iconGap.toDp() }))
        if (shownText.isNotEmpty()) Text(shownText, style = m.style, color = fg, maxLines = 1,
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
    /**
     * The vault screen's grid state: it keeps each folder's scroll position and starts a new folder
     * at the top, which a state of this grid's own would not see (the grid stays on screen).
     */
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    /** A file's TalkBack actions of the normal grid (favourite, tags, rename, move, delete). */
    fileActions: (FileWithTags) -> List<CustomAccessibilityAction> = { emptyList() },
) {
    // The per-item tap handler is keyed on the file id only: read the latest callback, or a tap
    // would keep doing what it did when the cell first appeared (e.g. open while selecting).
    val currentOnOpen by rememberUpdatedState(onOpen)
    val list = remember { mutableStateListOf<FileWithTags>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) { list.clear(); list.addAll(items) } }
    // The vault as it is now, for the end of a drag: an update that came meanwhile was held back.
    val currentItems by rememberUpdatedState(items)

    var draggedId by remember { mutableStateOf<String?>(null) }
    /** The end of a drag: a drop shows and saves the order, any other end the stored one ([endDrag]). */
    fun finishDrag(dropped: Boolean) {
        dragging = false; draggedId = null
        val end = endDrag(dropped, list.toList(), currentItems) { it.file.id }
        if (end.shown != list) { list.clear(); list.addAll(end.shown) }
        end.saved?.let(onReorder)
    }
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

    // Auto-scroll while the item is held near the top or bottom edge: the zones, the wait and the
    // ramp of the vault's move drag (see edgeDepth, edgeSpeed, edgeRamp), by the time between
    // frames, so a 120 Hz screen scrolls as fast as a 60 Hz one.
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        val zone = with(density) { EDGE_ZONE.toPx() }
        val maxSpeed = with(density) { EDGE_MAX_SPEED.toPx() }
        var last = androidx.compose.runtime.withFrameNanos { it }
        var start = -1L // when the finger entered the scrolling part of a zone; -1 = it is not there
        while (draggedId != null) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val h = gridState.layoutInfo.viewportSize.height.toFloat()
            val speed = edgeSpeed(edgeDepth(pointer.y, h, zone), maxSpeed)
            if (speed == 0f) start = -1L else if (start < 0L) start = now
            val dy = if (speed == 0f) 0f else speed * edgeRamp((now - start) / 1_000_000) * (now - last) / 1_000_000_000f
            last = now
            if (dy != 0f) {
                gridState.scrollBy(dy)
                retarget()
            }
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
                    selectionMode = inSelection,
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
                            // The same actions as in the other orders, plus the steps of the reorder.
                            this.customActions = fileActions(fwt) + if (!reorderEnabled) emptyList() else listOf(
                                CustomAccessibilityAction(str(R.string.move_earlier)) { moveBy(fwt.file.id, -1); true },
                                CustomAccessibilityAction(str(R.string.move_later)) { moveBy(fwt.file.id, 1); true },
                            )
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
                                    // Files added, removed or renamed during the drag join the order dropped.
                                    onDragEnd = { finishDrag(dropped = true) },
                                    // Cancelled by the system before the release: nothing is saved, and the
                                    // grid shows the stored order again (left moved, a later drop saved it too).
                                    onDragCancel = { finishDrag(dropped = false) },
                                )
                            } finally {
                                // Handler removed mid-drag (e.g. a filter turned reordering off): unfreeze
                                // the list without persisting a half-done order.
                                if (draggedId == fwt.file.id) finishDrag(dropped = false)
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
    /** Selection mode: unselected covers show the empty ring, as in the other orders. */
    selectionMode: Boolean,
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
                    file = item.file, display = display, compact = true, selectionMode = selectionMode)
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
                    Modifier.fillMaxWidth().aspectRatio(1f), file = item.file, display = display, selectionMode = selectionMode)
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
