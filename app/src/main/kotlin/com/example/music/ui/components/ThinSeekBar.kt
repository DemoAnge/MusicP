package com.example.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.music.core.theme.SeekInactive
import com.example.music.core.theme.SpotifyGreen
import kotlin.math.roundToInt

@Composable
fun ThinSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    var draggingFraction by remember { mutableFloatStateOf(-1f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val thumbPx = with(density) { 10.dp.toPx() }

    val fraction = if (draggingFraction >= 0f) {
        draggingFraction
    } else {
        (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun fractionAt(x: Float): Float =
        ((x - thumbPx / 2f) / (barWidthPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { barWidthPx = it.width.toFloat() }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    onSeek((fractionAt(offset.x) * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> draggingFraction = fractionAt(offset.x) },
                    onDragEnd = {
                        onSeek((draggingFraction.coerceIn(0f, 1f) * duration).toLong())
                        draggingFraction = -1f
                    },
                    onDragCancel = { draggingFraction = -1f },
                    onHorizontalDrag = { change, _ ->
                        draggingFraction = fractionAt(change.position.x)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(SeekInactive),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(SpotifyGreen),
        )
        Box(
            modifier = Modifier
                .offset {
                    val travel = (barWidthPx - thumbPx).coerceAtLeast(0f)
                    IntOffset((fraction * travel).roundToInt(), 0)
                }
                .size(10.dp)
                .clip(CircleShape)
                .background(SpotifyGreen),
        )
    }
}
