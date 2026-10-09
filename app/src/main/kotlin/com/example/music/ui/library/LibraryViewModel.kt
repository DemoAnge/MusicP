package com.example.music.ui.library

import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.BrowseMode
import com.example.music.domain.model.DeleteTracksResult
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.LibraryUiState
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.SortMode
import com.example.music.domain.model.Track
import com.example.music.domain.model.UserPlaylist
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.DeleteLocalTracksUseCase
import com.example.music.domain.usecase.GetLocalTracksUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
import com.example.music.domain.usecase.SearchYouTubeUseCase
import com.example.music.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLocalTracks: GetLocalTracksUseCase,
    observePlayerState: ObservePlayerStateUseCase,
    private val observePrefs: ObserveLibraryPrefsUseCase,
    private val playTrack: PlayTrackUseCase,
    private val controls: ControlPlaybackUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val deleteTracks: DeleteLocalTracksUseCase,
    private val searchYouTube: SearchYouTubeUseCase,
) : ViewModel() {

    private val browseMode = MutableStateFlow(BrowseMode.HOME)
    private val sortMode = MutableStateFlow(SortMode.TITLE)
    private val selectedGroupKey = MutableStateFlow<String?>(null)
    private val selecting = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var pendingDelete: List<Track> = emptyList()

    private val allTracks: StateFlow<List<Track>> = getLocalTracks.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private data class Filters(
        val browse: BrowseMode,
        val sort: SortMode,
        val groupKey: String?,
    )

    private data class PrefsSnap(
        val favorites: Set<String>,
        val recents: List<String>,
        val playlists: List<UserPlaylist>,
        val ignoredFolders: Set<String>,
    )

    private val filters = combine(browseMode, sortMode, selectedGroupKey) { browse, sort, group ->
        Filters(browse, sort, group)
    }

    private val selection = combine(selecting, selectedIds) { on, ids -> on to ids }

    private val prefsSnap = combine(
        observePrefs.favorites(),
        observePrefs.recents(),
        observePrefs.playlists(),
        observePrefs.ignoredFolders(),
    ) { favorites, recents, playlists, ignored ->
        PrefsSnap(favorites, recents, playlists, ignored)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        allTracks,
        prefsSnap,
        filters,
        selection,
        _refreshing,
    ) { tracks, prefs, filter, sel, refreshing ->
        runCatching {
            buildState(tracks, prefs, filter).copy(
                selecting = sel.first,
                selectedIds = sel.second,
                refreshing = refreshing,
            )
        }.getOrDefault(
            LibraryUiState(
                favoriteIds = prefs.favorites,
                playlists = prefs.playlists,
                ignoredFolders = prefs.ignoredFolders,
                selecting = sel.first,
                selectedIds = sel.second,
                refreshing = refreshing,
            ),
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryUiState(isHome = true),
    )

    fun loadTracks() {
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { getLocalTracks.refresh() }
            _refreshing.value = false
        }
    }

    fun setBrowse(mode: BrowseMode) {
        browseMode.value = mode
        selectedGroupKey.value = null
        exitSelection()
    }

    fun setSort(mode: SortMode) {
        sortMode.value = mode
    }

    fun openGroup(key: String) {
        if (key.isBlank()) return
        selectedGroupKey.value = key
    }

    fun openAlbum(track: Track) {
        browseMode.value = BrowseMode.ALBUMS
        selectedGroupKey.value = albumKey(track)
        exitSelection()
    }

    fun openAlbumGroup(group: LibraryGroup) {
        browseMode.value = BrowseMode.ALBUMS
        selectedGroupKey.value = group.key
        exitSelection()
    }

    fun openArtist(track: Track) {
        browseMode.value = BrowseMode.ARTISTS
        selectedGroupKey.value = track.artist
        exitSelection()
    }

    fun closeGroup() {
        selectedGroupKey.value = null
        if (selecting.value) exitSelection()
    }

    fun toggleLiked(trackId: String) {
        viewModelScope.launch { runCatching { toggleFavorite(trackId) } }
    }

    fun play(track: Track) {
        val queue = uiState.value.visibleTracks.ifEmpty { playableTracks() }
        viewModelScope.launch { runCatching { playTrack(track, queue) } }
    }

    fun playNext(track: Track) {
        if (track.isVideo) return
        controls.playNext(track)
        _notice.value = "Se reproducirá a continuación"
    }

    fun addToQueue(track: Track) {
        if (track.isVideo) return
        controls.addToQueue(track)
        _notice.value = "Se agregó al final de la cola"
    }

    fun playAll() {
        val list = uiState.value.visibleTracks.ifEmpty { playableTracks() }
        if (list.isEmpty()) return
        viewModelScope.launch { runCatching { playTrack(list.first(), list) } }
    }

    fun shuffleAll() {
        val list = uiState.value.visibleTracks.ifEmpty { playableTracks() }
        if (list.isEmpty()) return
        runCatching { controls.setShuffle(true) }
        viewModelScope.launch { runCatching { playTrack(list.random(), list) } }
    }

    fun playGroup(group: LibraryGroup) {
        val list = group.tracks.filter { !it.isVideo }
        val track = list.firstOrNull() ?: return
        viewModelScope.launch { runCatching { playTrack(track, list) } }
    }

    fun searchOnYouTube(track: Track) {
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return
        viewModelScope.launch {
            val found = runCatching { searchYouTube(query) }.getOrDefault(emptyList())
            val queue = found.ifEmpty { listOf(searchYouTube.placeholder(query)) }
            runCatching { playTrack(queue.first(), queue) }
            _notice.value = "Buscando en YouTube · Brave"
        }
    }

    fun createPlaylist(name: String, addTrackId: String? = null) {
        viewModelScope.launch {
            val created = runCatching { observePrefs.createPlaylist(name) }.getOrNull()
            if (created == null) {
                _notice.value = "Ponle un nombre a la lista"
                return@launch
            }
            if (!addTrackId.isNullOrBlank()) {
                runCatching { observePrefs.addToPlaylist(created.id, addTrackId) }
            }
            _notice.value = "Lista “${created.name}” creada"
        }
    }

    fun deleteCurrentPlaylist() {
        val id = uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { observePrefs.deletePlaylist(id) }
            closeGroup()
            _notice.value = "Lista eliminada"
        }
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            runCatching { observePrefs.addToPlaylist(playlistId, trackId) }
            _notice.value = "Agregada a la lista"
        }
    }

    fun removeFromCurrentPlaylist(trackId: String) {
        val id = uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { observePrefs.removeFromPlaylist(id, trackId) }
            _notice.value = "Quitada de la lista"
        }
    }

    fun ignoreCurrentFolder() {
        val path = uiState.value.visibleTracks.firstOrNull()?.folderPath?.trim().orEmpty()
        if (path.isEmpty()) return
        viewModelScope.launch {
            runCatching { observePrefs.setFolderIgnored(path, true) }
            closeGroup()
            _notice.value = "Carpeta oculta. Puedes mostrarla otra vez en Ajustes."
        }
    }

    fun unignoreFolder(path: String) {
        viewModelScope.launch { runCatching { observePrefs.setFolderIgnored(path, false) } }
    }

    fun enterSelection() {
        selecting.value = true
    }

    fun exitSelection() {
        selecting.value = false
        selectedIds.value = emptySet()
    }

    fun startSelection(trackId: String) {
        selecting.value = true
        selectedIds.value = setOf(trackId)
    }

    fun toggleSelectTrack(trackId: String) {
        selecting.value = true
        selectedIds.update { current ->
            if (trackId in current) current - trackId else current + trackId
        }
        if (selectedIds.value.isEmpty()) selecting.value = false
    }

    fun toggleSelectGroup(group: LibraryGroup) {
        val ids = group.tracks.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        selecting.value = true
        selectedIds.update { current ->
            if (ids.all { it in current }) current - ids else current + ids
        }
        if (selectedIds.value.isEmpty()) selecting.value = false
    }

    fun selectAllVisible() {
        val snapshot = uiState.value
        val ids = if (snapshot.showingGroups) {
            snapshot.groups.flatMap { group -> group.tracks.map { it.id } }
        } else {
            snapshot.visibleTracks.map { it.id }
        }.filter { it.isNotBlank() }.toSet()
        selecting.value = true
        selectedIds.value = ids
    }

    fun consumeNotice() {
        _notice.value = null
    }

    fun deleteSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        val tracks = allTracks.value.filter { it.id in ids }.ifEmpty {
            uiState.value.visibleTracks.filter { it.id in ids } +
                uiState.value.groups.flatMap { it.tracks }.filter { it.id in ids }
        }.distinctBy { it.id }
        if (tracks.isEmpty()) return
        pendingDelete = tracks
        viewModelScope.launch {
            when (val result = runCatching { deleteTracks(tracks) }.getOrNull()) {
                is DeleteTracksResult.NeedConsent ->
                    _events.emit(LibraryEvent.RequestDeleteConsent(result.intentSender))
                is DeleteTracksResult.Deleted -> onDeleted(tracks, result.count)
                is DeleteTracksResult.Empty -> _notice.value = "Nada que eliminar"
                is DeleteTracksResult.Error -> _notice.value = result.message
                null -> _notice.value = "No se pudo eliminar"
            }
        }
    }

    fun onDeleteConsentResult(granted: Boolean) {
        if (!granted) return
        val tracks = pendingDelete
        val ids = tracks.map { it.id }.toSet()
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= 30) {
                onDeleted(tracks, tracks.size)
                return@launch
            }
            when (val result = runCatching { deleteTracks(tracks) }.getOrNull()) {
                is DeleteTracksResult.NeedConsent ->
                    _events.emit(LibraryEvent.RequestDeleteConsent(result.intentSender))
                is DeleteTracksResult.Deleted -> onDeleted(tracks, result.count)
                else -> {
                    getLocalTracks.dropCached(ids)
                    onDeleted(tracks, tracks.size)
                }
            }
        }
    }

    private suspend fun onDeleted(tracks: List<Track>, count: Int) {
        val ids = tracks.map { it.id }.toSet()
        runCatching { controls.removeFromQueue(ids) }
        runCatching { observePrefs.removeIds(ids) }
        getLocalTracks.dropCached(ids)
        runCatching { getLocalTracks.refresh() }
        exitSelection()
        pendingDelete = emptyList()
        _notice.value = if (count <= 1) "Archivo eliminado del dispositivo"
        else "$count archivos eliminados del dispositivo"
    }

    private fun playableTracks(): List<Track> {
        val ignored = uiState.value.ignoredFolders
        return allTracks.value.filter { !it.isVideo && it.folderPath !in ignored }
    }

    private fun buildState(
        tracks: List<Track>,
        prefs: PrefsSnap,
        filter: Filters,
    ): LibraryUiState {
        val songs = sortTracks(
            tracks.filter { !it.isVideo && (it.folderPath.isBlank() || it.folderPath !in prefs.ignoredFolders) },
            filter.sort,
        )
        val albumGroups = albumGroups(songs)
        val base = LibraryUiState(
            browse = filter.browse,
            sort = filter.sort,
            favoriteIds = prefs.favorites,
            playlists = prefs.playlists,
            ignoredFolders = prefs.ignoredFolders,
        )
        return when (filter.browse) {
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
                val liked = sortTracks(songs.filter { it.id in prefs.favorites }, filter.sort)
                base.copy(
                    visibleTracks = liked,
                    countLabel = if (liked.isEmpty()) "Sin canciones marcadas" else countLabel(liked),
                )
            }
            BrowseMode.RECENTS -> {
                val played = recentsList(songs, prefs.recents)
                val visible = if (filter.sort == SortMode.TITLE) played else sortTracks(played, filter.sort)
                base.copy(
                    visibleTracks = visible,
                    countLabel = if (visible.isEmpty()) "Aún no has reproducido nada" else countLabel(visible),
                )
            }
            BrowseMode.PLAYLISTS -> {
                val groups = prefs.playlists.map { playlist ->
                    val byId = songs.associateBy { it.id }
                    val list = playlist.trackIds.mapNotNull { byId[it] }
                    LibraryGroup(
                        key = playlistKey(playlist.id),
                        title = playlist.name,
                        subtitle = if (list.isEmpty()) "Vacía" else countLabel(list),
                        artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                        tracks = list,
                    )
                }
                val selected = filter.groupKey?.let { key -> groups.firstOrNull { it.key == key } }
                if (selected == null) {
                    base.copy(
                        showingGroups = true,
                        groups = groups,
                        countLabel = if (groups.isEmpty()) "Crea una lista" else "${groups.size} listas",
                    )
                } else {
                    detailState(base, selected, playlistId = selected.key.removePrefix(PLAYLIST_PREFIX))
                }
            }
            BrowseMode.FOLDERS,
            BrowseMode.ARTISTS,
            BrowseMode.ALBUMS,
            -> {
                val groups = when (filter.browse) {
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
                val selected = filter.groupKey?.let { key -> groups.firstOrNull { it.key == key } }
                if (selected == null) {
                    base.copy(
                        showingGroups = true,
                        groups = groups,
                        countLabel = when (filter.browse) {
                            BrowseMode.FOLDERS -> "${groups.size} carpetas"
                            BrowseMode.ARTISTS -> "${groups.size} artistas"
                            else -> "${groups.size} álbumes"
                        },
                    )
                } else {
                    detailState(base, selected)
                }
            }
        }
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

    private fun sortTracks(tracks: List<Track>, sort: SortMode): List<Track> {
        return when (sort) {
            SortMode.TITLE -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortMode.DATE_NEW -> tracks.sortedByDescending { it.dateAddedEpochSec }
            SortMode.DATE_OLD -> tracks.sortedBy { it.dateAddedEpochSec }
            SortMode.SIZE_LARGE -> tracks.sortedByDescending { it.sizeBytes }
            SortMode.SIZE_SMALL -> tracks.sortedBy { it.sizeBytes }
        }
    }

    private fun countLabel(tracks: List<Track>): String = "${tracks.size} canciones"

    private fun albumKey(track: Track): String = "${track.artist}\u0000${track.album}"

    private companion object {
        const val PLAYLIST_PREFIX = "pl:"
        fun playlistKey(id: String) = "$PLAYLIST_PREFIX$id"
    }
}

sealed interface LibraryEvent {
    data class RequestDeleteConsent(val sender: IntentSender) : LibraryEvent
}
