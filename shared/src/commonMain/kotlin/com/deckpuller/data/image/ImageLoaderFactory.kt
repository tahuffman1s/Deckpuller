package com.deckpuller.data.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path

/**
 * The single Coil image loader used everywhere — registered as the Compose singleton (so every
 * [coil3.compose.AsyncImage] uses it) and injected into the prefetcher and the burst-overlay
 * bitmap loaders. Two things matter for smoothness:
 *
 *  - It reuses the shared Ktor [HttpClient], so card images and API calls share one connection pool.
 *  - It keeps a **memory _and_ disk cache**. Coil 3 with the Ktor fetcher creates neither by
 *    default, so without this every card thumbnail re-downloads on each scroll/revisit (the jank
 *    you feel). Coil 2's default Android loader gave us the disk cache for free; this restores it.
 */
fun newImageLoader(context: PlatformContext, httpClient: HttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(KtorNetworkFetcherFactory(httpClient)) }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(imageCacheDir(context))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

/** Platform directory backing Coil's on-disk image cache (app cache dir, safe to evict). */
expect fun imageCacheDir(context: PlatformContext): Path
