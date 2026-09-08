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

    private fun readFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    private fun readRecents(): List<String> =
        prefs.getString(KEY_RECENTS, "")
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private companion object {
        const val PREFS_NAME = "library_prefs"
        const val KEY_FAVORITES = "favorite_ids"
        const val KEY_RECENTS = "recent_ids"
        const val MAX_RECENTS = 80
    }
}
