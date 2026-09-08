package com.example.music.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String?,
    val mediaUri: String,
    val source: PlaybackSource,
    val mimeType: String? = null,
    val isVideo: Boolean = false,
    val embeddedLyrics: String? = null,
    val folderPath: String = "",
    val folderName: String = "",
    val dateAddedEpochSec: Long = 0L,
    val sizeBytes: Long = 0L,
)
