package com.example.music.domain.model

data class LyricsLine(
    val timeMs: Long,
    val text: String,
)

data class SyncedLyrics(
    val trackId: String,
    val lines: List<LyricsLine>,
    val plainText: String?,
) {
    fun lineAt(positionMs: Long, offsetMs: Long = 0L): LyricsLine? {
        if (lines.isEmpty()) return null
        val t = positionMs + offsetMs
        return lines.lastOrNull { it.timeMs <= t } ?: lines.first()
    }

    fun lineIndexAt(positionMs: Long, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val t = positionMs + offsetMs
        return lines.indexOfLast { it.timeMs <= t }.coerceAtLeast(0)
    }
}
