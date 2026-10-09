package com.example.music.player

import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface IPlayerService {
    val state: StateFlow<PlayerState>

    suspend fun play(track: Track, queue: List<Track>)
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun playNext(track: Track)
    fun addToQueue(track: Track)
    fun moveInQueue(fromIndex: Int, toIndex: Int)
    fun playQueueIndex(index: Int)
    fun removeFromQueue(trackIds: Set<String>)
    fun clearError()
    fun seekBy(deltaMs: Long)
    fun reopenWebBridge()
    fun release()
}
