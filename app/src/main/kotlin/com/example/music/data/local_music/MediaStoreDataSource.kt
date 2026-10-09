package com.example.music.data.local_music

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import com.example.music.data.lyrics.Id3LyricsReader
import com.example.music.domain.model.DeleteTracksResult
import com.example.music.domain.model.PlaybackSource
import com.example.music.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun loadTracks(): List<Track> {
        return runCatching {
            queryAudio()
                .filter { !it.isVideo }
                .distinctBy { it.mediaUri }
                .sortedBy { it.title.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun requestDelete(tracks: List<Track>): DeleteTracksResult {
        val uris = tracks.mapNotNull { track ->
            track.mediaUri.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { Uri.parse(raw) }.getOrNull()
            }
        }.distinct()
        if (uris.isEmpty()) return DeleteTracksResult.Empty

        val mediaUris = uris.filter { uri ->
            uri.authority.orEmpty().contains("media", ignoreCase = true)
        }
        val otherUris = uris.filterNot { it in mediaUris.toSet() }

        var deleted = 0
        for (uri in otherUris) {
            if (deleteUriDirect(uri)) deleted++
        }

        if (mediaUris.isEmpty()) {
            return if (deleted > 0) DeleteTracksResult.Deleted(deleted) else DeleteTracksResult.Empty
        }

        if (Build.VERSION.SDK_INT >= 30) {
            val consent = runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, mediaUris).intentSender
            }.getOrNull()
            if (consent != null) return DeleteTracksResult.NeedConsent(consent)
        }

        var recoverable: android.content.IntentSender? = null
        for (uri in mediaUris) {
            try {
                if (deleteUriDirect(uri)) deleted++
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && error is RecoverableSecurityException) {
                    recoverable = error.userAction.actionIntent.intentSender
                    break
                }
            }
        }
        if (recoverable != null) return DeleteTracksResult.NeedConsent(recoverable)
        return if (deleted > 0) DeleteTracksResult.Deleted(deleted)
        else DeleteTracksResult.Error("No se pudo eliminar")
    }

    private fun deleteUriDirect(uri: Uri): Boolean {
        runCatching {
            if (context.contentResolver.delete(uri, null, null) > 0) return true
        }.onFailure { error ->
            if (error is SecurityException) throw error
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val removed = runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }.getOrDefault(false)
            if (removed) return true
        }
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val file = File(path)
            if (file.exists() && file.delete()) return true
        }
        return false
    }

    fun loadFromUri(uri: Uri, mimeHint: String?): Track {
        return runCatching { loadFromUriInternal(uri, mimeHint) }.getOrElse {
            Track(
                id = "local-uri-${uri.hashCode()}",
                title = "Sin título",
                artist = "Artista desconocido",
                album = "Álbum desconocido",
                durationMs = 0L,
                artworkUri = null,
                mediaUri = uri.toString(),
                source = PlaybackSource.LOCAL,
            )
        }
    }

    private fun loadFromUriInternal(uri: Uri, mimeHint: String?): Track {
        val mime = mimeHint?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val isVideo = isVideoFile(mime, queryDisplayName(uri), uri.path, uri.toString())
        val displayName = queryDisplayName(uri)
        val storeTrack = runCatching { findInMediaStore(uri) }.getOrNull()
        if (storeTrack != null) {
            val parsed = runCatching { Uri.parse(storeTrack.mediaUri) }.getOrDefault(uri)
            return enrich(storeTrack, parsed, isVideo || storeTrack.isVideo, displayName)
        }
        val embedded = readEmbedded(uri, isVideo, readPicture = true)
        val fromDownloads = uri.path?.contains("Download", ignoreCase = true) == true
        val display = resolveDisplay(
            rawTitle = null,
            rawArtist = null,
            rawAlbum = null,
            displayName = displayName,
            embeddedTitle = embedded.title,
            embeddedArtist = embedded.artist,
            embeddedAlbum = embedded.album,
            fromDownloads = fromDownloads,
        )
        val duration = embedded.durationMs ?: 0L
        val (folderPath, folderName) = folderOf(
            relativePath = if (fromDownloads) "Download" else null,
            dataPath = uri.path,
        )
        return Track(
            id = "local-uri-${uri.hashCode()}",
            title = display.title,
            artist = display.artist,
            album = display.album,
            durationMs = duration,
            artworkUri = embedded.artworkUri ?: thumbnailFromUri(uri, "uri-${uri.hashCode()}"),
            mediaUri = uri.toString(),
            source = PlaybackSource.LOCAL,
            mimeType = mime,
            isVideo = isVideo,
            folderPath = folderPath,
            folderName = folderName,
            dateAddedEpochSec = System.currentTimeMillis() / 1000L,
            sizeBytes = querySize(uri),
        )
    }

    private fun queryAudio(): List<Track> = runCatching {
        val collection = audioCollection()
        val projection = audioProjection()
        val selection = buildString {
            append("(${MediaStore.Audio.Media.IS_RINGTONE} = 0 OR ${MediaStore.Audio.Media.IS_RINGTONE} IS NULL)")
            append(" AND (${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 OR ${MediaStore.Audio.Media.IS_NOTIFICATION} IS NULL)")
            append(" AND (${MediaStore.Audio.Media.IS_ALARM} = 0 OR ${MediaStore.Audio.Media.IS_ALARM} IS NULL)")
            if (Build.VERSION.SDK_INT >= 29) {
                append(" AND ${MediaStore.Audio.Media.IS_PENDING} = 0")
            }
        }
        val result = mutableListOf<Track>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val id = cursor.lng(MediaStore.Audio.Media._ID)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = cursor.str(MediaStore.Audio.Media.DISPLAY_NAME)
                    val relativePath = cursor.str(RELATIVE_PATH)
                    val mime = cursor.str(MediaStore.Audio.Media.MIME_TYPE)
                    val albumId = cursor.lng(MediaStore.Audio.Media.ALBUM_ID)
                    val rawTitle = cursor.str(MediaStore.Audio.Media.TITLE)
                    val rawArtist = cursor.str(MediaStore.Audio.Media.ARTIST)
                    val rawAlbum = cursor.str(MediaStore.Audio.Media.ALBUM)
                    val duration = cursor.lng(MediaStore.Audio.Media.DURATION)
                    val dataPath = if (Build.VERSION.SDK_INT < 29) cursor.str(MediaStore.MediaColumns.DATA) else null
                    val (folderPath, folderName) = folderOf(relativePath, dataPath)
                    val video = isVideoFile(mime, displayName, dataPath, uri.toString())
                    val track = Track(
                        id = "local-a-$id",
                        title = rawTitle.orEmpty(),
                        artist = rawArtist.orEmpty(),
                        album = rawAlbum.orEmpty(),
                        durationMs = duration,
                        artworkUri = albumArtUri(albumId),
                        mediaUri = uri.toString(),
                        source = PlaybackSource.LOCAL,
                        mimeType = mime,
                        isVideo = video,
                        folderPath = folderPath,
                        folderName = folderName,
                        dateAddedEpochSec = cursor.lng(MediaStore.Audio.Media.DATE_ADDED),
                        sizeBytes = cursor.lng(MediaStore.MediaColumns.SIZE),
                    )
                    val fromDoVimu = isFromDoVimu(relativePath, displayName)
                    val enriched = enrich(track, uri, isVideo = video, displayName, relativePath, albumId)
                    if (enriched.isVideo) return@runCatching
                    if (enriched.durationMs >= 1000L || isFromDownloads(relativePath, displayName) || fromDoVimu) {
                        result += enriched
                    }
                }
            }
        }
        result
    }.getOrDefault(emptyList())

    private fun queryVideo(): List<Track> = runCatching {
        val collection = videoCollection()
        val projection = videoProjection()
        val selection = if (Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.Video.Media.IS_PENDING} = 0"
        } else {
            null
        }
        val result = mutableListOf<Track>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val id = cursor.lng(MediaStore.Video.Media._ID)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = cursor.str(MediaStore.Video.Media.DISPLAY_NAME)
                    val relativePath = cursor.str(RELATIVE_PATH)
                    val mime = cursor.str(MediaStore.Video.Media.MIME_TYPE)
                    val rawTitle = cursor.str(MediaStore.Video.Media.TITLE)
                    val rawArtist = cursor.str(MediaStore.Video.Media.ARTIST)
                    val rawAlbum = cursor.str(MediaStore.Video.Media.ALBUM)
                    val duration = cursor.lng(MediaStore.Video.Media.DURATION)
                    val dataPath = if (Build.VERSION.SDK_INT < 29) cursor.str(MediaStore.MediaColumns.DATA) else null
                    val (folderPath, folderName) = folderOf(relativePath, dataPath)
                    val video = isVideoFile(mime ?: "video/*", displayName, dataPath, uri.toString())
                    val track = Track(
                        id = "local-v-$id",
                        title = rawTitle.orEmpty(),
                        artist = rawArtist.orEmpty(),
                        album = rawAlbum.orEmpty(),
                        durationMs = duration,
                        artworkUri = null,
                        mediaUri = uri.toString(),
                        source = PlaybackSource.LOCAL,
                        mimeType = mime ?: "video/*",
                        isVideo = video,
                        folderPath = folderPath,
                        folderName = folderName,
                        dateAddedEpochSec = cursor.lng(MediaStore.Video.Media.DATE_ADDED),
                        sizeBytes = cursor.lng(MediaStore.MediaColumns.SIZE),
                    )
                    val fromDoVimu = isFromDoVimu(relativePath, displayName)
                    val enriched = enrich(track, uri, isVideo = video, displayName, relativePath, albumId = 0L)
                    if (enriched.durationMs >= 1000L || isFromDownloads(relativePath, displayName) || fromDoVimu) {
                        result += enriched
                    }
                }
            }
        }
        result
    }.getOrDefault(emptyList())

    private fun enrich(
        track: Track,
        uri: Uri,
        isVideo: Boolean,
        displayName: String?,
        relativePath: String? = null,
        albumId: Long = 0L,
    ): Track {
        val fromDownloads = isFromDownloads(relativePath, displayName)
        var duration = track.durationMs
        val classifiedVideo = isVideo || isVideoFile(track.mimeType, displayName, uri.path, track.mediaUri)
        var artwork: String? = null

        val needsMetadata = duration <= 0L && pretty(track.title) == null
        val embedded = if (needsMetadata) {
            readEmbedded(uri, classifiedVideo, readPicture = false)
        } else {
            Embedded()
        }

        val display = resolveDisplay(
            rawTitle = track.title,
            rawArtist = track.artist,
            rawAlbum = track.album,
            displayName = displayName,
            embeddedTitle = embedded.title,
            embeddedArtist = embedded.artist,
            embeddedAlbum = embedded.album,
            fromDownloads = fromDownloads,
        )
        if (duration <= 0L) duration = embedded.durationMs ?: 0L
        artwork = safeArtwork(embedded.artworkUri)

        return track.copy(
            title = display.title,
            artist = display.artist,
            album = display.album,
            durationMs = duration.coerceAtLeast(0L),
            artworkUri = artwork,
            isVideo = classifiedVideo,
            embeddedLyrics = embedded.lyrics,
        )
    }

    private fun findInMediaStore(uri: Uri): Track? {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        val uriString = uri.toString()
        return when {
            uriString.contains("/video/", ignoreCase = true) ->
                queryById(videoCollection(), id, isVideo = true)
            uriString.contains("/audio/", ignoreCase = true) ->
                queryById(audioCollection(), id, isVideo = false)
            else -> queryById(audioCollection(), id, isVideo = false)
                ?: queryById(videoCollection(), id, isVideo = true)
        }
    }

    private fun queryById(collection: Uri, id: Long, isVideo: Boolean): Track? {
        val projection = if (isVideo) videoProjection() else audioProjection()
        val titleCol = if (isVideo) MediaStore.Video.Media.TITLE else MediaStore.Audio.Media.TITLE
        val artistCol = if (isVideo) MediaStore.Video.Media.ARTIST else MediaStore.Audio.Media.ARTIST
        val albumCol = if (isVideo) MediaStore.Video.Media.ALBUM else MediaStore.Audio.Media.ALBUM
        val durationCol = if (isVideo) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION
        val nameCol = if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Audio.Media.DISPLAY_NAME
        val mimeCol = if (isVideo) MediaStore.Video.Media.MIME_TYPE else MediaStore.Audio.Media.MIME_TYPE
        context.contentResolver.query(
            ContentUris.withAppendedId(collection, id),
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val displayName = cursor.str(nameCol)
            val relativePath = cursor.str(RELATIVE_PATH)
            val mime = cursor.str(mimeCol)
            val albumId = if (isVideo) 0L else cursor.lng(MediaStore.Audio.Media.ALBUM_ID)
            val itemUri = ContentUris.withAppendedId(collection, id)
            val dataPath = if (Build.VERSION.SDK_INT < 29) cursor.str(MediaStore.MediaColumns.DATA) else null
            val (folderPath, folderName) = folderOf(relativePath, dataPath)
            val classifiedVideo = isVideoFile(mime, displayName, dataPath, itemUri.toString())
            val draft = Track(
                id = if (classifiedVideo) "local-v-$id" else "local-a-$id",
                title = cursor.str(titleCol).orEmpty(),
                artist = cursor.str(artistCol).orEmpty(),
                album = cursor.str(albumCol).orEmpty(),
                durationMs = cursor.lng(durationCol),
                artworkUri = if (classifiedVideo) null else albumArtUri(albumId),
                mediaUri = itemUri.toString(),
                source = PlaybackSource.LOCAL,
                mimeType = mime,
                isVideo = classifiedVideo,
                folderPath = folderPath,
                folderName = folderName,
                dateAddedEpochSec = cursor.lng(MediaStore.MediaColumns.DATE_ADDED),
                sizeBytes = cursor.lng(MediaStore.MediaColumns.SIZE),
            )
            return enrich(draft, itemUri, classifiedVideo, displayName, relativePath, albumId)
        }
        return null
    }

    private fun readEmbedded(uri: Uri, isVideo: Boolean, readPicture: Boolean = false): Embedded {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val lyrics = (
                if (Build.VERSION.SDK_INT >= 31) {
                    retriever.extractMetadata(METADATA_KEY_LYRICS)
                } else {
                    null
                }
            )?.trim()?.takeIf { it.isNotEmpty() }
                ?: Id3LyricsReader.read(context.contentResolver, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val artwork = if (readPicture) {
                val picture = runCatching { retriever.embeddedPicture }.getOrNull()
                when {
                    picture != null && picture.isNotEmpty() -> persistBytes(picture, "emb-${uri.hashCode()}")
                    isVideo -> persistFrame(retriever, "frm-${uri.hashCode()}")
                    else -> null
                }
            } else {
                null
            }
            Embedded(
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                artist = artist?.trim()?.takeIf { it.isNotEmpty() },
                album = album?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = duration?.takeIf { it > 0L },
                artworkUri = artwork,
                lyrics = lyrics?.trim()?.takeIf { it.isNotEmpty() },
            )
        } catch (_: RuntimeException) {
            Embedded()
        } catch (_: Exception) {
            Embedded()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun thumbnailFromUri(uri: Uri, key: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val file = File(thumbsDir(), "$key.jpg")
        if (file.exists() && file.length() > 0L) return Uri.fromFile(file).toString()
        return try {
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Uri.fromFile(file).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun persistBytes(bytes: ByteArray, key: String): String? {
        return try {
            val file = File(thumbsDir(), "$key.jpg")
            if (!file.exists() || file.length() == 0L) file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun persistFrame(retriever: MediaMetadataRetriever, key: String): String? {
        return runCatching {
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    320,
                ) ?: retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    320,
                )
            } else {
                retriever.frameAtTime
            } ?: return null
            val file = File(thumbsDir(), "$key.jpg")
            file.outputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun thumbsDir(): File =
        File(context.cacheDir, "media_thumbs").apply { mkdirs() }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) return cursor.getString(i)
                }
            }
        return uri.lastPathSegment
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val i = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (i >= 0) return cursor.getLong(i)
                }
            }
        return 0L
    }

    private fun audioCollection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun audioProjection(): Array<String> {
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            cols += MediaStore.Audio.Media.RELATIVE_PATH
            cols += MediaStore.Audio.Media.IS_PENDING
        } else {
            cols += MediaStore.MediaColumns.DATA
        }
        return cols.toTypedArray()
    }

    private fun videoProjection(): Array<String> {
        val cols = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            cols += MediaStore.Video.Media.RELATIVE_PATH
            cols += MediaStore.Video.Media.IS_PENDING
        } else {
            cols += MediaStore.MediaColumns.DATA
        }
        return cols.toTypedArray()
    }

    private fun albumArtUri(albumId: Long): String? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString()
    }

    private fun safeArtwork(uri: String?): String? {
        if (uri.isNullOrBlank() || uri == "0" || uri.endsWith("/albumart/0")) return null
        return uri
    }

    private fun android.database.Cursor.str(column: String): String? {
        val i = getColumnIndex(column)
        if (i < 0) return null
        return getString(i)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun android.database.Cursor.lng(column: String): Long {
        val i = getColumnIndex(column)
        if (i < 0) return 0L
        return getLong(i)
    }

    private data class Embedded(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long? = null,
        val artworkUri: String? = null,
        val lyrics: String? = null,
    )

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
        const val RELATIVE_PATH = "relative_path"
        const val METADATA_KEY_LYRICS = 29

        private val unknownLabels = setOf(
            "<unknown>", "unknown", "unknown artist", "unknown album",
            "artista desconocido", "álbum desconocido", "album desconocido",
            "sin título", "untitled",
        )

        private val junkAlbumLabels = setOf(
            "download", "downloads", "descargas", "dovimu",
            "music", "musica", "música", "audio", "video", "videos",
        )

        private val junkTitleSuffixes = setOf(
            "official video", "official audio", "video oficial", "audio oficial",
            "lyric video", "lyrics", "visualizer", "videoclip", "audio", "hd", "hq",
        )

        data class DisplayMeta(
            val title: String,
            val artist: String,
            val album: String,
        )

        private val audioExtensions = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma",
            "amr", "aiff", "aif", "mid", "midi",
        )
        private val videoExtensions = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "mpeg", "mpg",
            "flv", "wmv", "3gp", "3gpp", "ts", "m2ts", "vob", "mpe",
        )

        fun fileExtension(displayName: String?, dataPath: String?, mediaUri: String?): String {
            val raw = sequenceOf(displayName, dataPath, mediaUri)
                .mapNotNull { it?.substringAfterLast('/')?.substringAfterLast('\\') }
                .firstOrNull { it.contains('.') }
                .orEmpty()
            return raw.substringAfterLast('.', missingDelimiterValue = "")
                .substringBefore('?')
                .substringBefore('#')
                .lowercase()
        }

        fun isVideoFile(
            mime: String?,
            displayName: String?,
            dataPath: String?,
            mediaUri: String?,
        ): Boolean {
            val ext = fileExtension(displayName, dataPath, mediaUri)
            if (ext in audioExtensions) return false
            val mimeLower = mime?.lowercase().orEmpty()
            if (mimeLower.startsWith("video/")) return true
            if (ext in videoExtensions) return true
            if (mediaUri.orEmpty().contains("/video/", ignoreCase = true)) return true
            return false
        }

        fun folderOf(relativePath: String?, dataPath: String?): Pair<String, String> {
            val fromRelative = relativePath?.trim()?.trimEnd('/')
            val fromData = dataPath?.let { path ->
                val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
                    .substringBeforeLast('\\', missingDelimiterValue = "")
                parent
                    .substringAfter("/emulated/0/", missingDelimiterValue = "")
                    .ifBlank {
                        parent.substringAfter("/storage/", missingDelimiterValue = parent)
                    }
                    .trim('/')
            }
            val raw = fromRelative?.takeIf { it.isNotBlank() } ?: fromData.orEmpty()
            if (raw.isBlank()) return "Otras" to "Otras"
            val last = raw.substringAfterLast('/').substringAfterLast('\\')
            val name = pretty(last) ?: pretty(raw) ?: "Otras"
            return raw to name
        }

        fun looksRaw(value: String?): Boolean = value?.contains('_') == true

        fun isUnknown(value: String?): Boolean {
            val v = value?.trim().orEmpty()
            if (v.isEmpty()) return true
            return v.lowercase() in unknownLabels
        }

        fun pretty(value: String?): String? {
            if (isUnknown(value)) return null
            val cleaned = value.orEmpty()
                .replace('_', ' ')
                .replace('\u00A0', ' ')
                .replace(Regex("[\\s]+"), " ")
                .trim()
            if (cleaned.isEmpty() || isUnknown(cleaned)) return null
            return cleaned
        }

        fun prettyPerson(value: String?): String? {
            val v = pretty(value) ?: return null
            if (v.lowercase() in unknownLabels) return null
            return v
        }

        fun splitParts(value: String): List<String> {
            return value
                .split(" - ", " – ", " — ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        fun parseFilename(displayName: String): Triple<String?, String?, String?> {
            val stem = pretty(displayName.substringBeforeLast('.')) ?: return Triple(null, null, null)
            val parts = splitParts(stem)
            return when (parts.size) {
                0 -> Triple(null, null, null)
                1 -> Triple(parts[0], null, null)
                2 -> Triple(parts[1], parts[0], null)
                else -> {
                    val maybeAlbum = parts.drop(2).joinToString(" - ")
                    val album = maybeAlbum.takeIf { part ->
                        part.lowercase() !in junkTitleSuffixes && part.lowercase() !in junkAlbumLabels
                    }
                    Triple(parts[1], parts[0], album)
                }
            }
        }

        fun isJunkAlbum(
            album: String?,
            title: String?,
            artist: String?,
            displayName: String?,
        ): Boolean {
            val a = pretty(album) ?: return true
            val lower = a.lowercase()
            if (lower in unknownLabels || lower in junkAlbumLabels) return true
            val t = pretty(title)
            val ar = pretty(artist)
            if (t != null && a.equals(t, ignoreCase = true)) return true
            if (ar != null && a.equals(ar, ignoreCase = true)) return true
            if (t != null && ar != null && a.equals("$ar - $t", ignoreCase = true)) return true
            val stem = pretty(displayName?.substringBeforeLast('.'))
            if (stem != null && a.equals(stem, ignoreCase = true)) return true
            val stemParts = stem?.let(::splitParts).orEmpty()
            if (stemParts.size >= 2 && a.equals(stemParts.last(), ignoreCase = true) &&
                t != null && a.equals(t, ignoreCase = true)
            ) {
                return true
            }
            return false
        }

        fun resolveDisplay(
            rawTitle: String?,
            rawArtist: String?,
            rawAlbum: String?,
            displayName: String?,
            embeddedTitle: String? = null,
            embeddedArtist: String? = null,
            embeddedAlbum: String? = null,
            fromDownloads: Boolean,
        ): DisplayMeta {
            val parsed = displayName?.let(::parseFilename)
            var artist = prettyPerson(rawArtist) ?: prettyPerson(embeddedArtist) ?: parsed?.second
            var title = pretty(rawTitle) ?: pretty(embeddedTitle)
            val stem = pretty(displayName?.substringBeforeLast('.'))
            if (title == null || (stem != null && title.equals(stem, ignoreCase = true))) {
                title = parsed?.first ?: title
                if (artist == null) artist = parsed?.second
            }
            val titleParts = title?.let(::splitParts).orEmpty()
            if (titleParts.size >= 2) {
                val splitArtist = titleParts[0]
                val splitTitle = titleParts[1]
                if (artist == null || artist.equals(splitArtist, ignoreCase = true)) {
                    artist = artist ?: splitArtist
                    title = splitTitle
                }
            }
            var album = pretty(rawAlbum) ?: pretty(embeddedAlbum) ?: parsed?.third
            if (isJunkAlbum(album, title, artist, displayName)) album = null
            return DisplayMeta(
                title = title?.ifBlank { null } ?: "Sin título",
                artist = artist ?: "Artista desconocido",
                album = album ?: if (fromDownloads) "Descargas" else "Álbum desconocido",
            )
        }

        fun isFromDownloads(relativePath: String?, displayName: String?): Boolean {
            return relativePath.orEmpty().contains("download", ignoreCase = true) ||
                displayName.orEmpty().startsWith("Download", ignoreCase = true)
        }

        fun isFromDoVimu(relativePath: String?, displayName: String?): Boolean {
            val hay = "${relativePath.orEmpty()} ${displayName.orEmpty()}".lowercase()
            return "dovimu" in hay
        }
    }
}
