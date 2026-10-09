package com.example.music.ui.driving

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.music.core.theme.Accent
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.ui.voice.VoiceMicButton
import com.example.music.ui.voice.rememberVoiceCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DrivingScreen(
    onClose: () -> Unit,
    viewModel: DrivingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val track = playerState.currentTrack
    val view = LocalView.current
    val capture = rememberVoiceCapture(
        onTranscript = viewModel::onSpoken,
        onStatus = viewModel::setVoiceStatus,
    )
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    BackHandler(onBack = onClose)
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(playerState.sleepEndsAtEpochMs) {
        val ends = playerState.sleepEndsAtEpochMs
        if (ends <= 0L) return@LaunchedEffect
        while (isActive && System.currentTimeMillis() < ends) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Salir del modo conducción",
                    tint = OnBackground,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            VoiceMicButton(capture = capture, size = 56.dp, iconSize = 32.dp)
        }
        val sleepLabel = sleepRemainingLabel(
            endsAt = playerState.sleepEndsAtEpochMs,
            atEndOfTrack = playerState.sleepAtEndOfTrack,
            now = now,
        )
        if (sleepLabel != null) {
            Text(sleepLabel, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = track?.title ?: "Nada en reproducción",
            color = OnBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = track?.artist ?: "Elige una canción y vuelve aquí",
            color = ArtistGray,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!voiceStatus.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = voiceStatus.orEmpty(),
                color = Accent,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrivingButton(
                onClick = viewModel::skipPrevious,
                size = 96.dp,
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = OnBackground,
                    modifier = Modifier.size(56.dp),
                )
            }
            DrivingButton(
                onClick = viewModel::togglePlayPause,
                size = 128.dp,
                filled = true,
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(72.dp),
                )
            }
            DrivingButton(
                onClick = viewModel::skipNext,
                size = 96.dp,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    tint = OnBackground,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun DrivingButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Accent else Color(0xFF222222))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

internal fun sleepRemainingLabel(endsAt: Long, atEndOfTrack: Boolean, now: Long): String? {
    if (atEndOfTrack) return "Duerme al final de la pista"
    if (endsAt <= 0L) return null
    val left = (endsAt - now).coerceAtLeast(0L)
    val minutes = left / 60_000L
    val seconds = (left % 60_000L) / 1_000L
    return "Duerme en %d:%02d".format(minutes, seconds)
}
