package com.deckpuller

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deckpuller.ui.AppRoot
import com.deckpuller.ui.theme.DeckPullerTheme
import org.koin.compose.KoinContext

/**
 * The shared Compose entry point hosted by both platforms: Android's `MainActivity.setContent`
 * and iOS's `ComposeUIViewController`. [KoinContext] binds the already-started Koin graph into
 * the composition so `koinViewModel()` resolves on every platform.
 */
@Composable
fun App() {
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
