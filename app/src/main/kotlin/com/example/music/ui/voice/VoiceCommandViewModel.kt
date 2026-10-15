package com.example.music.ui.voice

import android.content.Context
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.VoiceCommandParser
import com.example.music.domain.model.VoiceResult
import com.example.music.domain.usecase.HandleVoiceCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
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
    private var listenAfterTts = false
    private var ttsReady = false
    private var tts: TextToSpeech? = TextToSpeech(appContext) { status ->
        val engineTts = tts ?: return@TextToSpeech
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return@TextToSpeech
        val spanish = Locale.forLanguageTag("es")
        engineTts.language = if (engineTts.isLanguageAvailable(spanish) >= TextToSpeech.LANG_AVAILABLE) {
            spanish
        } else {
            Locale.getDefault()
        }
        engineTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onTtsFinished()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onTtsFinished()
            }
            override fun onDone(utteranceId: String?) {
                onTtsFinished()
            }
        })
    }

    fun onMicTapped() {
        when (_ui.value.phase) {
            VoicePhase.Idle -> enterCommand("Te escucho")
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
        ttsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onCleared()
    }

    private fun enterCommand(prompt: String) {
        if (!engine.prepare()) {
            goIdle("El reconocimiento de voz no está disponible")
            return
        }
        consecutiveErrors = 0
        _ui.value = VoiceUiState(
            phase = VoicePhase.Command,
            listening = false,
            prompt = prompt,
        )
        startIdleTimer()
        if (!speakThenListen(prompt)) {
            _ui.value = _ui.value.copy(listening = true)
            engine.start()
        }
    }

    private fun goIdle(prompt: String) {
        idleJob?.cancel()
        idleJob = null
        consecutiveErrors = 0
        listenAfterTts = false
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
        val soft = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NO_MATCH
        if (!soft) consecutiveErrors += 1
        if (consecutiveErrors >= MAX_ERRORS) {
            goIdle("Voz desactivada")
            return
        }
        val delayMs = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> BUSY_MS
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> RESTART_MS
            else -> RESTART_MS
        }
        _ui.value = _ui.value.copy(listening = true, prompt = _ui.value.prompt.ifBlank { "Te escucho" })
        engine.scheduleRestart(delayMs)
    }

    private fun onTranscript(text: String) {
        if (_ui.value.phase == VoicePhase.Idle) return
        _ui.value = _ui.value.copy(listening = false)
        consecutiveErrors = 0
        val rest = VoiceCommandParser.stripWakePrefix(text).ifBlank {
            if (VoiceCommandParser.isWakeWord(text)) "" else text
        }
        if (rest.isBlank()) {
            _ui.value = _ui.value.copy(prompt = "Te escucho", listening = true)
            startIdleTimer()
            engine.scheduleRestart(RESTART_MS)
            return
        }
        viewModelScope.launch {
            val result = runCatching { handleVoice(rest) }
                .getOrElse { VoiceResult("No se pudo") }
            if (result.exit) {
                _events.tryEmit(VoiceEvent.Exit)
                goIdle("")
                return@launch
            }
            if (result.openQueue) {
                _openQueue.value = true
                _events.tryEmit(VoiceEvent.OpenQueue)
            }
            enterCommand(result.message)
        }
    }

    private fun speakThenListen(text: String): Boolean {
        if (!ttsReady || text.isBlank()) return false
        listenAfterTts = true
        val spoken = runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!spoken) {
            listenAfterTts = false
            return false
        }
        return true
    }

    private fun onTtsFinished() {
        if (!listenAfterTts) return
        listenAfterTts = false
        if (_ui.value.phase == VoicePhase.Idle) return
        _ui.value = _ui.value.copy(listening = true)
        engine.scheduleRestart(AFTER_TTS_MS)
    }

    private companion object {
        const val IDLE_MS = 15_000L
        const val RESTART_MS = 450L
        const val AFTER_TTS_MS = 250L
        const val BUSY_MS = 900L
        const val MAX_ERRORS = 12
        const val UTTERANCE = "voice-confirm"
    }
}
