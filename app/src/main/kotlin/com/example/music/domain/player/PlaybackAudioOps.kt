package com.example.music.domain.player

import com.example.music.domain.library.TrackKind
import com.example.music.domain.model.EqPreset
import kotlin.math.pow

object PlaybackAudioOps {
    const val MIN_SPEED = 0.8f
    const val MAX_SPEED = 1.5f
    const val CROSSFADE_MS = 400
    const val MAX_REPLAYGAIN_DB = 8f

    fun clampSpeed(speed: Float): Float = speed.coerceIn(MIN_SPEED, MAX_SPEED)

    fun speedFor(kind: TrackKind, musicSpeed: Float, spokenSpeed: Float): Float {
        val raw = if (kind == TrackKind.MUSIC) musicSpeed else spokenSpeed
        return clampSpeed(raw)
    }

    fun linearGainFromDb(db: Float): Float {
        val clamped = db.coerceIn(-MAX_REPLAYGAIN_DB, MAX_REPLAYGAIN_DB)
        return 10.0.pow(clamped / 20.0).toFloat()
    }

    /** Band gain in millibels for the Android Equalizer. */
    fun eqGainMb(preset: EqPreset, centerHz: Int): Int {
        if (preset == EqPreset.OFF) return 0
        val hz = centerHz.coerceAtLeast(1)
        val db = when (preset) {
            EqPreset.OFF -> 0f
            EqPreset.BASS -> when {
                hz < 120 -> 6f
                hz < 250 -> 4f
                hz < 500 -> 2f
                hz > 8_000 -> -2f
                else -> 0f
            }
            EqPreset.VOICE -> when {
                hz < 200 -> -4f
                hz in 800..4_000 -> 5f
                hz > 8_000 -> -2f
                else -> 1f
            }
            EqPreset.BRIGHT -> when {
                hz < 200 -> -2f
                hz > 8_000 -> 6f
                hz > 4_000 -> 4f
                else -> 0f
            }
        }
        return (db * 100f).toInt()
    }
}
