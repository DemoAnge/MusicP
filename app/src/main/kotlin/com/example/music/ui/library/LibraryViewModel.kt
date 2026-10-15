package com.example.music.ui.library

import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.library.LibraryCatalog
import com.example.music.domain.model.BrowseMode
import com.example.music.domain.model.DeleteTracksResult
import com.example.music.domain.library.TrackClassifier
import com.example.music.domain.model.LibraryBrowseQuery
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.LibraryPrefsSnapshot
import com.example.music.domain.model.LibraryUiState
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.SortMode
import com.example.music.domain.model.Track
import com.example.music.domain.usecase.BuildLibraryStateUseCase
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.DeleteLocalTracksUseCase
import com.example.music.domain.usecase.GetLocalTracksUseCase
import com.example.music.domain.usecase.IgnoreFolderUseCase
import com.example.music.domain.usecase.ManagePlaylistsUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
import com.example.music.domain.usecase.PlaylistMutation
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
    private val buildLibraryState: BuildLibraryStateUseCase,
    private val playlists: ManagePlaylistsUseCase,
    private val ignoreFolder: IgnoreFolderUseCase,
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
    private val pickingForPlaylist = MutableStateFlow(false)
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

    private val filters = combine(browseMode, sortMode, selectedGroupKey, pickingForPlaylist) {
            browse, sort, group, picking ->
        LibraryBrowseQuery(browse, sort, group, picking)
    }

    private val selection = combine(selecting, selectedIds) { on, ids -> on to ids }

    private val prefsSnap = combine(
        observePrefs.favorites(),
        observePrefs.recents(),
        observePrefs.playlists(),
        observePrefs.ignoredFolders(),
        observePrefs.scanPrefs(),
    ) { favorites, recents, playlists, ignored, scan ->
        LibraryPrefsSnapshot(favorites, recents, playlists, ignored, scan)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        allTracks,
        prefsSnap,
        filters,
        selection,
        _refreshing,
    ) { tracks, prefs, query, sel, refreshing ->
        runCatching {
            buildLibraryState(tracks, prefs, query).copy(
                selecting = sel.first,
                selectedIds = sel.second,
                refreshing = refreshing,
            )
        }.getOrDefault(
            LibraryUiState(
                favoriteIds = prefs.favorites,
                playlists = prefs.playlists,
                ignoredFolders = prefs.ignoredFolders,
                scan = prefs.scan,
                selecting = sel.first,
                selectedIds = sel.second,
                pickingForPlaylist = query.pickingForPlaylist,
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

    fun shiftBrowse(delta: Int) {
        val modes = BrowseMode.entries
        val index = modes.indexOf(browseMode.value)
        val next = modes.getOrNull(index + delta) ?: return
        if (next != browseMode.value) setBrowse(next)
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
        selectedGroupKey.value = LibraryCatalog.albumKey(track)
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
        if (selecting.value || pickingForPlaylist.value) exitSelection()
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

    fun playSelected() {
        val list = selectedTracks()
        val first = list.firstOrNull() ?: return
        viewModelScope.launch { runCatching { playTrack(first, list) } }
        _notice.value = if (list.size == 1) {
            "Reproduciendo 1 canción"
        } else {
            "Reproduciendo ${list.size} canciones"
        }
        exitSelection()
    }

    fun addSelectedToQueue() {
        val list = selectedTracks()
        if (list.isEmpty()) return
        controls.addToQueue(list)
        _notice.value = if (list.size == 1) {
            "Se agregó a la cola de reproducción"
        } else {
            "Se agregaron ${list.size} a la cola de reproducción"
        }
        exitSelection()
    }

    fun playSelectedNext() {
        val list = selectedTracks()
        if (list.isEmpty()) return
        controls.playNext(list)
        _notice.value = if (list.size == 1) {
            "Se reproducirá a continuación"
        } else {
            "Se reproducirán ${list.size} a continuación"
        }
        exitSelection()
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
            _notice.value = when (val result = runCatching { playlists.create(name, addTrackId) }.getOrNull()) {
                is PlaylistMutation.Created -> "Lista “${result.playlist.name}” creada"
                PlaylistMutation.BlankName -> "Ponle un nombre a la lista"
                else -> "No se pudo crear la lista"
            }
        }
    }

    fun deleteCurrentPlaylist() {
        val id = uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { playlists.delete(id) }
            closeGroup()
            _notice.value = "Lista eliminada"
        }
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            _notice.value = when (runCatching { playlists.add(playlistId, listOf(trackId)) }.getOrNull()) {
                is PlaylistMutation.Added -> "Agregada a la lista"
                PlaylistMutation.AlreadyPresent -> "Esas canciones ya están en la lista"
                else -> "No se pudo agregar a la lista"
            }
        }
    }

    fun startAddToPlaylist() {
        if (uiState.value.selectedPlaylistId.isNullOrBlank()) return
        pickingForPlaylist.value = true
        selecting.value = true
        selectedIds.value = emptySet()
    }

    fun confirmAddSelectedToPlaylist() {
        val playlistId = uiState.value.selectedPlaylistId ?: return
        val ids = selectedTracks().map { it.id }
        viewModelScope.launch {
            _notice.value = when (val result = runCatching { playlists.add(playlistId, ids) }.getOrNull()) {
                is PlaylistMutation.Added -> if (result.count == 1) {
                    "Se agregó a la lista"
                } else {
                    "Se agregaron ${result.count} a la lista"
                }
                PlaylistMutation.AlreadyPresent -> "Esas canciones ya están en la lista"
                else -> "No se pudo agregar a la lista"
            }
            exitSelection()
        }
    }

    fun removeFromCurrentPlaylist(trackId: String) {
        val id = uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { playlists.removeTrack(id, trackId) }
            _notice.value = "Quitada de la lista"
        }
    }

    fun removeSelectedFromPlaylist() {
        val id = uiState.value.selectedPlaylistId ?: return
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (runCatching { playlists.removeTracks(id, ids) }.getOrNull()) {
                PlaylistMutation.TrackRemoved -> _notice.value = if (ids.size == 1) {
                    "Quitada de la lista"
                } else {
                    "${ids.size} quitadas de la lista"
                }
                else -> _notice.value = "No se pudo quitar de la lista"
            }
            exitSelection()
        }
    }

    fun renameCurrentPlaylist(name: String) {
        val id = uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            _notice.value = when (val result = runCatching { playlists.rename(id, name) }.getOrNull()) {
                is PlaylistMutation.Renamed -> "Lista “${result.playlist.name}”"
                PlaylistMutation.BlankName -> "Ponle un nombre a la lista"
                else -> "No se pudo renombrar la lista"
            }
        }
    }

    fun movePlaylistTrack(fromIndex: Int, toIndex: Int) {
        val id = uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { playlists.reorderTracks(id, fromIndex, toIndex) }
        }
    }

    fun ignoreCurrentFolder() {
        val path = uiState.value.visibleTracks.firstOrNull()?.folderPath.orEmpty()
        viewModelScope.launch {
            if (!runCatching { ignoreFolder.ignore(path) }.getOrDefault(false)) return@launch
            closeGroup()
            _notice.value = "Carpeta oculta. Puedes mostrarla otra vez en Ajustes."
        }
    }

    fun unignoreFolder(path: String) {
        viewModelScope.launch { runCatching { ignoreFolder.restore(path) } }
    }

    fun enterSelection() {
        selecting.value = true
    }

    fun exitSelection() {
        selecting.value = false
        selectedIds.value = emptySet()
        pickingForPlaylist.value = false
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
        if (selectedIds.value.isEmpty() && !pickingForPlaylist.value) selecting.value = false
    }

    fun toggleSelectGroup(group: LibraryGroup) {
        val ids = group.tracks.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        selecting.value = true
        selectedIds.update { current ->
            if (ids.all { it in current }) current - ids else current + ids
        }
        if (selectedIds.value.isEmpty() && !pickingForPlaylist.value) selecting.value = false
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

    private fun selectedTracks(): List<Track> {
        val ids = selectedIds.value
        if (ids.isEmpty()) return emptyList()
        val snapshot = uiState.value
        val ordered = if (snapshot.showingGroups) {
            snapshot.groups.flatMap { it.tracks }
        } else {
            snapshot.visibleTracks
        }.filter { it.id in ids && !it.isVideo }
        val extra = allTracks.value.filter { it.id in ids && !it.isVideo }
        val seen = mutableSetOf<String>()
        val result = ArrayList<Track>(ids.size)
        (ordered + extra).forEach { track ->
            if (seen.add(track.id)) result.add(track)
        }
        return result
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
        val snapshot = uiState.value
        return allTracks.value.filter { track ->
            TrackClassifier.includeInLibrary(
                track = track,
                ignoredFolders = snapshot.ignoredFolders,
                showHidden = snapshot.scan.showHidden,
                showPodcasts = snapshot.scan.showPodcasts,
                showAudiobooks = snapshot.scan.showAudiobooks,
            )
        }
    }
}

sealed interface LibraryEvent {
    data class RequestDeleteConsent(val sender: IntentSender) : LibraryEvent
}
