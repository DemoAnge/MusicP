package com.example.music.player.web

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.music.core.CrashGuard
import com.example.music.domain.model.Track
import com.example.music.player.MusicPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class WebEngineState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val connected: Boolean = false,
    val needsGesture: Boolean = true,
    val title: String? = null,
    val artist: String? = null,
    val videoId: String? = null,
)

@Singleton
class BrowserTunnelPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _engineState = MutableStateFlow(WebEngineState())
    val engineState: StateFlow<WebEngineState> = _engineState.asStateFlow()

    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failed: SharedFlow<String> = _failed.asSharedFlow()

    private var server: LocalBridgeServer? = null
    private var token: String = ""
    private var lastTrack: Track? = null
    private var preferBrave: Boolean = true
    @Volatile private var lastClientAt: Long = 0L
    @Volatile private var watchdogRunning: Boolean = false

    fun setPreferBrave(value: Boolean) {
        preferBrave = value
    }

    fun play(track: Track, autoplay: Boolean = true, openBrowser: Boolean = autoplay) {
        lastTrack = track
        ensureServer()
        sendPlayCommand(track, autoplay)
        if (openBrowser) CrashGuard.run { openBridge() }
        if (autoplay || openBrowser) CrashGuard.run { startPlaybackService() }
        _engineState.update {
            it.copy(
                isPlaying = autoplay && it.connected && !it.needsGesture,
                title = track.title,
                artist = track.artist,
                videoId = videoIdOf(track),
            )
        }
    }

    fun pause() {
        send(JSONObject().put("type", "pause"))
        _engineState.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        val track = lastTrack
        if (track != null && !(_engineState.value.connected)) {
            play(track, autoplay = true)
            return
        }
        send(JSONObject().put("type", "play"))
        if (_engineState.value.needsGesture) {
            CrashGuard.run { openBridge() }
        }
    }

    fun seekTo(positionMs: Long) {
        send(JSONObject().put("type", "seek").put("positionMs", positionMs.coerceAtLeast(0L)))
        _engineState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        send(JSONObject().put("type", "rewind").put("deltaMs", deltaMs))
    }

    fun reopenBridge() {
        ensureServer()
        lastTrack?.let { sendPlayCommand(it, autoplay = true) }
        CrashGuard.run { openBridge() }
    }

    fun stop() {
        send(JSONObject().put("type", "pause"))
        lastTrack = null
        _engineState.value = WebEngineState()
    }

    private fun sendPlayCommand(track: Track, autoplay: Boolean) {
        val videoId = videoIdOf(track)
        if (videoId != null) {
            send(
                JSONObject()
                    .put("type", "load")
                    .put("videoId", videoId)
                    .put("autoplay", autoplay)
                    .put("title", track.title),
            )
        } else {
            send(
                JSONObject()
                    .put("type", "search")
                    .put("q", searchQueryOf(track))
                    .put("title", track.title),
            )
        }
    }

    private fun ensureServer() {
        if (server != null) return
        val html = runCatching {
            context.assets.open("web_bridge/index.html").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        token = UUID.randomUUID().toString()
        val created = LocalBridgeServer(html, token, ::onMessage)
        created.start()
        server = created
        startWatchdog()
    }

    private fun startWatchdog() {
        if (watchdogRunning) return
        watchdogRunning = true
        thread(name = "music-bridge-watch", isDaemon = true) {
            while (watchdogRunning && server != null) {
                Thread.sleep(2_000)
                val last = lastClientAt
                if (last > 0L && System.currentTimeMillis() - last > 6_000L) {
                    _engineState.update { it.copy(connected = false) }
                }
            }
            watchdogRunning = false
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    private fun openBridge() {
        val srv = server ?: return
        val url = "http://127.0.0.1:${srv.port}/?token=$token"
        BraveSupport.openBridge(context, url, preferBrave)
    }

    private fun send(json: JSONObject) {
        server?.send(json)
    }

    private fun onMessage(json: JSONObject) {
        lastClientAt = System.currentTimeMillis()
        when (json.optString("type")) {
            "hello" -> {
                _engineState.update { it.copy(connected = true, needsGesture = !json.optBoolean("armed", false)) }
                lastTrack?.let { sendPlayCommand(it, autoplay = true) }
            }
            "ended" -> _ended.tryEmit(Unit)
            "error" -> _failed.tryEmit(json.optString("message", "YouTube no pudo reproducir"))
            "state" -> {
                val srv = server
                _engineState.update {
                    it.copy(
                        connected = srv?.connected?.get() == true,
                        isPlaying = json.optBoolean("playing", false),
                        positionMs = json.optLong("positionMs", it.positionMs),
                        durationMs = json.optLong("durationMs", it.durationMs).takeIf { d -> d > 0 } ?: it.durationMs,
                        needsGesture = json.optBoolean("needsGesture", it.needsGesture),
                        title = json.optString("title").ifBlank { it.title },
                        artist = json.optString("artist").ifBlank { it.artist },
                        videoId = json.optString("videoId").ifBlank { it.videoId },
                    )
                }
            }
        }
    }

    companion object {
        fun videoIdOf(track: Track): String? {
            val raw = track.mediaUri
            if (raw.startsWith("yt:") && !raw.startsWith("ytsearch:")) {
                return raw.removePrefix("yt:").takeIf { it.length >= 8 }
            }
            return null
        }

        fun searchQueryOf(track: Track): String {
            val raw = track.mediaUri
            if (raw.startsWith("ytsearch:")) return raw.removePrefix("ytsearch:")
            return listOf(track.title, track.artist).filter { it.isNotBlank() && it != "YouTube" }.joinToString(" ")
        }
    }
}
