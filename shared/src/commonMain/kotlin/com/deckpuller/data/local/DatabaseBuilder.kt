package com.deckpuller.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Applies DeckPuller's migrations, bundled SQLite driver and IO query context to a
 * platform-built [AppDatabase] builder, then builds it. Each platform supplies the builder
 * (Android needs a `Context`, iOS a file path); everything after is identical.
 */
fun RoomDatabase.Builder<AppDatabase>.buildDeckPullerDatabase(): AppDatabase =
    addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
