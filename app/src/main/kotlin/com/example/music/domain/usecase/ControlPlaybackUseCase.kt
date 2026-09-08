package com.example.music.domain.usecase

import com.example.music.domain.model.RepeatMode
import com.example.music.player.IPlayerService
import javax.inject.Inject

class ControlPlaybackUseCase @Inject constructor(
    private val playerService: IPlayerService,
) {
    fun togglePlayPause() = playerService.togglePlayPause()
    fun pause() = playerService.pause()
    fun resume() = playerService.resume()
    fun seekTo(positionMs: Long) = playerService.seekTo(positionMs)
    fun skipNext() = playerService.skipNext()
    fun skipPrevious() = playerService.skipPrevious()
    fun setShuffle(enabled: Boolean) = playerService.setShuffle(enabled)
    fun setRepeat(mode: RepeatMode) = playerService.setRepeat(mode)
}
