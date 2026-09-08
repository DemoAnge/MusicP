package com.example.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.SurfaceElevated
import com.example.music.data.local_music.EmbeddedArtwork
import com.example.music.domain.model.Track

@Composable
fun AlbumArt(
    artworkUri: String?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    trackId: String? = null,
    mediaUri: String? = null,
    isVideo: Boolean = false,
) {
    val context = LocalContext.current
    var resolved by remember(trackId, mediaUri, artworkUri) { mutableStateOf<String?>(null) }
    var failed by remember(trackId, resolved) { mutableStateOf(false) }

    LaunchedEffect(trackId, mediaUri) {
        resolved = if (!trackId.isNullOrBlank() && !mediaUri.isNullOrBlank()) {
            runCatching { EmbeddedArtwork.resolve(context, trackId, mediaUri, isVideo) }.getOrNull()
        } else {
            null
        } ?: artworkUri?.takeIf { uri ->
            uri.isNotBlank() && uri != "0" && !uri.endsWith("/albumart/0") && !uri.contains("/albumart/")
        }
    }

    val model = resolved
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (model.isNullOrBlank() || failed) {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = contentDescription,
                tint = ArtistGray,
                modifier = Modifier.size(size / 2),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .memoryCacheKey(trackId ?: model)
                    .diskCacheKey(trackId ?: model)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { _: AsyncImagePainter.State.Error -> failed = true },
            )
        }
    }
}

@Composable
fun AlbumArt(
    track: Track,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AlbumArt(
        artworkUri = track.artworkUri,
        contentDescription = track.title,
        size = size,
        modifier = modifier,
        trackId = track.id,
        mediaUri = track.mediaUri,
        isVideo = track.isVideo,
    )
}
