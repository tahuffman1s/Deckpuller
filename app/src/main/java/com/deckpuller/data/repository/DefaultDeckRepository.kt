package com.deckpuller.data.repository

import com.deckpuller.data.ArchidektUrlParser
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.DeckDao
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.dto.ArchidektCardDto
import com.deckpuller.data.remote.dto.ScryfallCardDto
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallIdentifier
import com.deckpuller.data.toDomain
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
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

    override fun observeDecks(): Flow<List<DeckWithCards>> = dao.observeDecks()

    override fun observeDeck(id: Long): Flow<Deck?> =
        dao.observeDeck(id).map { it?.toDomain() }

    override suspend fun importDeck(url: String): Long {
        val archidektId = ArchidektUrlParser.parseDeckId(url) ?: throw InvalidDeckUrlException()
        return importDeckById(archidektId, url)
    }

    override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long {
        val deckDto = archidektApi.getDeck(archidektId)
        val scryfallById = fetchScryfall(deckDto.cards.map { it.card.uid })
        val cards = deckDto.cards.map { entry ->
            buildCard(deckId = 0, entry = entry, scryfall = scryfallById[entry.card.uid], pulled = 0)
        }
        imagePrefetcher.prefetch(cards.mapNotNull { it.imageUrl })
        return dao.insertDeckWithCards(
            DeckEntity(
                name = deckDto.name,
                archidektId = archidektId,
                sourceUrl = sourceUrl,
                importedAt = System.currentTimeMillis(),
            ),
            cards,
        )
    }

    override suspend fun refreshDeck(deckId: Long) {
        val deck = dao.deckById(deckId) ?: return
        val deckDto = archidektApi.getDeck(deck.archidektId)
        val scryfallById = fetchScryfall(deckDto.cards.map { it.card.uid })
        val previousPulled = dao.cardsForDeck(deckId).associate { it.scryfallId to it.pulledQty }
        val cards = deckDto.cards.map { entry ->
            val prior = previousPulled[entry.card.uid] ?: 0
            buildCard(
                deckId = deckId,
                entry = entry,
                scryfall = scryfallById[entry.card.uid],
                pulled = prior.coerceAtMost(entry.quantity),
            )
        }
        imagePrefetcher.prefetch(cards.mapNotNull { it.imageUrl })
        dao.replaceCards(deckId, deckDto.name, cards)
    }

    override suspend fun resetProgress(deckId: Long) = dao.resetProgress(deckId)

    override suspend fun deleteDeck(deckId: Long) = dao.deleteDeck(deckId)

    override suspend fun setPulled(cardId: Long, pulled: Int) = dao.updatePulled(cardId, pulled)

    override suspend fun searchDecks(username: String): List<DeckSummary> =
        archidektApi.searchByOwner(username = username).results.map { dto ->
            DeckSummary(
                archidektId = dto.id.toString(),
                name = dto.name,
                cardCount = dto.size,
                thumbnailUrl = dto.featured.ifBlank { null },
            )
        }

    private suspend fun fetchScryfall(uids: List<String>): Map<String, ScryfallCardDto> =
        uids.distinct()
            .chunked(SCRYFALL_BATCH)
            .flatMapIndexed { index, chunk ->
                if (index > 0) delay(SCRYFALL_THROTTLE_MS)
                val request = ScryfallCollectionRequest(chunk.map { ScryfallIdentifier(it) })
                scryfallApi.getCollection(request).data
            }
            .associateBy { it.id }

    private fun buildCard(
        deckId: Long,
        entry: ArchidektCardDto,
        scryfall: ScryfallCardDto?,
        pulled: Int,
    ) = CardEntity(
        deckId = deckId,
        scryfallId = entry.card.uid,
        name = scryfall?.name ?: entry.card.oracleCard.name,
        typeLine = scryfall?.bestTypeLine() ?: "Unknown",
        imageUrl = scryfall?.bestImageUrl(),
        requiredQty = entry.quantity,
        pulledQty = pulled,
    )
}
