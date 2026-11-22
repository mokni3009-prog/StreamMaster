package com.streammaster.tv.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultPreloadManager
import androidx.media3.exoplayer.source.MediaSource
import com.streammaster.tv.util.DeviceCaps
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/**
 * Channel preloading (GUIDE B21/B22, D6/D7).
 *
 * Wraps Media3's [DefaultPreloadManager]: the channels most likely to be zapped
 * to next (next / previous in the bouquet) are preloaded so a channel switch
 * starts from already-fetched data instead of a cold open. The prepared
 * [MediaSource] is handed to the player via [getMediaSource] when the user
 * actually switches.
 *
 * Low-RAM mode (SPEC §10): only ONE channel is preloaded
 * ([DeviceCaps.isLowRam]); on normal devices the next and the previous channel
 * are preloaded. Preloading is best-effort — any failure is logged and the
 * player simply builds a fresh source, so this class can never break playback.
 */
@OptIn(UnstableApi::class)
@Singleton
class PreloadHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkStack: NetworkStack
) {
    private var preloadManager: DefaultPreloadManager? = null
    private var currentFactoryKey: String? = null

    /**
     * Preloads [mediaItems] around [currentIndex]. [settings] selects the
     * datasource factory (VOD vs live). Call again whenever the current channel
     * changes; old preloads are invalidated first.
     */
    fun preload(
        mediaItems: List<MediaItem>,
        currentIndex: Int,
        settings: com.streammaster.tv.data.model.AppSettings,
        isVod: Boolean
    ) {
        if (mediaItems.isEmpty()) return
        try {
            val factoryKey = "${settings.ua}|${settings.dns}|$isVod"
            if (preloadManager == null || currentFactoryKey != factoryKey) {
                preloadManager?.release()
                preloadManager = DefaultPreloadManager.Builder(
                    context,
                    networkStack.mediaSourceFactory(settings, isVod)
                ).build()
                currentFactoryKey = factoryKey
            }
            val manager = preloadManager ?: return
            val maxCount = if (DeviceCaps.isLowRam(context)) 1 else 2
            manager.invalidate()
            manager.setCurrentPlayingIndex(currentIndex)
            // Neighbours first (next, then previous), closest first.
            val order = listOf(currentIndex + 1, currentIndex - 1)
                .filter { it in mediaItems.indices }
                .take(maxCount)
            for ((rank, index) in order.withIndex()) {
                try {
                    manager.add(mediaItems[index], rank)
                } catch (e: Exception) {
                    Log.w(TAG, "Preload add failed for index $index", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Channel preload failed (best-effort)", e)
        }
    }

    /** Returns the preloaded source for [mediaItem], or null if not preloaded. */
    fun getMediaSource(mediaItem: MediaItem): MediaSource? = try {
        preloadManager?.getMediaSource(mediaItem)
    } catch (e: Exception) {
        Log.w(TAG, "Preload getMediaSource failed", e)
        null
    }

    fun clear() {
        try {
            preloadManager?.invalidate()
        } catch (e: Exception) {
            Log.w(TAG, "Preload invalidate failed", e)
        }
    }

    fun release() {
        try {
            preloadManager?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Preload release failed", e)
        }
        preloadManager = null
        currentFactoryKey = null
    }
}
