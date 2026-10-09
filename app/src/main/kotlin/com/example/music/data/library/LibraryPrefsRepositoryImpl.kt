package com.example.music.data.library

import android.content.Context
import com.example.music.domain.repository.LibraryPrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LibraryPrefsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : LibraryPrefsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val favorites = MutableStateFlow(readFavorites())
    private val recents = MutableStateFlow(readRecents())
    private val lockScreenPromptDismissed = MutableStateFlow(
        prefs.getBoolean(KEY_LOCK_SCREEN_PROMPT, false),
    )
    private val preferBrave = MutableStateFlow(
        prefs.getBoolean(KEY_PREFER_BRAVE, true),
    )
    private val searchHistory = MutableStateFlow(readSearchHistory())

    override fun observeFavoriteIds(): Flow<Set<String>> = favorites.asStateFlow()

    override fun observeRecentIds(): Flow<List<String>> = recents.asStateFlow()

    override suspend fun toggleFavorite(trackId: String) {
        if (trackId.isBlank()) return
        mutex.withLock {
            val next = favorites.value.toMutableSet()
            if (!next.add(trackId)) next.remove(trackId)
            favorites.value = next
            prefs.edit().putStringSet(KEY_FAVORITES, HashSet(next)).apply()
        }
    }

    override suspend fun recordPlay(trackId: String) {
        if (trackId.isBlank()) return
        mutex.withLock {
            val next = (listOf(trackId) + recents.value.filter { it != trackId }).take(MAX_RECENTS)
            recents.value = next
            prefs.edit().putString(KEY_RECENTS, next.joinToString("\n")).apply()
        }
    }

    override fun observeLockScreenPromptDismissed(): Flow<Boolean> =
        lockScreenPromptDismissed.asStateFlow()

    override suspend fun setLockScreenPromptDismissed(dismissed: Boolean) {
        mutex.withLock {
            lockScreenPromptDismissed.value = dismissed
            prefs.edit().putBoolean(KEY_LOCK_SCREEN_PROMPT, dismissed).apply()
        }
    }

    override fun observePreferBrave(): Flow<Boolean> = preferBrave.asStateFlow()

    override suspend fun setPreferBrave(enabled: Boolean) {
        mutex.withLock {
            preferBrave.value = enabled
            prefs.edit().putBoolean(KEY_PREFER_BRAVE, enabled).apply()
        }
    }

    override fun observeSearchHistory(): Flow<List<String>> = searchHistory.asStateFlow()

    override suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            val next = (listOf(trimmed) + searchHistory.value.filter { !it.equals(trimmed, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY)
            searchHistory.value = next
            prefs.edit().putString(KEY_SEARCH_HISTORY, next.joinToString("\n")).apply()
        }
    }

    override suspend fun removeSearchQuery(query: String) {
        mutex.withLock {
            val next = searchHistory.value.filter { !it.equals(query, ignoreCase = true) }
            searchHistory.value = next
            prefs.edit().putString(KEY_SEARCH_HISTORY, next.joinToString("\n")).apply()
        }
    }

    override suspend fun clearSearchHistory() {
        mutex.withLock {
            searchHistory.value = emptyList()
            prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        }
    }

    override suspend fun removeIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            val nextFav = favorites.value.filterNot { it in ids }.toSet()
            val nextRecents = recents.value.filterNot { it in ids }
            favorites.value = nextFav
            recents.value = nextRecents
            prefs.edit()
                .putStringSet(KEY_FAVORITES, HashSet(nextFav))
                .putString(KEY_RECENTS, nextRecents.joinToString("\n"))
                .apply()
        }
    }

    private fun readFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    private fun readRecents(): List<String> = readLineList(KEY_RECENTS)

    private fun readSearchHistory(): List<String> = readLineList(KEY_SEARCH_HISTORY)

    private fun readLineList(key: String): List<String> =
        prefs.getString(key, "")
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private companion object {
        const val PREFS_NAME = "library_prefs"
        const val KEY_FAVORITES = "favorite_ids"
        const val KEY_RECENTS = "recent_ids"
        const val KEY_LOCK_SCREEN_PROMPT = "lock_screen_prompt_dismissed"
        const val KEY_PREFER_BRAVE = "prefer_brave"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val MAX_RECENTS = 80
        const val MAX_SEARCH_HISTORY = 12
    }
}
