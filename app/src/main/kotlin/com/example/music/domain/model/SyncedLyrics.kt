package com.example.music.domain.model

data class LyricsLine(
    val timeMs: Long,
    val text: String,
)

data class SyncedLyrics(
    val trackId: String,
    val lines: List<LyricsLine>,
    val plainText: String?,
)
