package com.deckpuller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchidektUrlParserTest {

    @Test
    fun `extracts id from full url with deck name`() {
        val id = ArchidektUrlParser.parseDeckId("https://archidekt.com/decks/1234567/my-cool-deck")
        assertEquals("1234567", id)
    }

    @Test
    fun `extracts id from url without trailing name`() {
        val id = ArchidektUrlParser.parseDeckId("https://archidekt.com/decks/9988776")
        assertEquals("9988776", id)
    }

    @Test
    fun `extracts id ignoring query params and trailing slash`() {
        val id = ArchidektUrlParser.parseDeckId("archidekt.com/decks/42/?tab=view")
        assertEquals("42", id)
    }

    @Test
    fun `returns null for url with no deck id`() {
        assertNull(ArchidektUrlParser.parseDeckId("https://archidekt.com/search/decks"))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(ArchidektUrlParser.parseDeckId("   "))
    }
}
