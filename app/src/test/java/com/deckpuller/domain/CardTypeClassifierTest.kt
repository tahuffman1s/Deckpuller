package com.deckpuller.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTypeClassifierTest {

    @Test
    fun `classifies a simple creature`() {
        assertEquals("Creature", CardTypeClassifier.primaryType("Legendary Creature — Elf Druid"))
    }

    @Test
    fun `artifact creature classified as creature`() {
        assertEquals("Creature", CardTypeClassifier.primaryType("Artifact Creature — Golem"))
    }

    @Test
    fun `classifies a land`() {
        assertEquals("Land", CardTypeClassifier.primaryType("Basic Land — Forest"))
    }

    @Test
    fun `classifies an instant`() {
        assertEquals("Instant", CardTypeClassifier.primaryType("Instant"))
    }

    @Test
    fun `uses front face of a modal double-faced type line`() {
        assertEquals("Creature", CardTypeClassifier.primaryType("Creature — Elf // Land — Forest"))
    }

    @Test
    fun `blank type line is Unknown`() {
        assertEquals("Unknown", CardTypeClassifier.primaryType(""))
        assertEquals("Unknown", CardTypeClassifier.primaryType(null))
    }

    @Test
    fun `unrecognized type line is Other`() {
        assertEquals("Other", CardTypeClassifier.primaryType("Dungeon"))
    }
}
