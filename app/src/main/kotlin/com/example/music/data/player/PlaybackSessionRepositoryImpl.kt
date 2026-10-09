package com.example.music.data.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.music.domain.model.PlaybackSession
import com.example.music.domain.repository.PlaybackSessionRepository
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PlaybackSessionRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : PlaybackSessionRepository {

    private val gson = Gson()

    override suspend fun save(session: PlaybackSession) {
        val slim = session.copy(
            queue = session.queue.map { it.copy(embeddedLyrics = null) },
            originalQueue = session.originalQueue.map { it.copy(embeddedLyrics = null) },
        )
        runCatching {
            store.edit { prefs -> prefs[KEY_JSON] = gson.toJson(slim) }
        }
    }

    override suspend fun load(): PlaybackSession? {
        val json = runCatching { store.data.first()[KEY_JSON] }.getOrNull().orEmpty()
        if (json.isBlank()) return null
        return runCatching { gson.fromJson(json, PlaybackSession::class.java) }.getOrNull()
    }

    override suspend fun clear() {
        runCatching { store.edit { it.remove(KEY_JSON) } }
    }

    private companion object {
        val KEY_JSON = stringPreferencesKey("session_json")
    }
}
