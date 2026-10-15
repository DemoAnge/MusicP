package com.example.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.Accent
import com.example.music.core.theme.SurfaceElevated
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlayerState
import com.example.music.domain.player.WebBridgeOps

@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onReopenBridge: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack
    val palette = rememberArtworkPalette(track)
    if (track == null) return
    val web = track.source == PlaybackSource.WEB
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .background(
                Brush.horizontalGradient(
                    colors = palette.take(2).ifEmpty { listOf(SurfaceElevated, SurfaceElevated) }
                        .map { it.copy(alpha = 0.55f) },
                ),
            ),
    ) {
        if (web && !playerState.webBridgeConnected) {
            Text(
                text = WebBridgeOps.TAP_TO_REOPEN,
                color = Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReopenBridge)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        ThinSeekBar(
            positionMs = playerState.positionMs,
            durationMs = playerState.durationMs,
            onSeek = onSeek,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenNowPlaying),
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
                        text = if (web) {
                            WebBridgeOps.sourceSubtitle(track.artist)
                        } else {
                            track.artist
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArtistGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = OnBackground)
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Accent),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = OnBackground)
            }
        }
    }
}
