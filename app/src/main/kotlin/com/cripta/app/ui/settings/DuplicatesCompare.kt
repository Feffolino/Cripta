package com.cripta.app.ui.settings

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import com.cripta.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cripta.app.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.formatDuration

/** A pending destructive choice, confirmed before anything is shredded. */
private sealed interface DupAction {
    data class KeepOnly(val group: SettingsViewModel.DupGroup, val keep: SettingsViewModel.DupCandidate) : DupAction
    data class DeleteOne(val group: SettingsViewModel.DupGroup, val target: SettingsViewModel.DupCandidate) : DupAction
    data class KeepSome(val group: SettingsViewModel.DupGroup, val keepIds: Set<String>) : DupAction
}

/**
 * Full-screen, side-by-side comparison of duplicate groups. Every copy shows its cover, resolution,
 * size, duration, date and tags; the suggested copy to keep is highlighted with the reasons. Keeping
 * one copy deletes the others and moves their tags onto it, so deleting a tagged copy while keeping
 * an untagged one never loses the tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesCompare(
    title: String,
    groups: List<SettingsViewModel.DupGroup>,
    summary: String,
    emptyText: String,
    notice: String?,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    channelFor: (FileEntity) -> java.nio.channels.SeekableByteChannel,
    imageBytes: suspend (FileEntity) -> ByteArray,
    onKeepOnly: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearNotice: () -> Unit,
    onDismiss: () -> Unit,
    /** Days a removed copy stays in the trash, or null when the trash is off (removal is final). */
    trashDays: Int? = null,
    /** True while the last resolution can be undone (its copies are in the trash). */
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
) {
    var pending by remember { mutableStateOf<DupAction?>(null) }
    /** Group + index of the copy being previewed full screen. */
    var preview by remember { mutableStateOf<Pair<SettingsViewModel.DupGroup, Int>?>(null) }
    // The notice stays longer while it offers "Annulla", so there is time to reach it.
    LaunchedEffect(notice, canUndo) {
        if (notice != null) { kotlinx.coroutines.delay(if (canUndo) 8000L else 4000L); onClearNotice() }
    }
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets,
                    colors = com.cripta.app.ui.components.criptaTopBarColors(),
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(CriptaIcons.Close, str(R.string.close)) } },
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(summary, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item(key = "notice") {
                    // Outcome of the last resolution, with "Annulla" while the copies sit in the trash.
                    var shown by remember { mutableStateOf("") }
                    if (notice != null) shown = notice   // keep the text while the panel collapses
                    AnimatedVisibility(
                        visible = notice != null,
                        enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                        exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(shown, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                                if (canUndo) TextButton(onClick = onUndo) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
                            }
                        }
                    }
                }
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        Text(emptyText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 24.dp))
                    }
                }
                items(groups, key = { g -> g.candidates.joinToString("|") { it.file.id } }) { g ->
                    GroupCard(
                        // Resolved groups fade out and the rest slide up instead of jumping.
                        modifier = Modifier.animateItem(
                            fadeInSpec = Motion.enter(), placementSpec = Motion.enter(Motion.LONG), fadeOutSpec = Motion.exit(),
                        ),
                        group = g,
                        thumb = thumb,
                        onKeep = { c -> pending = DupAction.KeepOnly(g, c) },
                        onDelete = { c -> pending = DupAction.DeleteOne(g, c) },
                        onPreview = { c -> preview = g to g.candidates.indexOf(c) },
                    )
                }
            }
        }

        preview?.let { (g, index) ->
            PreviewDialog(
                group = g,
                startIndex = index,
                channelFor = channelFor,
                imageBytes = imageBytes,
                onKeep = { c -> preview = null; pending = DupAction.KeepOnly(g, c) },
                onDismiss = { preview = null },
            )
        }

        pending?.let { action -> ConfirmDialog(action, trashDays, onConfirm = {
            when (action) {
                is DupAction.KeepOnly -> onKeepOnly(action.keep.file.id)
                is DupAction.DeleteOne -> onDelete(action.target.file.id)
                is DupAction.KeepSome -> Unit
            }
            pending = null
        }, onDismiss = { pending = null }) }
    }
}

/**
 * "Media simili" one group at a time, full screen, swiped up like "Da sistemare" of the faces. The
 * copy in view takes the free space (tap: the viewer, full screen); the group's copies in a row
 * under it, each kept or deleted (tap switches it and puts it in view, long press only shows it);
 * the tags the kept copy will have; "Perché è consigliata"; then "Tieni N, elimina M" across the
 * width and "Non sono simili" (remembered: never grouped again). With the trash on nothing is
 * confirmed, "Annulla" stays at the bottom; once every group is done the page says what was freed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarPager(
    title: String,
    groups: List<SettingsViewModel.DupGroup>,
    emptyText: String,
    notice: String?,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    imageBytes: suspend (FileEntity) -> ByteArray,
    onKeep: (Set<String>) -> Unit,
    onNotSimilar: (List<String>) -> Unit,
    onOpen: (List<String>, String) -> Unit,
    onClearNotice: () -> Unit,
    onDismiss: () -> Unit,
    /** Space freed and groups resolved so far, and groups marked "non simili". */
    freed: DupFreed = DupFreed(),
    apart: Int = 0,
    initialPage: Int = 0,
    onPage: (Int) -> Unit = {},
    trashDays: Int? = null,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
) {
    var pending by remember { mutableStateOf<DupAction?>(null) }
    var why by remember { mutableStateOf<SettingsViewModel.DupGroup?>(null) }
    var compare by remember { mutableStateOf<Pair<SettingsViewModel.DupCandidate, SettingsViewModel.DupCandidate>?>(null) }
    LaunchedEffect(notice, canUndo) {
        if (notice != null) { kotlinx.coroutines.delay(if (canUndo) 8000L else 4000L); onClearNotice() }
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage.coerceIn(0, maxOf(0, groups.size - 1))) { groups.size }
    LaunchedEffect(pager) { androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { onPage(it) } }
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text(title) },
                // DS Simili: the position as a muted caption at the bar's end.
                actions = {
                    if (groups.isNotEmpty()) Text(str(R.string.x_of_y, (pager.currentPage + 1).coerceAtMost(groups.size), groups.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp))
                },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } })
        }, bottomBar = {
            // Outcome of the last answer, with "Annulla" while the copies sit in the trash.
            var shown by remember { mutableStateOf("") }
            if (notice != null) shown = notice   // keep the text while it fades out
            AnimatedVisibility(notice != null, enter = fadeIn(Motion.enter()), exit = fadeOut(Motion.exit())) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                    Text(shown, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (canUndo) TextButton(onClick = onUndo) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) }
                }
            }
        }) { pad ->
            if (groups.isEmpty()) {
                DoneOrEmpty(freed, apart, emptyText, onDismiss, Modifier.fillMaxSize().padding(pad))
                return@Scaffold
            }
            androidx.compose.foundation.pager.VerticalPager(state = pager, modifier = Modifier.fillMaxSize().padding(pad),
                key = { groups.getOrNull(it)?.candidates?.joinToString("|") { c -> c.file.id } ?: it }, beyondViewportPageCount = 1) { page ->
                val g = groups.getOrNull(page) ?: return@VerticalPager
                SimilarPage(g, thumb,
                    onOpen = { c -> onOpen(g.candidates.map { it.file.id }, c.file.id) },
                    onKeep = { keep -> if (trashDays != null) onKeep(keep) else pending = DupAction.KeepSome(g, keep) },
                    onNotSimilar = { onNotSimilar(g.candidates.map { it.file.id }) },
                    onWhy = { why = g })
            }
        }

        why?.let { g ->
            WhyRecommended(g, thumb, onDismiss = { why = null },
                onCompare = { a, b -> compare = a to b },
                onPlayBoth = { a, b -> onOpen(listOf(a.file.id, b.file.id), a.file.id) })
        }
        compare?.let { (a, b) -> SlideCompare(a, b, thumb, imageBytes) { compare = null } }

        pending?.let { action -> ConfirmDialog(action, trashDays, onConfirm = {
            if (action is DupAction.KeepSome) onKeep(action.keepIds)
            pending = null
        }, onDismiss = { pending = null }) }
    }
}

/** No group left: what the session freed, or that the scan found nothing. */
@Composable
private fun DoneOrEmpty(freed: DupFreed, apart: Int, emptyText: String, onClose: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (freed.groups == 0 && apart == 0) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(CriptaIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(str(R.string.all_sorted), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text(buildString {
            if (freed.bytes > 0) append(str(R.string.dup_freed, formatBytes(freed.bytes), plural(R.plurals.groups_n, freed.groups)))
            // In the trash the files still take their space: say so instead of "freed".
            if (freed.trashedBytes > 0) {
                if (isNotEmpty()) append(" ")
                append(str(R.string.dup_to_trash, formatBytes(freed.trashedBytes), plural(R.plurals.groups_n, freed.groups)))
            }
            if (apart > 0) {
                if (isNotEmpty()) append(" ")
                append(plural(R.plurals.groups_apart, apart))
            }
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        com.cripta.app.ui.components.FilledTonalButton(onClick = onClose, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp)) {
            com.cripta.app.ui.components.ButtonText(str(R.string.close))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SimilarPage(
    g: SettingsViewModel.DupGroup,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onOpen: (SettingsViewModel.DupCandidate) -> Unit,
    onKeep: (Set<String>) -> Unit,
    onNotSimilar: () -> Unit,
    onWhy: () -> Unit,
) {
    // The suggested copy is kept and in view at first; the others are to delete.
    var keep by remember(g) { mutableStateOf(setOf(g.bestId)) }
    var focusId by remember(g) { mutableStateOf(g.bestId) }
    val focus = g.candidates.firstOrNull { it.file.id == focusId } ?: g.best
    val removing = g.candidates.filter { it.file.id !in keep }
    // The tags go to the first kept copy (in the suggested order), as the resolution does.
    val heir = g.candidates.firstOrNull { it.file.id in keep }
    val moving = heir?.let { h -> removing.flatMap { it.tags }.distinct().filterNot { it in h.tags } }.orEmpty()
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val picture: @Composable (Modifier) -> Unit = { m ->
        val f = focus.file
        val video = VaultRepository.isVideo(f.mimeType)
        Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(com.cripta.app.ui.theme.CoverShape)
                .clickable(onClickLabel = if (video) str(R.string.watch_fullscreen) else str(R.string.open_fullscreen)) { onOpen(focus) },
                contentAlignment = Alignment.Center) {
                val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                com.cripta.app.ui.components.CoverPlaceholderBackground()
                Crossfade(bmp, label = "similarcover") { b ->
                    if (b != null) Image(b.asImageBitmap(), f.originalName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.size(52.dp).clip(CircleShape).background(com.cripta.app.ui.components.OnImageGlass), contentAlignment = Alignment.Center) {
                    Icon(if (video) CriptaIcons.PlayArrow else CriptaIcons.ZoomIn, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                if (video) formatDuration(f.durationMs)?.let { d ->
                    Text(d, color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape)
                            .background(com.cripta.app.ui.components.OnImageGlass).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            // The name under the picture, never on it (DS: title, then the caption).
            Text(f.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp))
            Text(buildString {
                append(listOfNotNull(focus.resolution?.let { "${it.first}×${it.second}" }, formatBytes(f.sizeBytes),
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale).format(java.util.Date(f.importedAt))).joinToString(" · "))
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (focus.folder.isNotEmpty()) {
                Text(focus.folder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val details: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(g.candidates, key = { it.file.id }) { c ->
                    val on = c.file.id in keep
                    val best = c.file.id == g.bestId
                    // Not clipped as a whole: the rounded clip cut the marker (out past the picture's
                    // corner) and the ends of the text under it. The picture is rounded on its own.
                    Column(Modifier.width(72.dp)
                        .combinedClickable(onClickLabel = if (on) str(R.string.delete) else str(R.string.keep_short), onLongClickLabel = str(R.string.bring_into_view),
                            onLongClick = { focusId = c.file.id }) {
                            keep = if (on) keep - c.file.id else keep + c.file.id
                            focusId = c.file.id
                        }
                        .semantics(mergeDescendants = true) { selected = on }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            val b by produceState<android.graphics.Bitmap?>(null, c.file.id) { value = thumb(c.file) }
                            Box(Modifier.size(72.dp).clip(com.cripta.app.ui.theme.CoverShape)
                                .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, com.cripta.app.ui.theme.CoverShape).padding(6.dp)
                                    .clip(RoundedCornerShape(11.dp)) else Modifier)) {
                                com.cripta.app.ui.components.CoverPlaceholderBackground()
                                b?.let { Image(it.asImageBitmap(), c.file.originalName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            // State marker: the ring and a tick for kept, a bin for deleted (never colour alone).
                            // DS: cr-check 24dp when kept, a 28dp glass disc with the bin when deleted.
                            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(if (on) 24.dp else 28.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else com.cripta.app.ui.components.OnImageGlass),
                                contentAlignment = Alignment.Center) {
                                Icon(if (on) CriptaIcons.Check else CriptaIcons.Delete, null, modifier = Modifier.size(14.dp),
                                    tint = if (on) MaterialTheme.colorScheme.onPrimary else Color.White)
                            }
                        }
                        Text(if (on) str(R.string.keep_short) else str(R.string.delete), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        // How many tags each copy carries helps pick the one to keep (the DS dropped it).
                        Text(listOfNotNull(if (best) str(R.string.recommended_f) else null,
                            if (c.tags.isEmpty()) str(R.string.no_tags_lower) else plural(R.plurals.tags_n, c.tags.size)).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            if (heir != null && (heir.tags.isNotEmpty() || moving.isNotEmpty())) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Whose tags these are: without the heading they read as every copy's.
                    Text(if (keep.size > 1) str(R.string.tags_of, heir.file.originalName) else str(R.string.tags_of_kept),
                        style = com.cripta.app.ui.theme.HeadingStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // DS Simili: tags at the default (32dp) size.
                        heir.tags.forEach { t -> com.cripta.app.ui.components.TagPill(t, com.cripta.app.ui.theme.tagColor(t), base = MaterialTheme.colorScheme.background,
                            size = com.cripta.app.ui.components.TagPillSize.Medium) }
                        moving.forEach { t ->
                            com.cripta.app.ui.components.TagPill(t, com.cripta.app.ui.theme.tagColor(t), base = MaterialTheme.colorScheme.background,
                                leadingIcon = CriptaIcons.Add, size = com.cripta.app.ui.components.TagPillSize.Medium)
                        }
                    }
                    if (moving.isNotEmpty()) Text(
                        if (moving.size == 1) str(R.string.tag_comes_from_deleted, moving.single())
                        else str(R.string.tags_come_from_deleted),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            com.cripta.app.ui.components.TextButton(onClick = onWhy, modifier = Modifier.offset(x = (-12).dp)) {
                com.cripta.app.ui.components.ButtonText(str(R.string.why_recommended))
            }
        }
    }
    // The answer stays in sight: only the details above it scroll when they are long (many tags).
    val actions: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onKeep(keep) }, enabled = keep.isNotEmpty() && removing.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                com.cripta.app.ui.components.ButtonText(when {
                    keep.isEmpty() -> str(R.string.keep_at_least_one)
                    removing.isEmpty() -> str(R.string.mark_one_to_delete)
                    // The space freed: in portrait no title says it.
                    else -> str(R.string.keep_delete_summary, keep.size, removing.size, formatBytes(removing.sumOf { it.file.sizeBytes }))
                })
            }
            com.cripta.app.ui.components.FilledTonalButton(onClick = onNotSimilar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                com.cripta.app.ui.components.ButtonText(str(R.string.not_similar))
            }
        }
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            picture(Modifier.weight(1.3f).fillMaxHeight())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupTitle(g, removing)
                details(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                actions(Modifier.fillMaxWidth())
            }
        }
    } else {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // The details take at most 40% of the page (then scroll): the picture always keeps room.
            val detailsMax = maxHeight * 0.4f
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                picture(Modifier.weight(1f).fillMaxWidth())
                details(Modifier.fillMaxWidth().heightIn(max = detailsMax).verticalScroll(rememberScrollState()))
                actions(Modifier.fillMaxWidth())
            }
        }
    }
}

/** "3 copie simili · recuperi 212 MB": what the current choice frees. */
@Composable
private fun GroupTitle(g: SettingsViewModel.DupGroup, removing: List<SettingsViewModel.DupCandidate>) {
    Text(str(R.string.similar_copies_reclaim, g.candidates.size, formatBytes(removing.sumOf { it.file.sizeBytes })),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

private fun facts(c: SettingsViewModel.DupCandidate) = com.cripta.app.data.dedup.CopyComparison.Facts(
    width = c.resolution?.first, height = c.resolution?.second, bytes = c.file.sizeBytes, durationMs = c.file.durationMs,
    importedAt = c.file.importedAt, video = VaultRepository.isVideo(c.file.mimeType), mp4 = c.file.mimeType == "video/mp4",
    favorite = c.file.isFavorite, hasLink = !c.file.sourceUrl.isNullOrBlank(),
)

/**
 * "Perché è consigliata": the suggested copy against one other (picked when there are more), the
 * decisive reasons apart from the rest, the tags (which never decide), and two ways to see it:
 * the slide comparison and both copies one after the other in the viewer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WhyRecommended(
    g: SettingsViewModel.DupGroup,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onDismiss: () -> Unit,
    onCompare: (SettingsViewModel.DupCandidate, SettingsViewModel.DupCandidate) -> Unit,
    onPlayBoth: (SettingsViewModel.DupCandidate, SettingsViewModel.DupCandidate) -> Unit,
) {
    val best = g.best
    val others = g.candidates.filter { it.file.id != g.bestId }
    var otherId by remember(g) { mutableStateOf(others.firstOrNull()?.file?.id) }
    val other = others.firstOrNull { it.file.id == otherId } ?: others.firstOrNull() ?: return
    var picking by remember { mutableStateOf(false) }
    val dateFmt = remember(com.cripta.app.Strings.locale) { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale) }
    val reasons = remember(best, other) {
        com.cripta.app.data.dedup.CopyComparison.reasons(facts(best), facts(other),
            date = { dateFmt.format(java.util.Date(it)) }, duration = { formatDuration(it).orEmpty() })
    }
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = {
            TopAppBar(windowInsets = com.cripta.app.ui.components.criptaTopBarInsets, colors = com.cripta.app.ui.components.criptaTopBarColors(),
                title = { Text(str(R.string.why_x, best.file.originalName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(CriptaIcons.ArrowBack, str(R.string.back)) } })
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(best, other).forEach { c ->
                        Column(Modifier.weight(1f)) {
                            val b by produceState<android.graphics.Bitmap?>(null, c.file.id) { value = thumb(c.file) }
                            Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(com.cripta.app.ui.theme.CoverShape)
                                .then(if (c === best) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, com.cripta.app.ui.theme.CoverShape) else Modifier)) {
                                com.cripta.app.ui.components.CoverPlaceholderBackground()
                                b?.let { Image(it.asImageBitmap(), c.file.originalName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            Text(c.file.originalName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp))
                            if (c === best) Text(str(R.string.recommended_f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (others.size > 1) Box {
                    Row(Modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).clickable { picking = true },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(str(R.string.compared_to), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(other.file.originalName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Icon(CriptaIcons.ArrowDropDown, str(R.string.choose_copy_to_compare))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        others.forEach { o ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(o.file.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { otherId = o.file.id; picking = false })
                        }
                    }
                }
                val major = reasons.filter { it.major }
                val minor = reasons.filter { !it.major }
                if (major.isNotEmpty()) ReasonBlock(str(R.string.counts_more), major, MaterialTheme.colorScheme.primary)
                if (minor.isNotEmpty()) ReasonBlock(if (major.isEmpty()) str(R.string.the_differences) else str(R.string.also), minor, MaterialTheme.colorScheme.onSurfaceVariant)
                if (major.isEmpty() && minor.isEmpty()) Text(str(R.string.copies_equivalent),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val theirs = other.tags.filterNot { it in best.tags }
                if (best.tags.isNotEmpty() || theirs.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(str(R.string.tags_dont_decide), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        best.tags.forEach { t -> com.cripta.app.ui.components.TagPill(t, com.cripta.app.ui.theme.tagColor(t), base = MaterialTheme.colorScheme.background) }
                        theirs.forEach { t ->
                            com.cripta.app.ui.components.TagPill(t, com.cripta.app.ui.theme.tagColor(t), base = MaterialTheme.colorScheme.background,
                                leadingIcon = CriptaIcons.Add)
                        }
                    }
                    Text(str(R.string.tags_all_end_on_kept) + (if (theirs.isNotEmpty()) str(R.string.tags_plus_other) else "."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.cripta.app.ui.components.FilledTonalButton(onClick = { onCompare(best, other) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(CriptaIcons.SwapHoriz, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    com.cripta.app.ui.components.ButtonText(str(R.string.compare_slider))
                }
                com.cripta.app.ui.components.FilledTonalButton(onClick = { onPlayBoth(best, other) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    com.cripta.app.ui.components.ButtonText(str(R.string.watch_one_after_other))
                }
            }
        }
    }
}

@Composable
private fun ReasonBlock(title: String, reasons: List<com.cripta.app.data.dedup.CopyComparison.Reason>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.semantics { heading() })
        reasons.forEach { r ->
            Column {
                Text(r.title, style = MaterialTheme.typography.bodyLarge)
                Text(r.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * One picture, half from each copy, split by a line dragged sideways: photos at screen size
 * (decoded in memory), videos by their cover. The names sit on the dark glass at the bottom.
 */
@Composable
private fun SlideCompare(
    a: SettingsViewModel.DupCandidate,
    b: SettingsViewModel.DupCandidate,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    imageBytes: suspend (FileEntity) -> ByteArray,
    onDismiss: () -> Unit,
) {
    suspend fun picture(c: SettingsViewModel.DupCandidate): android.graphics.Bitmap? =
        if (!VaultRepository.isImage(c.file.mimeType)) thumb(c.file)
        else runCatching { decodeFitted(imageBytes(c.file), 2048) }.getOrNull() ?: thumb(c.file)
    val left by produceState<android.graphics.Bitmap?>(null, a.file.id) { value = picture(a) }
    val right by produceState<android.graphics.Bitmap?>(null, b.file.id) { value = picture(b) }
    var split by remember { mutableFloatStateOf(0.5f) }
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(CriptaIcons.Close, str(R.string.close), tint = Color.White) }
                    Text(str(R.string.drag_line_to_compare), color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
                    }
                    .pointerInput(Unit) { detectTapGestures { p -> split = (p.x / size.width).coerceIn(0f, 1f) } }
                    .semantics { contentDescription = str(R.string.compare_left_right, a.file.originalName, b.file.originalName) }) {
                    right?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    left?.let {
                        Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                            .drawWithContent { clipRect(right = size.width * split) { this@drawWithContent.drawContent() } })
                    }
                    val x = maxWidth * split
                    Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = x - 1.dp).width(2.dp).fillMaxHeight().background(Color.White))
                    Box(Modifier.absoluteOffset(x = x - 18.dp).align(AbsoluteAlignment.CenterLeft).size(36.dp).clip(CircleShape)
                        .background(com.cripta.app.ui.components.OnImageGlass), contentAlignment = Alignment.Center) {
                        Icon(CriptaIcons.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    listOf(a to AbsoluteAlignment.BottomLeft, b to AbsoluteAlignment.BottomRight).forEach { (c, at) ->
                        Text(c.file.originalName, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(at).padding(12.dp).widthIn(max = maxWidth / 2 - 24.dp)
                                .clip(CircleShape).background(com.cripta.app.ui.components.OnImageGlass).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * A photo of the side-by-side preview, at most 4095 pixels on its longest side: decoded whole, a
 * photo of tens of megapixels took hundreds of megabytes and was too large to draw. Null when
 * [bytes] are not a picture.
 */
internal fun previewBitmap(bytes: ByteArray): android.graphics.Bitmap? = decodeFitted(bytes, 2048)

/** [bytes] decoded with the longest side at most about [side] (power-of-two subsampling, in memory). */
private fun decodeFitted(bytes: ByteArray, side: Int): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * " The copies removed are in …" when some of [removed] sit in a folder none of [kept] is in: the
 * kept copy stays in its own folder, and that one loses the file.
 */
private fun otherFolders(removed: List<SettingsViewModel.DupCandidate>, kept: List<SettingsViewModel.DupCandidate>): String {
    val keptIn = kept.map { it.folder }.toSet()
    val from = removed.map { it.folder }.filter { it.isNotEmpty() }.distinct()
    return if (from.any { it !in keptIn }) str(R.string.copies_removed_in, from.joinToString(", ")) else ""
}

@Composable
private fun ConfirmDialog(action: DupAction, trashDays: Int?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val fate = if (trashDays != null) str(R.string.trash_for_days, trashDays)
        else str(R.string.delete_secure)
    val (title, body) = when (action) {
        is DupAction.KeepOnly -> {
            val others = action.group.candidates.filter { it.file.id != action.keep.file.id }
            val moving = others.flatMap { it.tags }.distinct().filterNot { it in action.keep.tags }
            val t = plural(R.plurals.delete_other_copies_q, others.size)
            val b = buildString {
                append(str(R.string.stays_x, action.keep.file.originalName))
                append(if (moving.isEmpty()) str(R.string.no_tags_to_move)
                    else str(R.string.tags_moved_to_kept, moving.joinToString(", ") { "#$it" }))
                append(fate)
                append(otherFolders(others, listOf(action.keep)))
            }
            t to b
        }
        is DupAction.KeepSome -> {
            val keep = action.group.candidates.filter { it.file.id in action.keepIds }
            val others = action.group.candidates.filter { it.file.id !in action.keepIds }
            val heir = keep.firstOrNull()
            val moving = heir?.let { h -> others.flatMap { it.tags }.distinct().filterNot { it in h.tags } }.orEmpty()
            val t = plural(R.plurals.delete_copies_q, others.size)
            t to buildString {
                append(if (keep.size == 1) str(R.string.stays_x, heir?.file?.originalName) else str(R.string.n_copies_stay, keep.size))
                if (moving.isNotEmpty() && heir != null) append(str(R.string.tags_pass_to, moving.joinToString(", ") { "#$it" }, heir.file.originalName))
                append(fate)
                append(otherFolders(others, keep))
            }
        }
        is DupAction.DeleteOne -> {
            val g = action.group
            val heir = if (g.bestId != action.target.file.id) g.best
                else g.candidates.firstOrNull { it.file.id != action.target.file.id }
            val moving = heir?.let { h -> action.target.tags.filterNot { it in h.tags } }.orEmpty()
            str(R.string.delete_this_copy_q) to buildString {
                                append(if (trashDays != null) str(R.string.copy_will_trash, action.target.file.originalName, trashDays) else str(R.string.copy_will_delete, action.target.file.originalName))
                if (moving.isNotEmpty() && heir != null) {
                    append(str(R.string.its_tags_moved_to, moving.joinToString(", ") { "#$it" }, heir.file.originalName))
                }
                append(otherFolders(listOf(action.target), g.candidates.filter { it.file.id != action.target.file.id }))
            }
        }
    }
    com.cripta.app.ui.components.CriptaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                com.cripta.app.ui.components.ButtonText(if (trashDays != null) str(R.string.move_to_trash) else str(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { com.cripta.app.ui.components.ButtonText(str(R.string.cancel)) } },
    )
}

@Composable
private fun GroupCard(
    modifier: Modifier = Modifier,
    group: SettingsViewModel.DupGroup,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onKeep: (SettingsViewModel.DupCandidate) -> Unit,
    onDelete: (SettingsViewModel.DupCandidate) -> Unit,
    onPreview: (SettingsViewModel.DupCandidate) -> Unit,
) {
    // No frame and no lines: each group is a heading, its copies and its action; space separates groups.
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val total = group.candidates.sumOf { it.file.sizeBytes }
            val reclaim = total - group.best.file.sizeBytes
            Text(
                str(R.string.copies_total_reclaim, group.candidates.size, formatBytes(total), formatBytes(reclaim)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.Top) {
                Icon(CriptaIcons.ThumbUp, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Text(
                    buildString {
                        append(str(R.string.recommended_x, group.best.file.originalName))
                        if (group.reasons.isNotEmpty()) append(" — ${group.reasons.joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                group.candidates.forEach { c ->
                    CandidateCard(
                        c = c,
                        best = c.file.id == group.bestId,
                        bestPixels = group.best.pixels,
                        thumb = thumb,
                        onKeep = { onKeep(c) },
                        onDelete = { onDelete(c) },
                        onPreview = { onPreview(c) },
                    )
                }
            }
            Button(onClick = { onKeep(group.best) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                val n = group.candidates.size - 1
                com.cripta.app.ui.components.ButtonText(plural(R.plurals.keep_recommended, n))
            }
            if (group.tagsToMove.isNotEmpty()) {
                Text(
                    str(R.string.tags_moved_to_kept, group.tagsToMove.joinToString(", ") { "#$it" }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    c: SettingsViewModel.DupCandidate,
    best: Boolean,
    bestPixels: Long,
    thumb: suspend (FileEntity) -> android.graphics.Bitmap?,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
) {
    val f = c.file
    // No card around a copy: the suggested one is marked by the label on its cover.
    Box(Modifier.width(176.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Tap the cover to watch the video / see the photo full size before choosing.
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(com.cripta.app.ui.theme.CoverShape).clickable(onClick = onPreview)) {
                val bmp by produceState<android.graphics.Bitmap?>(null, f.id) { value = thumb(f) }
                com.cripta.app.ui.components.CoverPlaceholderBackground()
                Crossfade(bmp, label = "dupthumb") { b ->
                    if (b != null) {
                        Image(b.asImageBitmap(), f.originalName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    VaultRepository.isVideo(f.mimeType) -> CriptaIcons.Movie
                                    VaultRepository.isImage(f.mimeType) -> CriptaIcons.Image
                                    else -> CriptaIcons.InsertDriveFile
                                },
                                null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                if (best) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                        Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(CriptaIcons.ThumbUp, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                            Text(str(R.string.recommended), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                if (VaultRepository.isVideo(f.mimeType) || VaultRepository.isImage(f.mimeType)) {
                    Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape)
                        .background(com.cripta.app.ui.components.OnImageGlass), contentAlignment = Alignment.Center) {
                        Icon(
                            if (VaultRepository.isVideo(f.mimeType)) CriptaIcons.PlayArrow else CriptaIcons.ZoomIn,
                            str(R.string.preview), tint = Color.White, modifier = Modifier.size(26.dp),
                        )
                    }
                }
                if (f.isFavorite) com.cripta.app.ui.components.CoverStar(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Text(f.originalName, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // The folder: "Tieni solo questa" deletes the others from theirs.
            if (c.folder.isNotEmpty()) {
                Text(c.folder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Resolution is highlighted when it is lower than the suggested copy's.
            c.resolution?.let { (w, h) ->
                val lower = c.pixels in 1 until bestPixels
                Text("$w×$h", style = MaterialTheme.typography.labelMedium,
                    color = if (lower) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            Text(
                listOfNotNull(
                    formatBytes(f.sizeBytes),
                    formatDuration(f.durationMs),
                    f.mimeType.substringAfter('/').uppercase().take(6),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, com.cripta.app.Strings.locale).format(java.util.Date(f.importedAt)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!f.sourceUrl.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(CriptaIcons.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(str(R.string.source_link_lower), modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (c.tags.isEmpty()) {
                Text(str(R.string.no_tags), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.tags.forEach { t ->
                        com.cripta.app.ui.components.TagPill(t, com.cripta.app.ui.theme.tagColor(t),
                            base = MaterialTheme.colorScheme.background)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                com.cripta.app.ui.components.FilledTonalButton(onClick = onKeep, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(str(R.string.keep_only_this), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                }
                IconButton(onClick = onDelete) {
                    Icon(CriptaIcons.Delete, str(R.string.delete_this_copy), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Full-screen preview of one copy of a duplicate group: videos play in place (streamed from the
 * decrypting channel, never written to disk), photos show at full size. Arrows switch between the
 * copies of the group, keeping the same playback position, so they can be compared directly.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PreviewDialog(
    group: SettingsViewModel.DupGroup,
    startIndex: Int,
    channelFor: (FileEntity) -> java.nio.channels.SeekableByteChannel,
    imageBytes: suspend (FileEntity) -> ByteArray,
    onKeep: (SettingsViewModel.DupCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, group.candidates.lastIndex)) }
    // Position carried across copies so the same moment of each video can be compared.
    var positionMs by remember { mutableLongStateOf(0L) }
    val c = group.candidates[index]
    val best = c.file.id == group.bestId
    com.cripta.app.ui.components.CriptaDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(CriptaIcons.Close, str(R.string.close), tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(c.file.originalName, color = Color.White, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                str(R.string.copy_x_of_y, index + 1, group.candidates.size),
                                c.resolution?.let { "${it.first}×${it.second}" },
                                formatBytes(c.file.sizeBytes),
                                if (best) str(R.string.recommended_m) else null,
                            ).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val f = c.file
                    when {
                        VaultRepository.isVideo(f.mimeType) -> androidx.compose.runtime.key(f.id) {
                            PreviewVideo(f, channelFor, positionMs) { positionMs = it }
                        }
                        VaultRepository.isImage(f.mimeType) -> {
                            // (read, picture): a picture that cannot be read says so instead of loading forever.
                            val bmp by produceState<Pair<Boolean, android.graphics.Bitmap?>>(false to null, f.id) {
                                value = true to runCatching { previewBitmap(imageBytes(f)) }.getOrNull()
                            }
                            val (read, b) = bmp
                            if (b != null) Image(b.asImageBitmap(), f.originalName, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize())
                            else if (read) Text(str(R.string.preview_unavailable), color = Color.White)
                            else androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                        }
                        else -> Text(str(R.string.preview_unavailable), color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { index-- }, enabled = index > 0) {
                        Icon(CriptaIcons.KeyboardArrowLeft, str(R.string.previous_copy),
                            tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                    Button(onClick = { onKeep(c) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { com.cripta.app.ui.components.ButtonText(str(R.string.keep_only_this)) }
                    IconButton(onClick = { index++ }, enabled = index < group.candidates.lastIndex) {
                        Icon(CriptaIcons.KeyboardArrowRight, str(R.string.next_copy),
                            tint = if (index < group.candidates.lastIndex) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PreviewVideo(
    file: FileEntity,
    channelFor: (FileEntity) -> java.nio.channels.SeekableByteChannel,
    startMs: Long,
    onPosition: (Long) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val player = remember(file.id) {
        // A preview, scrubbed by hand: a bigger buffer than a clip's, still far below the default.
        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).setLoadControl(smallBuffer(maxMs = 15_000, bytes = 32 shl 20)).build().apply {
            // As in the main viewer: route audio as media and handle audio focus (pause when another
            // app takes it), which this preview did not do.
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            val factory = com.cripta.app.viewer.EncryptedDataSource.Factory(
                channelProvider = { channelFor(file) },
                plaintextLength = file.sizeBytes,
            )
            val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            setMediaSource(
                androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory, extractors)
                    .createMediaSource(androidx.media3.common.MediaItem.fromUri("cripta://${file.id}"))
            )
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            prepare()
            if (startMs > 0) seekTo(startMs)
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose {
            onPosition(player.currentPosition)
            player.release()
        }
    }
    // Pause when the app goes to the background (Home, screen off) or the vault locks (the lock gate
    // stops this lifecycle too): the preview kept playing with nothing on screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, player) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { androidx.media3.ui.PlayerView(it).apply { this.player = player; useController = true } },
        modifier = Modifier.fillMaxSize(),
    )
}
