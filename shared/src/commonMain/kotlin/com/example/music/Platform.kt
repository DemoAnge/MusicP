package com.example.music

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform