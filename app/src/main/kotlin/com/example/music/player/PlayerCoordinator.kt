package com.example.music.player

import com.example.music.core.CrashGuard
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class PlayerCoordinator @Inject constructor(
    private val localPlayer: LocalPlayerService,
) : IPlayerService {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, t ->
            CrashGuard.run { }
            android.util.Log.e("PlayerCoordinator", "coroutine", t)
        },
    )

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var originalQueue: List<Track> = emptyList()
    private var consecutiveFailures = 0

    init {
        localPlayer.engineState
            .onEach { engine ->
                _state.update {
                    it.copy(
                        isPlaying = engine.isPlaying,
                        positionMs = engine.positionMs,
                        durationMs = engine.durationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                    )
                }
            }
            .launchIn(scope)

        localPlayer.ended.onEach { CrashGuard.run { onEngineEnded() } }.launchIn(scope)
        localPlayer.failed.onEach { message ->
            consecutiveFailures += 1
            _state.update { it.copy(isPlaying = false, errorMessage = message) }
            if (consecutiveFailures < 3 && _state.value.queue.size > 1) {
                CrashGuard.run { skipNext() }
            }
        }.launchIn(scope)
    }

    override suspend fun play(track: Track, queue: List<Track>) {
        val resolvedQueue = if (queue.isEmpty()) listOf(track) else queue
        originalQueue = resolvedQueue
        val index = resolvedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val playQueue = if (_state.value.isShuffleEnabled) {
            listOf(resolvedQueue[index]) + resolvedQueue.filterIndexed { i, _ -> i != index }.shuffled()
        } else {
            resolvedQueue
        }
        val playIndex = playQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playInternal(playQueue[playIndex], playQueue, playIndex)
    }

    override fun pause() {
        CrashGuard.run { localPlayer.pause() }
        _state.update { it.copy(isPlaying = false) }
    }

    override fun resume() {
        if (_state.value.currentTrack == null) return
        CrashGuard.run { localPlayer.resume() }
        _state.update { it.copy(isPlaying = true, errorMessage = null) }
    }

    override fun togglePlayPause() {
        CrashGuard.run {
            if (_state.value.isPlaying) pause() else resume()
        }
    }

    override fun seekTo(positionMs: Long) {
        CrashGuard.run { localPlayer.seekTo(positionMs) }
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    override fun skipNext() {
        val snapshot = _state.value
        val queue = snapshot.queue
        if (queue.isEmpty()) return
        val nextIndex = if (snapshot.queueIndex + 1 < queue.size) snapshot.queueIndex + 1 else 0
        scope.launch { playInternal(queue[nextIndex], queue, nextIndex) }
    }

    override fun skipPrevious() {
        val snapshot = _state.value
        val queue = snapshot.queue
        if (queue.isEmpty()) return
        if (snapshot.positionMs > 3_000L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (snapshot.queueIndex > 0) snapshot.queueIndex - 1 else queue.lastIndex
        scope.launch { playInternal(queue[prevIndex], queue, prevIndex) }
    }

    override fun setShuffle(enabled: Boolean) {
        val current = _state.value.currentTrack
        val queue = if (enabled) {
            val rest = originalQueue.filter { it.id != current?.id }.shuffled()
            listOfNotNull(current) + rest
        } else {
            originalQueue
        }
        val index = current?.let { c -> queue.indexOfFirst { it.id == c.id } } ?: -1
        _state.update { it.copy(isShuffleEnabled = enabled, queue = queue, queueIndex = index) }
    }

    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    override fun removeFromQueue(trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        CrashGuard.run {
            val snapshot = _state.value
            originalQueue = originalQueue.filter { it.id !in trackIds }
            val remaining = snapshot.queue.filter { it.id !in trackIds }
            val currentId = snapshot.currentTrack?.id
            val currentDeleted = currentId != null && currentId in trackIds
            if (!currentDeleted) {
                val newIndex = remaining.indexOfFirst { it.id == currentId }
                _state.update { it.copy(queue = remaining, queueIndex = newIndex) }
                return@run
            }
            if (remaining.isEmpty()) {
                CrashGuard.run { localPlayer.stop() }
                _state.value = PlayerState(
                    isShuffleEnabled = snapshot.isShuffleEnabled,
                    repeatMode = snapshot.repeatMode,
                )
                return@run
            }
            val next = snapshot.queue.drop(snapshot.queueIndex + 1).firstOrNull { it.id !in trackIds }
                ?: snapshot.queue.take(snapshot.queueIndex).firstOrNull { it.id !in trackIds }
                ?: remaining.first()
            playInternal(next, remaining, remaining.indexOfFirst { it.id == next.id }.coerceAtLeast(0))
        }
    }

    override fun release() {
        // El ExoPlayer es singleton: soltarlo cortaría la música en segundo plano.
    }

    private fun playInternal(track: Track, queue: List<Track>, index: Int) {
        _state.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = index,
                isPlaying = true,
                positionMs = 0L,
                durationMs = track.durationMs,
                errorMessage = null,
            )
        }
        consecutiveFailures = 0
        try {
            localPlayer.play(track)
        } catch (t: Throwable) {
            _state.update { it.copy(isPlaying = false, errorMessage = t.message) }
        }
    }

    private fun onEngineEnded() {
        val snapshot = _state.value
        when (snapshot.repeatMode) {
            RepeatMode.ONE -> {
                val track = snapshot.currentTrack ?: return
                playInternal(track, snapshot.queue, snapshot.queueIndex)
            }
            RepeatMode.ALL -> skipNext()
            RepeatMode.OFF -> {
                if (snapshot.queueIndex >= snapshot.queue.lastIndex) {
                    pause()
                    seekTo(0L)
                } else {
                    skipNext()
                }
            }
        }
    }
}
