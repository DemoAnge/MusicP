package com.example.music.domain.player

import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.Track

/**
 * Mixed local+web queue: one engine at a time.
 * Skip walks the list by index and may change source.
 */
object MixedPlaybackOps {
    fun isWeb(track: Track?): Boolean = track?.source == PlaybackSource.WEB

    fun usesExoPlaylist(queue: List<Track>): Boolean =
        queue.isNotEmpty() && queue.all { it.source == PlaybackSource.LOCAL }

    fun isMixed(queue: List<Track>): Boolean {
        var local = false
        var web = false
        for (track in queue) {
            if (track.source == PlaybackSource.WEB) web = true else local = true
            if (local && web) return true
        }
        return false
    }

    fun pauseLocalWhenPlaying(track: Track): Boolean = isWeb(track)

    fun pauseWebWhenPlaying(track: Track): Boolean = !isWeb(track)

    fun nextIndex(queueSize: Int, index: Int, wrap: Boolean): Int? {
        if (queueSize <= 0) return null
        val next = index + 1
        return when {
            next < queueSize -> next
            wrap -> 0
            else -> null
        }
    }

    fun previousIndex(queueSize: Int, index: Int): Int? {
        if (queueSize <= 0) return null
        return if (index > 0) index - 1 else queueSize - 1
    }
}
