package com.example.music.domain.usecase

import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.VoiceCommand
import com.example.music.domain.model.VoiceCommandParser
import com.example.music.domain.model.VoiceResult
import javax.inject.Inject

class HandleVoiceCommandUseCase @Inject constructor(
    private val controls: ControlPlaybackUseCase,
    private val observePlayerState: ObservePlayerStateUseCase,
) {
    operator fun invoke(spoken: String): VoiceResult {
        val command = VoiceCommandParser.parse(spoken)
            ?: return VoiceResult("No entendí. Di play, pausa, siguiente, cola…")
        val playing = observePlayerState().value
        return when (command) {
            VoiceCommand.Play -> {
                if (playing.currentTrack == null) VoiceResult("Nada en reproducción")
                else run("Play") { controls.resume() }
            }
            VoiceCommand.Pause -> run("Pausa") { controls.pause() }
            VoiceCommand.Next -> run("Siguiente") { controls.skipNext() }
            VoiceCommand.Previous -> run("Anterior") { controls.skipPrevious() }
            VoiceCommand.Shuffle -> {
                val next = !playing.isShuffleEnabled
                run(if (next) "Mezclar" else "Mezclar off") { controls.setShuffle(next) }
            }
            VoiceCommand.RepeatOne -> run("Repetir 1") { controls.setRepeat(RepeatMode.ONE) }
            VoiceCommand.RepeatAll -> run("Repetir todos") { controls.setRepeat(RepeatMode.ALL) }
            VoiceCommand.Queue -> VoiceResult("Cola", openQueue = true)
            VoiceCommand.Exit -> VoiceResult("Salir", exit = true)
        }
    }

    private fun run(message: String, block: () -> Unit): VoiceResult {
        return runCatching { block() }
            .fold(
                onSuccess = { VoiceResult(message) },
                onFailure = { VoiceResult("No se pudo") },
            )
    }
}
