package com.example.music.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.model.EqPreset
import com.example.music.domain.model.LocalAudioPrefs
import com.example.music.domain.usecase.ControlPlaybackUseCase
import com.example.music.domain.usecase.IgnoreFolderUseCase
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.SearchYouTubeUseCase
import com.example.music.player.web.BraveSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: ObserveLibraryPrefsUseCase,
    private val ignoreFolder: IgnoreFolderUseCase,
    private val controls: ControlPlaybackUseCase,
    searchYouTube: SearchYouTubeUseCase,
) : ViewModel() {

    val braveInstalled: Boolean = BraveSupport.installedPackage(context) != null
    val bravePackage: String? = BraveSupport.installedPackage(context)
    val hasYouTubeKey: Boolean = searchYouTube.hasApiKey()

    val preferBrave: StateFlow<Boolean> = prefs.preferBrave().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )

    val ignoredFolders: StateFlow<Set<String>> = prefs.ignoredFolders().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptySet(),
    )

    val scanPrefs = prefs.scanPrefs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.music.domain.model.LibraryScanPrefs(),
    )

    val playbackSpeed: StateFlow<Float> = prefs.playbackSpeed().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        1f,
    )

    val localAudio: StateFlow<LocalAudioPrefs> = prefs.localAudio().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LocalAudioPrefs(),
    )

    val karaoke: StateFlow<Boolean> = prefs.karaoke().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    fun unignoreFolder(path: String) {
        viewModelScope.launch { runCatching { ignoreFolder.restore(path) } }
    }

    fun setShowHidden(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setShowHidden(enabled) } }
    }

    fun setShowPodcasts(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setShowPodcasts(enabled) } }
    }

    fun setShowAudiobooks(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setShowAudiobooks(enabled) } }
    }

    fun setPreferBrave(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setPreferBrave(enabled) } }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { runCatching { prefs.setPlaybackSpeed(speed) } }
    }

    fun setSpokenSpeed(speed: Float) {
        viewModelScope.launch { runCatching { prefs.setSpokenSpeed(speed) } }
    }

    fun setNormalizeVolume(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setNormalizeVolume(enabled) } }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setCrossfadeEnabled(enabled) } }
    }

    fun setEqPreset(preset: EqPreset) {
        viewModelScope.launch { runCatching { prefs.setEqPreset(preset) } }
    }

    fun setKaraoke(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setKaraoke(enabled) } }
    }

    fun reopenBridge() = controls.reopenWebBridge()

    fun installBrave() {
        runCatching { context.startActivity(BraveSupport.playStoreIntent()) }
    }
}
