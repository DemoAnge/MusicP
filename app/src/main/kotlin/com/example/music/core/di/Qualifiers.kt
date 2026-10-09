package com.example.music.core.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LyricsClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YouTubeClient
