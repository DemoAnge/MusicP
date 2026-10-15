package com.example.music.domain.model

enum class SleepOption(val minutes: Int) {
    OFF(0),
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_45(45),
    MINUTES_60(60),
    END_OF_TRACK(0),
    ;

    companion object {
        fun nearestMinutes(value: Int): SleepOption {
            if (value <= 0) return OFF
            return listOf(MINUTES_15, MINUTES_30, MINUTES_45, MINUTES_60)
                .minBy { kotlin.math.abs(it.minutes - value) }
        }
    }
}
