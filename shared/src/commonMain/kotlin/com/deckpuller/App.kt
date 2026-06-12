package com.deckpuller

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.deckpuller.ui.AppRoot
import com.deckpuller.ui.theme.DeckPullerTheme
import org.koin.compose.KoinContext
import org.koin.mp.KoinPlatform

/**
 * The shared Compose entry point hosted by both platforms: Android's `MainActivity.setContent`
 * and iOS's `ComposeUIViewController`. [KoinContext] binds the already-started Koin graph into
 * the composition so `koinViewModel()` resolves on every platform.
 */
@Composable
fun App() {
    // Point Coil's Compose singleton at our cached, Ktor-backed loader so every AsyncImage (and
    // the SingletonImageLoader-based burst bitmap loader) shares one memory+disk cache. Without
    // this, AsyncImage falls back to Coil 3's uncached default and re-downloads art while scrolling.
    setSingletonImageLoaderFactory { KoinPlatform.getKoin().get<ImageLoader>() }
    KoinContext {
        DeckPullerTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                AppRoot()
            }
        }
    }
}
