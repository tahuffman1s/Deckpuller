package com.deckpuller.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.deckpuller.data.image.CoilImagePrefetcher
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.local.DeckDao
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.data.repository.DefaultDeckRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindDeckRepository(impl: DefaultDeckRepository): DeckRepository

    @Binds
    @Singleton
    abstract fun bindImagePrefetcher(impl: CoilImagePrefetcher): ImagePrefetcher

    companion object {

        @Provides
        @Singleton
        fun provideJson(): Json = Json { ignoreUnknownKeys = true }

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "DeckPuller/1.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        @Provides
        @Singleton
        fun provideArchidektApi(okHttpClient: OkHttpClient, json: Json): ArchidektApi =
            Retrofit.Builder()
                .baseUrl("https://archidekt.com/api/")
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ArchidektApi::class.java)

        @Provides
        @Singleton
        fun provideScryfallApi(okHttpClient: OkHttpClient, json: Json): ScryfallApi =
            Retrofit.Builder()
                .baseUrl("https://api.scryfall.com/")
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ScryfallApi::class.java)

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "deckpuller.db")
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()

        @Provides
        @Singleton
        fun provideDeckDao(db: AppDatabase): DeckDao = db.deckDao()

        @Provides
        @Singleton
        fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
            ImageLoader(context)
    }
}
