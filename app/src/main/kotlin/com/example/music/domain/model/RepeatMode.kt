package com.example.music.domain.model

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

fun RepeatMode.next(): RepeatMode = when (this) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}
