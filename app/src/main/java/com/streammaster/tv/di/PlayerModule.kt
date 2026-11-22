package com.streammaster.tv.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.player.createExoPlayer
import com.streammaster.tv.player.createLibVlc
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.videolan.libvlc.LibVLC
import javax.annotation.Nullable
import javax.inject.Singleton

/**
 * CONTRACT §9: ExoPlayer + LibVLC factories. The factory code itself lives
 * in agent 3's player/PlayerFactory.kt with the exact signatures
 * createExoPlayer(context, settings): ExoPlayer? and
 * createLibVlc(context, settings): LibVLC — this module only wires them.
 * ExoPlayer is null on API 21–22 (Media3 requires API 23+); VLC is the
 * primary engine there.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    @Nullable
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): ExoPlayer? {
        val settings = runBlocking { settingsRepository.settings.first() }
        return createExoPlayer(context, settings)
    }

    @Provides
    @Singleton
    fun provideLibVlc(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): LibVLC {
        val settings = runBlocking { settingsRepository.settings.first() }
        return createLibVlc(context, settings)
    }
}
