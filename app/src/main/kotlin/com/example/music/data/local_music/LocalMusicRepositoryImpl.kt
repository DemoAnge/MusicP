package com.example.music.data.local_music

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.music.domain.model.DeleteTracksResult
import com.example.music.domain.model.Track
import com.example.music.domain.repository.LocalMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@Singleton
class LocalMusicRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    @ApplicationContext private val context: Context,
) : LocalMusicRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracks = MutableStateFlow<List<Track>>(emptyList())
    private val mediaChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                mediaChanges.tryEmit(Unit)
            }
        }
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                resolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, observer)
            }
        }
        scope.launch {
            mediaChanges.debounce(750).collect {
                runCatching { getTracks() }
            }
        }
    }

    override fun observeTracks(): Flow<List<Track>> = tracks

    override suspend fun getTracks(): List<Track> = withContext(Dispatchers.IO) {
        runCatching { dataSource.loadTracks() }
            .getOrDefault(tracks.value)
            .also { tracks.value = it }
    }

    override suspend fun search(query: String): List<Track> {
        val haystack = if (tracks.value.isEmpty()) getTracks() else tracks.value
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return haystack
        return haystack.filter { track ->
            track.title.lowercase().contains(needle) ||
                track.artist.lowercase().contains(needle) ||
                track.album.lowercase().contains(needle)
        }
    }

    override suspend fun trackFromUri(uri: String, mimeType: String?): Track =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { Uri.parse(uri) }.getOrThrow()
            dataSource.loadFromUri(parsed, mimeType)
        }

    override suspend fun deleteTracks(tracks: List<Track>): DeleteTracksResult =
        withContext(Dispatchers.IO) {
            runCatching { dataSource.requestDelete(tracks) }
                .getOrElse { DeleteTracksResult.Error(it.message ?: "No se pudo eliminar") }
        }

    override fun dropCached(ids: Set<String>) {
        if (ids.isEmpty()) return
        tracks.value = tracks.value.filter { it.id !in ids }
    }

    fun asSearchFlow(query: String): Flow<List<Track>> =
        tracks.map { list ->
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) list
            else list.filter {
                it.title.lowercase().contains(needle) ||
                    it.artist.lowercase().contains(needle) ||
                    it.album.lowercase().contains(needle)
            }
        }
}
