package com.deckpuller.data.repository

import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeDecks(): Flow<List<DeckWithCards>>
    fun observeDeck(id: Long): Flow<Deck?>

    /** @return new deck id. @throws com.deckpuller.data.InvalidDeckUrlException for an unparseable URL. */
    suspend fun importDeck(url: String): Long

    /** @return new deck id. Imports directly from an Archidekt numeric id (used by username browse). */
    suspend fun importDeckById(archidektId: String, sourceUrl: String): Long

    /** Re-fetch the deck from Archidekt, preserving pulled progress by scryfallId. */
    suspend fun refreshDeck(deckId: Long)

    suspend fun resetProgress(deckId: Long)
    suspend fun deleteDeck(deckId: Long)
    suspend fun setPulled(cardId: Long, pulled: Int)

    /** List a user's public Archidekt decks by exact username. */
    suspend fun searchDecks(username: String): List<DeckSummary>

    /**
     * The Scryfall colour identity (e.g. `["G", "U"]`) for a single card, used to theme
     * the pull screen from the commander. Returns empty for colourless or on any failure.
     */
    suspend fun colorIdentity(scryfallId: String): List<String>

    /** Cheapest USD price per scryfallId (front of the printing). Missing/failed -> null. */
    suspend fun prices(scryfallIds: List<String>): Map<String, Double?>
}
