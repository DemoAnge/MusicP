package com.example.music.domain.usecase

import com.example.music.domain.model.VoiceCommand
import com.example.music.domain.model.VoiceCommandParser
import javax.inject.Inject

class HandleVoiceCommandUseCase @Inject constructor(
    private val searchTracks: SearchTracksUseCase,
    private val searchYouTube: SearchYouTubeUseCase,
    private val playTrack: PlayTrackUseCase,
    private val controls: ControlPlaybackUseCase,
) {
    suspend operator fun invoke(spoken: String): String {
        val command = VoiceCommandParser.parse(spoken) ?: return "No entendí. Prueba: pausa, siguiente, pon Queen."
        return when (command) {
            VoiceCommand.Pause -> {
                controls.pause()
                "Pausa"
            }
            VoiceCommand.Resume -> {
                controls.resume()
                "Reproduciendo"
            }
            VoiceCommand.Next -> {
                controls.skipNext()
                "Siguiente"
            }
            VoiceCommand.Previous -> {
                controls.skipPrevious()
                "Anterior"
            }
            VoiceCommand.Rewind -> {
                controls.seekBy(-10_000L)
                "Atrás 10 segundos"
            }
            is VoiceCommand.PlayQuery -> playQuery(command.query)
        }
    }

    private suspend fun playQuery(query: String): String {
        val local = runCatching { searchTracks(query) }.getOrNull()
        val track = local?.songs?.firstOrNull()
            ?: local?.artists?.firstOrNull()?.tracks?.firstOrNull()
            ?: local?.albums?.firstOrNull()?.tracks?.firstOrNull()
        if (track != null) {
            val queue = local?.songs?.ifEmpty { listOf(track) } ?: listOf(track)
            runCatching { playTrack(track, queue) }
            return "Reproduciendo ${track.title}"
        }
        val youtube = runCatching { searchYouTube(query) }.getOrDefault(emptyList())
            .ifEmpty { listOf(searchYouTube.placeholder(query)) }
        runCatching { playTrack(youtube.first(), youtube) }
        return "Buscando en YouTube: $query"
    }
}
