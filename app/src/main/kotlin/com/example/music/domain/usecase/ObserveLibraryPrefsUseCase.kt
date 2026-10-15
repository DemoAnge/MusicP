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
    fun ignoredFolders() = prefs.observeIgnoredFolders()
    fun scanPrefs() = prefs.observeScanPrefs()
    suspend fun setShowHidden(enabled: Boolean) = prefs.setShowHidden(enabled)
    suspend fun setShowPodcasts(enabled: Boolean) = prefs.setShowPodcasts(enabled)
    suspend fun setShowAudiobooks(enabled: Boolean) = prefs.setShowAudiobooks(enabled)
    fun playbackSpeed() = prefs.observePlaybackSpeed()
    suspend fun setPlaybackSpeed(speed: Float) = prefs.setPlaybackSpeed(speed)
}
