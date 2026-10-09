package com.example.music.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = ArtistGray,
    outline = SeekTrack,
    inverseSurface = SurfaceElevated,
    inverseOnSurface = OnBackground,
    inversePrimary = Accent,
)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MusicTypography,
        content = content,
    )
}
