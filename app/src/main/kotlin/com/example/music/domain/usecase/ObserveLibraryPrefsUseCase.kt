package com.example.music.domain.usecase

import com.example.music.domain.repository.LibraryPrefsRepository
import javax.inject.Inject

class ObserveLibraryPrefsUseCase @Inject constructor(
    private val prefs: LibraryPrefsRepository,
) {
    fun favorites() = prefs.observeFavoriteIds()
    fun recents() = prefs.observeRecentIds()
    suspend fun removeIds(ids: Set<String>) = prefs.removeIds(ids)
    fun lockScreenPromptDismissed() = prefs.observeLockScreenPromptDismissed()
    suspend fun setLockScreenPromptDismissed(dismissed: Boolean) =
        prefs.setLockScreenPromptDismissed(dismissed)
    fun preferBrave() = prefs.observePreferBrave()
    suspend fun setPreferBrave(enabled: Boolean) = prefs.setPreferBrave(enabled)
    fun searchHistory() = prefs.observeSearchHistory()
    suspend fun recordSearch(query: String) = prefs.recordSearch(query)
    suspend fun removeSearchQuery(query: String) = prefs.removeSearchQuery(query)
    suspend fun clearSearchHistory() = prefs.clearSearchHistory()
    fun playlists() = prefs.observePlaylists()
    suspend fun createPlaylist(name: String) = prefs.createPlaylist(name)
    suspend fun deletePlaylist(id: String) = prefs.deletePlaylist(id)
    suspend fun addToPlaylist(playlistId: String, trackId: String) =
        prefs.addToPlaylist(playlistId, trackId)
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) =
        prefs.addToPlaylist(playlistId, trackIds)
    suspend fun removeFromPlaylist(playlistId: String, trackId: String) =
        prefs.removeFromPlaylist(playlistId, trackId)
    fun ignoredFolders() = prefs.observeIgnoredFolders()
    suspend fun setFolderIgnored(folderPath: String, ignored: Boolean) =
        prefs.setFolderIgnored(folderPath, ignored)
    fun playbackSpeed() = prefs.observePlaybackSpeed()
    suspend fun setPlaybackSpeed(speed: Float) = prefs.setPlaybackSpeed(speed)
}
