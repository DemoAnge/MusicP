package com.example.music.domain.model

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val errorMessage: String? = null,
    val webBridgeConnected: Boolean = false,
    val webNeedsGesture: Boolean = false,
    val playbackSpeed: Float = 1f,
    val sleepEndsAtEpochMs: Long = 0L,
    val sleepAtEndOfTrack: Boolean = false,
)
