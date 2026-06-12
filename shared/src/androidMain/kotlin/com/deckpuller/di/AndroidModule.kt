package com.deckpuller.di

import androidx.room.Room
import com.deckpuller.data.CollectionImporter
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.local.buildDeckPullerDatabase
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.prefs.createDataStore
import com.deckpuller.data.update.UpdateManager
import com.deckpuller.ui.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Android-specific bindings: Room/DataStore on disk, and self-update.
 *
 * Note: `PlatformContext` (a typealias for `android.content.Context` on Android) is NOT
 * registered here — koin-android already makes the `Context` injectable via
 * `androidContext(app)`, so `get<PlatformContext>()` in the shared module resolves to it.
 * Registering `single<PlatformContext> { androidContext() }` would resolve itself recursively
 * (StackOverflowError), since `androidContext()` is just `get<Context>()`.
 */
val androidModule = module {
    single {
        Room.databaseBuilder<AppDatabase>(androidContext(), "deckpuller.db")
            .buildDeckPullerDatabase(Dispatchers.IO)
    }

    single {
        UserPreferences(
            createDataStore {
                androidContext().filesDir.resolve("datastore/user_prefs.preferences_pb").absolutePath
            },
        )
    }

    single { CollectionImporter(androidContext()) }

    // OkHttp client used by UpdateManager for GitHub release downloads/redirects.
    single {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "DeckPuller/1.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    single { UpdateManager(get(), androidContext()) }
    viewModel { UpdateViewModel(get()) }
}
