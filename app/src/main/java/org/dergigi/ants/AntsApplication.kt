package org.dergigi.ants

import android.app.Application
import android.content.Context
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.maxBitmapSize
import coil3.size.Size
import kotlinx.coroutines.Dispatchers

class AntsApplication : Application(), SingletonImageLoader.Factory {
    private var images: ImageLoader? = null

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder().maxSizeBytes(minOf(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16)).build()
        }
        .maxBitmapSize(Size(1536, 1536))
        .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(2))
        .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(4))
        .build().also { images = it }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) images?.memoryCache?.clear()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        images?.memoryCache?.clear()
    }
}
