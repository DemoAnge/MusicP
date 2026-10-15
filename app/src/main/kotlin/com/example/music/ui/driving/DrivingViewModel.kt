package com.example.music.ui.driving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.SyncedLyrics
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.GetSyncedLyricsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DrivingViewModel @Inject constructor(
    observePlayerState: ObservePlayerStateUseCase,
    private val controls: ControlPlaybackUseCase,
    private val getSyncedLyrics: GetSyncedLyricsUseCase,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _lyrics = MutableStateFlow<SyncedLyrics?>(null)
    val lyrics: StateFlow<SyncedLyrics?> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playerState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    _lyrics.value = if (track == null) {
                        null
                    } else {
                        runCatching { getSyncedLyrics(track) }.getOrNull()
                    }
                }
        }
    }

    fun togglePlayPause() = controls.togglePlayPause()
    fun skipNext() = controls.skipNext()
    fun skipPrevious() = controls.skipPrevious()
    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }
}
