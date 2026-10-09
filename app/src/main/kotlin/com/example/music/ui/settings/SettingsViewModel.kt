package com.example.music.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.usecase.ControlPlaybackUseCase
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

    fun setPreferBrave(enabled: Boolean) {
        viewModelScope.launch { runCatching { prefs.setPreferBrave(enabled) } }
    }

    fun reopenBridge() = controls.reopenWebBridge()

    fun installBrave() {
        runCatching { context.startActivity(BraveSupport.playStoreIntent()) }
    }
}
