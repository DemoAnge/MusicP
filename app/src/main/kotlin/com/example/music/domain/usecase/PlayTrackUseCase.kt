package com.example.music.domain.usecase

import com.example.music.domain.model.Track
import com.example.music.player.IPlayerService
import javax.inject.Inject

class PlayTrackUseCase @Inject constructor(
    private val playerService: IPlayerService,
    private val recordRecentPlay: RecordRecentPlayUseCase,
) {
    suspend operator fun invoke(track: Track, queue: List<Track>) {
        if (track.isVideo) return
        val musicQueue = queue.filter { !it.isVideo }
        runCatching { recordRecentPlay(track.id) }
        runCatching { playerService.play(track, musicQueue) }
    }
}
