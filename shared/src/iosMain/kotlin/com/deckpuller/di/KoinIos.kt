package com.deckpuller.di

import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/** Called once from the Swift app delegate before any Compose content is shown. */
fun doInitKoin() {
    startKoin {
        modules(sharedModule, iosModule)
    }
    // Make every AsyncImage / prefetch share the Koin-provided, Ktor-backed ImageLoader.
    SingletonImageLoader.setSafe { KoinPlatform.getKoin().get<ImageLoader>() }
}
