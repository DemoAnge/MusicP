package com.example.music.domain.repository

import com.example.music.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LocalMusicRepository {
    fun observeTracks(): Flow<List<Track>>
    suspend fun getTracks(): List<Track>
    suspend fun search(query: String): List<Track>
    suspend fun trackFromUri(uri: String, mimeType: String?): Track
}
