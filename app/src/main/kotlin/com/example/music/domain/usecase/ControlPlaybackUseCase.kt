package com.example.music.domain.usecase

import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track
import com.example.music.player.IPlayerService
import javax.inject.Inject

class ControlPlaybackUseCase @Inject constructor(
    private val playerService: IPlayerService,
) {
    fun togglePlayPause() = playerService.togglePlayPause()
    fun pause() = playerService.pause()
    fun resume() = playerService.resume()
    fun seekTo(positionMs: Long) = playerService.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = playerService.seekBy(deltaMs)
    fun reopenWebBridge() = playerService.reopenWebBridge()
    fun skipNext() = playerService.skipNext()
    fun skipPrevious() = playerService.skipPrevious()
    fun setShuffle(enabled: Boolean) = playerService.setShuffle(enabled)
    fun setRepeat(mode: RepeatMode) = playerService.setRepeat(mode)
    fun playNext(track: Track) = playerService.playNext(track)
    fun addToQueue(track: Track) = playerService.addToQueue(track)
    fun moveInQueue(fromIndex: Int, toIndex: Int) = playerService.moveInQueue(fromIndex, toIndex)
    fun playQueueIndex(index: Int) = playerService.playQueueIndex(index)
    fun removeFromQueue(trackIds: Set<String>) = playerService.removeFromQueue(trackIds)
    fun clearError() = playerService.clearError()
}
