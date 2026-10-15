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
): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = 180_000L,
    artworkUri = null,
    mediaUri = mediaUri,
    source = source,
    isVideo = isVideo,
    folderPath = folderPath,
    folderName = folderName,
    dateAddedEpochSec = dateAddedEpochSec,
    sizeBytes = sizeBytes,
)
