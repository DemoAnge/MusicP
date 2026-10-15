package com.example.music

import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.Track

fun testTrack(
    id: String,
    title: String = id,
    artist: String = "Artist",
    album: String = "Album",
    source: PlaybackSource = PlaybackSource.LOCAL,
    isVideo: Boolean = false,
    folderPath: String = "",
    folderName: String = "",
    dateAddedEpochSec: Long = 0L,
    sizeBytes: Long = 0L,
    mediaUri: String = "content://media/$id",
    durationMs: Long = 180_000L,
    mimeType: String? = null,
    artworkUri: String? = null,
): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artworkUri = artworkUri,
    mediaUri = mediaUri,
    source = source,
    mimeType = mimeType,
    isVideo = isVideo,
    folderPath = folderPath,
    folderName = folderName,
    dateAddedEpochSec = dateAddedEpochSec,
    sizeBytes = sizeBytes,
)
