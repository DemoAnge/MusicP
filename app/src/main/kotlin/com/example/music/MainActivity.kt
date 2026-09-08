package com.example.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.music.core.theme.Background
import com.example.music.core.theme.MusicTheme
import com.example.music.domain.usecase.OpenIncomingMediaUseCase
import com.example.music.domain.usecase.PlayTrackUseCase
import com.example.music.ui.MusicApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playTrack: PlayTrackUseCase
    @Inject lateinit var openIncomingMedia: OpenIncomingMediaUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { enableEdgeToEdge() }
        super.onCreate(savedInstanceState)
        setContent {
            MusicTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    MusicApp()
                }
            }
        }
        runCatching { handleIncoming(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uri = incomingUri(intent) ?: return
        val mime = intent.type ?: contentResolver.getType(uri)
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        lifecycleScope.launch {
            runCatching {
                val track = openIncomingMedia(uri.toString(), mime)
                playTrack(track, listOf(track))
            }
        }
    }

    private fun incomingUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
    }
}
