package com.cripta.app.ui.download

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.cripta.app.R
import com.cripta.app.data.StreamSource
import com.cripta.app.str
import com.cripta.app.ui.components.formatBytes
import com.cripta.app.ui.components.formatDuration
import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.ui.theme.Motion

/** What kind of video a request is for, by the file its address names; null for anything else. */
internal fun streamKind(url: String): String? {
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    return when (path.substringAfterLast('/').substringAfterLast('.', "")) {
        "m3u8" -> "HLS"
        "mpd" -> "DASH"
        "mp4", "m4v", "webm", "mov" -> "MP4"
        else -> null
    }
}

/** Playlists first (a page that streams also loads loose MP4 pieces), each kind in the order seen. */
internal fun streamRank(kind: String?): Int = when (kind) { "HLS" -> 0; "DASH" -> 1; else -> 2 }

/**
 * A failed link whose page was never searched: the downloader alone could not take it, so its page
 * can be opened to find the video its player loads. Not a stream already found in a page.
 */
internal fun canFindInPage(job: com.cripta.app.data.DownloadJob): Boolean =
    job.phase == com.cripta.app.data.DownloadPhase.FAILED && job.stream == null

/**
 * [webViewAgent] without what tells a page it runs in a WebView ("; wv", "Version/4.0"): players
 * that check for it refuse to start or serve another page, and the stream never gets requested.
 */
internal fun browserUserAgent(webViewAgent: String): String =
    webViewAgent.replace(Regex(""";\s*wv\b"""), "").replace(Regex("""\sVersion/\d+(\.\d+)*"""), "")

/**
 * What a stream found in a page is, read from the stream itself: its tallest picture (0 = not
 * told), its length in seconds (0 = not told) and about how much it weighs (null = not told).
 */
internal data class StreamBrief(val height: Int, val durationSec: Int, val bytes: Long? = null)

/**
 * "42:10 · 1080p · ≈ 1.8 GB" for a stream's row: its length, picture and weight, each left out
 * when unknown; null when none is. The weight is always an estimate (the parts are joined on
 * download), hence the "≈".
 */
internal fun streamBrief(height: Int, durationText: String?, sizeText: String? = null): String? =
    listOfNotNull(durationText, height.takeIf { it > 0 }?.let { "${it}p" }, sizeText?.let { "≈ $it" })
        .joinToString(" · ").ifEmpty { null }

/**
 * The stream most likely to be the video wanted: the longest one read (a trailer or an
 * advertisement is shorter), the first listed on a tie. Null with fewer than two streams, where
 * there is nothing to choose, or when no length is known.
 */
internal fun likelyStream(urls: List<String>, durationOf: (String) -> Int?): String? {
    if (urls.size < 2) return null
    var best: String? = null
    var longest = 0
    for (u in urls) {
        val d = durationOf(u) ?: continue
        if (d > longest) { longest = d; best = u }
    }
    return best
}

/** The most streams listed for a page: one that streams in MP4 pieces asks for hundreds. */
private const val MAX_STREAMS = 30

/**
 * The page searched for its video, shared between [PageVideoFinder] (the page itself) and
 * Download's top bar (where the page is, and stop or reload).
 */
@Stable
internal class PageSearch(val pageUrl: String) {
    /** Where the page is now: links followed and redirects move it. */
    var address by mutableStateOf(pageUrl)
    /** 0..100, as the page reports it. */
    var progress by mutableIntStateOf(0)
    /** Loading now: from a page's start to its end, or until [stop]. */
    var loading by mutableStateOf(true)
    internal var web: WebView? = null

    val host: String get() = hostOf(address)
    val secure: Boolean get() = address.startsWith("https://")

    fun stop() { web?.stopLoading(); loading = false }
    fun reload() { loading = true; progress = 0; web?.reload() }
}

/**
 * Where the page is, under its title in Download's top bar: the lock of a secure page (a globe
 * otherwise) and the site.
 */
@Composable
internal fun PageSearchAddress(search: PageSearch) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (search.secure) CriptaIcons.Lock else CriptaIcons.World, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(search.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * Stop and reload in one place, as in a browser: the cross while the page loads, reload once it
 * has loaded or was stopped.
 */
@Composable
internal fun PageSearchReload(search: PageSearch) {
    IconButton(onClick = { if (search.loading) search.stop() else search.reload() }) {
        Crossfade(search.loading, animationSpec = Motion.enter(), label = "reloadStop") { l ->
            Icon(if (l) CriptaIcons.Close else CriptaIcons.Refresh,
                str(if (l) R.string.stop else R.string.dl_find_reload), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The page [search] opened inside the app, to find the video the downloader could not: many
 * sites put the stream's address (an HLS playlist) nowhere in the page and ask for it from the
 * player, with a token, a referer or cookies. Every request the page makes is watched; the ones
 * for a video are listed under it, and the one tapped goes back with what the site asked to
 * serve it ([onPick]).
 *
 * The page runs its own scripts, so it gets nothing of the app: no access to files, no other
 * windows, only web links followed. Cookies, cache and storage are wiped when it closes.
 *
 * A page pushed over Download: its title, address and stop/reload are Download's top bar
 * ([PageSearchAddress], [PageSearchReload]); the page fills the rest, a thin bar over its top edge
 * while it loads (so it never jumps), and what is found sits on a sheet under it: a hint until a
 * video shows up, then the streams under a heading with their count, the sheet folding to its
 * heading with a tap to show more of the page. Each stream is read apart ([brief]) for its length,
 * picture and weight, and the longest is marked "Forse è questo", so the video wanted is told
 * from a trailer or an advertisement the page loads too.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PageVideoFinder(
    search: PageSearch,
    modifier: Modifier = Modifier,
    brief: suspend (StreamSource) -> StreamBrief?,
    onPick: (StreamSource) -> Unit,
    onClose: () -> Unit,
) {
    val found = remember { mutableStateListOf<StreamSource>() }
    // What each stream turned out to be once read: absent until then, null when it could not be.
    val briefs = remember { mutableStateMapOf<String, StreamBrief?>() }
    var title by remember { mutableStateOf<String?>(null) }

    BackHandler { search.web?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }
    DisposableEffect(Unit) {
        onDispose {
            search.web?.run { stopLoading(); loadUrl("about:blank"); clearHistory(); clearCache(true); clearFormData(); destroy() }
            search.web = null
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    // Landscape: the page on the left at full height, what is found in a panel on its right (as
    // Download keeps its queue on the right); portrait: the page above, the sheet under it.
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val page: @Composable (Modifier) -> Unit = { pageModifier ->
        Box(pageModifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // The player starts on its own where the site allows it: the stream shows up sooner.
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // As a phone browser lays a page out: a page without a mobile viewport gets a
                        // desktop width shrunk to fit the screen (not one wider than it, scrolling
                        // sideways), and the user can zoom in, without the old +/- buttons.
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // On (with onCreateWindow refusing every window below): off, a window.open() or a
                        // target="_blank" replaces the page itself, and the click that starts the player,
                        // taken by an advertisement's popup, left the page behind.
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setGeolocationEnabled(false)
                        // Players embedded from another site keep their session in cookies of their own.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.userAgentString = browserUserAgent(settings.userAgentString)
                        val agent = settings.userAgentString
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                search.progress = newProgress
                                if (newProgress >= 100) search.loading = false
                            }
                            override fun onReceivedTitle(view: WebView, t: String?) { title = t?.takeIf { it.isNotBlank() } }

                            // No other window, whoever asks: the page stays where it is and its player starts.
                            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
                        }
                        webViewClient = object : WebViewClient() {
                            // Only web links: a page cannot open another app ("intent:", "market:") from here.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.scheme?.lowercase() !in setOf("http", "https")

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { search.loading = true }
                            override fun onPageFinished(view: WebView, url: String?) { search.loading = false }

                            // The page followed a link or redirected: the top bar says where it is now.
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                if (!url.isNullOrBlank() && url != "about:blank") search.address = url
                            }

                            // Runs off the main thread for every request of the page; the page loads it as usual.
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                if (request.url.scheme?.lowercase() in setOf("http", "https") && streamKind(url) != null) {
                                    val headers = HashMap(request.requestHeaders.orEmpty())
                                    if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) headers["User-Agent"] = agent
                                    runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                                        ?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
                                    val stream = StreamSource(url, StreamSource.cleanHeaders(headers))
                                    view.post {
                                        if (found.size < MAX_STREAMS && found.none { it.url == url }) {
                                            found.add(stream)
                                            found.sortBy { streamRank(streamKind(it.url)) } // stable: order seen within a kind
                                        }
                                    }
                                }
                                return null
                            }
                        }
                        loadUrl(search.pageUrl)
                        search.web = this
                    }
                },
            )
            LoadBar(search, Modifier.align(Alignment.TopCenter))
        }
    }
    val pick: (StreamSource) -> Unit = { onPick(it.copy(title = title)) }
    if (landscape) {
        Row(modifier.fillMaxSize()) {
            page(Modifier.weight(1f).fillMaxHeight())
            FoundSheet(found, briefs, brief, side = true, onPick = pick,
                modifier = Modifier.padding(start = 8.dp).width(360.dp).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize()) {
            page(Modifier.fillMaxWidth().weight(1f))
            FoundSheet(found, briefs, brief, side = false, onPick = pick, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * What the page loaded, on the raised ground of the app's sheets: a hint until a video shows up,
 * then the streams under a heading with their count. Under the page ([side] false) it is a
 * drawer that is part of the screen, like [com.cripta.app.ui.components.SplitPanel] (the page
 * above stays touchable, the player too): its grip and heading follow the finger, a pull down from
 * the top of the list folds it, and it settles open or folded to its heading, on a fling or past
 * halfway; a tap on the heading does the same. Beside the page ([side] true, landscape) it is a
 * panel as tall as the page, always open, its rows filling it.
 */
@Composable
private fun FoundSheet(
    found: List<StreamSource>,
    briefs: MutableMap<String, StreamBrief?>,
    brief: suspend (StreamSource) -> StreamBrief?,
    side: Boolean,
    onPick: (StreamSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawer = rememberFoldState()
    val likely = likelyStream(found.map { it.url }) { u -> briefs[u]?.durationSec?.takeIf { it > 0 } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = if (side) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = found.isEmpty(),
            transitionSpec = { (fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit())) using SizeTransform(clip = false) { _, _ -> Motion.enter() } },
            label = "foundStreams",
        ) { none ->
            if (none) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    if (side) Spacer(Modifier.height(10.dp)) else Handle()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically) {
                        Mark { Icon(CriptaIcons.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(str(R.string.dl_find_none), style = MaterialTheme.typography.bodyLarge)
                            Text(str(R.string.dl_find_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    val dragState = rememberDraggableState { d -> drawer.dragBy(d) }
                    Column(Modifier.fillMaxWidth().then(
                        if (side) Modifier.padding(top = 10.dp)
                        else Modifier
                            .clickable(role = Role.Button,
                                onClickLabel = str(if (drawer.folded) R.string.expand else R.string.collapse)) { drawer.toggle() }
                            .draggable(dragState, Orientation.Vertical, onDragStopped = { v -> drawer.settle(v) }),
                    )) {
                        if (!side) Handle()
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Heading(str(R.string.dl_find_found), Modifier.weight(1f))
                            Text(com.cripta.app.ui.components.formatCount(found.size), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    Column(
                        if (side) Modifier.weight(1f, fill = false)
                        else Modifier.nestedScroll(drawer.nested).foldable(drawer),
                    ) {
                        LazyColumn(Modifier.fillMaxWidth().then(if (side) Modifier.weight(1f, fill = false) else Modifier.heightIn(max = 288.dp)),
                            contentPadding = PaddingValues(bottom = 4.dp)) {
                            items(found, key = { it.url }) { s ->
                                StreamRow(s, briefs, brief, likely = s.url == likely, modifier = Modifier.animateItem()) { onPick(s) }
                            }
                        }
                        Text(str(R.string.dl_find_footer), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
                    }
                    // Folded, the heading keeps the room under it that the open list's end has.
                    if (!side) Spacer(Modifier.height(14.dp * drawer.fold.value))
                }
            }
        }
    }
}

/**
 * The drawer's fold: 0 open, 1 folded to its heading, following the finger in between. [body] is
 * the height in px of what folds away, measured as it is laid out.
 */
@Stable
private class FoldState(private val scope: kotlinx.coroutines.CoroutineScope, startFolded: Boolean) {
    val fold = Animatable(if (startFolded) 1f else 0f)
    var body = 1f
    val folded: Boolean get() = fold.targetValue >= 0.5f

    fun dragBy(px: Float) { scope.launch { fold.snapTo((fold.value + px / body).coerceIn(0f, 1f)) } }
    fun toggle() = animateTo(if (folded) 0f else 1f)
    /** End of a drag: a fling decides, else the nearer end. */
    fun settle(velocityPx: Float) = animateTo(when {
        velocityPx > 1200f -> 1f
        velocityPx < -1200f -> 0f
        else -> if (fold.value > 0.5f) 1f else 0f
    })
    private fun animateTo(target: Float) {
        scope.launch { fold.animateTo(target, if (target > fold.value) Motion.exit(Motion.MEDIUM) else Motion.enter(Motion.LONG)) }
    }

    /** The list pulled down at its top folds the drawer; pushed up while it is folding, it opens first. */
    val nested = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && fold.value > 0f) { dragBy(available.y); return Offset(0f, available.y) }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && source == NestedScrollSource.UserInput) { dragBy(available.y); return Offset(0f, available.y) }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (fold.value > 0f && fold.value < 1f) { settle(available.y); return available }
            return Velocity.Zero
        }
    }
}

@Composable
private fun rememberFoldState(): FoldState {
    val scope = rememberCoroutineScope()
    var savedFolded by rememberSaveable { mutableStateOf(false) }
    val state = remember { FoldState(scope, savedFolded) }
    LaunchedEffect(state) { snapshotFlow { state.folded }.collect { savedFolded = it } }
    return state
}

/** Shows only the unfolded share of what it wraps, cut at its bottom; measures it for the drag. */
private fun Modifier.foldable(state: FoldState): Modifier = clipToBounds().layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0))
    state.body = p.height.toFloat().coerceAtLeast(1f)
    val h = (p.height * (1f - state.fold.value)).roundToInt().coerceAtLeast(0)
    layout(p.width, h) { p.place(0, 0) }
}

/** The page loading: a thin bar over its top edge, so the page never moves for it. */
@Composable
private fun LoadBar(search: PageSearch, modifier: Modifier) {
    AnimatedVisibility(
        visible = search.loading && search.progress < 100,
        enter = fadeIn(Motion.enter()), exit = fadeOut(Motion.exit()),
        modifier = modifier,
    ) {
        val smooth by animateFloatAsState(search.progress / 100f, tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "pageLoad")
        LinearProgressIndicator(progress = { smooth }, modifier = Modifier.fillMaxWidth().height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

/** The sheet's grip, as on the app's sheets. */
@Composable
private fun Handle() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
    }
}

/** The round 40dp mark a row starts with, on the ground the stream rows sit on. */
@Composable
private fun Mark(content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) { content() }
}

/**
 * One stream found, like a queue entry: a round mark with its kind (HLS, DASH, MP4), what the
 * stream says of itself ("42:10 · 1080p · ≈ 1.8 GB") as the line, "Forse è questo" on the likely
 * one, the site and the file under it, and a chevron: a tap takes it. Read once its row is on
 * screen; until then the line is the site, and when it cannot be read it says so, still tappable.
 */
@Composable
private fun StreamRow(
    s: StreamSource,
    briefs: MutableMap<String, StreamBrief?>,
    brief: suspend (StreamSource) -> StreamBrief?,
    likely: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LaunchedEffect(s.url) { if (s.url !in briefs) briefs[s.url] = brief(s) }
    val read = s.url in briefs
    val host = hostOf(s.url)
    val file = s.url.substringBefore('?').substringAfterLast('/')
    val told = briefs[s.url]?.let { b ->
        streamBrief(b.height, formatDuration(b.durationSec * 1000L).takeIf { b.durationSec > 0 }, b.bytes?.let(::formatBytes))
    }
    val unreadable = read && told == null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val maybe = str(R.string.dl_find_maybe)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark {
            Text(streamKind(s.url).orEmpty(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                color = if (unreadable) muted else primary, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                when { told != null -> told; unreadable -> str(R.string.dl_find_unreadable); else -> host },
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (unreadable) muted else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildAnnotatedString {
                    if (likely) {
                        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append(maybe) }
                        append(" · ")
                    }
                    append(if (told != null || unreadable) "$host · $file" else file)
                },
                style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(CriptaIcons.ChevronRight, null, tint = muted, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@').removePrefix("www.")
