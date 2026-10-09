package com.example.music.domain.repository

import com.example.music.domain.model.UserPlaylist
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
    fun observeSearchHistory(): Flow<List<String>>
    suspend fun recordSearch(query: String)
    suspend fun removeSearchQuery(query: String)
    suspend fun clearSearchHistory()
    fun observePlaylists(): Flow<List<UserPlaylist>>
    suspend fun createPlaylist(name: String): UserPlaylist?
    suspend fun deletePlaylist(id: String)
    suspend fun addToPlaylist(playlistId: String, trackId: String)
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>)
    suspend fun removeFromPlaylist(playlistId: String, trackId: String)
    fun observeIgnoredFolders(): Flow<Set<String>>
    suspend fun setFolderIgnored(folderPath: String, ignored: Boolean)
    fun observePlaybackSpeed(): Flow<Float>
    suspend fun setPlaybackSpeed(speed: Float)
}
