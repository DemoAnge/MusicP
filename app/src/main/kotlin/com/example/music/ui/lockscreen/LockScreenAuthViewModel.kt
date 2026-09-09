package com.example.music.ui.lockscreen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.domain.usecase.ObserveLibraryPrefsUseCase
import com.example.music.domain.usecase.ObservePlayerStateUseCase
import com.example.music.player.MusicPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LockScreenAuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: ObserveLibraryPrefsUseCase,
    private val observePlayerState: ObservePlayerStateUseCase,
) : ViewModel() {

    private val notificationGranted = MutableStateFlow(hasNotificationPermission())

    val authorized: StateFlow<Boolean> = notificationGranted

    val showPrompt: StateFlow<Boolean> = combine(
        notificationGranted,
        prefs.lockScreenPromptDismissed(),
    ) { granted, dismissed ->
        Build.VERSION.SDK_INT >= 33 && !granted && !dismissed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showBanner: StateFlow<Boolean> = combine(
        notificationGranted,
        prefs.lockScreenPromptDismissed(),
    ) { granted, dismissed ->
        Build.VERSION.SDK_INT >= 33 && !granted && dismissed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refresh() {
        notificationGranted.value = hasNotificationPermission()
        val playing = observePlayerState().value.isPlaying
        if (notificationGranted.value && playing) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MusicPlaybackService::class.java),
                )
            }
        }
    }

    fun dismiss() {
        viewModelScope.launch { prefs.setLockScreenPromptDismissed(true) }
    }

    fun reshow() {
        viewModelScope.launch { prefs.setLockScreenPromptDismissed(false) }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
