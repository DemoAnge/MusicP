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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.music.core.CrashGuard
import com.example.music.data.local_music.EmbeddedArtwork
import com.example.music.domain.model.EqPreset
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlaybackUserMessage
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track
import com.example.music.domain.player.PlaybackAudioOps
import com.example.music.player.audio.LocalAudioEffects
import com.example.music.player.audio.ReplayGainAudioProcessor
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
    private val replayGainProcessor = ReplayGainAudioProcessor()
    private val audioEffects = LocalAudioEffects()
    @Volatile private var crossfadeEnabled = false
    @Volatile private var fadingOut = false
    @Volatile private var normalizeRequestId: String? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(replayGainProcessor),
                        )
                        .build()
                }
            },
        )
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
                    if (playbackState == Player.STATE_ENDED &&
                        (!exoPlayer.hasNextMediaItem() || exoPlayer.pauseAtEndOfMediaItems)
                    ) {
                        _ended.tryEmit(Unit)
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                CrashGuard.run { audioEffects.setSession(audioSessionId) }
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
                    onTrackFadedIn()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _engineState.update { it.copy(isPlaying = false) }
                _failed.tryEmit(PlaybackUserMessage.local(error.errorCode, error.message))
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
            CrashGuard.run { audioEffects.setSession(exoPlayer.audioSessionId) }
            CrashGuard.run { exoPlayer.volume = 1f }
            fadingOut = false
        } catch (t: Throwable) {
            _engineState.update { it.copy(isPlaying = false) }
            _failed.tryEmit(PlaybackUserMessage.fromThrowable(t))
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

    fun setSpeed(speed: Float) {
        val clamped = PlaybackAudioOps.clampSpeed(speed)
        CrashGuard.run {
            exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(clamped)
        }
    }

    fun applyNormalization(track: Track?, enabled: Boolean) {
        if (!enabled || track == null || track.mediaUri.isBlank()) {
            normalizeRequestId = null
            replayGainProcessor.linearGain = 1f
            return
        }
        val id = track.id
        val uri = track.mediaUri
        normalizeRequestId = id
        scope.launch(Dispatchers.IO) {
            val db = com.example.music.data.local_music.ReplayGainReader.readDb(context, uri) ?: 0f
            if (normalizeRequestId == id) {
                replayGainProcessor.linearGain = PlaybackAudioOps.linearGainFromDb(db)
            }
        }
    }

    fun setEqPreset(preset: EqPreset) {
        audioEffects.setPreset(preset)
        CrashGuard.run { audioEffects.setSession(exoPlayer.audioSessionId) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        crossfadeEnabled = enabled
        if (!enabled) {
            fadingOut = false
            CrashGuard.run { exoPlayer.volume = 1f }
        }
    }

    fun setPauseAtEndOfMediaItems(pause: Boolean) {
        CrashGuard.run { exoPlayer.pauseAtEndOfMediaItems = pause }
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
        if (track.source == PlaybackSource.WEB) return false
        if (track.mediaUri.isBlank()) return false
        if (track.mediaUri.startsWith("yt:") || track.mediaUri.startsWith("ytsearch:")) return false
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
        audioEffects.release()
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                CrashGuard.run {
                    val position = runCatching { exoPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
                    val duration = runCatching { exoPlayer.duration }.getOrDefault(0L).takeIf { d -> d > 0 }
                        ?: _engineState.value.durationMs
                    _engineState.update {
                        it.copy(
                            positionMs = position,
                            durationMs = duration,
                            mediaIndex = exoPlayer.currentMediaItemIndex,
                            mediaId = exoPlayer.currentMediaItem?.mediaId ?: it.mediaId,
                        )
                    }
                    applyCrossfade(position, duration)
                }
                delay(200)
            }
        }
    }

    private fun applyCrossfade(positionMs: Long, durationMs: Long) {
        if (!crossfadeEnabled || exoPlayer.pauseAtEndOfMediaItems) {
            if (exoPlayer.volume != 1f) CrashGuard.run { exoPlayer.volume = 1f }
            return
        }
        if (durationMs <= PlaybackAudioOps.CROSSFADE_MS * 2L) return
        if (!exoPlayer.hasNextMediaItem() && exoPlayer.repeatMode == Player.REPEAT_MODE_OFF) return
        val remaining = durationMs - positionMs
        if (remaining in 0..PlaybackAudioOps.CROSSFADE_MS) {
            fadingOut = true
            val volume = (remaining.toFloat() / PlaybackAudioOps.CROSSFADE_MS).coerceIn(0.12f, 1f)
            CrashGuard.run { exoPlayer.volume = volume }
        }
    }

    private fun onTrackFadedIn() {
        if (!crossfadeEnabled || !fadingOut) {
            CrashGuard.run { exoPlayer.volume = 1f }
            fadingOut = false
            return
        }
        fadingOut = false
        CrashGuard.run { exoPlayer.volume = 0.12f }
        scope.launch {
            val steps = 8
            val stepMs = PlaybackAudioOps.CROSSFADE_MS / steps
            for (i in 1..steps) {
                if (!crossfadeEnabled) break
                CrashGuard.run { exoPlayer.volume = (i.toFloat() / steps).coerceIn(0.12f, 1f) }
                delay(stepMs.toLong())
            }
            if (crossfadeEnabled) CrashGuard.run { exoPlayer.volume = 1f }
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
