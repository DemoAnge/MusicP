package com.example.music.domain.usecase

import com.example.music.domain.repository.LibraryPrefsRepository
import javax.inject.Inject

class ObserveLibraryPrefsUseCase @Inject constructor(
    private val prefs: LibraryPrefsRepository,
) {
    fun favorites() = prefs.observeFavoriteIds()
    fun recents() = prefs.observeRecentIds()
    suspend fun removeIds(ids: Set<String>) = prefs.removeIds(ids)
    fun lockScreenPromptDismissed() = prefs.observeLockScreenPromptDismissed()
    suspend fun setLockScreenPromptDismissed(dismissed: Boolean) =
        prefs.setLockScreenPromptDismissed(dismissed)
    fun preferBrave() = prefs.observePreferBrave()
    suspend fun setPreferBrave(enabled: Boolean) = prefs.setPreferBrave(enabled)
}
