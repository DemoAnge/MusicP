package com.example.music.widget

import android.content.Context
import com.example.music.player.PlayerCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Singleton
class PlaybackWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: PlayerCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        coordinator.state
            .map { state ->
                WidgetSnapshot(
                    trackId = state.currentTrack?.id,
                    title = state.currentTrack?.title.orEmpty(),
                    artist = state.currentTrack?.artist.orEmpty(),
                    artworkUri = state.currentTrack?.artworkUri,
                    isPlaying = state.isPlaying,
                )
            }
            .distinctUntilChanged()
            .onEach {
                runCatching { PlaybackWidgetProvider.updateAll(context, coordinator.state.value) }
            }
            .launchIn(scope)
    }

    private data class WidgetSnapshot(
        val trackId: String?,
        val title: String,
        val artist: String,
        val artworkUri: String?,
        val isPlaying: Boolean,
    )
}
