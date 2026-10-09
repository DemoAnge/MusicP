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
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.DeleteLocalTracksUseCase
import com.example.music.domain.usecase.GetLocalTracksUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
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
) : ViewModel() {

    private val browseMode = MutableStateFlow(BrowseMode.SONGS)
    private val sortMode = MutableStateFlow(SortMode.TITLE)
    private val selectedGroupKey = MutableStateFlow<String?>(null)
    private val selecting = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

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

    private val filters = combine(browseMode, sortMode, selectedGroupKey) { browse, sort, group ->
        Filters(browse, sort, group)
    }

    private val selection = combine(selecting, selectedIds) { on, ids -> on to ids }

    val uiState: StateFlow<LibraryUiState> = combine(
        allTracks,
        observePrefs.favorites(),
        observePrefs.recents(),
        filters,
        selection,
    ) { tracks, favorites, recents, filter, sel ->
        runCatching {
            buildState(tracks, favorites, recents, filter).copy(
                selecting = sel.first,
                selectedIds = sel.second,
            )
        }.getOrDefault(
            LibraryUiState(favoriteIds = favorites, selecting = sel.first, selectedIds = sel.second),
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryUiState(),
    )

    fun loadTracks() {
        viewModelScope.launch {
            runCatching { getLocalTracks.refresh() }
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

    fun closeGroup() {
        selectedGroupKey.value = null
        if (selecting.value) exitSelection()
    }

    fun toggleLiked(trackId: String) {
        viewModelScope.launch { runCatching { toggleFavorite(trackId) } }
    }

    fun play(track: Track) {
        val queue = uiState.value.visibleTracks.ifEmpty { allTracks.value.filter { !it.isVideo } }
        viewModelScope.launch { runCatching { playTrack(track, queue) } }
    }

    fun playAll() {
        val list = uiState.value.visibleTracks.ifEmpty { allTracks.value.filter { !it.isVideo } }
        if (list.isEmpty()) return
        viewModelScope.launch { runCatching { playTrack(list.first(), list) } }
    }

    fun shuffleAll() {
        val list = uiState.value.visibleTracks.ifEmpty { allTracks.value.filter { !it.isVideo } }
        if (list.isEmpty()) return
        runCatching { controls.setShuffle(true) }
        viewModelScope.launch { runCatching { playTrack(list.random(), list) } }
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

    private fun buildState(
        tracks: List<Track>,
        favorites: Set<String>,
        recents: List<String>,
        filter: Filters,
    ): LibraryUiState {
        val songs = sortTracks(tracks.filter { !it.isVideo }, filter.sort)
        return when (filter.browse) {
            BrowseMode.SONGS -> LibraryUiState(
                browse = filter.browse,
                sort = filter.sort,
                visibleTracks = songs,
                favoriteIds = favorites,
                countLabel = countLabel(songs),
            )
            BrowseMode.FAVORITES -> {
                val liked = sortTracks(tracks.filter { it.id in favorites && !it.isVideo }, filter.sort)
                LibraryUiState(
                    browse = filter.browse,
                    sort = filter.sort,
                    visibleTracks = liked,
                    favoriteIds = favorites,
                    countLabel = if (liked.isEmpty()) "Sin canciones marcadas" else countLabel(liked),
                )
            }
            BrowseMode.RECENTS -> {
                val byId = tracks.filter { !it.isVideo }.associateBy { it.id }
                val played = recents.mapNotNull { byId[it] }
                val visible = if (filter.sort == SortMode.TITLE) played else sortTracks(played, filter.sort)
                LibraryUiState(
                    browse = filter.browse,
                    sort = filter.sort,
                    visibleTracks = visible,
                    favoriteIds = favorites,
                    countLabel = if (visible.isEmpty()) "Aún no has reproducido nada" else countLabel(visible),
                )
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
                    else -> groupTracks(songs, { "${it.artist}\u0000${it.album}" }) { first, list ->
                        LibraryGroup(
                            key = "${first.artist}\u0000${first.album}",
                            title = first.album,
                            subtitle = "${first.artist} · ${list.size}",
                            artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                            tracks = list,
                        )
                    }
                }.distinctBy { it.key }.sortedBy { it.title.lowercase() }
                val selected = filter.groupKey?.let { key -> groups.firstOrNull { it.key == key } }
                if (selected == null) {
                    LibraryUiState(
                        browse = filter.browse,
                        sort = filter.sort,
                        showingGroups = true,
                        groups = groups,
                        favoriteIds = favorites,
                        countLabel = when (filter.browse) {
                            BrowseMode.FOLDERS -> "${groups.size} carpetas"
                            BrowseMode.ARTISTS -> "${groups.size} artistas"
                            else -> "${groups.size} álbumes"
                        },
                    )
                } else {
                    val visible = selected.tracks.distinctBy { it.mediaUri.ifBlank { it.id } }
                    LibraryUiState(
                        browse = filter.browse,
                        sort = filter.sort,
                        selectedGroupKey = selected.key,
                        selectedGroupTitle = selected.title,
                        visibleTracks = visible,
                        favoriteIds = favorites,
                        countLabel = countLabel(visible),
                    )
                }
            }
        }
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
}

sealed interface LibraryEvent {
    data class RequestDeleteConsent(val sender: IntentSender) : LibraryEvent
}
