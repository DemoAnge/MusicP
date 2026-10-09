package com.example.music.domain.repository

import kotlinx.coroutines.flow.Flow

interface LibraryPrefsRepository {
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeRecentIds(): Flow<List<String>>
    suspend fun toggleFavorite(trackId: String)
    suspend fun recordPlay(trackId: String)
    suspend fun removeIds(ids: Set<String>)
    fun observeLockScreenPromptDismissed(): Flow<Boolean>
    suspend fun setLockScreenPromptDismissed(dismissed: Boolean)
    fun observePreferBrave(): Flow<Boolean>
    suspend fun setPreferBrave(enabled: Boolean)
}
