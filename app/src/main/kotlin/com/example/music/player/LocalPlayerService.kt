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
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
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
        .setPauseAtEndOfMediaItems(false)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _engineState = MutableStateFlow(EngineState())
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failed: SharedFlow<String> = _failed.asSharedFlow()

    private var positionJob: Job? = null
    private var queuedTracks: List<Track> = emptyList()

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
                    _engineState.update {
                        it.copy(
                            durationMs = duration,
                            mediaIndex = exoPlayer.currentMediaItemIndex,
                            mediaId = exoPlayer.currentMediaItem?.mediaId,
                        )
                    }
                    if (playbackState == Player.STATE_ENDED && !exoPlayer.hasNextMediaItem()) {
                        _ended.tryEmit(Unit)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                CrashGuard.run {
                    val index = exoPlayer.currentMediaItemIndex
                    _engineState.update {
                        it.copy(
                            mediaIndex = index,
                            mediaId = mediaItem?.mediaId,
                            positionMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L),
                            durationMs = runCatching { exoPlayer.duration }.getOrDefault(0L).takeIf { d -> d > 0 }
                                ?: queuedTracks.getOrNull(index)?.durationMs
                                ?: it.durationMs,
                        )
                    }
                    queuedTracks.getOrNull(index)?.let(::enrichArtwork)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _engineState.update { it.copy(isPlaying = false) }
                _failed.tryEmit(error.message ?: "No se pudo reproducir")
            }
        })
    }

    fun setQueue(
        tracks: List<Track>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        if (tracks.isEmpty()) {
            stop()
            return
        }
        queuedTracks = tracks
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        val items = tracks.map { it.toMediaItem() }
        try {
            exoPlayer.setMediaItems(items, index, startPositionMs.coerceAtLeast(0L))
            exoPlayer.prepare()
            if (playWhenReady) {
                exoPlayer.play()
                startPlaybackService()
            } else {
                exoPlayer.pause()
            }
            _engineState.update {
                it.copy(
                    isPlaying = playWhenReady,
                    positionMs = startPositionMs.coerceAtLeast(0L),
                    durationMs = tracks[index].durationMs,
                    mediaIndex = index,
                    mediaId = tracks[index].id,
                )
            }
            enrichArtwork(tracks[index])
        } catch (t: Throwable) {
            _engineState.update { it.copy(isPlaying = false) }
            _failed.tryEmit(t.message ?: "No se pudo reproducir")
        }
    }

    fun seekToIndex(index: Int, play: Boolean = true) {
        val count = exoPlayer.mediaItemCount
        if (count <= 0) return
        val safe = index.coerceIn(0, count - 1)
        CrashGuard.run {
            exoPlayer.seekTo(safe, 0L)
            if (play) {
                exoPlayer.play()
                startPlaybackService()
            }
            _engineState.update {
                it.copy(
                    mediaIndex = safe,
                    mediaId = exoPlayer.getMediaItemAt(safe).mediaId,
                    positionMs = 0L,
                    isPlaying = play || exoPlayer.isPlaying,
                    durationMs = queuedTracks.getOrNull(safe)?.durationMs ?: it.durationMs,
                )
            }
        }
    }

    fun addMediaItems(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(0, queuedTracks.size)
        queuedTracks = queuedTracks.toMutableList().apply { addAll(safeIndex, tracks) }
        CrashGuard.run { exoPlayer.addMediaItems(safeIndex, tracks.map { it.toMediaItem() }) }
    }

    fun removeMediaItem(index: Int) {
        if (index !in queuedTracks.indices) return
        queuedTracks = queuedTracks.toMutableList().apply { removeAt(index) }
        CrashGuard.run { exoPlayer.removeMediaItem(index) }
        if (queuedTracks.isEmpty()) stop()
    }

    fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queuedTracks.indices) return
        val target = toIndex.coerceIn(0, queuedTracks.lastIndex)
        if (fromIndex == target) return
        queuedTracks = queuedTracks.toMutableList().apply {
            add(target, removeAt(fromIndex))
        }
        CrashGuard.run { exoPlayer.moveMediaItem(fromIndex, target) }
        _engineState.update {
            it.copy(mediaIndex = exoPlayer.currentMediaItemIndex, mediaId = exoPlayer.currentMediaItem?.mediaId)
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        CrashGuard.run {
            exoPlayer.repeatMode = when (mode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
        }
    }

    fun canPlay(track: Track): Boolean {
        if (track.mediaUri.isBlank()) return false
        val uri = runCatching { Uri.parse(track.mediaUri) }.getOrNull() ?: return false
        return when (uri.scheme) {
            "file" -> uri.path?.let { path -> File(path).exists() } == true
            "content" -> runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L } == true
            }.getOrDefault(false)
            else -> true
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val parsed = runCatching { Uri.parse(mediaUri) }.getOrNull() ?: Uri.EMPTY
        val artwork = artworkUri
            ?.takeIf { it.isNotBlank() && it != "0" && !it.endsWith("/albumart/0") }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artwork)
            .build()
        return MediaItem.Builder()
            .setUri(parsed)
            .setMediaId(id)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
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
        queuedTracks = emptyList()
        CrashGuard.run { exoPlayer.stop() }
        CrashGuard.run { exoPlayer.clearMediaItems() }
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
                            mediaIndex = exoPlayer.currentMediaItemIndex,
                            mediaId = exoPlayer.currentMediaItem?.mediaId ?: it.mediaId,
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
