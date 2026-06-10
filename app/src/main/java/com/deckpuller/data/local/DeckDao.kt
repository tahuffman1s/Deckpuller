package com.deckpuller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Transaction
    @Query("SELECT * FROM decks LIMIT 1")
    fun observeDeck(): Flow<DeckWithCards?>

    @Insert
    suspend fun insertDeck(deck: DeckEntity)

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("DELETE FROM decks")
    suspend fun clearDecks()

    @Query("UPDATE cards SET pulledQty = :pulled WHERE id = :cardId")
    suspend fun updatePulled(cardId: Long, pulled: Int)

    @Transaction
    suspend fun replaceDeck(deck: DeckEntity, cards: List<CardEntity>) {
        clearDecks()
        insertDeck(deck)
        insertCards(cards)
    }
}
