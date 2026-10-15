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
    fun lineAt(positionMs: Long): LyricsLine? {
        if (lines.isEmpty()) return null
        return lines.lastOrNull { it.timeMs <= positionMs } ?: lines.first()
    }
}
