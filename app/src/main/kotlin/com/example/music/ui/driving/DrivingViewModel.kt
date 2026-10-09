package com.example.music.ui.driving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.PlayerState
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.HandleVoiceCommandUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DrivingViewModel @Inject constructor(
    observePlayerState: ObservePlayerStateUseCase,
    private val controls: ControlPlaybackUseCase,
    private val handleVoice: HandleVoiceCommandUseCase,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private val _voiceStatus = MutableStateFlow<String?>(null)
    val voiceStatus: StateFlow<String?> = _voiceStatus.asStateFlow()

    fun togglePlayPause() = controls.togglePlayPause()
    fun skipNext() = controls.skipNext()
    fun skipPrevious() = controls.skipPrevious()

    fun onSpoken(text: String) {
        viewModelScope.launch {
            _voiceStatus.value = runCatching { handleVoice(text) }
                .getOrElse { it.message ?: "No se pudo" }
        }
    }

    fun setVoiceStatus(message: String) {
        _voiceStatus.value = message
    }
}
