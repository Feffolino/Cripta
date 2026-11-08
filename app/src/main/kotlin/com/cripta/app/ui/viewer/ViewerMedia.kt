@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cripta.app.ui.viewer

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.ui.theme.CriptaIcons
import androidx.compose.foundation.layout.width
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
internal fun MediaPage(
    id: String,
    refreshKey: Int,
    isCurrent: Boolean,
    chromeVisible: Boolean,
    vm: ViewerViewModel,
    setChrome: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    onOpenDetails: () -> Unit,
    onClose: () -> Unit,
    nextId: String?,
    onNext: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    onSeekClear: (androidx.compose.ui.unit.Dp) -> Unit,
    pageShift: () -> Float,
    controlsTimeoutMs: Int,
    onPausedChanged: (Boolean) -> Unit,
    hideControlsTick: Int = 0,
    selfManagedControls: Boolean = false,
    controlsHeld: Boolean = false,
    onMissing: () -> Unit,
    onEditNote: (String) -> Unit = {},
) {
    var retry by remember(id) { mutableIntStateOf(0) }
    val state by produceState<ViewerState>(initialValue = ViewerState.Loading, id, refreshKey, retry) {
        if (value is ViewerState.Error) value = ViewerState.Loading
        value = vm.stateFor(id)
    }
    // The file was deleted meanwhile (e.g. from the note editor): drop its page.
    val latestMissing by androidx.compose.runtime.rememberUpdatedState(onMissing)
    LaunchedEffect(state, isCurrent) {
        val s = state
        if (isCurrent && s is ViewerState.Error && s.missing) latestMissing()
    }
    // Crossfade between loading and content, keyed on the kind of state only, so a refresh of the
    // same page (favorite, tags) doesn't re-animate or rebuild the player.
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(Motion.enter(Motion.MEDIUM)) togetherWith fadeOut(Motion.exit(Motion.MEDIUM)) },
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
        label = "mediaPage",
    ) { s ->
        when (s) {
            is ViewerState.Loading -> CenteredPage(onTap = onToggleChrome) { DelayedSpinner(color = Color.White) }
            is ViewerState.Error -> CenteredPage(onTap = onToggleChrome) {
                Icon(CriptaIcons.ErrorOutline, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
                Text(s.message, color = Color.White, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                if (!s.missing) {
                    com.cripta.app.ui.components.FilledTonalButton(
                        onClick = { retry++ },
                        colors = ButtonDefaults.filledTonalButtonColors(contentColor = Color.White),
                        modifier = Modifier.padding(top = 20.dp).heightIn(min = 48.dp),
                    ) {
                        Icon(CriptaIcons.Refresh, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(str(R.string.retry))
                    }
                }
            }
            is ViewerState.Photo -> ZoomableImage(s.bytes, s.file.originalName, onSingleTap = onToggleChrome)
            is ViewerState.Video -> if (isCurrent) VideoPlayer(s.file, vm, controlsVisible = chromeVisible, onControlsVisibilityChanged = setChrome, onOpenDetails = onOpenDetails, onClose = onClose, nextId = nextId, onNext = onNext, topInset = topInset, bottomInset = bottomInset, onSeekClear = onSeekClear, pageShift = pageShift, controlsTimeoutMs = controlsTimeoutMs, onPausedChanged = onPausedChanged, hideControlsTick = hideControlsTick, selfManagedControls = selfManagedControls, controlsHeld = controlsHeld)
                else CenteredPage(onTap = onToggleChrome) { DelayedSpinner(color = Color.White) }
            is ViewerState.Note -> NoteView(s.text, onSingleTap = onToggleChrome,
                onTextChange = { vm.saveNoteText(s.file, it) },
                onEdit = { onEditNote(s.file.id) }, editShown = chromeVisible, bottomInset = bottomInset)
            is ViewerState.Pdf -> PdfView(s.bytes, onSingleTap = onToggleChrome)
            is ViewerState.Other -> CenteredPage(onTap = onToggleChrome) {
                Text(str(R.string.no_preview), color = Color.White, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge)
                Text(str(R.string.no_preview_hint), color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Centred message page (loading, error, unsupported type). A tap anywhere toggles the chrome, so
 *  the actions can always be brought back even where there is no media to tap. */
@Composable
private fun CenteredPage(onTap: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val latestTap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { latestTap() }) }
            .padding(horizontal = 32.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * Spinner that appears only after [delayMs] (fading in), so fast opens never flash it. Shared by the
 * viewer, the note editor and the favorites grid.
 */
@Composable
internal fun DelayedSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    delayMs: Long = 250L,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs); show = true }
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(Motion.enter(Motion.MEDIUM)),
        exit = fadeOut(Motion.exit(Motion.SHORT)),
        label = "delayedSpinner",
    ) {
        CircularProgressIndicator(color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color)
    }
}

@Composable
private fun ZoomableImage(bytes: ByteArray, name: String, onSingleTap: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    // The tap detector reads a drag that nobody consumed (a swipe up / down at 1x) as a tap: when
    // the last gesture dragged, its tap and a quick second tap are not a tap / double tap.
    var dragEndedAt by remember { mutableStateOf(0L) }
    val afterDrag = { android.os.SystemClock.uptimeMillis() - dragEndedAt < 600L }

    AsyncImage(
        model = ImageRequest.Builder(ctx).data(bytes).diskCachePolicy(CachePolicy.DISABLED).build(),
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Pinch to zoom / drag to pan, but only CONSUME the gesture while zoomed or
                // actively pinching. At 1x a single-finger horizontal drag is left unconsumed
                // so the HorizontalPager can swipe between media (fixes swipe stuck on a page).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var moved = 0f
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        moved += pan.getDistance()
                        if (moved > viewConfiguration.touchSlop && event.changes.none { it.pressed }) {
                            dragEndedAt = android.os.SystemClock.uptimeMillis()
                        }
                        val newScale = (scale.value * zoom).coerceIn(1f, 6f)
                        if (newScale != scale.value) scope.launch { scale.snapTo(newScale) }
                        if (newScale > 1f) {
                            // Clamped to the scaled content's bounds (as the video and PDF viewers
                            // do): unclamped, the photo could be dragged fully off-screen. Also
                            // re-clamps while pinching out, when the allowed range shrinks.
                            val maxX = (newScale - 1f) * size.width / 2f
                            val maxY = (newScale - 1f) * size.height / 2f
                            val nx = (offX.value + pan.x).coerceIn(-maxX, maxX)
                            val ny = (offY.value + pan.y).coerceIn(-maxY, maxY)
                            scope.launch { offX.snapTo(nx) }
                            scope.launch { offY.snapTo(ny) }
                        } else if (offX.value != 0f || offY.value != 0f) {
                            scope.launch { offX.snapTo(0f) }
                            scope.launch { offY.snapTo(0f) }
                        }
                        if (newScale > 1f || zoom != 1f) {
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!afterDrag()) onSingleTap() },
                    onDoubleTap = { tap ->
                        if (!afterDrag()) scope.launch {
                            if (scale.value > 1f) {
                                launch { scale.animateTo(1f) }
                                launch { offX.animateTo(0f) }
                                launch { offY.animateTo(0f) }
                            } else {
                                val target = 2.5f
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                launch { scale.animateTo(target) }
                                launch { offX.animateTo((cx - tap.x) * (target - 1f)) }
                                launch { offY.animateTo((cy - tap.y) * (target - 1f)) }
                            }
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                translationX = offX.value; translationY = offY.value
            },
    )
}

/**
 * A note as you read it: its Markdown drawn (headings, bold, lists…, no markers). A check-list box
 * ticks with a tap, saved at once; "Modifica" opens the editor, so a note is always editable from here.
 */
@Composable
private fun NoteView(
    text: String,
    onSingleTap: () -> Unit,
    onTextChange: (String) -> Unit,
    onEdit: () -> Unit,
    editShown: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    var current by remember(text) { mutableStateOf(text) }
    val palette = com.cripta.app.ui.note.NoteMarkdown.Palette(
        muted = Color.White.copy(alpha = 0.6f), accent = MaterialTheme.colorScheme.primary,
        codeBackground = Color.White.copy(alpha = 0.1f),
    )
    // Lines grouped into runs of plain Markdown and single check-list items (those get a tap target).
    val blocks = remember(current) {
        val out = mutableListOf<Pair<Int, String>>()   // (line index or -1, text)
        val run = StringBuilder()
        var fence = false
        current.split('\n').forEachIndexed { i, line ->
            if (com.cripta.app.ui.note.NoteMarkdown.isFence(line)) fence = !fence
            if (!fence && com.cripta.app.ui.note.NoteMarkdown.checkState(line) != null) {
                if (run.isNotEmpty()) { out += -1 to run.toString().removeSuffix("\n"); run.clear() }
                out += i to line
            } else run.append(line).append('\n')
        }
        if (run.isNotEmpty()) out += -1 to run.toString().removeSuffix("\n")
        out
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0C10))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) { detectTapGestures(onTap = { onSingleTap() }) }
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
        ) {
            if (current.isBlank()) Text(str(R.string.note_empty), color = Color.White.copy(alpha = 0.6f))
            // Long-press selects text to copy; a single tap still toggles the chrome.
            SelectionContainer {
                Column {
                    blocks.forEach { (index, part) ->
                        if (index < 0) {
                            Text(com.cripta.app.ui.note.NoteMarkdown.render(part, palette, keepMarkers = false),
                                color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        } else {
                            val done = com.cripta.app.ui.note.NoteMarkdown.checkState(part) == true
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(MaterialTheme.shapes.small)
                                    .toggleableRow(done) { current = com.cripta.app.ui.note.NoteMarkdown.toggleCheck(current, index); onTextChange(current) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (done) CriptaIcons.CheckCircle else CriptaIcons.FormatChecklist, null,
                                    tint = if (done) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(end = 10.dp).size(22.dp))
                                Text(com.cripta.app.ui.note.NoteMarkdown.render(com.cripta.app.ui.note.NoteMarkdown.checkText(part), palette, keepMarkers = false),
                                    color = if (done) Color.White.copy(alpha = 0.55f) else Color.White,
                                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = editShown,
            enter = fadeIn(Motion.enter(Motion.SHORT)), exit = fadeOut(Motion.exit(Motion.SHORT)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomInset + 16.dp),
        ) {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onEdit,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(CriptaIcons.Edit, null) }, text = { Text(str(R.string.edit)) },
            )
        }
    }
}

/** A check-list row: the whole row ticks the box (and says so to TalkBack). */
private fun Modifier.toggleableRow(checked: Boolean, onToggle: () -> Unit): Modifier =
    this.then(Modifier.toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Checkbox) { onToggle() })

/** Keeps a PDF open and renders pages on demand (one visible page at a time), so a large
 *  document doesn't rasterize every page into RAM up front. Render calls are serialized. */
private class PdfDoc(bytes: ByteArray) {
    private val doc = PDDocument.load(ByteArrayInputStream(bytes))
    private val renderer = PDFRenderer(doc)
    private val mutex = Mutex()
    /** The last pages drawn, so scrolling back up doesn't render them again (each is a few MB). */
    private val recent = android.util.LruCache<Int, Bitmap>(3)
    val pageCount: Int = doc.numberOfPages
    suspend fun render(index: Int): Bitmap? = recent.get(index) ?: mutex.withLock {
        recent.get(index) ?: withContext(Dispatchers.IO) {
            runCatching { renderer.renderImageWithDPI(index, 150f) }.getOrNull()
        }?.also { recent.put(index, it) }
    }
    fun close() { runCatching { doc.close() } }
}

@Composable
private fun PdfView(bytes: ByteArray, onSingleTap: () -> Unit) {
    val result by produceState<Result<PdfDoc>?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDoc(bytes) } }
    }
    val doc = result?.getOrNull()
    DisposableEffect(doc) { onDispose { doc?.close() } }
    when {
        result == null -> CenteredPage(onTap = onSingleTap) { DelayedSpinner(color = Color.White) }
        doc == null -> CenteredPage(onTap = onSingleTap) {
            Icon(CriptaIcons.ErrorOutline, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
            Text(str(R.string.pdf_open_failed),
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            Text(str(R.string.pdf_export_hint),
                color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        else -> {
            val listState = rememberLazyListState()
            // Pinch to zoom (1x..4x), double-tap to toggle 2.5x. At 1x one-finger drags are left
            // alone, so the list scrolls vertically and the pager still swipes horizontally.
            var zoom by remember { mutableFloatStateOf(1f) }
            var panX by remember { mutableFloatStateOf(0f) }
            var panY by remember { mutableFloatStateOf(0f) }
            val latestTap by androidx.compose.runtime.rememberUpdatedState(onSingleTap)
            Box(
                Modifier.fillMaxSize().background(Color(0xFF0A0C10)).clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val maxX = (zoom - 1f) * size.width / 2f
                                val maxY = (zoom - 1f) * size.height / 2f
                                if (pressed >= 2) {
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                    val pan = event.calculatePan()
                                    val mx = (zoom - 1f) * size.width / 2f
                                    val my = (zoom - 1f) * size.height / 2f
                                    panX = (panX + pan.x).coerceIn(-mx, mx)
                                    panY = (panY + pan.y).coerceIn(-my, my)
                                    event.changes.forEach { it.consume() }
                                } else if (pressed == 1 && zoom > 1f) {
                                    // Zoomed: one finger pans sideways (the list still scrolls vertically).
                                    val pan = event.calculatePan()
                                    if (pan.x != 0f) {
                                        panX = (panX + pan.x).coerceIn(-maxX, maxX)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                                if (zoom <= 1f) { panX = 0f; panY = 0f }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { latestTap() },
                            onDoubleTap = {
                                if (zoom > 1f) { zoom = 1f; panX = 0f; panY = 0f } else { zoom = 2.5f }
                            },
                        )
                    },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom
                        translationX = panX; translationY = panY
                    },
                ) {
                    items(doc.pageCount) { index ->
                        val bmp by produceState<Bitmap?>(initialValue = null, index, doc) { value = doc.render(index) }
                        val b = bmp
                        val desc = str(R.string.page_of, index + 1, doc.pageCount)
                        if (b != null) {
                            Image(b.asImageBitmap(), desc, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        } else {
                            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                                DelayedSpinner(color = Color.White)
                            }
                        }
                    }
                }
                // Page indicator: the page at the top of the screen.
                if (doc.pageCount > 1) {
                    val page by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f), shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
                    ) {
                        Text("$page / ${doc.pageCount}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}
