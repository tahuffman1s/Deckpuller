package com.deckpuller

import android.app.Application
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
    }
}
