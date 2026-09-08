package com.example.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.Background
import com.example.music.core.theme.SpotifyGreen
import com.example.music.core.theme.Surface
import com.example.music.ui.components.MiniPlayer
import com.example.music.ui.library.LibraryScreen
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
) {
    val navController = rememberNavController()
    val playerState by playerBarViewModel.playerState.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        containerColor = Background,
        bottomBar = {
            Column {
                if (playerState.currentTrack != null && current?.route != Destinations.NowPlaying) {
                    MiniPlayer(
                        playerState = playerState,
                        onTogglePlay = playerBarViewModel::togglePlayPause,
                        onSkipNext = playerBarViewModel::skipNext,
                        onSkipPrevious = playerBarViewModel::skipPrevious,
                        onOpenNowPlaying = {
                            navController.navigate(Destinations.NowPlaying) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                NavigationBar(containerColor = Surface) {
                    val items = listOf(
                        Triple(Destinations.Library, "Biblioteca", Icons.Filled.LibraryMusic),
                        Triple(Destinations.Search, "Buscar", Icons.Filled.Search),
                        Triple(Destinations.NowPlaying, "Ahora", Icons.AutoMirrored.Filled.QueueMusic),
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
                                selectedIconColor = SpotifyGreen,
                                selectedTextColor = SpotifyGreen,
                                indicatorColor = Surface,
                                unselectedIconColor = ArtistGray,
                                unselectedTextColor = ArtistGray,
                            ),
                        )
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
            composable(Destinations.Library) { LibraryScreen() }
            composable(Destinations.Search) { SearchScreen() }
            composable(Destinations.NowPlaying) { NowPlayingScreen() }
        }
    }
}
