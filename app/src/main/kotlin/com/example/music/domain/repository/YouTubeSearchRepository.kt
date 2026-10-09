package com.example.music.domain.repository

import com.example.music.domain.model.Track

interface YouTubeSearchRepository {
    fun hasApiKey(): Boolean
    suspend fun search(query: String): List<Track>
}
