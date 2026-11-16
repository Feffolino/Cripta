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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PageVideoFinder(pageUrl: String, onPick: (StreamSource) -> Unit, onClose: () -> Unit) {
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(CriptaIcons.Close, str(R.string.close)) }
            Column(Modifier.weight(1f)) {
                Text(title ?: str(R.string.dl_find_in_page), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hostOf(pageUrl), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())

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

        if (found.isEmpty()) {
            Text(str(R.string.dl_find_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        } else {
            Text(str(R.string.dl_find_found), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                items(found, key = { it.url }) { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onPick(s.copy(title = title)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(CriptaIcons.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(streamKind(s.url).orEmpty() + " · " + hostOf(s.url), style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.url.substringBefore('?').substringAfterLast('/'), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@').removePrefix("www.")
