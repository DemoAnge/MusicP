package com.example.music.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

@UnstableApi
class ReplayGainAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var linearGain: Float = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        ) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        val gain = linearGain
        val output = replaceOutputBuffer(remaining)
        if (abs(gain - 1f) < 0.001f) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        when (outputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= 2) {
                    val scaled = (inputBuffer.short * gain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output.putShort(scaled.toShort())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= 4) {
                    output.putFloat((inputBuffer.float * gain).coerceIn(-1f, 1f))
                }
            }
            else -> output.put(inputBuffer)
        }
        output.flip()
    }
}
