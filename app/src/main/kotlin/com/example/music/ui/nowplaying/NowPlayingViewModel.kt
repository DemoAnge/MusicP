package com.example.music.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.SleepOption
import com.example.music.domain.model.SyncedLyrics
import com.example.music.domain.model.next
import com.example.music.domain.player.LyricsTimingOps
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.GetSyncedLyricsUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    observePlayerState: ObservePlayerStateUseCase,
    private val controls: ControlPlaybackUseCase,
    private val getSyncedLyrics: GetSyncedLyricsUseCase,
    private val observePrefs: ObserveLibraryPrefsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private val _lyrics = MutableStateFlow<SyncedLyrics?>(null)
    val lyrics: StateFlow<SyncedLyrics?> = _lyrics.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    val lyricsOffset: StateFlow<Long> = combine(
        playerState.map { it.currentTrack?.id },
        observePrefs.lyricsOffsets(),
    ) { id, map ->
        id?.let { map[it] } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val karaoke: StateFlow<Boolean> = observePrefs.karaoke().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val isFavorite: StateFlow<Boolean> = combine(
        playerState.map { it.currentTrack?.id },
        observePrefs.favorites(),
    ) { trackId, favorites ->
        trackId != null && trackId in favorites
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            playerState
                .map { it.currentTrack?.id to it.currentTrack }
                .distinctUntilChanged()
                .collect { (_, track) ->
                    if (track == null) {
                        _lyrics.value = null
                        _showLyrics.value = false
                    } else {
                        _lyrics.value = runCatching { getSyncedLyrics(track) }.getOrNull()
                    }
                }
        }
    }

    fun togglePlayPause() = controls.togglePlayPause()
    fun skipNext() = controls.skipNext()
    fun skipPrevious() = controls.skipPrevious()
    fun seekTo(positionMs: Long) = controls.seekTo(positionMs)
    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }
    fun toggleLiked() {
        val id = playerState.value.currentTrack?.id ?: return
        viewModelScope.launch { runCatching { toggleFavorite(id) } }
    }
    fun toggleShuffle() {
        controls.setShuffle(!playerState.value.isShuffleEnabled)
    }

    fun cycleRepeat() {
        controls.setRepeat(playerState.value.repeatMode.next())
    }

    fun playQueueIndex(index: Int) = controls.playQueueIndex(index)
    fun removeFromQueue(trackId: String) = controls.removeFromQueue(setOf(trackId))
    fun moveInQueue(fromIndex: Int, toIndex: Int) = controls.moveInQueue(fromIndex, toIndex)
    fun reopenWebBridge() = controls.reopenWebBridge()
    fun setSleepTimer(option: SleepOption) = controls.setSleepTimer(option)

    fun nudgeLyricsOffset(steps: Int) {
        val id = playerState.value.currentTrack?.id ?: return
        val next = LyricsTimingOps.nudge(lyricsOffset.value, steps)
        viewModelScope.launch { runCatching { observePrefs.setLyricsOffset(id, next) } }
    }

    fun toggleKaraoke() {
        val next = !karaoke.value
        viewModelScope.launch { runCatching { observePrefs.setKaraoke(next) } }
    }
}
