package com.example.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.GroupedLocalSearch
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.Track
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
import com.example.music.domain.usecase.SearchTracksUseCase
import com.example.music.domain.usecase.SearchYouTubeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTracks: SearchTracksUseCase,
    private val searchYouTube: SearchYouTubeUseCase,
    observePlayerState: ObservePlayerStateUseCase,
    private val prefs: ObserveLibraryPrefsUseCase,
    private val playTrack: PlayTrackUseCase,
    private val controls: ControlPlaybackUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _local = MutableStateFlow(GroupedLocalSearch())
    val local: StateFlow<GroupedLocalSearch> = _local.asStateFlow()

    private val _youtubeResults = MutableStateFlow<List<Track>>(emptyList())
    val youtubeResults: StateFlow<List<Track>> = _youtubeResults.asStateFlow()

    private val _showAllSongs = MutableStateFlow(false)
    val showAllSongs: StateFlow<Boolean> = _showAllSongs.asStateFlow()

    val hasYouTubeKey: Boolean = searchYouTube.hasApiKey()

    val history: StateFlow<List<String>> = prefs.searchHistory().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private var searchJob: Job? = null
    private var youtubeJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        _showAllSongs.value = false
        searchJob?.cancel()
        youtubeJob?.cancel()
        if (value.isBlank()) {
            _local.value = GroupedLocalSearch()
            _youtubeResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            _local.value = runCatching { searchTracks(value) }.getOrDefault(GroupedLocalSearch())
        }
        if (hasYouTubeKey) {
            youtubeJob = viewModelScope.launch {
                delay(400)
                _youtubeResults.value = runCatching { searchYouTube(value) }.getOrDefault(emptyList())
                    .take(5)
            }
        } else {
            _youtubeResults.value = emptyList()
        }
    }

    fun expandSongs() {
        _showAllSongs.value = true
    }

    fun applyHistory(value: String) {
        onQueryChange(value)
        rememberQuery(value)
    }

    fun removeHistory(value: String) {
        viewModelScope.launch { runCatching { prefs.removeSearchQuery(value) } }
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { prefs.clearSearchHistory() } }
    }

    fun play(track: Track) {
        rememberQuery(_query.value)
        viewModelScope.launch {
            val queue = if (track.source == PlaybackSource.WEB) {
                _youtubeResults.value.ifEmpty { listOf(track) }
            } else {
                _local.value.songs.ifEmpty { listOf(track) }
            }
            runCatching { playTrack(track, queue) }
        }
    }

    fun playGroup(group: LibraryGroup) {
        val track = group.tracks.firstOrNull() ?: return
        rememberQuery(_query.value)
        viewModelScope.launch {
            runCatching { playTrack(track, group.tracks) }
        }
    }

    fun playNext(track: Track) {
        if (track.isVideo) return
        rememberQuery(_query.value)
        controls.playNext(track)
    }

    fun searchOnYouTube() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        rememberQuery(q)
        viewModelScope.launch {
            val tracks = if (hasYouTubeKey) {
                _youtubeResults.value.ifEmpty {
                    runCatching { searchYouTube(q) }.getOrDefault(emptyList()).take(5)
                }
            } else {
                emptyList()
            }
            val queue = tracks.ifEmpty { listOf(searchYouTube.placeholder(q)) }
            runCatching { playTrack(queue.first(), queue) }
        }
    }

    private fun rememberQuery(value: String) {
        viewModelScope.launch { runCatching { prefs.recordSearch(value) } }
    }
}
