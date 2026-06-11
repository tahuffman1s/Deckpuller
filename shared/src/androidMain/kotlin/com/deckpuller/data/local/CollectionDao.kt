package com.deckpuller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.local.entity.OwnedTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collection_cards ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionCardEntity>>

    @Query("SELECT nameKey, SUM(quantity) AS qty FROM collection_cards GROUP BY nameKey")
    fun observeOwnedTotals(): Flow<List<OwnedTotal>>

    @Query("SELECT COUNT(*) FROM collection_cards")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(rows: List<CollectionCardEntity>)

    @Query("DELETE FROM collection_cards")
    suspend fun clear()

    /** Replace the entire collection atomically. */
    @Transaction
    suspend fun replaceAll(rows: List<CollectionCardEntity>) {
        clear()
        insertAll(rows)
    }
}
