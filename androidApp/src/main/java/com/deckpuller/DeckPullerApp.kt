package com.deckpuller

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.deckpuller.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class DeckPullerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Robolectric reuses one JVM across tests and instantiates the Application for each,
        // so Koin's process-global context can already be running; only start it once.
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@DeckPullerApp)
                androidLogger()
                modules(appModule)
            }
        }
        // Make every AsyncImage / prefetch share the Koin-provided, Ktor-backed ImageLoader.
        // setSafe won't overwrite an already-set loader (e.g. when Robolectric reuses the JVM).
        SingletonImageLoader.setSafe { GlobalContext.get().get<ImageLoader>() }
    }
}
