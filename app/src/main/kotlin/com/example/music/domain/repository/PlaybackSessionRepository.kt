package com.example.music.domain.repository

import com.example.music.domain.model.PlaybackSession

interface PlaybackSessionRepository {
    suspend fun save(session: PlaybackSession)
    suspend fun load(): PlaybackSession?
    suspend fun clear()
}
