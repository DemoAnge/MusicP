package com.example.music.player

import com.example.music.core.CrashGuard
import com.example.music.domain.model.PlaybackSession
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track
import com.example.music.domain.repository.PlaybackSessionRepository
import com.example.music.domain.usecase.RecordRecentPlayUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class PlayerCoordinator @Inject constructor(
    private val localPlayer: LocalPlayerService,
    private val sessionRepository: PlaybackSessionRepository,
    private val recordRecentPlay: RecordRecentPlayUseCase,
) : IPlayerService {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, t ->
            CrashGuard.run { }
            android.util.Log.e("PlayerCoordinator", "coroutine", t)
        },
    )
    private val mutex = Mutex()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var originalQueue: List<Track> = emptyList()
    private var consecutiveFailures = 0
    private var lastRecordedId: String? = null
    @Volatile private var persistEnabled = false

    init {
        localPlayer.engineState
            .onEach { engine ->
                _state.update { snapshot ->
                    val queue = snapshot.queue
                    val idx = engine.mediaIndex
                    val fromEngine = queue.getOrNull(idx)?.takeIf { engine.mediaId == null || it.id == engine.mediaId }
                        ?: queue.firstOrNull { it.id == engine.mediaId }
                    snapshot.copy(
                        isPlaying = engine.isPlaying,
                        positionMs = engine.positionMs,
                        durationMs = engine.durationMs.takeIf { d -> d > 0 } ?: snapshot.durationMs,
                        queueIndex = when {
                            fromEngine != null -> queue.indexOfFirst { it.id == fromEngine.id }.coerceAtLeast(0)
                            else -> snapshot.queueIndex
                        },
                        currentTrack = fromEngine ?: snapshot.currentTrack,
                    )
                }
                _state.value.currentTrack?.id?.let(::maybeRecord)
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

        scope.launch {
            restoreSession()
            persistEnabled = true
            _state
                .map { sessionKey(it) }
                .distinctUntilChanged()
                .onEach { persist() }
                .launchIn(scope)
            while (isActive) {
                delay(5_000)
                if (_state.value.currentTrack != null) persist()
            }
        }
    }

    override suspend fun play(track: Track, queue: List<Track>) {
        mutex.withLock {
            val resolvedQueue = if (queue.isEmpty()) listOf(track) else queue
            originalQueue = resolvedQueue
            val index = resolvedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val playQueue = if (_state.value.isShuffleEnabled) {
                listOf(resolvedQueue[index]) + resolvedQueue.filterIndexed { i, _ -> i != index }.shuffled()
            } else {
                resolvedQueue
            }
            val playIndex = playQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            loadQueue(playQueue, playIndex, 0L, playWhenReady = true)
        }
    }

    override fun pause() {
        CrashGuard.run { localPlayer.pause() }
        _state.update { it.copy(isPlaying = false) }
        persist()
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
        CrashGuard.run { localPlayer.seekToIndex(nextIndex) }
        applyIndex(queue, nextIndex)
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
        CrashGuard.run { localPlayer.seekToIndex(prevIndex) }
        applyIndex(queue, prevIndex)
    }

    override fun setShuffle(enabled: Boolean) {
        val current = _state.value.currentTrack
        val position = _state.value.positionMs
        val playing = _state.value.isPlaying
        val queue = if (enabled) {
            val rest = originalQueue.filter { it.id != current?.id }.shuffled()
            listOfNotNull(current) + rest
        } else {
            originalQueue.ifEmpty { _state.value.queue }
        }
        val index = current?.let { c -> queue.indexOfFirst { it.id == c.id } }?.coerceAtLeast(0) ?: 0
        _state.update { it.copy(isShuffleEnabled = enabled) }
        if (queue.isNotEmpty()) {
            CrashGuard.run { localPlayer.setQueue(queue, index, position, playWhenReady = playing) }
            _state.update {
                it.copy(
                    queue = queue,
                    queueIndex = index,
                    currentTrack = queue.getOrNull(index) ?: current,
                    positionMs = position,
                )
            }
        } else {
            _state.update { it.copy(queue = queue, queueIndex = index) }
        }
    }

    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
        localPlayer.setRepeatMode(mode)
    }

    override fun playNext(track: Track) {
        if (track.isVideo) return
        scope.launch {
            mutex.withLock {
                val snapshot = _state.value
                if (snapshot.currentTrack == null || snapshot.queue.isEmpty()) {
                    originalQueue = listOf(track)
                    loadQueue(listOf(track), 0, 0L, playWhenReady = true)
                    return@withLock
                }
                val insertAt = (snapshot.queueIndex + 1).coerceIn(0, snapshot.queue.size)
                val without = snapshot.queue.filterNot { it.id == track.id }
                val currentId = snapshot.currentTrack.id
                val currentIndex = without.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                val nextIndex = currentIndex + 1
                val queue = without.toMutableList().apply {
                    add(nextIndex.coerceIn(0, size), track)
                }
                originalQueue = originalQueue.filterNot { it.id == track.id } + track
                val newCurrent = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                _state.update { it.copy(queue = queue, queueIndex = newCurrent) }
                CrashGuard.run {
                    if (snapshot.queue.any { it.id == track.id }) {
                        localPlayer.setQueue(queue, newCurrent, snapshot.positionMs, snapshot.isPlaying)
                    } else {
                        localPlayer.addMediaItems(listOf(track), insertAt)
                    }
                }
            }
        }
    }

    override fun addToQueue(track: Track) {
        if (track.isVideo) return
        scope.launch {
            mutex.withLock {
                val snapshot = _state.value
                if (snapshot.currentTrack == null || snapshot.queue.isEmpty()) {
                    originalQueue = listOf(track)
                    loadQueue(listOf(track), 0, 0L, playWhenReady = true)
                    return@withLock
                }
                if (snapshot.queue.any { it.id == track.id }) return@withLock
                val queue = snapshot.queue + track
                originalQueue = originalQueue.filterNot { it.id == track.id } + track
                _state.update { it.copy(queue = queue) }
                CrashGuard.run { localPlayer.addMediaItems(listOf(track), queue.lastIndex) }
            }
        }
    }

    override fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val snapshot = _state.value
        val queue = snapshot.queue.toMutableList()
        if (fromIndex !in queue.indices) return
        val target = toIndex.coerceIn(0, queue.lastIndex)
        if (fromIndex == target) return
        val item = queue.removeAt(fromIndex)
        queue.add(target, item)
        originalQueue = queue
        val currentId = snapshot.currentTrack?.id
        val newIndex = currentId?.let { id -> queue.indexOfFirst { it.id == id } } ?: snapshot.queueIndex
        CrashGuard.run { localPlayer.moveMediaItem(fromIndex, target) }
        _state.update { it.copy(queue = queue, queueIndex = newIndex.coerceAtLeast(0)) }
    }

    override fun playQueueIndex(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        CrashGuard.run { localPlayer.seekToIndex(index) }
        applyIndex(queue, index)
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
                val newIndex = remaining.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                _state.update { it.copy(queue = remaining, queueIndex = newIndex) }
                CrashGuard.run {
                    localPlayer.setQueue(remaining, newIndex, snapshot.positionMs, snapshot.isPlaying)
                }
                return@run
            }
            if (remaining.isEmpty()) {
                CrashGuard.run { localPlayer.stop() }
                _state.value = PlayerState(
                    isShuffleEnabled = snapshot.isShuffleEnabled,
                    repeatMode = snapshot.repeatMode,
                )
                scope.launch { sessionRepository.clear() }
                return@run
            }
            val next = snapshot.queue.drop(snapshot.queueIndex + 1).firstOrNull { it.id !in trackIds }
                ?: snapshot.queue.take(snapshot.queueIndex).firstOrNull { it.id !in trackIds }
                ?: remaining.first()
            val nextIndex = remaining.indexOfFirst { it.id == next.id }.coerceAtLeast(0)
            loadQueue(remaining, nextIndex, 0L, playWhenReady = snapshot.isPlaying)
        }
    }

    override fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun release() {
        persist()
    }

    private fun loadQueue(queue: List<Track>, index: Int, positionMs: Long, playWhenReady: Boolean) {
        val safeIndex = index.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
        val track = queue.getOrNull(safeIndex)
        _state.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = if (track == null) -1 else safeIndex,
                isPlaying = playWhenReady && track != null,
                positionMs = positionMs,
                durationMs = track?.durationMs ?: 0L,
                errorMessage = null,
            )
        }
        consecutiveFailures = 0
        if (queue.isEmpty() || track == null) {
            CrashGuard.run { localPlayer.stop() }
            return
        }
        localPlayer.setRepeatMode(_state.value.repeatMode)
        try {
            localPlayer.setQueue(queue, safeIndex, positionMs, playWhenReady)
        } catch (t: Throwable) {
            _state.update { it.copy(isPlaying = false, errorMessage = t.message) }
        }
        track.id.let(::maybeRecord)
    }

    private fun applyIndex(queue: List<Track>, index: Int) {
        val track = queue.getOrNull(index) ?: return
        consecutiveFailures = 0
        _state.update {
            it.copy(
                currentTrack = track,
                queueIndex = index,
                isPlaying = true,
                positionMs = 0L,
                durationMs = track.durationMs,
                errorMessage = null,
            )
        }
        maybeRecord(track.id)
    }

    private fun onEngineEnded() {
        val snapshot = _state.value
        when (snapshot.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                resume()
            }
            RepeatMode.ALL -> skipNext()
            RepeatMode.OFF -> {
                pause()
                seekTo(0L)
            }
        }
    }

    private fun maybeRecord(trackId: String) {
        if (trackId.isBlank() || trackId == lastRecordedId) return
        lastRecordedId = trackId
        scope.launch { runCatching { recordRecentPlay(trackId) } }
    }

    private fun sessionKey(state: PlayerState): String {
        return listOf(
            state.currentTrack?.id.orEmpty(),
            state.queueIndex.toString(),
            state.queue.joinToString(",") { it.id },
            state.isShuffleEnabled.toString(),
            state.repeatMode.name,
        ).joinToString("|")
    }

    private fun persist() {
        if (!persistEnabled) return
        val snapshot = _state.value
        scope.launch(Dispatchers.IO) {
            if (snapshot.queue.isEmpty() || snapshot.currentTrack == null) {
                sessionRepository.clear()
                return@launch
            }
            sessionRepository.save(
                PlaybackSession(
                    queue = snapshot.queue,
                    originalQueue = originalQueue.ifEmpty { snapshot.queue },
                    queueIndex = snapshot.queueIndex,
                    positionMs = snapshot.positionMs,
                    isShuffleEnabled = snapshot.isShuffleEnabled,
                    repeatMode = snapshot.repeatMode,
                ),
            )
        }
    }

    private suspend fun restoreSession() {
        val session = withContext(Dispatchers.IO) { sessionRepository.load() } ?: return
        mutex.withLock {
            if (_state.value.currentTrack != null) return@withLock
            val playable = session.queue.filter { localPlayer.canPlay(it) }
            if (playable.isEmpty()) {
                sessionRepository.clear()
                return@withLock
            }
            val original = session.originalQueue.filter { track -> playable.any { it.id == track.id } }
                .ifEmpty { playable }
            val wantedId = session.queue.getOrNull(session.queueIndex)?.id
            val index = playable.indexOfFirst { it.id == wantedId }.let { if (it >= 0) it else 0 }
            originalQueue = original
            _state.update {
                it.copy(
                    isShuffleEnabled = session.isShuffleEnabled,
                    repeatMode = session.repeatMode,
                )
            }
            loadQueue(playable, index, session.positionMs.coerceAtLeast(0L), playWhenReady = false)
        }
    }
}
