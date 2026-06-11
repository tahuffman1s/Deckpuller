package com.deckpuller.di

import androidx.room.Room
import coil.ImageLoader
import com.deckpuller.data.CollectionImporter
import com.deckpuller.data.image.CoilImagePrefetcher
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.data.repository.DefaultCollectionRepository
import com.deckpuller.data.repository.DefaultDeckRepository
import com.deckpuller.data.update.UpdateManager
import com.deckpuller.ui.collection.CollectionViewModel
import com.deckpuller.ui.decklist.DeckListViewModel
import com.deckpuller.ui.importdeck.ImportViewModel
import com.deckpuller.ui.pull.PullViewModel
import com.deckpuller.ui.shopping.ShoppingListViewModel
import com.deckpuller.ui.update.UpdateViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val appModule = module {

    single { Json { ignoreUnknownKeys = true } }

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

    single<ArchidektApi> {
        Retrofit.Builder()
            .baseUrl("https://archidekt.com/api/")
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ArchidektApi::class.java)
    }

    single<ScryfallApi> {
        Retrofit.Builder()
            .baseUrl("https://api.scryfall.com/")
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ScryfallApi::class.java)
    }

    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "deckpuller.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().collectionDao() }

    single { ImageLoader(androidContext()) }

    single { UserPreferences(androidContext()) }
    single { CollectionImporter(androidContext()) }
    single { UpdateManager(get(), androidContext()) }

    single<ImagePrefetcher> { CoilImagePrefetcher(androidContext(), get()) }
    single<DeckRepository> { DefaultDeckRepository(get(), get(), get(), get()) }
    single<CollectionRepository> { DefaultCollectionRepository(get(), get()) }

    viewModel { PullViewModel(get(), get(), get()) }
    viewModel { ShoppingListViewModel(get(), get(), get()) }
    viewModel { CollectionViewModel(get(), get()) }
    viewModel { DeckListViewModel(get()) }
    viewModel { ImportViewModel(get(), get()) }
    viewModel { UpdateViewModel(get()) }
}
