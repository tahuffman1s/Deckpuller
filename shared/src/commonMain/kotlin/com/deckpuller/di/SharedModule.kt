package com.deckpuller.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.deckpuller.data.image.CoilImagePrefetcher
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.buildHttpClient
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.data.repository.DefaultCollectionRepository
import com.deckpuller.data.repository.DefaultDeckRepository
import com.deckpuller.ui.collection.CollectionViewModel
import com.deckpuller.ui.decklist.DeckListViewModel
import com.deckpuller.ui.importdeck.ImportViewModel
import com.deckpuller.ui.pull.PullViewModel
import com.deckpuller.ui.shopping.ShoppingListViewModel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Platform-agnostic graph: networking, database access, repositories and view models. Each
 * platform supplies a companion module ([com.deckpuller.di] android/ios) for the things that need
 * a `Context`/file path — the [PlatformContext], the [AppDatabase] builder, [UserPreferences],
 * and (Android only) the self-update machinery.
 */
val sharedModule = module {
    single { Json { ignoreUnknownKeys = true } }

    single { buildHttpClient(get()) }
    single { ArchidektApi(get()) }
    single { ScryfallApi(get()) }

    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().collectionDao() }

    single {
        ImageLoader.Builder(get<PlatformContext>())
            .components { add(KtorNetworkFetcherFactory(get<HttpClient>())) }
            .build()
    }
    single<ImagePrefetcher> { CoilImagePrefetcher(get(), get()) }

    single<DeckRepository> { DefaultDeckRepository(get(), get(), get(), get()) }
    single<CollectionRepository> { DefaultCollectionRepository(get(), get()) }

    viewModel { PullViewModel(get(), get(), get()) }
    viewModel { ShoppingListViewModel(get(), get(), get()) }
    viewModel { CollectionViewModel(get()) }
    viewModel { DeckListViewModel(get()) }
    viewModel { ImportViewModel(get(), get()) }
}
