package com.example.music.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") category: String = "10",
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") key: String,
    ): YouTubeSearchResponse
}

data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<YouTubeSearchItem> = emptyList(),
)

data class YouTubeSearchItem(
    @SerializedName("id") val id: YouTubeVideoId? = null,
    @SerializedName("snippet") val snippet: YouTubeSnippet? = null,
)

data class YouTubeVideoId(
    @SerializedName("videoId") val videoId: String? = null,
)

data class YouTubeSnippet(
    @SerializedName("title") val title: String? = null,
    @SerializedName("channelTitle") val channelTitle: String? = null,
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails? = null,
)

data class YouTubeThumbnails(
    @SerializedName("medium") val medium: YouTubeThumb? = null,
    @SerializedName("high") val high: YouTubeThumb? = null,
    @SerializedName("default") val default: YouTubeThumb? = null,
)

data class YouTubeThumb(
    @SerializedName("url") val url: String? = null,
)
