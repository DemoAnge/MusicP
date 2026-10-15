package com.example.music.domain.library

import com.example.music.domain.model.BrowseMode
import com.example.music.domain.model.LibraryBrowseQuery
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.LibraryPrefsSnapshot
import com.example.music.domain.model.LibraryUiState
import com.example.music.domain.model.SortMode
import com.example.music.domain.model.Track
import com.example.music.domain.model.UserPlaylist

object LibraryCatalog {
    const val PLAYLIST_PREFIX = "pl:"

    fun playlistKey(id: String) = "$PLAYLIST_PREFIX$id"

    fun albumKey(track: Track): String = "${track.artist}\u0000${track.album}"

    fun countLabel(tracks: List<Track>): String = "${tracks.size} canciones"

    fun idsToAddToPlaylist(existing: Collection<String>, selectedIds: List<String>): List<String> =
        selectedIds.filter { it.isNotBlank() && it !in existing }.distinct()

    fun build(
        tracks: List<Track>,
        prefs: LibraryPrefsSnapshot,
        query: LibraryBrowseQuery,
    ): LibraryUiState {
        val songs = sortTracks(
            tracks.filter { !it.isVideo && (it.folderPath.isBlank() || it.folderPath !in prefs.ignoredFolders) },
            query.sort,
        )
        val albumGroups = albumGroups(songs)
        val base = LibraryUiState(
            browse = query.browse,
            sort = query.sort,
            favoriteIds = prefs.favorites,
            playlists = prefs.playlists,
            ignoredFolders = prefs.ignoredFolders,
        )
        val state = when (query.browse) {
            BrowseMode.HOME -> base.copy(
                isHome = true,
                homeRecents = recentsList(songs, prefs.recents).take(16),
                homeLiked = songs.filter { it.id in prefs.favorites }.take(16),
                homeAlbums = albumGroups.take(18),
                countLabel = countLabel(songs),
            )
            BrowseMode.SONGS -> base.copy(
                visibleTracks = songs,
                countLabel = countLabel(songs),
            )
            BrowseMode.FAVORITES -> {
                val liked = sortTracks(songs.filter { it.id in prefs.favorites }, query.sort)
                base.copy(
                    visibleTracks = liked,
                    countLabel = if (liked.isEmpty()) "Sin canciones marcadas" else countLabel(liked),
                )
            }
            BrowseMode.RECENTS -> {
                val played = recentsList(songs, prefs.recents)
                val visible = if (query.sort == SortMode.TITLE) played else sortTracks(played, query.sort)
                base.copy(
                    visibleTracks = visible,
                    countLabel = if (visible.isEmpty()) "Aún no has reproducido nada" else countLabel(visible),
                )
            }
            BrowseMode.PLAYLISTS -> playlistsState(base, songs, prefs.playlists, query.groupKey)
            BrowseMode.FOLDERS,
            BrowseMode.ARTISTS,
            BrowseMode.ALBUMS,
            -> groupsState(base, songs, albumGroups, query)
        }
        return applyPicking(state, songs, prefs, query.pickingForPlaylist)
    }

    private fun playlistsState(
        base: LibraryUiState,
        songs: List<Track>,
        playlists: List<UserPlaylist>,
        groupKey: String?,
    ): LibraryUiState {
        val groups = playlists.map { playlist ->
            val byId = songs.associateBy { it.id }
            val list = playlist.trackIds.distinct().mapNotNull { byId[it] }.distinctBy { it.id }
            LibraryGroup(
                key = playlistKey(playlist.id),
                title = playlist.name,
                subtitle = if (list.isEmpty()) "Vacía" else countLabel(list),
                artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                tracks = list,
            )
        }
        val selected = groupKey?.let { key -> groups.firstOrNull { it.key == key } }
        return if (selected == null) {
            base.copy(
                showingGroups = true,
                groups = groups,
                countLabel = if (groups.isEmpty()) "Crea una lista" else "${groups.size} listas",
            )
        } else {
            detailState(base, selected, playlistId = selected.key.removePrefix(PLAYLIST_PREFIX))
        }
    }

    private fun groupsState(
        base: LibraryUiState,
        songs: List<Track>,
        albumGroups: List<LibraryGroup>,
        query: LibraryBrowseQuery,
    ): LibraryUiState {
        val groups = when (query.browse) {
            BrowseMode.FOLDERS -> groupTracks(songs, { it.folderPath.ifBlank { "Otras" } }) { first, list ->
                LibraryGroup(
                    key = first.folderPath.ifBlank { "Otras" },
                    title = first.folderName.ifBlank { "Otras" },
                    subtitle = "${list.size} · ${first.folderPath.ifBlank { "Otras" }}",
                    artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                    tracks = list,
                )
            }
            BrowseMode.ARTISTS -> groupTracks(songs, { it.artist }) { first, list ->
                LibraryGroup(
                    key = first.artist,
                    title = first.artist,
                    subtitle = countLabel(list),
                    artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                    tracks = list,
                )
            }
            else -> albumGroups
        }.distinctBy { it.key }.sortedBy { it.title.lowercase() }
        val selected = query.groupKey?.let { key -> groups.firstOrNull { it.key == key } }
        return if (selected == null) {
            base.copy(
                showingGroups = true,
                groups = groups,
                countLabel = when (query.browse) {
                    BrowseMode.FOLDERS -> "${groups.size} carpetas"
                    BrowseMode.ARTISTS -> "${groups.size} artistas"
                    else -> "${groups.size} álbumes"
                },
            )
        } else {
            detailState(base, selected)
        }
    }

    private fun applyPicking(
        state: LibraryUiState,
        songs: List<Track>,
        prefs: LibraryPrefsSnapshot,
        picking: Boolean,
    ): LibraryUiState {
        if (!picking) return state.copy(pickingForPlaylist = false)
        val playlistId = state.selectedPlaylistId
        if (playlistId.isNullOrBlank()) return state.copy(pickingForPlaylist = false)
        val existing = prefs.playlists.firstOrNull { it.id == playlistId }?.trackIds.orEmpty().toHashSet()
        val candidates = songs
            .filter { it.id !in existing }
            .distinctBy { it.mediaUri.ifBlank { it.id } }
        return state.copy(
            pickingForPlaylist = true,
            showingGroups = false,
            isHome = false,
            visibleTracks = candidates,
            groups = emptyList(),
            countLabel = if (candidates.isEmpty()) {
                "Todas las canciones ya están en esta lista"
            } else {
                "${candidates.size} canciones para agregar"
            },
        )
    }

    private fun detailState(
        base: LibraryUiState,
        selected: LibraryGroup,
        playlistId: String? = null,
    ): LibraryUiState {
        val visible = selected.tracks.distinctBy { it.mediaUri.ifBlank { it.id } }
        return base.copy(
            selectedGroupKey = selected.key,
            selectedGroupTitle = selected.title,
            selectedGroupSubtitle = selected.subtitle,
            selectedGroupArtwork = selected.artworkUri,
            selectedPlaylistId = playlistId,
            visibleTracks = visible,
            countLabel = countLabel(visible),
        )
    }

    private fun recentsList(songs: List<Track>, recents: List<String>): List<Track> {
        val byId = songs.associateBy { it.id }
        return recents.mapNotNull { byId[it] }
    }

    private fun albumGroups(songs: List<Track>): List<LibraryGroup> {
        return groupTracks(songs, { albumKey(it) }) { first, list ->
            LibraryGroup(
                key = albumKey(first),
                title = first.album.ifBlank { "Sin álbum" },
                subtitle = "${first.artist} · ${list.size}",
                artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                tracks = list,
            )
        }.distinctBy { it.key }.sortedBy { it.title.lowercase() }
    }

    private fun groupTracks(
        tracks: List<Track>,
        key: (Track) -> String,
        build: (Track, List<Track>) -> LibraryGroup,
    ): List<LibraryGroup> {
        return tracks.groupBy(key).map { (_, list) -> build(list.first(), list) }
    }

    fun sortTracks(tracks: List<Track>, sort: SortMode): List<Track> {
        return when (sort) {
            SortMode.TITLE -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortMode.DATE_NEW -> tracks.sortedByDescending { it.dateAddedEpochSec }
            SortMode.DATE_OLD -> tracks.sortedBy { it.dateAddedEpochSec }
            SortMode.SIZE_LARGE -> tracks.sortedByDescending { it.sizeBytes }
            SortMode.SIZE_SMALL -> tracks.sortedBy { it.sizeBytes }
        }
    }
}
