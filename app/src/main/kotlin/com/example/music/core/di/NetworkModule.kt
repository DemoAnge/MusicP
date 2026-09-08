package com.example.music.core.di

import com.example.music.BuildConfig
import com.example.music.core.network.LyricsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    @LyricsClient
    fun provideLyricsOkHttp(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MusicPlayer/1.0 (personal)")
                    .build()
                runCatching { chain.proceed(request) }.getOrThrow()
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideLyricsApi(@LyricsClient client: OkHttpClient): LyricsApi =
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyricsApi::class.java)
}
