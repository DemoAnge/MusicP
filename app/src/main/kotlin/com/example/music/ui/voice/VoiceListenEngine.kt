package com.example.music.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One-shot SpeechRecognizer. Created on demand, destroyed when idle.
 * All calls are posted to the main thread.
 */
internal class VoiceListenEngine(
    private val appContext: Context,
    private val onText: (String) -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var preferOffline = false
    private var listening = false
    private val restart = Runnable { start() }

    fun prepare(): Boolean = runOnMainSync {
        if (recognizer != null) return@runOnMainSync true
        var onDevice = false
        val created = runCatching {
            when {
                Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) -> {
                    onDevice = true
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                }
                SpeechRecognizer.isRecognitionAvailable(appContext) ->
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                else -> null
            }
        }.getOrNull() ?: return@runOnMainSync false
        preferOffline = onDevice
        created.setRecognitionListener(listener)
        recognizer = created
        true
    }

    fun start() {
        main.post {
            val sr = recognizer ?: return@post
            if (listening) return@post
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag("es-ES").toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            val started = runCatching {
                sr.startListening(intent)
                true
            }.getOrDefault(false)
            listening = started
            if (!started) onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun scheduleRestart(delayMs: Long) {
        main.removeCallbacks(restart)
        main.postDelayed(restart, delayMs)
    }

    fun stop() {
        main.removeCallbacks(restart)
        listening = false
        runOnMain {
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
        }
    }

    fun destroy() {
        main.removeCallbacks(restart)
        listening = false
        runOnMainSync {
            val current = recognizer
            recognizer = null
            runCatching { current?.cancel() }
            runCatching { current?.destroy() }
            true
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post(block)
    }

    private fun runOnMainSync(block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result = false
        val latch = CountDownLatch(1)
        main.post {
            result = runCatching { block() }.getOrDefault(false)
            latch.countDown()
        }
        runCatching { latch.await(2, TimeUnit.SECONDS) }
        return result
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onError(error: Int) {
            listening = false
            main.post { onError(error) }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            main.post { onText(text) }
        }
    }
}
