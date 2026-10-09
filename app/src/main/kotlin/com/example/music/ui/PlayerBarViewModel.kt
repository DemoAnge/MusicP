package com.example.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.PlayerState
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlayerBarViewModel @Inject constructor(
    observePlayerState: ObservePlayerStateUseCase,
    private val controls: ControlPlaybackUseCase,
) : ViewModel() {
    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    fun togglePlayPause() = controls.togglePlayPause()
    fun pause() = controls.pause()
    fun skipNext() = controls.skipNext()
    fun skipPrevious() = controls.skipPrevious()
    fun seekTo(positionMs: Long) = controls.seekTo(positionMs)
    fun clearError() = controls.clearError()
}
