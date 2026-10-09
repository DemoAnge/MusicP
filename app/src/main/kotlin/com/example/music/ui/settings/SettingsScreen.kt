package com.example.music.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.music.core.theme.ArtistGray
import com.example.music.core.theme.OnBackground
import com.example.music.core.theme.Accent
import com.example.music.core.theme.Surface

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val preferBrave by viewModel.preferBrave.collectAsStateWithLifecycle()
    val ignoredFolders by viewModel.ignoredFolders.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = OnBackground,
                )
            }
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, color = OnBackground)
        }
        Spacer(Modifier.height(16.dp))
        Text("Brave y YouTube", style = MaterialTheme.typography.titleMedium, color = OnBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (viewModel.braveInstalled) {
                "Brave está instalado${viewModel.bravePackage?.let { " ($it)" }.orEmpty()}."
            } else {
                "Brave no está en este teléfono. Instálalo para reproducir YouTube bajo el mando de la app."
            },
            color = ArtistGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!viewModel.braveInstalled) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::installBrave,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnBackground),
            ) {
                Text("Instalar Brave")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reproducir web en Brave", color = OnBackground, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Si está apagado, se abre el navegador predeterminado.",
                    color = ArtistGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = preferBrave,
                onCheckedChange = viewModel::setPreferBrave,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent),
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = viewModel::reopenBridge) {
            Text("Reabrir puente en el navegador")
        }
        Spacer(Modifier.height(24.dp))
        Text("Biblioteca", style = MaterialTheme.typography.titleMedium, color = OnBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Oculta una carpeta desde su pantalla (icono de ojo tachado). Aquí las vuelves a mostrar.",
            color = ArtistGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (ignoredFolders.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Ninguna carpeta oculta.", color = ArtistGray, style = MaterialTheme.typography.bodySmall)
        } else {
            ignoredFolders.sorted().forEach { path ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        path,
                        color = OnBackground,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.unignoreFolder(path) }) {
                        Text("Mostrar", color = Accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Búsqueda de YouTube", style = MaterialTheme.typography.titleMedium, color = OnBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (viewModel.hasYouTubeKey) {
                "Hay una clave de YouTube Data API v3 en local.properties. La búsqueda web usa resultados oficiales."
            } else {
                "Sin YOUTUBE_API_KEY en local.properties la app no lista videos. " +
                    "Escribe esa clave tú (no la pegues en el código) y recompila, o pega el enlace en la pestaña de Brave."
            },
            color = ArtistGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "El audio web suena en el navegador. La app no extrae ni descarga ese audio.",
            color = ArtistGray,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}
