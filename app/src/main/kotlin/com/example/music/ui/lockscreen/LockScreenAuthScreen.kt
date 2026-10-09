package com.example.music.ui.lockscreen

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.Background
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.Accent
import com.example.music.core.theme.Surface
import com.example.music.core.theme.SurfaceElevated

@Composable
fun LockScreenAuthScreen(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onSkip)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pantalla de bloqueo y notificación",
            style = MaterialTheme.typography.headlineSmall,
            color = OnBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "La notificación y la pantalla de bloqueo las pinta el sistema (como un widget). Permite las notificaciones para ver carátula, play, anterior y siguiente. En la pantalla de inicio también puedes añadir el widget Reproductor.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArtistGray,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        LockScreenPreview()
        Spacer(Modifier.height(16.dp))
        NotificationPreview()
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.White,
            ),
        ) {
            Text("Permitir controles")
        }
        TextButton(onClick = onSkip) {
            Text("Ahora no", color = ArtistGray)
        }
    }
}

@Composable
private fun LockScreenPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pantalla de bloqueo", style = MaterialTheme.typography.labelMedium, color = ArtistGray)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Album,
                contentDescription = null,
                tint = ArtistGray,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Tu canción", style = MaterialTheme.typography.titleMedium, color = OnBackground)
        Text("Artista", style = MaterialTheme.typography.bodyMedium, color = ArtistGray)
        Spacer(Modifier.height(12.dp))
        PlaybackButtons()
    }
}

@Composable
private fun NotificationPreview() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Album, contentDescription = null, tint = ArtistGray, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Notificación", style = MaterialTheme.typography.labelSmall, color = ArtistGray)
            Text("Tu canción", style = MaterialTheme.typography.titleSmall, color = OnBackground)
            Text("Artista", style = MaterialTheme.typography.bodySmall, color = ArtistGray)
        }
        PlaybackButtons(compact = true)
    }
}

@Composable
private fun PlaybackButtons(compact: Boolean = false) {
    val icon = if (compact) 22.dp else 28.dp
    val play = if (compact) 36.dp else 52.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = OnBackground, modifier = Modifier.size(icon))
        Box(
            modifier = Modifier
                .size(play)
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(if (compact) 22.dp else 30.dp),
            )
        }
        Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = OnBackground, modifier = Modifier.size(icon))
    }
}
