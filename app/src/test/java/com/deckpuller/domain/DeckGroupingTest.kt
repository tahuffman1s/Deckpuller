package com.deckpuller.domain

import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckGroupingTest {

    private fun card(name: String, typeLine: String) = DeckCard(
        id = 0,
        scryfallId = name,
        name = name,
        typeLine = typeLine,
        imageUrl = null,
        requiredQty = 1,
        pulledQty = 0,
    )

    @Test
    fun `groups by primary type and sorts cards by name within a group`() {
        val cards = listOf(
            card("Llanowar Elves", "Creature — Elf Druid"),
            card("Birds of Paradise", "Creature — Bird"),
            card("Forest", "Basic Land — Forest"),
        )

        val groups = DeckGrouping.group(cards)

        assertEquals(listOf("Creature", "Land"), groups.map { it.type })
        assertEquals(
            listOf("Birds of Paradise", "Llanowar Elves"),
            groups.first { it.type == "Creature" }.cards.map { it.name },
        )
    }

    @Test
    fun `groups are ordered by the canonical type order with Other and Unknown last`() {
        val cards = listOf(
            card("Mystery", "Dungeon"),
            card("Sol Ring", "Artifact"),
            card("Shock", "Instant"),
        )

        val groups = DeckGrouping.group(cards)

        assertEquals(listOf("Instant", "Artifact", "Other"), groups.map { it.type })
    }
}
