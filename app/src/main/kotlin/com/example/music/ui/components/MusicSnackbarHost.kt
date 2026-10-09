package com.example.music.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.music.core.theme.Accent
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.SurfaceElevated

@Composable
fun MusicSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = SurfaceElevated,
            contentColor = OnBackground,
            actionColor = Accent,
            shape = RoundedCornerShape(8.dp),
        )
    }
}
