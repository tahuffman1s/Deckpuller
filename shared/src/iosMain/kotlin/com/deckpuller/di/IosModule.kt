package com.deckpuller.di

import coil3.PlatformContext
import com.deckpuller.data.local.buildDeckPullerDatabase
import com.deckpuller.data.local.iosAppDatabaseBuilder
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.prefs.createDataStore
import com.deckpuller.data.prefs.iosDataStorePath
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

/** iOS-specific bindings: the platform context, the on-disk Room DB and DataStore. */
val iosModule = module {
    single<PlatformContext> { PlatformContext.INSTANCE }
    // Dispatchers.IO is internal on Kotlin/Native; Default is the public multiplatform pool.
    single { iosAppDatabaseBuilder().buildDeckPullerDatabase(Dispatchers.Default) }
    single { UserPreferences(createDataStore { iosDataStorePath() }) }
}
