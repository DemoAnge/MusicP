package com.example.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.BrowseMode
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.LibraryUiState
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.SortMode
import com.example.music.domain.model.Track
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.GetLocalTracksUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
import com.example.music.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLocalTracks: GetLocalTracksUseCase,
    observePlayerState: ObservePlayerStateUseCase,
    observePrefs: ObserveLibraryPrefsUseCase,
    private val playTrack: PlayTrackUseCase,
    private val controls: ControlPlaybackUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val browseMode = MutableStateFlow(BrowseMode.SONGS)
    private val sortMode = MutableStateFlow(SortMode.TITLE)
    private val selectedGroupKey = MutableStateFlow<String?>(null)

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

    val uiState: StateFlow<LibraryUiState> = combine(
        allTracks,
        observePrefs.favorites(),
        observePrefs.recents(),
        filters,
    ) { tracks, favorites, recents, filter ->
        runCatching { buildState(tracks, favorites, recents, filter) }
            .getOrDefault(LibraryUiState(favoriteIds = favorites))
    }.stateIn(
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
    }

    fun setSort(mode: SortMode) {
        sortMode.value = mode
    }

    fun openGroup(key: String) {
        selectedGroupKey.value = key
    }

    fun closeGroup() {
        selectedGroupKey.value = null
    }

    fun toggleLiked(trackId: String) {
        viewModelScope.launch { runCatching { toggleFavorite(trackId) } }
    }

    fun play(track: Track) {
        val queue = uiState.value.visibleTracks.ifEmpty { allTracks.value }
        viewModelScope.launch { runCatching { playTrack(track, queue) } }
    }

    fun playAll() {
        val list = uiState.value.visibleTracks.ifEmpty { allTracks.value }
        if (list.isEmpty()) return
        viewModelScope.launch { runCatching { playTrack(list.first(), list) } }
    }

    fun shuffleAll() {
        val list = uiState.value.visibleTracks.ifEmpty { allTracks.value }
        if (list.isEmpty()) return
        runCatching { controls.setShuffle(true) }
        viewModelScope.launch { runCatching { playTrack(list.random(), list) } }
    }

    private fun buildState(
        tracks: List<Track>,
        favorites: Set<String>,
        recents: List<String>,
        filter: Filters,
    ): LibraryUiState {
        val sortedAll = sortTracks(tracks, filter.sort)
        return when (filter.browse) {
            BrowseMode.SONGS -> LibraryUiState(
                browse = filter.browse,
                sort = filter.sort,
                visibleTracks = sortedAll,
                favoriteIds = favorites,
                countLabel = countLabel(sortedAll),
            )
            BrowseMode.FAVORITES -> {
                val liked = sortTracks(tracks.filter { it.id in favorites }, filter.sort)
                LibraryUiState(
                    browse = filter.browse,
                    sort = filter.sort,
                    visibleTracks = liked,
                    favoriteIds = favorites,
                    countLabel = if (liked.isEmpty()) "Sin canciones marcadas" else countLabel(liked),
                )
            }
            BrowseMode.RECENTS -> {
                val byId = tracks.associateBy { it.id }
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
                    BrowseMode.FOLDERS -> groupTracks(sortedAll, { it.folderPath.ifBlank { "Otras" } }) { first, list ->
                        LibraryGroup(
                            key = first.folderPath.ifBlank { "Otras" },
                            title = first.folderName.ifBlank { "Otras" },
                            subtitle = "${list.size} · ${first.folderPath.ifBlank { "Otras" }}",
                            artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                            tracks = list,
                        )
                    }
                    BrowseMode.ARTISTS -> groupTracks(sortedAll, { it.artist }) { first, list ->
                        LibraryGroup(
                            key = first.artist,
                            title = first.artist,
                            subtitle = countLabel(list),
                            artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                            tracks = list,
                        )
                    }
                    else -> groupTracks(sortedAll, { "${it.artist}\u0000${it.album}" }) { first, list ->
                        LibraryGroup(
                            key = "${first.artist}\u0000${first.album}",
                            title = first.album,
                            subtitle = "${first.artist} · ${list.size}",
                            artworkUri = list.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                            tracks = list,
                        )
                    }
                }.sortedBy { it.title.lowercase() }
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
                    LibraryUiState(
                        browse = filter.browse,
                        sort = filter.sort,
                        selectedGroupKey = selected.key,
                        selectedGroupTitle = selected.title,
                        visibleTracks = selected.tracks,
                        favoriteIds = favorites,
                        countLabel = countLabel(selected.tracks),
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

    private fun countLabel(tracks: List<Track>): String {
        val videos = tracks.count { it.isVideo }
        return if (videos == 0) "${tracks.size} canciones"
        else "${tracks.size - videos} canciones · $videos vídeos"
    }
}
