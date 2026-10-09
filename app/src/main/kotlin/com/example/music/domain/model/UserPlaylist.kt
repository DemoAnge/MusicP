package com.example.music.domain.model

data class UserPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAtEpochSec: Long = 0L,
)
