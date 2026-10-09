package com.example.music.ui.voice

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

internal fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun rememberActivityVoiceViewModel(): VoiceCommandViewModel {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    return if (activity != null) hiltViewModel(activity) else hiltViewModel()
}

@Composable
fun VoiceMicButton(
    state: VoiceUiState,
    onTap: () -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 26.dp,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onTap() else onPermissionDenied()
    }
    val active = state.phase != VoicePhase.Idle
    IconButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            when {
                state.phase != VoicePhase.Idle -> onTap()
                granted -> onTap()
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    state.listening -> Color(0xFF1DB954)
                    active -> Color(0xFF282828)
                    else -> Color.Transparent
                },
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = if (active) "Desactivar voz" else "Activar voz",
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
