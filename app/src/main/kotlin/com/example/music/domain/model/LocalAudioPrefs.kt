package com.example.music.domain.model

enum class EqPreset {
    OFF,
    BASS,
    VOICE,
    BRIGHT,
}

data class LocalAudioPrefs(
    val musicSpeed: Float = 1f,
    val spokenSpeed: Float = 1f,
    val normalizeVolume: Boolean = true,
    val crossfadeEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.OFF,
)
