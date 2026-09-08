package com.example.music.domain.repository

import com.example.music.domain.model.SyncedLyrics
import com.example.music.domain.model.Track

interface LyricsRepository {
    suspend fun getSyncedLyrics(track: Track): SyncedLyrics?
}
