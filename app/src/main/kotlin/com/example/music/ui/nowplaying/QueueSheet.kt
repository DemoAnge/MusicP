package com.example.music.ui.nowplaying

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.core.theme.Accent
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.Surface
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.Track
import com.example.music.domain.player.WebBridgeOps
import com.example.music.ui.components.AlbumArt
import com.example.music.ui.components.PlayingBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playerState: PlayerState,
    onDismiss: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Cola", style = MaterialTheme.typography.titleLarge, color = OnBackground)
            Text(
                text = "${playerState.queue.size} canciones · aplasta el asa y arrastra la canción",
                style = MaterialTheme.typography.bodyMedium,
                color = ArtistGray,
            )
            Spacer(Modifier.height(12.dp))
            if (playerState.queue.isEmpty()) {
                Text("La cola está vacía.", color = ArtistGray, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        playerState.queue,
                        key = { _, track -> track.id },
                    ) { index, track ->
                        QueueRow(
                            track = track,
                            index = index,
                            isCurrent = index == playerState.queueIndex,
                            isPlaying = index == playerState.queueIndex && playerState.isPlaying,
                            isFirst = index == 0,
                            isLast = index == playerState.queue.lastIndex,
                            lastIndex = playerState.queue.lastIndex,
                            onPlay = { onPlayIndex(index) },
                            onRemove = { onRemove(track.id) },
                            onMove = onMove,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    lastIndex: Int,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { 64.dp.toPx() }
    val currentIndex by rememberUpdatedState(index)
    val endIndex by rememberUpdatedState(lastIndex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Aplastar y arrastrar",
            tint = ArtistGray,
            modifier = Modifier
                .size(48.dp)
                .pointerInput(track.id) {
                    var accum = 0f
                    var from = index
                    var end = lastIndex
                    detectDragGestures(
                        onDragStart = {
                            accum = 0f
                            from = currentIndex
                            end = endIndex
                        },
                        onDragEnd = { accum = 0f },
                        onDragCancel = { accum = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accum += amount.y
                            val steps = (accum / rowPx).toInt()
                            if (steps == 0) return@detectDragGestures
                            val target = (from + steps).coerceIn(0, end)
                            if (target != from) {
                                onMove(from, target)
                                from = target
                                accum -= steps * rowPx
                            }
                        },
                    )
                },
        )
        Spacer(Modifier.width(4.dp))
        AlbumArt(track = track, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Accent else OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (track.source == PlaybackSource.WEB) {
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
        if (isPlaying) {
            PlayingBars()
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { onMove(index, index - 1) }, enabled = !isFirst, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir", tint = OnBackground)
        }
        IconButton(onClick = { onMove(index, index + 1) }, enabled = !isLast, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar", tint = OnBackground)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar de la cola", tint = ArtistGray)
        }
    }
}
