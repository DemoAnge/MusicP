package com.example.music.ui.nowplaying

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.example.music.core.theme.Background
import com.example.music.core.theme.Accent
import com.example.music.core.theme.Surface
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.SleepOption
import com.example.music.domain.model.SyncedLyrics
import com.example.music.domain.model.next
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.GetSyncedLyricsUseCase
import com.example.music.domain.usecase.HandleVoiceCommandUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observePlayerState: ObservePlayerStateUseCase,
    private val controls: ControlPlaybackUseCase,
    private val getSyncedLyrics: GetSyncedLyricsUseCase,
    observePrefs: ObserveLibraryPrefsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val handleVoice: HandleVoiceCommandUseCase,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = observePlayerState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlayerState(),
    )

    private val _lyrics = MutableStateFlow<SyncedLyrics?>(null)
    val lyrics: StateFlow<SyncedLyrics?> = _lyrics.asStateFlow()

    private val _palette = MutableStateFlow(listOf(Background, Surface))
    val palette: StateFlow<List<Color>> = _palette.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _voiceStatus = MutableStateFlow<String?>(null)
    val voiceStatus: StateFlow<String?> = _voiceStatus.asStateFlow()

    val isFavorite: StateFlow<Boolean> = combine(
        playerState.map { it.currentTrack?.id },
        observePrefs.favorites(),
    ) { trackId, favorites ->
        trackId != null && trackId in favorites
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            playerState
                .map { it.currentTrack?.id to it.currentTrack }
                .distinctUntilChanged()
                .collect { (_, track) ->
                    if (track == null) {
                        _lyrics.value = null
                        _showLyrics.value = false
                        _palette.value = listOf(Background, Surface)
                    } else {
                        _lyrics.value = runCatching { getSyncedLyrics(track) }.getOrNull()
                        _palette.value = runCatching { extractPalette(track.artworkUri) }
                            .getOrDefault(listOf(Background, Surface))
                    }
                }
        }
    }

    fun togglePlayPause() = controls.togglePlayPause()
    fun skipNext() = controls.skipNext()
    fun skipPrevious() = controls.skipPrevious()
    fun seekTo(positionMs: Long) = controls.seekTo(positionMs)
    fun rewind10() = controls.seekBy(-10_000L)
    fun forward10() = controls.seekBy(10_000L)
    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }
    fun toggleLiked() {
        val id = playerState.value.currentTrack?.id ?: return
        viewModelScope.launch { runCatching { toggleFavorite(id) } }
    }
    fun toggleShuffle() {
        controls.setShuffle(!playerState.value.isShuffleEnabled)
    }

    fun cycleRepeat() {
        controls.setRepeat(playerState.value.repeatMode.next())
    }

    fun playQueueIndex(index: Int) = controls.playQueueIndex(index)
    fun removeFromQueue(trackId: String) = controls.removeFromQueue(setOf(trackId))
    fun moveInQueue(fromIndex: Int, toIndex: Int) = controls.moveInQueue(fromIndex, toIndex)
    fun reopenWebBridge() = controls.reopenWebBridge()
    fun setPlaybackSpeed(speed: Float) = controls.setPlaybackSpeed(speed)
    fun setSleepTimer(option: SleepOption) = controls.setSleepTimer(option)

    fun onSpoken(text: String) {
        viewModelScope.launch {
            _voiceStatus.value = runCatching { handleVoice(text) }
                .getOrElse { it.message ?: "No se pudo" }
        }
    }

    fun setVoiceStatus(message: String) {
        _voiceStatus.value = message
    }

    private suspend fun extractPalette(artworkUri: String?): List<Color> = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) return@withContext listOf(Background, Surface)
        val bitmap = decodeBitmap(artworkUri) ?: return@withContext listOf(Background, Surface)
        val palette = Palette.from(bitmap).clearFilters().generate()
        val dominant = palette.dominantSwatch?.rgb?.let(::Color) ?: Background
        val dark = palette.darkMutedSwatch?.rgb?.let(::Color)
            ?: palette.darkVibrantSwatch?.rgb?.let(::Color)
            ?: Surface
        val accent = palette.vibrantSwatch?.rgb?.let(::Color) ?: Accent
        listOf(dominant.copy(alpha = 0.95f), dark.copy(alpha = 0.95f), accent.copy(alpha = 0.35f))
    }

    private fun decodeBitmap(artworkUri: String): Bitmap? {
        if (artworkUri.isBlank() || artworkUri == "0" || artworkUri.endsWith("/albumart/0")) return null
        return runCatching {
            when {
                artworkUri.startsWith("http") ->
                    java.net.URL(artworkUri).openStream().use { BitmapFactory.decodeStream(it) }
                artworkUri.startsWith("file:") -> {
                    val path = Uri.parse(artworkUri).path ?: return@runCatching null
                    BitmapFactory.decodeFile(path)
                }
                else -> context.contentResolver.openInputStream(Uri.parse(artworkUri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
        }.getOrNull()
    }
}
