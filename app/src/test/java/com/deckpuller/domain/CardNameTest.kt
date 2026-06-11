package com.deckpuller.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardNameTest {

    @Test
    fun `lowercases and trims`() {
        assertEquals("sol ring", CardName.normalize("  Sol Ring  "))
    }

    @Test
    fun `collapses internal whitespace`() {
        assertEquals("rhystic study", CardName.normalize("Rhystic   Study"))
    }

    @Test
    fun `folds accents`() {
        assertEquals("lim-dul the necromancer", CardName.normalize("Lim-Dûl the Necromancer"))
    }

    @Test
    fun `keeps double-faced separator consistent`() {
        assertEquals(
            "pestilent cauldron // restorative burst",
            CardName.normalize("Pestilent Cauldron // Restorative Burst"),
        )
    }

    @Test
    fun `normalizes loose dfc separator spacing to double-space slashes`() {
        assertEquals("a // b", CardName.normalize("A//B"))
        assertEquals("a // b", CardName.normalize("A / B"))
    }

    @Test
    fun `keeps apostrophes and commas but compares case-insensitively`() {
        assertEquals("conqueror's foothold", CardName.normalize("Conqueror's Foothold"))
        assertEquals("mazirek, kraul death priest", CardName.normalize("Mazirek, Kraul Death Priest"))
    }

    @Test
    fun `blank in blank out`() {
        assertEquals("", CardName.normalize("   "))
    }

    @Test
    fun `already normalised name is unchanged`() {
        val key = "sol ring"
        assertEquals(key, CardName.normalize(key))
    }
}
