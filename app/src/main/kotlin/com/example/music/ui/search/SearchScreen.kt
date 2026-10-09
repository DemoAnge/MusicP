package com.example.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.Accent
import com.example.music.core.theme.Surface
import com.example.music.core.theme.SurfaceElevated
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.PlayerState
import com.example.music.domain.model.Track
import com.example.music.ui.components.AlbumArt
import com.example.music.ui.library.TrackRow

private const val SONGS_PREVIEW = 8

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val local by viewModel.local.collectAsStateWithLifecycle()
    val youtubeResults by viewModel.youtubeResults.collectAsStateWithLifecycle()
    val showAllSongs by viewModel.showAllSongs.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Buscar", style = MaterialTheme.typography.headlineSmall, color = OnBackground)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Canciones, artistas, álbumes o YouTube") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() },
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda", tint = ArtistGray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = SurfaceElevated,
                focusedContainerColor = SurfaceElevated,
                unfocusedContainerColor = SurfaceElevated,
                focusedTextColor = OnBackground,
                unfocusedTextColor = OnBackground,
                cursorColor = Accent,
                focusedPlaceholderColor = ArtistGray,
                unfocusedPlaceholderColor = ArtistGray,
            ),
        )
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            HistoryPane(
                history = history,
                onSelect = viewModel::applyHistory,
                onRemove = viewModel::removeHistory,
                onClear = viewModel::clearHistory,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (local.isEmpty) {
                    item {
                        Text(
                            "Sin resultados locales para “$query”.",
                            color = ArtistGray,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                if (local.songs.isNotEmpty()) {
                    item { SectionLabel("Canciones") }
                    val visible = if (showAllSongs) local.songs else local.songs.take(SONGS_PREVIEW)
                    items(visible, key = { "s:${it.id}:${it.mediaUri}" }) { track ->
                        ResultTrack(track, playerState, viewModel::play, viewModel::playNext)
                    }
                    if (!showAllSongs && local.songs.size > SONGS_PREVIEW) {
                        item {
                            TextButton(onClick = viewModel::expandSongs) {
                                Text("Mostrar todas (${local.songs.size})", color = Accent)
                            }
                        }
                    }
                }
                if (local.artists.isNotEmpty()) {
                    item { SectionLabel("Artistas") }
                    items(local.artists, key = { it.key }) { group ->
                        SearchGroupRow(group = group, onClick = { viewModel.playGroup(group) })
                    }
                }
                if (local.albums.isNotEmpty()) {
                    item { SectionLabel("Álbumes") }
                    items(local.albums, key = { it.key }) { group ->
                        SearchGroupRow(group = group, onClick = { viewModel.playGroup(group) })
                    }
                }
                item { SectionLabel("YouTube · Brave") }
                if (youtubeResults.isNotEmpty()) {
                    items(youtubeResults, key = { "yt:${it.id}" }) { track ->
                        ResultTrack(track, playerState, viewModel::play, viewModel::playNext)
                    }
                }
                item {
                    Button(
                        onClick = viewModel::searchOnYouTube,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = OnBackground,
                        ),
                    ) {
                        Text(
                            if (viewModel.hasYouTubeKey && youtubeResults.isNotEmpty()) {
                                "Reproducir en Brave"
                            } else {
                                "Buscar en Brave"
                            },
                        )
                    }
                    if (!viewModel.hasYouTubeKey) {
                        Text(
                            "Sin clave de Data API: se abre Brave y pegas el enlace en el puente.",
                            color = ArtistGray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPane(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        Hint("Escribe para buscar en este teléfono. Si no está, ábrelo en YouTube con Brave.")
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recientes", style = MaterialTheme.typography.titleSmall, color = OnBackground)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) {
                Text("Borrar", color = ArtistGray)
            }
        }
        history.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(item) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = ArtistGray,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item,
                    color = OnBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = ArtistGray)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OnBackground,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultTrack(
    track: Track,
    playerState: PlayerState,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
) {
    val isCurrent = playerState.currentTrack?.id == track.id
    TrackRow(
        track = track,
        isCurrent = isCurrent,
        isPlaying = isCurrent && playerState.isPlaying,
        onClick = { onPlay(track) },
        onPlayNext = { onPlayNext(track) },
    )
}

@Composable
private fun SearchGroupRow(
    group: LibraryGroup,
    onClick: () -> Unit,
) {
    val first = group.tracks.firstOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(
            artworkUri = group.artworkUri,
            contentDescription = group.title,
            size = 52.dp,
            trackId = first?.id,
            mediaUri = first?.mediaUri,
            isVideo = first?.isVideo == true,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ArtistGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ArtistGray,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            color = ArtistGray,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
