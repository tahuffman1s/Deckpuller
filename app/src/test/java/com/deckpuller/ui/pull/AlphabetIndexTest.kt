package com.deckpuller.ui.pull

import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetIndexTest {

    private fun card(name: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = name, name = name,
        typeLine = "Creature", imageUrl = null, requiredQty = 1, pulledQty = 0,
    )

    @Test
    fun `maps first letters to their position in the flat list`() {
        val cards = listOf(card("Anger"), card("Battle Squadron"), card("Forest"))

        val index = buildAlphabetIndex(cards)

        // Anger=0, Battle=1, Forest=2
        assertEquals(0, index['A'])
        assertEquals(1, index['B'])
        assertEquals(2, index['F'])
        assertEquals(null, index['C'])
    }

    @Test
    fun `non-letter initials collapse to hash`() {
        val index = buildAlphabetIndex(listOf(card("9th Sphere")))
        assertEquals(0, index['#'])
    }
}
