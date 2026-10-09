package com.example.music.ui.nowplaying

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.Background
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.SeekTrack
import com.example.music.core.theme.Accent
import com.example.music.domain.model.LyricsLine
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.RepeatMode
import com.example.music.domain.model.SleepOption
import com.example.music.ui.components.AlbumArt
import com.example.music.ui.components.ThinSeekBar
import com.example.music.ui.components.formatMs
import com.example.music.ui.driving.sleepRemainingLabel
import com.example.music.ui.voice.VoiceMicButton
import com.example.music.ui.voice.rememberActivityVoiceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
    onOpenDriving: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val showLyrics by viewModel.showLyrics.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val voiceVm = rememberActivityVoiceViewModel()
    val voiceUi by voiceVm.ui.collectAsStateWithLifecycle()
    val openQueue by voiceVm.openQueue.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val track = playerState.currentTrack
    LaunchedEffect(openQueue) {
        if (openQueue) {
            showQueue = true
            voiceVm.consumeOpenQueue()
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                CloseRow(
                    onClose = onClose,
                    voice = {
                        VoiceMicButton(
                            state = voiceUi,
                            onTap = voiceVm::onMicTapped,
                            onPermissionDenied = voiceVm::onPermissionDenied,
                        )
                    },
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Elige una canción", style = MaterialTheme.typography.headlineSmall, color = OnBackground)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ábrela desde tu biblioteca. El mini reproductor te trae de vuelta aquí.",
                            color = ArtistGray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            return@Box
        }

        val lines = lyrics?.lines.orEmpty()
        val plain = lyrics?.plainText
        val hasLyrics = lines.isNotEmpty() || !plain.isNullOrBlank()
        val lyricsOpen = showLyrics && hasLyrics

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artSize = if (lyricsOpen) 120.dp else min(260.dp, maxHeight * 0.38f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = OnBackground,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                VoiceMicButton(
                    state = voiceUi,
                    onTap = voiceVm::onMicTapped,
                    onPermissionDenied = voiceVm::onPermissionDenied,
                )
                IconButton(onClick = { showQueue = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Cola",
                        tint = OnBackground,
                    )
                }
                IconButton(
                    onClick = viewModel::toggleLyrics,
                    modifier = Modifier.size(48.dp),
                    enabled = hasLyrics,
                ) {
                    Icon(
                        imageVector = if (lyricsOpen) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                        contentDescription = if (lyricsOpen) "Ocultar letra" else "Mostrar letra",
                        tint = when {
                            lyricsOpen -> Accent
                            hasLyrics -> OnBackground
                            else -> ArtistGray
                        },
                    )
                }
                IconButton(onClick = viewModel::toggleLiked, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de queridas" else "Marcar como querida",
                        tint = if (isFavorite) Accent else OnBackground,
                    )
                }
            }

            if (lyricsOpen) {
                AlbumArt(track = track, size = artSize)
                Spacer(Modifier.height(8.dp))
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
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AlbumArt(track = track, size = artSize)
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
            if (track.source == PlaybackSource.WEB) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Suena en Brave",
                    color = Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (!playerState.webBridgeConnected) {
                    Text(
                        text = "Vuelve a abrir Brave. Deja la pestaña del puente abierta.",
                        color = ArtistGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::reopenWebBridge) {
                        Text("Abrir Brave", color = Accent)
                    }
                } else if (playerState.webNeedsGesture) {
                    Text(
                        text = "Toca “Activar sonido” en Brave una vez.",
                        color = ArtistGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::reopenWebBridge) {
                        Text("Abrir Brave", color = Accent)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ThinSeekBar(
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                onSeek = viewModel::seekTo,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(playerState.positionMs), color = SeekTrack, style = MaterialTheme.typography.labelMedium)
                Text(formatMs(playerState.durationMs), color = SeekTrack, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportIcon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    onClick = viewModel::skipPrevious,
                    iconSize = 40.dp,
                )
                IconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Accent),
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                TransportIcon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    onClick = viewModel::skipNext,
                    iconSize = 40.dp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportIcon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Aleatorio",
                    onClick = viewModel::toggleShuffle,
                    tint = if (playerState.isShuffleEnabled) Accent else OnBackground,
                )
                TransportIcon(
                    imageVector = if (playerState.repeatMode == RepeatMode.ONE) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    },
                    contentDescription = "Repetir",
                    onClick = viewModel::cycleRepeat,
                    tint = if (playerState.repeatMode == RepeatMode.OFF) OnBackground else Accent,
                )
                TransportIcon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = "Temporizador de sueño",
                    onClick = { showSleep = true },
                    tint = if (playerState.sleepEndsAtEpochMs > 0L || playerState.sleepAtEndOfTrack) {
                        Accent
                    } else {
                        OnBackground
                    },
                )
                TransportIcon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = "Modo conducción",
                    onClick = onOpenDriving,
                )
            }
            SleepRemainingLabel(playerState = playerState)
            if (voiceUi.prompt.isNotBlank()) {
                Text(
                    voiceUi.prompt,
                    color = Accent,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        }
        if (showQueue) {
            QueueSheet(
                playerState = playerState,
                onDismiss = { showQueue = false },
                onPlayIndex = viewModel::playQueueIndex,
                onRemove = viewModel::removeFromQueue,
                onMove = viewModel::moveInQueue,
            )
        }
        if (showSleep) {
            SleepTimerDialog(
                onDismiss = { showSleep = false },
                onPick = { option ->
                    viewModel.setSleepTimer(option)
                    showSleep = false
                },
            )
        }
    }
}

@Composable
private fun SleepRemainingLabel(playerState: PlayerState) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playerState.sleepEndsAtEpochMs) {
        val ends = playerState.sleepEndsAtEpochMs
        if (ends <= 0L) return@LaunchedEffect
        while (isActive && System.currentTimeMillis() < ends) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sleepLabel = sleepRemainingLabel(
        endsAt = playerState.sleepEndsAtEpochMs,
        atEndOfTrack = playerState.sleepAtEndOfTrack,
        now = now,
    )
    if (sleepLabel != null) {
        Text(sleepLabel, color = Accent, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onPick: (SleepOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagar automáticamente") },
        text = {
            Column {
                SleepOption.entries.forEach { option ->
                    TextButton(onClick = { onPick(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(sleepOptionLabel(option), color = OnBackground)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Accent) }
        },
    )
}

private fun sleepOptionLabel(option: SleepOption): String = when (option) {
    SleepOption.OFF -> "Apagar"
    SleepOption.MINUTES_15 -> "15 minutos"
    SleepOption.MINUTES_30 -> "30 minutos"
    SleepOption.MINUTES_45 -> "45 minutos"
    SleepOption.MINUTES_60 -> "60 minutos"
    SleepOption.END_OF_TRACK -> "Fin de pista"
}

@Composable
private fun CloseRow(
    onClose: () -> Unit,
    voice: @Composable () -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Cerrar",
                tint = OnBackground,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        voice()
    }
}

@Composable
private fun TransportIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = OnBackground,
    iconSize: Dp = 28.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
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
                color = if (index == currentIndex) Accent else ArtistGray.copy(alpha = 0.7f),
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
