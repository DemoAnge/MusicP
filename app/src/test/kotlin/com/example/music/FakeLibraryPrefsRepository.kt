package com.example.music

import com.example.music.domain.model.UserPlaylist
import com.example.music.domain.repository.LibraryPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLibraryPrefsRepository : LibraryPrefsRepository {
    private val favorites = MutableStateFlow<Set<String>>(emptySet())
    private val recents = MutableStateFlow<List<String>>(emptyList())
    private val playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    private val ignored = MutableStateFlow<Set<String>>(emptySet())
    private val lock = MutableStateFlow(false)
    private val brave = MutableStateFlow(true)
    private val history = MutableStateFlow<List<String>>(emptyList())
    private val speed = MutableStateFlow(1f)
    private var nextId = 1

    override fun observeFavoriteIds() = favorites
    override fun observeRecentIds() = recents

    override suspend fun toggleFavorite(trackId: String) {
        val next = favorites.value.toMutableSet()
        if (!next.add(trackId)) next.remove(trackId)
        favorites.value = next
    }

    override suspend fun recordPlay(trackId: String) {
        recents.value = listOf(trackId) + recents.value.filter { it != trackId }
    }

    override suspend fun removeIds(ids: Set<String>) {
        favorites.value = favorites.value - ids
        recents.value = recents.value.filter { it !in ids }
        playlists.value = playlists.value.map { playlist ->
            playlist.copy(trackIds = playlist.trackIds.filter { it !in ids })
        }
    }

    override fun observeLockScreenPromptDismissed() = lock
    override suspend fun setLockScreenPromptDismissed(dismissed: Boolean) {
        lock.value = dismissed
    }

    override fun observePreferBrave() = brave
    override suspend fun setPreferBrave(enabled: Boolean) {
        brave.value = enabled
    }

    override fun observeSearchHistory() = history
    override suspend fun recordSearch(query: String) {
        history.value = (listOf(query) + history.value.filter { it != query }).take(12)
    }

    override suspend fun removeSearchQuery(query: String) {
        history.value = history.value.filter { it != query }
    }

    override suspend fun clearSearchHistory() {
        history.value = emptyList()
    }

    override fun observePlaylists() = playlists

    override suspend fun createPlaylist(name: String): UserPlaylist? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val created = UserPlaylist(id = "p${nextId++}", name = trimmed)
        playlists.value = playlists.value + created
        return created
    }

    override suspend fun deletePlaylist(id: String) {
        playlists.value = playlists.value.filterNot { it.id == id }
    }

    override suspend fun addToPlaylist(playlistId: String, trackId: String) =
        addToPlaylist(playlistId, listOf(trackId))

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        playlists.value = playlists.value.map { playlist ->
            if (playlist.id != playlistId) playlist
            else playlist.copy(
                trackIds = (playlist.trackIds + trackIds.filter { it.isNotBlank() }).distinct(),
            )
        }
    }

    override suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        playlists.value = playlists.value.map { playlist ->
            if (playlist.id != playlistId) playlist
            else playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
        }
    }

    override fun observeIgnoredFolders() = ignored

    override suspend fun setFolderIgnored(folderPath: String, ignored: Boolean) {
        val path = folderPath.trim()
        if (path.isEmpty()) return
        this.ignored.value = if (ignored) this.ignored.value + path else this.ignored.value - path
    }

    override fun observePlaybackSpeed() = speed
    override suspend fun setPlaybackSpeed(speed: Float) {
        this.speed.value = speed
    }
}
