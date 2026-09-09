package com.example.music.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle

import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.SpotifyGreen
import com.example.music.core.theme.Surface
import com.example.music.core.theme.SurfaceElevated
import com.example.music.domain.model.BrowseMode
import com.example.music.domain.model.LibraryGroup
import com.example.music.domain.model.SortMode
import com.example.music.domain.model.Track
import com.example.music.ui.components.AlbumArt
import com.example.music.ui.components.PlayingBars
import com.example.music.ui.components.formatBytes
import com.example.music.ui.components.formatMs

private val DeleteRed = Color(0xFFE53935)

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    showLockScreenBanner: Boolean = false,
    onEnableLockScreenControls: () -> Unit = {},
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            buildList {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    fun granted(): Boolean = mediaPermissions.any { permission ->
        permission != Manifest.permission.WRITE_EXTERNAL_STORAGE &&
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(granted()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = mediaPermissions.any { permission ->
            permission != Manifest.permission.WRITE_EXTERNAL_STORAGE && result[permission] == true
        } || granted()
        if (hasPermission) viewModel.loadTracks()
    }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    fun requestPermissions() {
        permissionLauncher.launch(mediaPermissions.toTypedArray())
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadTracks() else requestPermissions()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.RequestDeleteConsent -> {
                    runCatching {
                        deleteConsentLauncher.launch(IntentSenderRequest.Builder(event.sender).build())
                    }
                }
            }
        }
    }

    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeNotice()
    }

    BackHandler(enabled = ui.selecting || ui.selectedGroupKey != null) {
        if (ui.selecting) viewModel.exitSelection() else viewModel.closeGroup()
    }

    val showSize = ui.sort == SortMode.SIZE_LARGE || ui.sort == SortMode.SIZE_SMALL

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ui.selecting) {
                    IconButton(onClick = viewModel::exitSelection) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar selección", tint = OnBackground)
                    }
                } else if (ui.selectedGroupKey != null) {
                    IconButton(onClick = viewModel::closeGroup) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = OnBackground,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            ui.selecting -> {
                                val n = ui.selectedIds.size
                                if (n == 0) "Seleccionar" else if (n == 1) "1 seleccionada" else "$n seleccionadas"
                            }
                            else -> ui.selectedGroupTitle ?: "Tu biblioteca"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (ui.selecting) {
                            "Toca para marcar. Se borran del teléfono."
                        } else {
                            ui.countLabel.ifBlank { "Música y vídeo de este teléfono" }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArtistGray,
                    )
                }
                if (ui.selecting) {
                    IconButton(onClick = viewModel::selectAllVisible) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Seleccionar todo", tint = OnBackground)
                    }
                    IconButton(
                        onClick = { if (ui.selectedIds.isNotEmpty()) showDeleteConfirm = true },
                        enabled = ui.selectedIds.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar del dispositivo",
                            tint = if (ui.selectedIds.isEmpty()) ArtistGray else DeleteRed,
                        )
                    }
                } else {
                    IconButton(onClick = viewModel::enterSelection) {
                        Icon(Icons.Filled.Checklist, contentDescription = "Seleccionar", tint = OnBackground)
                    }
                    SortMenu(current = ui.sort, onSelect = viewModel::setSort)
                }
            }
            if (showLockScreenBanner && !ui.selecting) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .clickable(onClick = onEnableLockScreenControls)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Activa play, anterior y siguiente en bloqueo y en la notificación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Permitir", color = SpotifyGreen, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (!ui.selecting) {
                Spacer(Modifier.height(8.dp))
                BrowseChips(
                    selected = ui.browse,
                    onSelect = viewModel::setBrowse,
                )
            }
            Spacer(Modifier.height(12.dp))

            when {
                !hasPermission -> {
                    EmptyMessage(
                        modifier = Modifier.weight(1f),
                        title = "Permiso de audio",
                        body = "Para armar tu biblioteca necesitamos acceso al audio y al vídeo de este teléfono, incluida la carpeta Descargas.",
                        action = "Conceder permiso",
                        onAction = { requestPermissions() },
                    )
                }
                ui.showingGroups && ui.groups.isEmpty() -> {
                    EmptyMessage(
                        modifier = Modifier.weight(1f),
                        title = "Sin resultados",
                        body = "No hay audio ni vídeo visibles. Descarga un archivo al teléfono o ábrelo desde Descargas y pulsa actualizar.",
                        action = "Actualizar",
                        onAction = { viewModel.loadTracks() },
                    )
                }
                !ui.showingGroups && ui.visibleTracks.isEmpty() -> {
                    val (title, body) = when (ui.browse) {
                        BrowseMode.FAVORITES -> "Sin queridas" to "Toca el corazón de una canción para guardarla aquí."
                        BrowseMode.RECENTS -> "Sin recientes" to "Las canciones que reproduzcas aparecerán en esta lista."
                        else -> "Sin canciones" to "No hay audio ni vídeo visibles. Descarga un archivo al teléfono o ábrelo desde Descargas y pulsa actualizar."
                    }
                    EmptyMessage(
                        modifier = Modifier.weight(1f),
                        title = title,
                        body = body,
                        action = "Actualizar",
                        onAction = { viewModel.loadTracks() },
                    )
                }
                else -> {
                    if (!ui.selecting && !ui.showingGroups) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::playAll,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpotifyGreen,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Reproducir")
                            }
                            OutlinedButton(onClick = viewModel::shuffleAll) {
                                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = SpotifyGreen)
                                Spacer(Modifier.width(4.dp))
                                Text("Aleatorio", color = OnBackground)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (ui.showingGroups) {
                            itemsIndexed(
                                ui.groups,
                                key = { index, group -> "g:$index:${group.key}" },
                            ) { _, group ->
                                val groupSelected = group.tracks.isNotEmpty() &&
                                    group.tracks.all { it.id in ui.selectedIds }
                                GroupRow(
                                    group = group,
                                    selecting = ui.selecting,
                                    selected = groupSelected,
                                    onClick = {
                                        if (ui.selecting) viewModel.toggleSelectGroup(group)
                                        else viewModel.openGroup(group.key)
                                    },
                                    onLongClick = { viewModel.toggleSelectGroup(group) },
                                )
                            }
                        } else {
                            itemsIndexed(
                                ui.visibleTracks,
                                key = { index, track -> "t:$index:${track.id}:${track.mediaUri}" },
                            ) { _, track ->
                                val isCurrent = playerState.currentTrack?.id == track.id
                                TrackRow(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && playerState.isPlaying,
                                    isFavorite = track.id in ui.favoriteIds,
                                    showSize = showSize,
                                    selecting = ui.selecting,
                                    selected = track.id in ui.selectedIds,
                                    onClick = {
                                        if (ui.selecting) viewModel.toggleSelectTrack(track.id)
                                        else viewModel.play(track)
                                    },
                                    onLongClick = {
                                        if (ui.selecting) viewModel.toggleSelectTrack(track.id)
                                        else viewModel.startSelection(track.id)
                                    },
                                    onToggleFavorite = { viewModel.toggleLiked(track.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showDeleteConfirm) {
        val n = ui.selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceElevated,
            title = { Text("Eliminar del dispositivo", color = OnBackground) },
            text = {
                Text(
                    text = if (n == 1) {
                        "Se borrará 1 archivo de este teléfono. No se puede deshacer."
                    } else {
                        "Se borrarán $n archivos de este teléfono. No se puede deshacer."
                    },
                    color = ArtistGray,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelected()
                    },
                ) {
                    Text("Eliminar", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = OnBackground)
                }
            },
        )
    }
}

@Composable
private fun BrowseChips(
    selected: BrowseMode,
    onSelect: (BrowseMode) -> Unit,
) {
    val chips = listOf(
        BrowseMode.SONGS to "Canciones",
        BrowseMode.FOLDERS to "Carpetas",
        BrowseMode.ARTISTS to "Artistas",
        BrowseMode.ALBUMS to "Álbumes",
        BrowseMode.FAVORITES to "Queridas",
        BrowseMode.RECENTS to "Recientes",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen,
                    selectedLabelColor = Color.White,
                    containerColor = SurfaceElevated,
                    labelColor = OnBackground,
                ),
            )
        }
    }
}

@Composable
private fun SortMenu(
    current: SortMode,
    onSelect: (SortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        SortMode.TITLE to "Título A–Z",
        SortMode.DATE_NEW to "Más recientes",
        SortMode.DATE_OLD to "Más antiguas",
        SortMode.SIZE_LARGE to "Más pesadas",
        SortMode.SIZE_SMALL to "Más livianas",
    )
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar", tint = OnBackground)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SurfaceElevated,
        ) {
            options.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (mode == current) SpotifyGreen else OnBackground,
                        )
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: LibraryGroup,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) SurfaceElevated else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (selected) "Seleccionada" else "No seleccionada",
                tint = if (selected) SpotifyGreen else ArtistGray,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
            )
        }
        AlbumArt(
            artworkUri = group.artworkUri,
            contentDescription = group.title,
            size = 56.dp,
            trackId = group.tracks.firstOrNull()?.id,
            mediaUri = group.tracks.firstOrNull()?.mediaUri,
            isVideo = group.tracks.firstOrNull()?.isVideo == true,
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
        if (!selecting) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ArtistGray,
            )
        }
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = OnBackground)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = ArtistGray)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.White),
            ) {
                Text(action)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    showSize: Boolean = false,
    selecting: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected || isCurrent) SurfaceElevated else Color.Transparent)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (selected) "Seleccionada" else "No seleccionada",
                tint = if (selected) SpotifyGreen else ArtistGray,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
            )
        }
        AlbumArt(track = track, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) SpotifyGreen else OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (track.isVideo) append("Vídeo · ")
                    append(track.artist)
                    append(" · ")
                    append(formatMs(track.durationMs))
                    if (showSize) {
                        append(" · ")
                        append(formatBytes(track.sizeBytes))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ArtistGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selecting && onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de queridas" else "Marcar como querida",
                    tint = if (isFavorite) SpotifyGreen else ArtistGray,
                )
            }
        }
        if (isPlaying) {
            PlayingBars()
        }
    }
}
