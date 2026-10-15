package com.example.music.domain.usecase

import com.example.music.domain.library.LibraryCatalog
import com.example.music.domain.model.UserPlaylist
import com.example.music.domain.repository.LibraryPrefsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface PlaylistMutation {
    data class Created(val playlist: UserPlaylist) : PlaylistMutation
    data object BlankName : PlaylistMutation
    data class Added(val count: Int) : PlaylistMutation
    data object AlreadyPresent : PlaylistMutation
    data object Deleted : PlaylistMutation
    data object TrackRemoved : PlaylistMutation
    data object Missing : PlaylistMutation
}

class ManagePlaylistsUseCase @Inject constructor(
    private val prefs: LibraryPrefsRepository,
) {
    suspend fun create(name: String, addTrackId: String? = null): PlaylistMutation {
        val created = prefs.createPlaylist(name) ?: return PlaylistMutation.BlankName
        if (!addTrackId.isNullOrBlank()) {
            prefs.addToPlaylist(created.id, addTrackId)
        }
        return PlaylistMutation.Created(created)
    }

    suspend fun delete(playlistId: String): PlaylistMutation {
        if (playlistId.isBlank()) return PlaylistMutation.Missing
        prefs.deletePlaylist(playlistId)
        return PlaylistMutation.Deleted
    }

    suspend fun add(playlistId: String, trackIds: List<String>): PlaylistMutation {
        if (playlistId.isBlank()) return PlaylistMutation.Missing
        val existing = prefs.observePlaylists().first()
            .firstOrNull { it.id == playlistId }
            ?.trackIds
            .orEmpty()
        val toAdd = LibraryCatalog.idsToAddToPlaylist(existing, trackIds)
        if (toAdd.isEmpty()) return PlaylistMutation.AlreadyPresent
        prefs.addToPlaylist(playlistId, toAdd)
        return PlaylistMutation.Added(toAdd.size)
    }

    suspend fun removeTrack(playlistId: String, trackId: String): PlaylistMutation {
        if (playlistId.isBlank() || trackId.isBlank()) return PlaylistMutation.Missing
        prefs.removeFromPlaylist(playlistId, trackId)
        return PlaylistMutation.TrackRemoved
    }
}
