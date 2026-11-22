package com.streammaster.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import com.streammaster.tv.data.work.ReminderScheduler
import com.streammaster.tv.util.DeviceCaps
import dagger.hilt.android.HiltAndroidApp

/**
 * CONTRACT §2 — @HiltAndroidApp. onCreate initializes the program-reminder
 * scheduler (which also enqueues the periodic playlist/EPG WorkManager jobs)
 * and this class implements Coil's ImageLoaderFactory so the image pipeline
 * actually uses the capped disk cache (200 MB normal, 80 MB on low-RAM —
 * SPEC §10). NOTE: di/AppModule also provides an ImageLoader singleton with
 * the same caps for injection; the factory below is the one Coil's AsyncImage
 * resolves through Context.imageLoader.
 */
@HiltAndroidApp
class StreamMasterApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        val lowRam = DeviceCaps.isLowRam(this)
        val diskBytes = if (lowRam) 80L * 1024 * 1024 else 200L * 1024 * 1024
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Free image memory under pressure (SPEC §10, low-RAM devices).
        imageLoader.memoryCache?.clear()
    }
}
