package com.example.music.domain.player

object LyricsTimingOps {
    const val STEP_MS = 500L
    const val MIN_MS = -10_000L
    const val MAX_MS = 10_000L

    fun clamp(offsetMs: Long): Long = offsetMs.coerceIn(MIN_MS, MAX_MS)

    fun nudge(currentMs: Long, steps: Int): Long = clamp(currentMs + steps * STEP_MS)

    fun label(offsetMs: Long): String {
        val tenths = offsetMs / 100L
        val whole = tenths / 10
        val frac = kotlin.math.abs(tenths % 10)
        val sign = if (offsetMs > 0) "+" else if (offsetMs < 0) "−" else ""
        return if (frac == 0L) {
            "${sign}${kotlin.math.abs(whole)} s"
        } else {
            "${sign}${kotlin.math.abs(whole)},$frac s"
        }
    }
}
