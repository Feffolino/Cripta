package com.cripta.app.ui.download

import android.annotation.SuppressLint
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.cripta.app.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cripta.app.R
import com.cripta.app.data.StreamSource
import com.cripta.app.ui.theme.CriptaIcons
import com.cripta.app.str

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

/** The most streams listed for a page: one that streams in MP4 pieces asks for hundreds. */
private const val MAX_STREAMS = 30

/**
 * The page [pageUrl] opened inside the app, to find the video the downloader could not: many
 * sites put the stream's address (an HLS playlist) nowhere in the page and ask for it from the
 * player, with a token, a referer or cookies. Every request the page makes is watched; the ones
 * for a video are listed under it, and the one tapped goes back with what the site asked to
 * serve it ([onPick]).
 *
 * The page runs its own scripts, so it gets nothing of the app: no access to files, no other
 * windows, only web links followed. Cookies, cache and storage are wiped when it closes.
 *
 * A page pushed over Download, under its top bar: the web page fills it, and what is found sits
 * under it like the queue's entries (a heading, plain rows, no frame).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PageVideoFinder(pageUrl: String, modifier: Modifier = Modifier, onPick: (StreamSource) -> Unit, onClose: () -> Unit) {
    val found = remember { mutableStateListOf<StreamSource>() }
    var title by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }

    BackHandler { web?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }
    DisposableEffect(Unit) {
        onDispose {
            web?.run { stopLoading(); loadUrl("about:blank"); clearHistory(); clearCache(true); clearFormData(); destroy() }
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    Column(modifier.fillMaxSize()) {
        // The page loading: the queue's bar (thick, round ends), gone once it is in.
        AnimatedVisibility(
            visible = progress in 1..99,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            val smooth by animateFloatAsState(progress / 100f, tween(Motion.LONG, 0, Motion.EaseOutQuart), label = "pageLoad")
            LinearProgressIndicator(progress = { smooth },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // The player starts on its own where the site allows it: the stream shows up sooner.
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setGeolocationEnabled(false)
                    // Players embedded from another site keep their session in cookies of their own.
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    val agent = settings.userAgentString
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        override fun onReceivedTitle(view: WebView, t: String?) { title = t?.takeIf { it.isNotBlank() } }
                    }
                    webViewClient = object : WebViewClient() {
                        // Only web links: a page cannot open another app ("intent:", "market:") from here.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.url.scheme?.lowercase() !in setOf("http", "https")

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
                    loadUrl(pageUrl)
                    web = this
                }
            },
        )

        // What the page loaded: a hint until something shows up, then the streams to pick from.
        AnimatedContent(
            targetState = found.isEmpty(),
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "foundStreams",
        ) { none ->
            if (none) {
                Text(str(R.string.dl_find_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Heading(str(R.string.dl_find_found), Modifier.padding(horizontal = 16.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 208.dp)) {
                        items(found, key = { it.url }) { s -> StreamRow(s, Modifier.animateItem()) { onPick(s.copy(title = title)) } }
                    }
                }
            }
        }
    }
}

/** One stream found, like a queue entry: a round mark, what it is and where from, its file under it. */
@Composable
private fun StreamRow(s: StreamSource, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center) {
            Icon(CriptaIcons.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(streamKind(s.url).orEmpty() + " · " + hostOf(s.url), style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.url.substringBefore('?').substringAfterLast('/'), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@').removePrefix("www.")
