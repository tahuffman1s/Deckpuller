package com.deckpuller.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckCardTest {

    private fun card(printings: List<OwnedPrinting>) = DeckCard(
        id = 1,
        scryfallId = "x",
        name = "Sol Ring",
        typeLine = "Artifact",
        imageUrl = null,
        requiredQty = 1,
        pulledQty = 0,
        ownedPrintings = printings,
    )

    private fun printing(finish: String) = OwnedPrinting(
        setCode = "C21",
        finish = finish,
        quantity = 1,
        binderName = "main",
    )

    @Test
    fun `not foil with no owned printings`() {
        assertFalse(card(emptyList()).isFoil)
    }

    @Test
    fun `not foil when every printing is normal`() {
        assertFalse(card(listOf(printing("normal"), printing("normal"))).isFoil)
    }

    @Test
    fun `foil when any printing is foil`() {
        assertTrue(card(listOf(printing("normal"), printing("foil"))).isFoil)
    }

    @Test
    fun `foil for etched and other non-normal finishes`() {
        assertTrue(card(listOf(printing("etched"))).isFoil)
        assertTrue(card(listOf(printing("foil-etched"))).isFoil)
    }
}
