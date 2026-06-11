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
    @Query("SELECT * FROM decks ORDER BY importedAt DESC")
    fun observeDecks(): Flow<List<DeckWithCards>>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<DeckWithCards?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun deckById(id: Long): DeckEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun cardsForDeck(deckId: Long): List<CardEntity>

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsForDeck(deckId: Long)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)

    @Query("UPDATE cards SET pulledQty = :pulled WHERE id = :cardId")
    suspend fun updatePulled(cardId: Long, pulled: Int)

    @Query("UPDATE cards SET pulledQty = 0 WHERE deckId = :deckId")
    suspend fun resetProgress(deckId: Long)

    @Query("UPDATE decks SET name = :name WHERE id = :id")
    suspend fun updateDeckName(id: Long, name: String)

    /** Insert a deck and its cards atomically; returns the new deck id. */
    @Transaction
    suspend fun insertDeckWithCards(deck: DeckEntity, cards: List<CardEntity>): Long {
        val deckId = insertDeck(deck)
        insertCards(cards.map { it.copy(deckId = deckId) })
        return deckId
    }

    /** Replace a deck's cards (used by refresh); name is updated too. */
    @Transaction
    suspend fun replaceCards(deckId: Long, name: String, cards: List<CardEntity>) {
        deleteCardsForDeck(deckId)
        insertCards(cards.map { it.copy(deckId = deckId) })
        updateDeckName(deckId, name)
    }
}
