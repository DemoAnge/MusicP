package com.example.music.player.audio

import android.media.audiofx.Equalizer
import com.example.music.core.CrashGuard
import com.example.music.domain.model.EqPreset
import com.example.music.domain.player.PlaybackAudioOps

class LocalAudioEffects {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0
    private var preset: EqPreset = EqPreset.OFF

    fun setSession(sessionId: Int) {
        if (this.sessionId == sessionId && equalizer != null) {
            applyPreset()
            return
        }
        this.sessionId = sessionId
        rebuild()
    }

    fun setPreset(preset: EqPreset) {
        if (this.preset == preset && equalizer != null) return
        this.preset = preset
        rebuild()
    }

    fun release() {
        CrashGuard.run { equalizer?.release() }
        equalizer = null
    }

    private fun rebuild() {
        CrashGuard.run { equalizer?.release() }
        equalizer = null
        if (preset == EqPreset.OFF || sessionId == 0) return
        equalizer = runCatching {
            Equalizer(0, sessionId).apply {
                enabled = true
                applyBands(this, preset)
            }
        }.getOrNull()
    }

    private fun applyPreset() {
        val eq = equalizer ?: return rebuild()
        CrashGuard.run { applyBands(eq, preset) }
    }

    private fun applyBands(eq: Equalizer, preset: EqPreset) {
        val range = eq.bandLevelRange
        val min = range[0].toInt()
        val max = range[1].toInt()
        val bands = eq.numberOfBands.toInt()
        for (i in 0 until bands) {
            val hz = eq.getCenterFreq(i.toShort()) / 1000
            val level = PlaybackAudioOps.eqGainMb(preset, hz).coerceIn(min, max)
            eq.setBandLevel(i.toShort(), level.toShort())
        }
        eq.enabled = preset != EqPreset.OFF
    }
}
