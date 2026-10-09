package com.example.music.domain.model

data class PlaybackSession(
    val queue: List<Track> = emptyList(),
    val originalQueue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val positionMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)
