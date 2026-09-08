package com.example.music.ui.nowplaying

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.Background
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.SeekTrack
import com.example.music.core.theme.SpotifyGreen
import com.example.music.domain.model.LyricsLine
import com.example.music.domain.model.RepeatMode
import com.example.music.ui.components.AlbumArt
import com.example.music.ui.components.ThinSeekBar
import com.example.music.ui.components.formatMs

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val track = playerState.currentTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        if (track != null) {
            if (!track.artworkUri.isNullOrBlank() && track.artworkUri != "0") {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(70.dp) else Modifier),
                    alpha = 0.45f,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        palette.ifEmpty { listOf(Background, Background) }.map { it.copy(alpha = 0.72f) } + Background,
                    )
                ),
        )

        if (track == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Elige una canción en tu biblioteca", color = ArtistGray)
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            AlbumArt(track = track, size = 240.dp)
            val lines = lyrics?.lines.orEmpty()
            val plain = lyrics?.plainText
            val hasLyrics = lines.isNotEmpty() || !plain.isNullOrBlank()
            if (hasLyrics) {
                Spacer(Modifier.height(10.dp))
                if (lines.isNotEmpty()) {
                    SyncedLyricsView(
                        lines = lines,
                        positionMs = playerState.positionMs,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = plain.orEmpty(),
                        color = OnBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = ArtistGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.album,
                style = MaterialTheme.typography.labelMedium,
                color = ArtistGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hasLyrics) {
                Spacer(Modifier.weight(1f))
            }

            ThinSeekBar(
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                onSeek = viewModel::seekTo,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(playerState.positionMs), color = SeekTrack, style = MaterialTheme.typography.labelMedium)
                Text(formatMs(playerState.durationMs), color = SeekTrack, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (playerState.isShuffleEnabled) SpotifyGreen else OnBackground,
                    )
                }
                IconButton(onClick = viewModel::skipPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = OnBackground,
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = viewModel::skipNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Siguiente",
                        tint = OnBackground,
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = viewModel::cycleRepeat) {
                    Icon(
                        imageVector = if (playerState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repetir",
                        tint = if (playerState.repeatMode == RepeatMode.OFF) OnBackground else SpotifyGreen,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SyncedLyricsView(
    lines: List<LyricsLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val currentIndex = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem(currentIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lines) { index, line ->
            Text(
                text = line.text,
                color = if (index == currentIndex) SpotifyGreen else ArtistGray.copy(alpha = 0.7f),
                style = if (index == currentIndex) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }
    }
}
