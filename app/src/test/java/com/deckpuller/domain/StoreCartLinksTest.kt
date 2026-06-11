package com.deckpuller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCartLinksTest {

    private val items = listOf(
        StoreCartLinks.BuyItem(name = "Sol Ring", quantity = 1),
        StoreCartLinks.BuyItem(name = "Rhystic Study", quantity = 2),
    )

    @Test
    fun `clipboard text is one line per card with quantity`() {
        assertEquals("1 Sol Ring\n2 Rhystic Study", StoreCartLinks.clipboardText(items))
    }

    @Test
    fun `tcgplayer url encodes the list with double-pipe separator`() {
        val url = StoreCartLinks.tcgPlayerUrl(items)
        assertTrue(url.startsWith("https://www.tcgplayer.com/massentry?productline=Magic&c="))
        // "1 Sol Ring||2 Rhystic Study" url-encoded
        assertTrue(url.contains("1%20Sol%20Ring%7C%7C2%20Rhystic%20Study"))
    }

    @Test
    fun `card kingdom builder url points at the builder with the list`() {
        val url = StoreCartLinks.cardKingdomUrl(items)
        assertTrue(url.startsWith("https://www.cardkingdom.com/builder"))
    }

    @Test
    fun `tcgplayer falls back to plain massentry when list exceeds url budget`() {
        val huge = (1..500).map { StoreCartLinks.BuyItem("Card Number $it With A Long Name", 1) }
        val url = StoreCartLinks.tcgPlayerUrl(huge)
        assertEquals("https://www.tcgplayer.com/massentry?productline=Magic", url)
    }
}
