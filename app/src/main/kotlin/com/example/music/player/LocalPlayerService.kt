package com.example.music.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.music.core.CrashGuard
import com.example.music.data.local_music.EmbeddedArtwork
import com.example.music.domain.model.Track
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPlayerService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _engineState = MutableStateFlow(EngineState())
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failed: SharedFlow<String> = _failed.asSharedFlow()

    private var positionJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                CrashGuard.run {
                    _engineState.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        startPlaybackService()
                        startPositionUpdates()
                    } else {
                        positionJob?.cancel()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                CrashGuard.run {
                    val duration = runCatching { exoPlayer.duration }.getOrDefault(0L).takeIf { it > 0 } ?: 0L
                    _engineState.update { it.copy(durationMs = duration) }
                    if (playbackState == Player.STATE_ENDED) {
                        _ended.tryEmit(Unit)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _engineState.update { it.copy(isPlaying = false) }
                _failed.tryEmit(error.message ?: "No se pudo reproducir")
            }
        })
    }

    fun play(track: Track) {
        val mediaUri = track.mediaUri.takeIf { it.isNotBlank() } ?: return
        val parsed = runCatching { Uri.parse(mediaUri) }.getOrNull() ?: return
        try {
            val artwork = track.artworkUri
                ?.takeIf { it.isNotBlank() && it != "0" && !it.endsWith("/albumart/0") }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(artwork)
                .build()
            val item = MediaItem.Builder()
                .setUri(parsed)
                .setMediaId(track.id)
                .setMimeType(track.mimeType)
                .setMediaMetadata(metadata)
                .build()
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.play()
            _engineState.update {
                it.copy(
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = track.durationMs,
                )
            }
            CrashGuard.run { startPlaybackService() }
            enrichArtwork(track)
        } catch (t: Throwable) {
            _engineState.update { it.copy(isPlaying = false) }
            _failed.tryEmit(t.message ?: "No se pudo reproducir")
        }
    }

    private fun enrichArtwork(track: Track) {
        scope.launch {
            val resolved = runCatching {
                EmbeddedArtwork.resolve(context, track.id, track.mediaUri, track.isVideo)
            }.getOrNull()
            val artUri = resolved?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val bytes = withContext(Dispatchers.IO) { artUri?.let { loadArtworkBytes(it) } }
            if (artUri == null && bytes == null) return@launch
            CrashGuard.run {
                val current = exoPlayer.currentMediaItem ?: return@run
                if (current.mediaId != track.id) return@run
                val index = exoPlayer.currentMediaItemIndex
                if (index < 0) return@run
                val meta = current.mediaMetadata.buildUpon()
                    .setArtworkUri(artUri ?: current.mediaMetadata.artworkUri)
                    .apply {
                        if (bytes != null) {
                            setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    .build()
                exoPlayer.replaceMediaItem(
                    index,
                    current.buildUpon().setMediaMetadata(meta).build(),
                )
            }
            CrashGuard.run { startPlaybackService() }
        }
    }

    private fun loadArtworkBytes(uri: Uri): ByteArray? {
        return runCatching {
            when (uri.scheme) {
                "file" -> uri.path?.let { path ->
                    File(path).takeIf { it.exists() && it.length() > 64L }?.readBytes()
                }
                else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }?.takeIf { it.size > 64 }
        }.getOrNull()
    }

    fun pause() {
        CrashGuard.run { exoPlayer.pause() }
    }

    fun resume() {
        CrashGuard.run { startPlaybackService() }
        CrashGuard.run { exoPlayer.play() }
    }

    fun seekTo(positionMs: Long) {
        CrashGuard.run {
            exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
            _engineState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        }
    }

    fun stop() {
        CrashGuard.run { exoPlayer.stop() }
        _engineState.value = EngineState()
    }

    fun release() {
        CrashGuard.run { positionJob?.cancel() }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                CrashGuard.run {
                    _engineState.update {
                        it.copy(
                            positionMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L),
                            durationMs = runCatching { exoPlayer.duration }.getOrDefault(0L).takeIf { d -> d > 0 }
                                ?: it.durationMs,
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
