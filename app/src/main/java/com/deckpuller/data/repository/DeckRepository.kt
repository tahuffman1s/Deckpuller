package com.deckpuller.data.repository

import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeDeck(): Flow<Deck?>

    /** @throws com.deckpuller.data.InvalidDeckUrlException for an unparseable URL. */
    suspend fun importDeck(url: String)

    suspend fun setPulled(cardId: Long, pulled: Int)

    suspend fun clearDeck()
}
