package com.deckpuller.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Builds the [AppDatabase] under the iOS app's Documents directory. */
@OptIn(ExperimentalForeignApi::class)
fun iosAppDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val documentsUrl = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val path = requireNotNull(documentsUrl?.URLByAppendingPathComponent("deckpuller.db")?.path) {
        "Could not resolve the iOS Documents directory for the database"
    }
    return Room.databaseBuilder<AppDatabase>(name = path)
}
