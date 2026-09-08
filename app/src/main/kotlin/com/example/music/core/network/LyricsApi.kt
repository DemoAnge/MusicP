package com.example.music.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface LyricsApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null,
    ): LyricsDto
}

data class LyricsDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("trackName") val trackName: String? = null,
    @SerializedName("artistName") val artistName: String? = null,
    @SerializedName("syncedLyrics") val syncedLyrics: String? = null,
    @SerializedName("plainLyrics") val plainLyrics: String? = null,
)
