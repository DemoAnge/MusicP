package com.example.music.data.library

import android.content.Context
import com.example.music.domain.model.UserPlaylist
import com.example.music.domain.repository.LibraryPrefsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
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
    private val gson = Gson()
    private val favorites = MutableStateFlow(readFavorites())
    private val recents = MutableStateFlow(readRecents())
    private val lockScreenPromptDismissed = MutableStateFlow(
        prefs.getBoolean(KEY_LOCK_SCREEN_PROMPT, false),
    )
    private val preferBrave = MutableStateFlow(
        prefs.getBoolean(KEY_PREFER_BRAVE, true),
    )
    private val searchHistory = MutableStateFlow(readSearchHistory())
    private val playlists = MutableStateFlow(readPlaylists())
    private val ignoredFolders = MutableStateFlow(readIgnoredFolders())
    private val playbackSpeed = MutableStateFlow(readPlaybackSpeed())

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
            val nextPlaylists = playlists.value.map { playlist ->
                playlist.copy(trackIds = playlist.trackIds.filterNot { it in ids })
            }
            favorites.value = nextFav
            recents.value = nextRecents
            playlists.value = nextPlaylists
            prefs.edit()
                .putStringSet(KEY_FAVORITES, HashSet(nextFav))
                .putString(KEY_RECENTS, nextRecents.joinToString("\n"))
                .putString(KEY_PLAYLISTS, gson.toJson(nextPlaylists))
                .apply()
        }
    }

    override fun observePlaylists(): Flow<List<UserPlaylist>> = playlists.asStateFlow()

    override suspend fun createPlaylist(name: String): UserPlaylist? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return mutex.withLock {
            val created = UserPlaylist(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                createdAtEpochSec = System.currentTimeMillis() / 1000L,
            )
            val next = playlists.value + created
            playlists.value = next
            prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(next)).apply()
            created
        }
    }

    override suspend fun deletePlaylist(id: String) {
        if (id.isBlank()) return
        mutex.withLock {
            val next = playlists.value.filterNot { it.id == id }
            playlists.value = next
            prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(next)).apply()
        }
    }

    override suspend fun addToPlaylist(playlistId: String, trackId: String) {
        addToPlaylist(playlistId, listOf(trackId))
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        if (playlistId.isBlank()) return
        val incoming = trackIds.filter { it.isNotBlank() }.distinct()
        if (incoming.isEmpty()) return
        mutex.withLock {
            val next = playlists.value.map { playlist ->
                if (playlist.id != playlistId) playlist
                else {
                    val existing = playlist.trackIds.toHashSet()
                    val extra = incoming.filter { it !in existing }
                    if (extra.isEmpty()) playlist
                    else playlist.copy(trackIds = playlist.trackIds + extra)
                }
            }
            playlists.value = next
            prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(next)).apply()
        }
    }

    override suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        if (playlistId.isBlank() || trackId.isBlank()) return
        mutex.withLock {
            val next = playlists.value.map { playlist ->
                if (playlist.id != playlistId) playlist
                else playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
            }
            playlists.value = next
            prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(next)).apply()
        }
    }

    override fun observeIgnoredFolders(): Flow<Set<String>> = ignoredFolders.asStateFlow()

    override suspend fun setFolderIgnored(folderPath: String, ignored: Boolean) {
        val path = folderPath.trim()
        if (path.isEmpty()) return
        mutex.withLock {
            val next = ignoredFolders.value.toMutableSet()
            if (ignored) next.add(path) else next.remove(path)
            ignoredFolders.value = next
            prefs.edit().putStringSet(KEY_IGNORED_FOLDERS, HashSet(next)).apply()
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

    private fun readPlaylists(): List<UserPlaylist> {
        val json = prefs.getString(KEY_PLAYLISTS, "").orEmpty()
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<UserPlaylist>>() {}.type
        return runCatching { gson.fromJson<List<UserPlaylist>>(json, type) }.getOrNull().orEmpty()
            .map { playlist -> playlist.copy(trackIds = playlist.trackIds.distinct()) }
    }

    override fun observePlaybackSpeed(): Flow<Float> = playbackSpeed.asStateFlow()

    override suspend fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.8f, 1.5f)
        mutex.withLock {
            playbackSpeed.value = clamped
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, clamped).apply()
        }
    }

    private fun readIgnoredFolders(): Set<String> =
        prefs.getStringSet(KEY_IGNORED_FOLDERS, emptySet())?.toSet().orEmpty()

    private fun readPlaybackSpeed(): Float =
        prefs.getFloat(KEY_PLAYBACK_SPEED, 1f).coerceIn(0.8f, 1.5f)

    private companion object {
        const val PREFS_NAME = "library_prefs"
        const val KEY_FAVORITES = "favorite_ids"
        const val KEY_RECENTS = "recent_ids"
        const val KEY_LOCK_SCREEN_PROMPT = "lock_screen_prompt_dismissed"
        const val KEY_PREFER_BRAVE = "prefer_brave"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_PLAYLISTS = "user_playlists_json"
        const val KEY_IGNORED_FOLDERS = "ignored_folders"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val MAX_RECENTS = 80
        const val MAX_SEARCH_HISTORY = 12
    }
}
