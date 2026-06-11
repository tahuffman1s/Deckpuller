package com.deckpuller.data

import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun `maps DeckWithCards to domain Deck preserving fields`() {
        val entity = DeckWithCards(
            deck = DeckEntity(id = 1L, name = "Deck", archidektId = "1", sourceUrl = "url/1", importedAt = 5L),
            cards = listOf(
                CardEntity(
                    id = 7L, deckId = 1L, scryfallId = "uid-1", name = "Forest",
                    typeLine = "Basic Land — Forest", category = "Ramp", imageUrl = "f.jpg",
                    requiredQty = 4, pulledQty = 2,
                ),
            ),
        )

        val deck = entity.toDomain()

        assertEquals("Deck", deck.name)
        val card = deck.cards.single()
        assertEquals(7L, card.id)
        assertEquals("uid-1", card.scryfallId)
        assertEquals("Forest", card.name)
        assertEquals("Basic Land — Forest", card.typeLine)
        assertEquals("f.jpg", card.imageUrl)
        assertEquals(4, card.requiredQty)
        assertEquals(2, card.pulledQty)
        assertEquals("Ramp", card.category)
    }
}
