package com.example.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.Track
import com.example.music.domain.usecase.ControlPlaybackUseCase
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
    private val playTrack: PlayTrackUseCase,
    private val controls: ControlPlaybackUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Track>>(emptyList())
    val results: StateFlow<List<Track>> = _results.asStateFlow()

    private val _youtubeResults = MutableStateFlow<List<Track>>(emptyList())
    val youtubeResults: StateFlow<List<Track>> = _youtubeResults.asStateFlow()

    val hasYouTubeKey: Boolean = searchYouTube.hasApiKey()

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private var searchJob: Job? = null
    private var youtubeJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        youtubeJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            _results.value = runCatching { searchTracks(value) }.getOrDefault(emptyList())
        }
        if (hasYouTubeKey && value.isNotBlank()) {
            youtubeJob = viewModelScope.launch {
                delay(400)
                val found = runCatching { searchYouTube(value) }.getOrDefault(emptyList())
                _youtubeResults.value = found.filter { it.mediaUri.startsWith("yt:") && !it.mediaUri.startsWith("ytsearch:") }
            }
        } else {
            _youtubeResults.value = emptyList()
        }
    }

    fun play(track: Track) {
        viewModelScope.launch {
            val queue = if (track.source == PlaybackSource.WEB) {
                _youtubeResults.value.ifEmpty { listOf(track) }
            } else {
                _results.value.filter { !it.isVideo }
            }
            runCatching { playTrack(track, queue) }
        }
    }

    fun playNext(track: Track) {
        if (track.isVideo) return
        controls.playNext(track)
    }

    fun searchOnYouTube() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            val tracks = if (hasYouTubeKey) {
                _youtubeResults.value.ifEmpty {
                    runCatching { searchYouTube(q) }.getOrDefault(emptyList())
                        .filter { it.mediaUri.startsWith("yt:") && !it.mediaUri.startsWith("ytsearch:") }
                }
            } else {
                emptyList()
            }
            val queue = tracks.ifEmpty { listOf(searchYouTube.placeholder(q)) }
            val track = queue.first()
            runCatching { playTrack(track, queue) }
        }
    }
}
