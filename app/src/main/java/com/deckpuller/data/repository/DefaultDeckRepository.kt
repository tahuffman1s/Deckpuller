package com.deckpuller.data.repository

import com.deckpuller.data.ArchidektUrlParser
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.DeckDao
import com.deckpuller.data.local.entity.CURRENT_DECK_ID
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallIdentifier
import com.deckpuller.data.toDomain
import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val SCRYFALL_BATCH = 75
private const val SCRYFALL_THROTTLE_MS = 100L

class DefaultDeckRepository @Inject constructor(
    private val archidektApi: ArchidektApi,
    private val scryfallApi: ScryfallApi,
    private val dao: DeckDao,
    private val imagePrefetcher: ImagePrefetcher,
) : DeckRepository {

    override fun observeDeck(): Flow<Deck?> =
        dao.observeDeck().map { it?.toDomain() }

    override suspend fun importDeck(url: String) {
        val deckId = ArchidektUrlParser.parseDeckId(url) ?: throw InvalidDeckUrlException()
        val deckDto = archidektApi.getDeck(deckId)

        val scryfallById = deckDto.cards
            .map { it.card.uid }
            .distinct()
            .chunked(SCRYFALL_BATCH)
            .flatMapIndexed { index, chunk ->
                if (index > 0) delay(SCRYFALL_THROTTLE_MS)
                val request = ScryfallCollectionRequest(chunk.map { ScryfallIdentifier(it) })
                scryfallApi.getCollection(request).data
            }
            .associateBy { it.id }

        val cards = deckDto.cards.map { entry ->
            val scryfall = scryfallById[entry.card.uid]
            CardEntity(
                deckId = CURRENT_DECK_ID,
                scryfallId = entry.card.uid,
                name = scryfall?.name ?: entry.card.oracleCard.name,
                typeLine = scryfall?.bestTypeLine() ?: "Unknown",
                imageUrl = scryfall?.bestImageUrl(),
                requiredQty = entry.quantity,
                pulledQty = 0,
            )
        }

        imagePrefetcher.prefetch(cards.mapNotNull { it.imageUrl })

        dao.replaceDeck(
            DeckEntity(name = deckDto.name, importedAt = System.currentTimeMillis()),
            cards,
        )
    }

    override suspend fun setPulled(cardId: Long, pulled: Int) =
        dao.updatePulled(cardId, pulled)

    override suspend fun clearDeck() = dao.clearDecks()
}
