package com.example.music.player

import com.example.music.core.CrashGuard
import com.example.music.domain.library.TrackClassifier
import com.example.music.domain.library.TrackKind
import com.example.music.domain.model.LocalAudioPrefs
import com.example.music.domain.model.PlaybackSession
import com.example.music.domain.model.PlaybackUserMessage
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.SleepOption
import com.example.music.domain.model.Track
import com.example.music.domain.player.MixedPlaybackOps
import com.example.music.domain.player.PlaybackAudioOps
import com.example.music.domain.player.PlaybackQueueOps
import com.example.music.domain.player.WebBridgeOps
import com.example.music.domain.player.RemoveQueueMutation
import com.example.music.domain.player.RestorePlaybackSession
import com.example.music.domain.repository.LibraryPrefsRepository
import com.example.music.domain.repository.PlaybackSessionRepository
import com.example.music.domain.usecase.RecordRecentPlayUseCase
import com.example.music.player.web.BrowserTunnelPlayer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class PlayerCoordinator @Inject constructor(
    private val localPlayer: LocalPlayerService,
    private val webPlayer: BrowserTunnelPlayer,
    private val sessionRepository: PlaybackSessionRepository,
    private val libraryPrefs: LibraryPrefsRepository,
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
    private var sleepJob: Job? = null
    private val ready = CompletableDeferred<Unit>()
    private var audioPrefs = LocalAudioPrefs()
    private var lastNormalizedId: String? = null

    init {
        libraryPrefs.observePreferBrave()
            .onEach { webPlayer.setPreferBrave(it) }
            .launchIn(scope)
        libraryPrefs.observeLocalAudio()
            .onEach { prefs ->
                audioPrefs = prefs
                CrashGuard.run { localPlayer.setCrossfadeEnabled(prefs.crossfadeEnabled) }
                CrashGuard.run { localPlayer.setEqPreset(prefs.eqPreset) }
                lastNormalizedId = null
                applyLocalAudio(_state.value.currentTrack)
            }
            .launchIn(scope)

        localPlayer.engineState
            .onEach { engine ->
                if (isWeb(_state.value.currentTrack)) return@onEach
                val previousId = _state.value.currentTrack?.id
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
                val next = _state.value.currentTrack
                next?.id?.let(::maybeRecord)
                if (next?.id != previousId) applyLocalAudio(next)
            }
            .launchIn(scope)

        webPlayer.engineState
            .onEach { engine ->
                if (!isWeb(_state.value.currentTrack)) return@onEach
                _state.update { snapshot ->
                    snapshot.copy(
                        isPlaying = engine.isPlaying,
                        positionMs = engine.positionMs,
                        durationMs = engine.durationMs.takeIf { d -> d > 0 } ?: snapshot.durationMs,
                        webBridgeConnected = engine.connected,
                        webNeedsGesture = engine.needsGesture,
                        errorMessage = if (engine.connected &&
                            snapshot.errorMessage == PlaybackUserMessage.BRIDGE_DOWN
                        ) {
                            null
                        } else {
                            snapshot.errorMessage
                        },
                    )
                }
            }
            .launchIn(scope)

        localPlayer.ended.onEach {
            if (isWeb(_state.value.currentTrack)) return@onEach
            CrashGuard.run { onEngineEnded() }
        }.launchIn(scope)
        webPlayer.ended.onEach {
            if (!isWeb(_state.value.currentTrack)) return@onEach
            CrashGuard.run { onEngineEnded() }
        }.launchIn(scope)
        localPlayer.failed.onEach { message ->
            if (isWeb(_state.value.currentTrack)) return@onEach
            onEngineFailed(message)
        }.launchIn(scope)
        webPlayer.failed.onEach { message ->
            if (!isWeb(_state.value.currentTrack)) return@onEach
            onEngineFailed(message)
        }.launchIn(scope)

        scope.launch {
            runCatching { restoreSession() }
            persistEnabled = true
            if (!ready.isCompleted) ready.complete(Unit)
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
            val load = PlaybackQueueOps.play(track, queue, _state.value.isShuffleEnabled) ?: return@withLock
            originalQueue = load.originalQueue
            loadQueue(load.playQueue, load.playIndex, 0L, playWhenReady = true)
        }
    }

    override fun pause() {
        val web = isWeb(_state.value.currentTrack)
        CrashGuard.run {
            if (web) webPlayer.pause() else localPlayer.pause()
        }
        _state.update { it.copy(isPlaying = false) }
        persist()
    }

    override fun resume() {
        if (_state.value.currentTrack == null) return
        CrashGuard.run {
            if (isWeb(_state.value.currentTrack)) {
                webPlayer.resume()
            } else {
                localPlayer.resume()
            }
        }
        _state.update { it.copy(isPlaying = true, errorMessage = null) }
    }

    override fun togglePlayPause() {
        CrashGuard.run {
            if (_state.value.isPlaying) pause() else resume()
        }
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        CrashGuard.run {
            if (isWeb(_state.value.currentTrack)) {
                webPlayer.seekTo(clamped)
            } else {
                localPlayer.seekTo(clamped)
            }
        }
        _state.update { it.copy(positionMs = clamped) }
    }

    override fun seekBy(deltaMs: Long) {
        if (isWeb(_state.value.currentTrack)) {
            CrashGuard.run { webPlayer.seekBy(deltaMs) }
            return
        }
        val snapshot = _state.value
        val duration = snapshot.durationMs
        val ceiling = if (duration > 0L) duration else Long.MAX_VALUE
        seekTo((snapshot.positionMs + deltaMs).coerceIn(0L, ceiling))
    }

    override fun skipNext() {
        consecutiveFailures = 0
        skipNextInternal()
    }

    override fun skipPrevious() {
        consecutiveFailures = 0
        val snapshot = _state.value
        val queue = snapshot.queue
        if (queue.isEmpty()) return
        if (snapshot.positionMs > 3_000L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (snapshot.queueIndex > 0) snapshot.queueIndex - 1 else queue.lastIndex
        skipToIndex(queue, prevIndex)
    }

    suspend fun awaitReady() {
        ready.await()
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
            loadQueue(queue, index, position, playWhenReady = playing)
        } else {
            _state.update { it.copy(queue = queue, queueIndex = index) }
        }
    }

    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
        if (!isWeb(_state.value.currentTrack)) {
            localPlayer.setRepeatMode(mode)
        }
    }

    override fun playNext(track: Track) {
        playNext(listOf(track))
    }

    override fun playNext(tracks: List<Track>) {
        scope.launch {
            mutex.withLock {
                val snapshot = _state.value
                val mutation = PlaybackQueueOps.playNext(
                    tracks,
                    snapshot.queue,
                    originalQueue,
                    snapshot.currentTrack?.id,
                ) ?: return@withLock
                originalQueue = mutation.originalQueue
                if (mutation.startFresh) {
                    loadQueue(mutation.queue, 0, 0L, playWhenReady = true)
                    return@withLock
                }
                _state.update { it.copy(queue = mutation.queue, queueIndex = mutation.currentIndex) }
                if (isAllLocal(mutation.queue)) {
                    CrashGuard.run {
                        localPlayer.setQueue(
                            mutation.queue,
                            mutation.currentIndex,
                            snapshot.positionMs,
                            snapshot.isPlaying,
                        )
                    }
                }
            }
        }
    }

    override fun addToQueue(track: Track) {
        addToQueue(listOf(track))
    }

    override fun addToQueue(tracks: List<Track>) {
        scope.launch {
            mutex.withLock {
                val snapshot = _state.value
                val mutation = PlaybackQueueOps.add(
                    tracks,
                    snapshot.queue,
                    originalQueue,
                    currentEmpty = snapshot.currentTrack == null,
                ) ?: return@withLock
                originalQueue = mutation.originalQueue
                if (mutation.startFresh) {
                    loadQueue(mutation.queue, 0, 0L, playWhenReady = true)
                    return@withLock
                }
                _state.update { it.copy(queue = mutation.queue) }
                if (isAllLocal(mutation.queue)) {
                    CrashGuard.run { localPlayer.addMediaItems(mutation.added, mutation.appendAt) }
                }
            }
        }
    }

    override fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val snapshot = _state.value
        val mutation = PlaybackQueueOps.move(
            snapshot.queue,
            fromIndex,
            toIndex,
            snapshot.currentTrack?.id,
            snapshot.queueIndex,
            originalQueue = originalQueue,
            shuffleEnabled = snapshot.isShuffleEnabled,
        ) ?: return
        originalQueue = mutation.originalQueue
        _state.update { it.copy(queue = mutation.queue, queueIndex = mutation.currentIndex) }
        if (isAllLocal(mutation.queue)) {
            CrashGuard.run { localPlayer.moveMediaItem(mutation.fromIndex, mutation.toIndex) }
        }
    }

    override fun playQueueIndex(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        skipToIndex(queue, index)
    }

    override fun removeFromQueue(trackIds: Set<String>) {
        CrashGuard.run {
            val snapshot = _state.value
            when (
                val mutation = PlaybackQueueOps.remove(
                    trackIds,
                    snapshot.queue,
                    originalQueue,
                    snapshot.currentTrack?.id,
                    snapshot.queueIndex,
                )
            ) {
                RemoveQueueMutation.NoOp -> Unit
                is RemoveQueueMutation.KeptPlaying -> {
                    originalQueue = mutation.originalQueue
                    _state.update { it.copy(queue = mutation.remaining, queueIndex = mutation.newIndex) }
                    if (isAllLocal(mutation.remaining) && !isWeb(snapshot.currentTrack)) {
                        CrashGuard.run {
                            localPlayer.setQueue(
                                mutation.remaining,
                                mutation.newIndex,
                                snapshot.positionMs,
                                snapshot.isPlaying,
                            )
                        }
                    }
                }
                RemoveQueueMutation.Cleared -> {
                    originalQueue = emptyList()
                    CrashGuard.run {
                        localPlayer.stop()
                        webPlayer.stop()
                    }
                    _state.value = PlayerState(
                        isShuffleEnabled = snapshot.isShuffleEnabled,
                        repeatMode = snapshot.repeatMode,
                        playbackSpeed = snapshot.playbackSpeed,
                    )
                    scope.launch { sessionRepository.clear() }
                }
                is RemoveQueueMutation.JumpTo -> {
                    originalQueue = mutation.originalQueue
                    loadQueue(mutation.remaining, mutation.nextIndex, 0L, playWhenReady = snapshot.isPlaying)
                }
            }
        }
    }

    override fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun reopenWebBridge() {
        CrashGuard.run { webPlayer.reopenBridge() }
    }

    override fun setPlaybackSpeed(speed: Float) {
        applySpeed(PlaybackAudioOps.clampSpeed(speed))
    }

    override fun setSleepTimer(option: SleepOption) {
        sleepJob?.cancel()
        sleepJob = null
        when (option) {
            SleepOption.OFF -> {
                applyPauseAtEndOfTrack(false)
                _state.update { it.copy(sleepEndsAtEpochMs = 0L, sleepAtEndOfTrack = false) }
            }
            SleepOption.END_OF_TRACK -> {
                _state.update { it.copy(sleepEndsAtEpochMs = 0L, sleepAtEndOfTrack = true) }
                applyPauseAtEndOfTrack(!isWeb(_state.value.currentTrack))
            }
            else -> {
                applyPauseAtEndOfTrack(false)
                val delayMs = option.minutes * 60_000L
                val endsAt = System.currentTimeMillis() + delayMs
                _state.update { it.copy(sleepEndsAtEpochMs = endsAt, sleepAtEndOfTrack = false) }
                sleepJob = scope.launch {
                    delay(delayMs)
                    CrashGuard.run { pause() }
                    _state.update { it.copy(sleepEndsAtEpochMs = 0L, sleepAtEndOfTrack = false) }
                }
            }
        }
    }

    override fun persistSession() {
        persist()
    }

    fun persistSessionBlocking() {
        persistEnabled = true
        val session = sessionToSave()
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(2_000) {
                if (session == null) sessionRepository.clear()
                else sessionRepository.save(session)
            }
        }
    }

    override fun release() {
        sleepJob?.cancel()
        sleepJob = null
        persistSessionBlocking()
    }

    private fun skipNextInternal() {
        val snapshot = _state.value
        val queue = snapshot.queue
        if (queue.isEmpty()) return
        val nextIndex = if (snapshot.queueIndex + 1 < queue.size) snapshot.queueIndex + 1 else 0
        skipToIndex(queue, nextIndex)
    }

    private fun skipToIndex(queue: List<Track>, index: Int) {
        val track = queue.getOrNull(index) ?: return
        if (isAllLocal(queue) && !isWeb(track) && !isWeb(_state.value.currentTrack)) {
            CrashGuard.run { localPlayer.seekToIndex(index) }
            applyIndex(queue, index)
            return
        }
        loadQueue(queue, index, 0L, playWhenReady = true)
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
                webBridgeConnected = if (isWeb(track)) it.webBridgeConnected else false,
                webNeedsGesture = isWeb(track),
            )
        }
        consecutiveFailures = 0
        if (queue.isEmpty() || track == null) {
            CrashGuard.run {
                localPlayer.stop()
                webPlayer.stop()
            }
            return
        }
        try {
            if (isWeb(track)) {
                CrashGuard.run { localPlayer.pause() }
                applyPauseAtEndOfTrack(false)
                webPlayer.play(track, autoplay = playWhenReady, openBrowser = playWhenReady)
            } else {
                CrashGuard.run { webPlayer.pause() }
                CrashGuard.run { localPlayer.setRepeatMode(_state.value.repeatMode) }
                applyLocalAudio(track)
                applyPauseAtEndOfTrack(_state.value.sleepAtEndOfTrack)
                CrashGuard.run {
                    if (isAllLocal(queue)) {
                        localPlayer.setQueue(queue, safeIndex, positionMs, playWhenReady)
                    } else {
                        localPlayer.setQueue(listOf(track), 0, positionMs, playWhenReady)
                    }
                }
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(isPlaying = false, errorMessage = PlaybackUserMessage.fromThrowable(t))
            }
        }
        track.id.let(::maybeRecord)
    }

    private fun applyIndex(queue: List<Track>, index: Int) {
        val track = queue.getOrNull(index) ?: return
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
        if (snapshot.sleepAtEndOfTrack) {
            pause()
            sleepJob?.cancel()
            sleepJob = null
            applyPauseAtEndOfTrack(false)
            _state.update { it.copy(sleepEndsAtEpochMs = 0L, sleepAtEndOfTrack = false) }
            return
        }
        when (snapshot.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                resume()
            }
            RepeatMode.ALL -> skipNextInternal()
            RepeatMode.OFF -> {
                pause()
                seekTo(0L)
            }
        }
    }

    private fun onEngineFailed(message: String) {
        consecutiveFailures += 1
        val mapped = if (isWeb(_state.value.currentTrack)) {
            PlaybackUserMessage.web(message)
        } else {
            message.ifBlank { PlaybackUserMessage.GENERIC }
        }
        _state.update { it.copy(isPlaying = false, errorMessage = mapped) }
        if (!WebBridgeOps.shouldAdvanceQueueOnFailure(mapped)) {
            consecutiveFailures = 0
            return
        }
        if (consecutiveFailures < 3 && _state.value.queue.size > 1) {
            CrashGuard.run { skipNextInternal() }
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
        val session = sessionToSave()
        scope.launch(Dispatchers.IO) {
            if (session == null) sessionRepository.clear()
            else sessionRepository.save(session)
        }
    }

    private fun sessionToSave(): PlaybackSession? {
        val snapshot = _state.value
        if (snapshot.queue.isEmpty() || snapshot.currentTrack == null) return null
        val enginePosition = if (isWeb(snapshot.currentTrack)) {
            snapshot.positionMs
        } else {
            localPlayer.engineState.value.positionMs
        }
        return PlaybackSession(
            queue = snapshot.queue,
            originalQueue = originalQueue.ifEmpty { snapshot.queue },
            queueIndex = snapshot.queueIndex,
            positionMs = enginePosition.coerceAtLeast(0L),
            isShuffleEnabled = snapshot.isShuffleEnabled,
            repeatMode = snapshot.repeatMode,
        )
    }

    private suspend fun restoreSession() {
        val session = withContext(Dispatchers.IO) { sessionRepository.load() } ?: return
        val restored = withContext(Dispatchers.IO) {
            RestorePlaybackSession.restore(session) { isWeb(it) || localPlayer.canPlay(it) }
        }
        mutex.withLock {
            if (_state.value.currentTrack != null) return@withLock
            if (restored == null) {
                sessionRepository.clear()
                return@withLock
            }
            originalQueue = restored.original
            _state.update {
                it.copy(
                    isShuffleEnabled = restored.isShuffleEnabled,
                    repeatMode = restored.repeatMode,
                )
            }
            loadQueue(restored.playable, restored.index, restored.positionMs, playWhenReady = false)
        }
    }

    private fun applyPauseAtEndOfTrack(enabled: Boolean) {
        CrashGuard.run { localPlayer.setPauseAtEndOfMediaItems(enabled) }
    }

    private fun applyLocalAudio(track: Track?) {
        val kind = track?.let { TrackClassifier.kind(it) } ?: TrackKind.MUSIC
        val speed = PlaybackAudioOps.speedFor(kind, audioPrefs.musicSpeed, audioPrefs.spokenSpeed)
        applySpeed(speed)
        if (track == null || isWeb(track)) {
            lastNormalizedId = null
            CrashGuard.run { localPlayer.applyNormalization(null, false) }
            return
        }
        if (lastNormalizedId == track.id) return
        lastNormalizedId = track.id
        CrashGuard.run { localPlayer.applyNormalization(track, audioPrefs.normalizeVolume) }
    }

    private fun applySpeed(speed: Float) {
        val clamped = PlaybackAudioOps.clampSpeed(speed)
        _state.update { it.copy(playbackSpeed = clamped) }
        if (!isWeb(_state.value.currentTrack)) {
            CrashGuard.run { localPlayer.setSpeed(clamped) }
        }
    }

    private fun isWeb(track: Track?): Boolean = MixedPlaybackOps.isWeb(track)

    private fun isAllLocal(queue: List<Track>): Boolean =
        MixedPlaybackOps.usesExoPlaylist(queue)
}
