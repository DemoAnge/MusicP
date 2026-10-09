package com.example.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.Track
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
import com.example.music.domain.usecase.SearchTracksUseCase
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
    observePlayerState: ObservePlayerStateUseCase,
    private val playTrack: PlayTrackUseCase,
    private val controls: ControlPlaybackUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Track>>(emptyList())
    val results: StateFlow<List<Track>> = _results.asStateFlow()

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            _results.value = runCatching { searchTracks(value) }.getOrDefault(emptyList())
        }
    }

    fun play(track: Track) {
        viewModelScope.launch {
            val queue = _results.value.filter { !it.isVideo }
            runCatching { playTrack(track, queue) }
        }
    }

    fun playNext(track: Track) {
        if (track.isVideo) return
        controls.playNext(track)
    }
}
