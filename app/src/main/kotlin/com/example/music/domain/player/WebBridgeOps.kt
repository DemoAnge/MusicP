package com.example.music.domain.player

import com.example.music.domain.model.PlaybackUserMessage

object WebBridgeOps {
    const val STALE_AFTER_MS = 8_000L
    const val NOTIFY_AFTER_MS = 12_000L
    const val PLAYING_IN_BRAVE = "Suena en Brave"
    const val TAP_TO_REOPEN = "Toca para reabrir puente"

    fun isFresh(lastClientAt: Long, now: Long): Boolean {
        if (lastClientAt <= 0L) return false
        return now - lastClientAt <= STALE_AFTER_MS
    }

    fun shouldNotifyBridgeDown(
        lastClientAt: Long,
        now: Long,
        hasActiveTrack: Boolean,
        alreadyNotified: Boolean,
    ): Boolean {
        if (alreadyNotified || !hasActiveTrack || lastClientAt <= 0L) return false
        return now - lastClientAt >= NOTIFY_AFTER_MS
    }

    fun shouldAdvanceQueueOnFailure(message: String): Boolean {
        if (message == PlaybackUserMessage.FILE_MISSING || message == PlaybackUserMessage.UNSUPPORTED) {
            return true
        }
        val lower = message.lowercase()
        if (message == PlaybackUserMessage.BRIDGE_DOWN ||
            message == PlaybackUserMessage.YOUTUBE ||
            message == PlaybackUserMessage.YOUTUBE_EMBED ||
            "puente" in lower ||
            "youtube" in lower ||
            "brave" in lower ||
            "embeb" in lower
        ) {
            return false
        }
        return true
    }

    fun sourceSubtitle(artist: String): String {
        val trimmed = artist.trim()
        return if (trimmed.isEmpty() ||
            trimmed.equals("YouTube", ignoreCase = true) ||
            trimmed.equals("Brave", ignoreCase = true)
        ) {
            PLAYING_IN_BRAVE
        } else {
            "$trimmed · $PLAYING_IN_BRAVE"
        }
    }
}
