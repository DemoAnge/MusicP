package com.example.music.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.Background
import com.example.music.core.theme.Accent
import com.example.music.core.theme.Surface
import com.example.music.ui.components.MiniPlayer
import com.example.music.ui.library.LibraryScreen
import com.example.music.ui.lockscreen.LockScreenAuthScreen
import com.example.music.ui.lockscreen.LockScreenAuthViewModel
import com.example.music.ui.nowplaying.NowPlayingScreen
import com.example.music.ui.search.SearchScreen

private object Destinations {
    const val Library = "library"
    const val Search = "search"
    const val NowPlaying = "nowplaying"
}

@Composable
fun MusicApp(
    playerBarViewModel: PlayerBarViewModel = hiltViewModel(),
    lockScreenAuthViewModel: LockScreenAuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val playerState by playerBarViewModel.playerState.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onNowPlaying = current?.route == Destinations.NowPlaying
    val showLockPrompt by lockScreenAuthViewModel.showPrompt.collectAsStateWithLifecycle()
    val showLockBanner by lockScreenAuthViewModel.showBanner.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        lockScreenAuthViewModel.refresh()
        if (!it) lockScreenAuthViewModel.dismiss()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) lockScreenAuthViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun closeNowPlaying() {
        if (!navController.popBackStack()) {
            navController.navigate(Destinations.Library) { launchSingleTop = true }
        }
    }

    fun openNowPlaying() {
        navController.navigate(Destinations.NowPlaying) {
            launchSingleTop = true
        }
    }

    BackHandler(enabled = onNowPlaying) { closeNowPlaying() }

    LaunchedEffect(playerState.errorMessage) {
        val message = playerState.errorMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        snackbar.showSnackbar("No se pudo reproducir. $message")
        playerBarViewModel.clearError()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!onNowPlaying) {
                    Column {
                        if (playerState.currentTrack != null) {
                            MiniPlayer(
                                playerState = playerState,
                                onTogglePlay = playerBarViewModel::togglePlayPause,
                                onSkipNext = playerBarViewModel::skipNext,
                                onRewind = playerBarViewModel::rewind10,
                                onSeek = playerBarViewModel::seekTo,
                                onOpenNowPlaying = { openNowPlaying() },
                            )
                        }
                        NavigationBar(containerColor = Surface) {
                            val items = listOf(
                                Triple(Destinations.Library, "Biblioteca", Icons.Filled.LibraryMusic),
                                Triple(Destinations.Search, "Buscar", Icons.Filled.Search),
                            )
                            items.forEach { (route, label, icon) ->
                                val selected = current?.hierarchy?.any { it.route == route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(Destinations.Library) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Accent,
                                        selectedTextColor = Accent,
                                        indicatorColor = Surface,
                                        unselectedIconColor = ArtistGray,
                                        unselectedTextColor = ArtistGray,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destinations.Library,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable(Destinations.Library) {
                    LibraryScreen(
                        showLockScreenBanner = showLockBanner,
                        onEnableLockScreenControls = lockScreenAuthViewModel::reshow,
                    )
                }
                composable(Destinations.Search) { SearchScreen() }
                composable(Destinations.NowPlaying) {
                    NowPlayingScreen(onClose = { closeNowPlaying() })
                }
            }
        }
        if (showLockPrompt) {
            LockScreenAuthScreen(
                onAllow = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        lockScreenAuthViewModel.refresh()
                    }
                },
                onSkip = lockScreenAuthViewModel::dismiss,
            )
        }
    }
}
