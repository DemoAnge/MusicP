package com.example.music.domain.repository

import kotlinx.coroutines.flow.Flow

interface LibraryPrefsRepository {
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeRecentIds(): Flow<List<String>>
    suspend fun toggleFavorite(trackId: String)
    suspend fun recordPlay(trackId: String)
}
