package com.deckpuller.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScryfallImageUrlTest {

    private val normal =
        "https://cards.scryfall.io/normal/front/b/c/bcde2345-0000-0000-0000-000000000000.jpg"
    private val small =
        "https://cards.scryfall.io/small/front/b/c/bcde2345-0000-0000-0000-000000000000.jpg"

    @Test
    fun `retargets a full-size card image at the thumbnail rendition`() {
        assertEquals(small, scryfallSized(normal, "small"))
    }

    @Test
    fun `retargets a back face too`() {
        assertEquals(
            "https://cards.scryfall.io/small/back/b/c/bcde2345.jpg",
            scryfallSized("https://cards.scryfall.io/normal/back/b/c/bcde2345.jpg", "small"),
        )
    }

    @Test
    fun `leaves a url that is already the requested size alone`() {
        assertEquals(small, scryfallSized(small, "small"))
    }

    @Test
    fun `passes through urls it does not recognise`() {
        assertNull(scryfallSized(null, "small"))
        assertEquals("ramp.jpg", scryfallSized("ramp.jpg", "small"))
        assertEquals(
            "https://example.com/normal/front/a/b/c.jpg",
            scryfallSized("https://example.com/normal/front/a/b/c.jpg", "small"),
        )
        // Unknown leading segment — not a size, so nothing is rewritten.
        assertEquals(
            "https://cards.scryfall.io/huge/front/a/b/c.jpg",
            scryfallSized("https://cards.scryfall.io/huge/front/a/b/c.jpg", "small"),
        )
    }

    @Test
    fun `leaves non-jpg renditions alone`() {
        // The png rendition is only served under the png path, so swapping the size
        // segment would 404.
        val png = "https://cards.scryfall.io/png/front/a/b/c.png"
        assertEquals(png, scryfallSized(png, "small"))
    }

    @Test
    fun `builds thumbnail urls from a bare id`() {
        assertEquals(
            "https://cards.scryfall.io/small/front/b/c/bcde2345.jpg",
            scryfallImageUrl("BCDE2345"),
        )
        assertNull(scryfallImageUrl(null))
        assertNull(scryfallImageUrl("x"))
    }
}
