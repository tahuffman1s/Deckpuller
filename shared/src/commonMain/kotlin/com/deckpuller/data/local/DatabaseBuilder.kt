package com.deckpuller.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.CoroutineContext

/**
 * Applies DeckPuller's migrations, bundled SQLite driver and query context to a platform-built
 * [AppDatabase] builder, then builds it. Each platform supplies the builder (Android needs a
 * `Context`, iOS a file path) and the [queryContext] (`Dispatchers.IO`, which lives in the
 * platform source sets — it isn't visible from commonMain); everything after is identical.
 */
fun RoomDatabase.Builder<AppDatabase>.buildDeckPullerDatabase(
    queryContext: CoroutineContext,
): AppDatabase =
    addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryContext)
        .build()
