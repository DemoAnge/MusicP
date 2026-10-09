package com.example.music.ui.voice

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.VoiceCommandParser
import com.example.music.domain.model.VoiceResult
import com.example.music.domain.usecase.HandleVoiceCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoicePhase { Idle, Wake, Command }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val listening: Boolean = false,
    val prompt: String = "",
)

sealed interface VoiceEvent {
    data object OpenQueue : VoiceEvent
    data object Exit : VoiceEvent
}

@HiltViewModel
class VoiceCommandViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val handleVoice: HandleVoiceCommandUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(VoiceUiState())
    val ui: StateFlow<VoiceUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    private val _openQueue = MutableStateFlow(false)
    val openQueue: StateFlow<Boolean> = _openQueue.asStateFlow()

    private val engine = VoiceListenEngine(
        appContext = appContext,
        onText = ::onTranscript,
        onError = ::onListenError,
    )
    private var idleJob: Job? = null
    private var consecutiveErrors = 0

    fun onMicTapped() {
        when (_ui.value.phase) {
            VoicePhase.Idle -> enterWake()
            VoicePhase.Wake, VoicePhase.Command -> goIdle("Voz desactivada")
        }
    }

    fun onPermissionDenied() {
        goIdle("Sin permiso de micrófono")
    }

    fun consumeOpenQueue() {
        _openQueue.value = false
    }

    override fun onCleared() {
        goIdle(prompt = "")
        super.onCleared()
    }

    private fun enterWake() {
        if (!engine.prepare()) {
            goIdle("El reconocimiento de voz no está disponible")
            return
        }
        consecutiveErrors = 0
        _ui.value = VoiceUiState(
            phase = VoicePhase.Wake,
            listening = true,
            prompt = "Di «música» para activar",
        )
        startIdleTimer()
        engine.start()
    }

    private fun enterCommand(prompt: String) {
        consecutiveErrors = 0
        _ui.value = VoiceUiState(
            phase = VoicePhase.Command,
            listening = false,
            prompt = prompt,
        )
        startIdleTimer()
        engine.scheduleRestart(RESTART_MS)
    }

    private fun goIdle(prompt: String) {
        idleJob?.cancel()
        idleJob = null
        consecutiveErrors = 0
        engine.destroy()
        _ui.value = VoiceUiState(phase = VoicePhase.Idle, listening = false, prompt = prompt)
        if (prompt.isNotBlank()) {
            viewModelScope.launch {
                delay(2_000)
                if (_ui.value.phase == VoicePhase.Idle) {
                    _ui.value = VoiceUiState()
                }
            }
        }
    }

    private fun startIdleTimer() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(IDLE_MS)
            goIdle("Voz desactivada")
        }
    }

    private fun onListenError(error: Int) {
        if (_ui.value.phase == VoicePhase.Idle) return
        _ui.value = _ui.value.copy(listening = false)
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            goIdle("Sin permiso de micrófono")
            return
        }
        consecutiveErrors += 1
        if (consecutiveErrors >= MAX_ERRORS) {
            goIdle("Voz desactivada")
            return
        }
        val delayMs = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> BUSY_MS
            else -> RESTART_MS
        }
        engine.scheduleRestart(delayMs)
    }

    private fun onTranscript(text: String) {
        if (_ui.value.phase == VoicePhase.Idle) return
        _ui.value = _ui.value.copy(listening = false)
        consecutiveErrors = 0
        when (_ui.value.phase) {
            VoicePhase.Idle -> Unit
            VoicePhase.Wake -> {
                if (VoiceCommandParser.isWakeWord(text)) {
                    enterCommand("Activada. Te escucho")
                } else {
                    _ui.value = _ui.value.copy(prompt = "Di «música» para activar")
                    engine.scheduleRestart(RESTART_MS)
                }
            }
            VoicePhase.Command -> {
                if (text.isBlank()) {
                    engine.scheduleRestart(RESTART_MS)
                    return
                }
                val result = runCatching { handleVoice(text) }
                    .getOrElse { VoiceResult("No se pudo") }
                if (result.exit) {
                    _events.tryEmit(VoiceEvent.Exit)
                    goIdle("")
                    return
                }
                if (result.openQueue) {
                    _openQueue.value = true
                    _events.tryEmit(VoiceEvent.OpenQueue)
                }
                enterCommand(result.message)
            }
        }
    }

    private companion object {
        const val IDLE_MS = 15_000L
        const val RESTART_MS = 450L
        const val BUSY_MS = 900L
        const val MAX_ERRORS = 6
    }
}
