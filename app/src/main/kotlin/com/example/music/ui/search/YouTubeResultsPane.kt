package com.example.music.ui.search

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlayerState
import com.example.music.domain.player.YouTubePageLinks

/**
 * Una sola WebView para toda la actividad. Si se destruye al salir de Buscar, YouTube se corta.
 */
private object RetainedYouTubeWebView {
    private var view: WebView? = null
    private var loaded: String = ""
    var onPage: ((String) -> Unit)? = null

    fun obtain(context: android.content.Context): WebView {
        val existing = view
        if (existing != null) return existing
        return create(context).also { view = it }
    }

    fun load(webView: WebView, url: String) {
        if (url.isBlank() || url == loaded) return
        loaded = url
        webView.loadUrl(url)
    }

    fun destroy() {
        val web = view ?: return
        view = null
        loaded = ""
        onPage = null
        (web.parent as? ViewGroup)?.removeView(web)
        runCatching {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.onPause()
            web.removeAllViews()
            web.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(context: android.content.Context): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            val agent = settings.userAgentString
            if (agent.contains("; wv")) {
                settings.userAgentString = agent.replace("; wv", "")
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !YouTubePageLinks.allowInApp(request.url?.toString().orEmpty())
                }

                @Deprecated("Deprecated in API 24")
                override fun shouldOverrideUrlLoading(view: WebView, nextUrl: String): Boolean {
                    return !YouTubePageLinks.allowInApp(nextUrl)
                }

                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                    if (!finishedUrl.isNullOrBlank()) onPage?.invoke(finishedUrl)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    if (url.isNotBlank()) onPage?.invoke(url)
                }
            }
        }
    }
}

@Composable
fun YouTubePlaybackDock(
    url: String,
    expanded: Boolean,
    handleBack: Boolean,
    playerState: PlayerState,
    onPauseLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(url) {
        if (url.isBlank()) RetainedYouTubeWebView.destroy()
    }
    if (url.isBlank()) return

    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentPage by remember(url) { mutableStateOf(url) }
    val watch = YouTubePageLinks.isWatch(currentPage)
    val localPlaying = playerState.isPlaying && playerState.currentTrack?.source != PlaybackSource.WEB

    LaunchedEffect(currentPage) {
        if (watch) onPauseLocal()
    }

    BackHandler(enabled = handleBack && canGoBack) {
        webView?.goBack()
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(if (expanded) 280.dp else 96.dp),
        factory = { context ->
            RetainedYouTubeWebView.obtain(context).also { web ->
                (web.parent as? ViewGroup)?.removeView(web)
            }
        },
        update = { view ->
            webView = view
            RetainedYouTubeWebView.onPage = { page ->
                view.post {
                    if (page != currentPage) currentPage = page
                }
            }
            RetainedYouTubeWebView.load(view, url)
            if (watch && localPlaying) view.onPause() else view.onResume()
            canGoBack = view.canGoBack()
        },
        onRelease = { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        },
    )
}
