package com.deckpuller.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.local.entity.DeckEntity

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Database(
    entities = [DeckEntity::class, CardEntity::class, CollectionCardEntity::class],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        /** Adds the Archidekt category column without wiping existing decks. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE cards ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the ManaBox collection table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS collection_cards (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nameKey TEXT NOT NULL, name TEXT NOT NULL, setCode TEXT NOT NULL, " +
                        "setName TEXT NOT NULL, collectorNumber TEXT NOT NULL, scryfallId TEXT, " +
                        "finish TEXT NOT NULL, condition TEXT NOT NULL, language TEXT NOT NULL, " +
                        "binderName TEXT NOT NULL, quantity INTEGER NOT NULL)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_collection_cards_nameKey ON collection_cards(nameKey)")
            }
        }
    }
}
