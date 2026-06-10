package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtoParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses archidekt deck ignoring unknown fields`() {
        val payload = """
        {
          "id": 42,
          "name": "My Deck",
          "private": false,
          "cards": [
            {
              "id": 1, "quantity": 4, "modifier": "Normal",
              "card": {
                "id": 9, "uid": "abc-123", "rarity": "common",
                "oracleCard": { "name": "Llanowar Elves", "types": ["Creature"] }
              }
            }
          ]
        }
        """.trimIndent()

        val deck = json.decodeFromString<ArchidektDeckDto>(payload)

        assertEquals("My Deck", deck.name)
        assertEquals(1, deck.cards.size)
        assertEquals(4, deck.cards[0].quantity)
        assertEquals("abc-123", deck.cards[0].card.uid)
        assertEquals("Llanowar Elves", deck.cards[0].card.oracleCard.name)
    }

    @Test
    fun `parses scryfall single-faced card`() {
        val payload = """
        {
          "data": [
            {
              "id": "abc-123",
              "name": "Llanowar Elves",
              "type_line": "Creature — Elf Druid",
              "image_uris": { "small": "s.jpg", "normal": "n.jpg" }
            }
          ],
          "not_found": []
        }
        """.trimIndent()

        val resp = json.decodeFromString<ScryfallCollectionResponse>(payload)
        val card = resp.data.single()

        assertEquals("Creature — Elf Druid", card.bestTypeLine())
        assertEquals("n.jpg", card.bestImageUrl())
        assertEquals(0, resp.notFound.size)
    }

    @Test
    fun `parses scryfall double-faced card using front face for type and image`() {
        val payload = """
        {
          "data": [
            {
              "id": "dfc-1",
              "name": "Front // Back",
              "card_faces": [
                { "type_line": "Creature — Werewolf", "image_uris": { "normal": "front.jpg" } },
                { "type_line": "Creature — Werewolf", "image_uris": { "normal": "back.jpg" } }
              ]
            }
          ]
        }
        """.trimIndent()

        val card = json.decodeFromString<ScryfallCollectionResponse>(payload).data.single()

        assertEquals("Creature — Werewolf", card.bestTypeLine())
        assertEquals("front.jpg", card.bestImageUrl())
        assertNull(card.imageUris)
    }
}
