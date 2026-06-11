package com.deckpuller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.local.entity.DeckEntity

@Database(
    entities = [DeckEntity::class, CardEntity::class, CollectionCardEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        /** Adds the Archidekt category column without wiping existing decks. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the ManaBox collection table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS collection_cards (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nameKey TEXT NOT NULL, name TEXT NOT NULL, setCode TEXT NOT NULL, " +
                        "setName TEXT NOT NULL, collectorNumber TEXT NOT NULL, scryfallId TEXT, " +
                        "finish TEXT NOT NULL, condition TEXT NOT NULL, language TEXT NOT NULL, " +
                        "binderName TEXT NOT NULL, quantity INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_cards_nameKey ON collection_cards(nameKey)")
            }
        }
    }
}
