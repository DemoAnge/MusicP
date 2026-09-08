package com.example.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.SeekInactive
import com.example.music.core.theme.SpotifyGreen
import com.example.music.core.theme.SurfaceElevated
import com.example.music.domain.model.PlayerState

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack ?: return
    val progress = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .clickable(onClick = onOpenNowPlaying),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = SpotifyGreen,
            trackColor = SeekInactive,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(track = track, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArtistGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSkipPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = OnBackground)
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = OnBackground)
            }
        }
    }
}
