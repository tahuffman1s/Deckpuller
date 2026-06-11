package com.deckpuller.ui.pull

import com.deckpuller.domain.model.CardGroup
import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetIndexTest {

    private fun card(name: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = name, name = name,
        typeLine = "Creature", imageUrl = null, requiredQty = 1, pulledQty = 0,
    )

    @Test
    fun `maps first letters to lazy indices accounting for sticky headers`() {
        val groups = listOf(
            CardGroup("Creature", listOf(card("Anger"), card("Battle Squadron"))),
            CardGroup("Land", listOf(card("Forest"))),
        )

        val index = buildAlphabetIndex(groups)

        // header=0, Anger=1, Battle=2, header=3, Forest=4
        assertEquals(1, index['A'])
        assertEquals(2, index['B'])
        assertEquals(4, index['F'])
        assertEquals(null, index['C'])
    }

    @Test
    fun `non-letter initials collapse to hash`() {
        val groups = listOf(CardGroup("Token", listOf(card("9th Sphere"))))
        val index = buildAlphabetIndex(groups)
        assertEquals(1, index['#'])
    }
}
