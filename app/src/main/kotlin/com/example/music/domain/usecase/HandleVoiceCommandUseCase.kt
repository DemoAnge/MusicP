package com.example.music.domain.usecase

import com.example.music.domain.library.TrackClassifier
import com.example.music.domain.model.GroupedLocalSearch
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.SleepOption
import com.example.music.domain.model.VoiceCommand
import com.example.music.domain.model.VoiceCommandParser
import com.example.music.domain.model.VoiceResult
import com.example.music.domain.voice.VoicePlayResolver
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.first

class HandleVoiceCommandUseCase @Inject constructor(
    private val controls: ControlPlaybackUseCase,
    private val observePlayerState: ObservePlayerStateUseCase,
    private val searchTracks: SearchTracksUseCase,
    private val playTrack: PlayTrackUseCase,
    private val searchYouTube: SearchYouTubeUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val prefs: ObserveLibraryPrefsUseCase,
) {
    suspend operator fun invoke(spoken: String): VoiceResult {
        val command = VoiceCommandParser.parse(spoken)
            ?: return VoiceResult("No entendí. Di pon, pausa, temporizador…")
        val playing = observePlayerState().value
        return when (command) {
            VoiceCommand.Play -> {
                if (playing.currentTrack == null) VoiceResult("Nada en reproducción")
                else run("Reproduciendo") { controls.resume() }
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
            is VoiceCommand.PlayQuery -> playQuery(command.query)
            VoiceCommand.LikeCurrent -> likeCurrent()
            is VoiceCommand.SetSleep -> {
                run(sleepSpoken(command.option)) { controls.setSleepTimer(command.option) }
            }
            VoiceCommand.SpeedDown -> setSpeed(stepSpeed(playing.playbackSpeed, -1))
            VoiceCommand.SpeedUp -> setSpeed(stepSpeed(playing.playbackSpeed, 1))
            is VoiceCommand.SetSpeed -> setSpeed(command.speed)
        }
    }

    private suspend fun playQuery(query: String): VoiceResult {
        val grouped = runCatching { searchTracks(query) }.getOrDefault(GroupedLocalSearch())
        val pick = VoicePlayResolver.pick(query, grouped)
        if (pick != null) {
            runCatching { playTrack(pick.track, pick.queue) }
            return VoiceResult(pick.spoken)
        }
        val found = runCatching { searchYouTube(query) }.getOrDefault(emptyList())
            .filter { it.mediaUri.startsWith("yt:") && !it.mediaUri.startsWith("ytsearch:") }
        if (found.isEmpty()) return VoiceResult("No está en el teléfono")
        runCatching { playTrack(found.first(), found) }
        return VoiceResult("En Brave")
    }

    private suspend fun likeCurrent(): VoiceResult {
        val id = observePlayerState().value.currentTrack?.id
            ?: return VoiceResult("Nada en reproducción")
        val favorites = runCatching { prefs.favorites().first() }.getOrDefault(emptySet())
        if (id !in favorites) {
            runCatching { toggleFavorite(id) }
        }
        return VoiceResult("Guardada")
    }

    private suspend fun setSpeed(speed: Float): VoiceResult {
        val clamped = speed.coerceIn(0.8f, 1.5f)
        val spoken = observePlayerState().value.currentTrack?.let { TrackClassifier.isSpoken(it) } == true
        if (spoken) {
            runCatching { prefs.setSpokenSpeed(clamped) }
        } else {
            runCatching { prefs.setPlaybackSpeed(clamped) }
        }
        return VoiceResult("Velocidad ${speedSpoken(clamped)}")
    }

    private fun run(message: String, block: () -> Unit): VoiceResult {
        return runCatching { block() }
            .fold(
                onSuccess = { VoiceResult(message) },
                onFailure = { VoiceResult("No se pudo") },
            )
    }

    companion object {
        private val SPEED_STEPS = floatArrayOf(0.8f, 1f, 1.25f, 1.5f)

        fun stepSpeed(current: Float, delta: Int): Float {
            val index = SPEED_STEPS.indices.minBy { abs(SPEED_STEPS[it] - current) }
            return SPEED_STEPS[(index + delta).coerceIn(0, SPEED_STEPS.lastIndex)]
        }

        fun speedSpoken(speed: Float): String =
            "%.2f".format(Locale.US, speed).trimEnd('0').trimEnd('.').replace('.', ',')

        fun sleepSpoken(option: SleepOption): String = when (option) {
            SleepOption.OFF -> "Temporizador off"
            SleepOption.MINUTES_15 -> "Temporizador 15 minutos"
            SleepOption.MINUTES_30 -> "Temporizador 30 minutos"
            SleepOption.MINUTES_45 -> "Temporizador 45 minutos"
            SleepOption.MINUTES_60 -> "Temporizador 60 minutos"
            SleepOption.END_OF_TRACK -> "Duerme al final"
        }
    }
}
