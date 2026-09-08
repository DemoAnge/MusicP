package com.example.music.data.lyrics

import android.content.Context
import android.net.Uri
import com.example.music.core.network.LyricsApi
import com.example.music.domain.model.SyncedLyrics
import com.example.music.domain.model.Track
import com.example.music.domain.repository.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val api: LyricsApi,
    @ApplicationContext private val context: Context,
) : LyricsRepository {
    override suspend fun getSyncedLyrics(track: Track): SyncedLyrics? = withContext(Dispatchers.IO) {
        val fromTag = track.embeddedLyrics?.trim().orEmpty()
        val fromFile = if (fromTag.isEmpty() && track.mediaUri.isNotBlank()) {
            runCatching {
                LocalLyricsReader.read(context, Uri.parse(track.mediaUri))
            }.getOrNull().orEmpty()
        } else {
            ""
        }
        val embedded = fromTag.ifEmpty { fromFile }
        if (embedded.isNotEmpty()) {
            val synced = LrcParser.parse(embedded)
            return@withContext SyncedLyrics(
                trackId = track.id,
                lines = synced,
                plainText = if (synced.isEmpty()) embedded else LrcParser.plainText(embedded),
            )
        }
        runCatching {
            val dto = api.getLyrics(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album.takeIf { it.isNotBlank() && it != "Álbum desconocido" && it != "Descargas" },
                durationSeconds = (track.durationMs / 1000).toInt().takeIf { it > 0 },
            )
            val synced = dto.syncedLyrics?.let(LrcParser::parse).orEmpty()
            if (synced.isEmpty() && dto.plainLyrics.isNullOrBlank()) return@withContext null
            SyncedLyrics(
                trackId = track.id,
                lines = synced,
                plainText = dto.plainLyrics,
            )
        }.getOrNull()
    }
}
