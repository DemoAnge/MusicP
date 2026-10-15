package com.example.music.domain.player

object ReplayGainParser {
    private val trackGain = Regex(
        """REPLAYGAIN_TRACK_GAIN\s*[:=]?\s*([+-]?\d+(?:\.\d+)?)\s*dB""",
        RegexOption.IGNORE_CASE,
    )
    private val albumGain = Regex(
        """REPLAYGAIN_ALBUM_GAIN\s*[:=]?\s*([+-]?\d+(?:\.\d+)?)\s*dB""",
        RegexOption.IGNORE_CASE,
    )
    private val r128Track = Regex(
        """R128_TRACK_GAIN\s*[:=]?\s*([+-]?\d+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parseDb(text: String): Float? {
        val raw = trackGain.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: r128Track.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.div(256f)
            ?: albumGain.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        return raw?.coerceIn(-PlaybackAudioOps.MAX_REPLAYGAIN_DB, PlaybackAudioOps.MAX_REPLAYGAIN_DB)
    }

    fun parseFromBytes(bytes: ByteArray): Float? {
        if (bytes.isEmpty()) return null
        return parseDb(bytes.toString(Charsets.ISO_8859_1))
    }
}
