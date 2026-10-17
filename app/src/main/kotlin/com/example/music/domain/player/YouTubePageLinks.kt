package com.example.music.domain.player

import java.net.URLEncoder

/**
 * URLs de la página oficial de YouTube que la búsqueda muestra dentro de la app.
 * No extrae resultados del HTML: solo arma el enlace y decide si el WebView puede seguir dentro.
 */
object YouTubePageLinks {
    fun resultsUrl(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""
        return "https://www.youtube.com/results?search_query=${encode(trimmed)}"
    }

    fun watchUrl(videoId: String): String {
        val id = videoId.trim()
        if (!VIDEO_ID.matches(id)) return ""
        return "https://www.youtube.com/watch?v=$id"
    }

    fun isWatch(rawUrl: String): Boolean {
        if (!allowInApp(rawUrl)) return false
        val url = rawUrl.lowercase()
        return url.contains("youtu.be/") ||
            url.contains("/watch") ||
            url.contains("/shorts/") ||
            url.contains("/live/")
    }

    fun videoIdOfMedia(mediaUri: String): String? {
        if (!mediaUri.startsWith("yt:") || mediaUri.startsWith("ytsearch:")) return null
        return mediaUri.removePrefix("yt:").takeIf { VIDEO_ID.matches(it) }
    }

    /** El WebView carga la página. Cualquier otro esquema o sitio se bloquea para no salir de la app. */
    fun allowInApp(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val schemeEnd = trimmed.indexOf(':')
        if (schemeEnd <= 0) return false
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        if (scheme != "https" && scheme != "http") return false
        val host = hostOf(trimmed) ?: return false
        return ALLOWED_HOSTS.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val hostPort = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.isEmpty()) return null
        val host = hostPort.substringAfter('@').substringBefore(':').lowercase()
        return host.takeIf { it.isNotEmpty() && !it.contains(' ') }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private val VIDEO_ID = Regex("^[\\w-]{11}$")

    private val ALLOWED_HOSTS = listOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "gstatic.com",
        "googleusercontent.com",
        "google.com",
        "googleapis.com",
    )
}
