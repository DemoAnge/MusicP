package com.example.music.domain.player

import com.example.music.domain.model.PlaybackSession
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.Track

data class RestoredPlayback(
    val playable: List<Track>,
    val original: List<Track>,
    val index: Int,
    val positionMs: Long,
    val isShuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
)

object RestorePlaybackSession {
    fun restore(session: PlaybackSession, canPlay: (Track) -> Boolean): RestoredPlayback? {
        val playable = session.queue.filter(canPlay)
        if (playable.isEmpty()) return null
        val original = session.originalQueue.filter(canPlay).distinctBy { it.id }
            .ifEmpty { playable }
        val wantedId = session.queue.getOrNull(session.queueIndex)?.id
        val index = playable.indexOfFirst { it.id == wantedId }.let { if (it >= 0) it else 0 }
        return RestoredPlayback(
            playable = playable,
            original = original,
            index = index,
            positionMs = session.positionMs.coerceAtLeast(0L),
            isShuffleEnabled = session.isShuffleEnabled,
            repeatMode = session.repeatMode,
        )
    }
}
