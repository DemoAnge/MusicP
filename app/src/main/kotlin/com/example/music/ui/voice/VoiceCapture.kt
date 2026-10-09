package com.example.music.ui.voice

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.music.core.theme.Accent
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.SurfaceElevated
import java.util.Locale

class VoiceCapture(
    val listening: Boolean,
    val available: Boolean,
    val start: () -> Unit,
)

@Composable
fun rememberVoiceCapture(
    onTranscript: (String) -> Unit,
    onStatus: (String) -> Unit,
): VoiceCapture {
    val appContext = LocalContext.current.applicationContext
    val activityContext = LocalContext.current
    val onTranscriptState = rememberUpdatedState(onTranscript)
    val onStatusState = rememberUpdatedState(onStatus)
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        val created = runCatching {
            when {
                Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) ->
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                SpeechRecognizer.isRecognitionAvailable(appContext) ->
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                else -> null
            }
        }.getOrNull()
        created?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                mainHandler.post { listening = true }
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onEndOfSpeech() {
                mainHandler.post { listening = false }
            }

            override fun onError(error: Int) {
                mainHandler.post {
                    listening = false
                    onStatusState.value(errorMessage(error))
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                mainHandler.post {
                    listening = false
                    if (text.isEmpty()) {
                        onStatusState.value("No escuché nada")
                    } else {
                        onTranscriptState.value(text)
                    }
                }
            }
        })
        recognizer = created
        onDispose {
            runCatching { created?.cancel() }
            runCatching { created?.destroy() }
            recognizer = null
            listening = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startListening(recognizer, onStatusState.value) { listening = it }
        } else {
            onStatusState.value("Sin permiso de micrófono")
        }
    }

    val start = {
        val granted = ContextCompat.checkSelfPermission(
            activityContext,
            Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            recognizer == null -> onStatusState.value("El reconocimiento de voz no está disponible")
            !granted -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> startListening(recognizer, onStatusState.value) { listening = it }
        }
    }

    return VoiceCapture(
        listening = listening,
        available = recognizer != null,
        start = start,
    )
}

@Composable
fun VoiceMicButton(
    capture: VoiceCapture,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 28.dp,
) {
    IconButton(
        onClick = capture.start,
        enabled = capture.available && !capture.listening,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (capture.listening) Accent else SurfaceElevated),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = if (capture.listening) "Escuchando" else "Hablar",
            tint = if (capture.listening) Color.White else OnBackground,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun startListening(
    recognizer: SpeechRecognizer?,
    onStatus: (String) -> Unit,
    setListening: (Boolean) -> Unit,
) {
    if (recognizer == null) {
        onStatus("El reconocimiento de voz no está disponible")
        return
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag("es-ES").toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        if (Build.VERSION.SDK_INT >= 23) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }
    runCatching {
        recognizer.startListening(intent)
        setListening(true)
        onStatus("Escuchando…")
    }.onFailure {
        setListening(false)
        onStatus(it.message ?: "No se pudo escuchar")
    }
}

private fun errorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> "No entendí. Prueba: pausa, siguiente, pon Queen."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché nada"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permiso de micrófono"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Sin red para el reconocedor. Prueba de nuevo."
    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        "El micrófono está ocupado"
    else -> "No se pudo escuchar"
}
