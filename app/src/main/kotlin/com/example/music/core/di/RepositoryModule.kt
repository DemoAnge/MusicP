package com.example.music.core.di

import com.example.music.data.library.LibraryPrefsRepositoryImpl
import com.example.music.data.local_music.LocalMusicRepositoryImpl
import com.example.music.data.lyrics.LyricsRepositoryImpl
import com.example.music.data.player.PlaybackSessionRepositoryImpl
import com.example.music.domain.repository.LibraryPrefsRepository
import com.example.music.domain.repository.LocalMusicRepository
import com.example.music.domain.repository.LyricsRepository
import com.example.music.domain.repository.PlaybackSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLocalMusicRepository(impl: LocalMusicRepositoryImpl): LocalMusicRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    @Singleton
    abstract fun bindLibraryPrefsRepository(impl: LibraryPrefsRepositoryImpl): LibraryPrefsRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackSessionRepository(
        impl: PlaybackSessionRepositoryImpl,
    ): PlaybackSessionRepository
}
