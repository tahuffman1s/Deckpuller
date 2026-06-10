package com.deckpuller.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.dto.ArchidektCardDetailDto
import com.deckpuller.data.remote.dto.ArchidektCardDto
import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektOracleCardDto
import com.deckpuller.data.remote.dto.ScryfallCardDto
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import com.deckpuller.data.remote.dto.ScryfallImageUris
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultDeckRepositoryTest {

    private lateinit var db: AppDatabase
    private val prefetched = mutableListOf<String>()
    private val fakePrefetcher = ImagePrefetcher { prefetched.addAll(it) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun archidektCard(uid: String, name: String, qty: Int) = ArchidektCardDto(
        quantity = qty,
        card = ArchidektCardDetailDto(uid = uid, oracleCard = ArchidektOracleCardDto(name)),
    )

    private fun scryfallCard(id: String, name: String, type: String) = ScryfallCardDto(
        id = id,
        name = name,
        typeLine = type,
        imageUris = ScryfallImageUris(small = "$id-s.jpg", normal = "$id-n.jpg"),
    )

    private fun repository(
        archidekt: ArchidektApi,
        scryfall: ScryfallApi,
    ) = DefaultDeckRepository(archidekt, scryfall, db.deckDao(), fakePrefetcher)

    @Test
    fun `importDeck throws on bad url`() = runTest {
        val repo = repository(
            archidekt = { fail("should not be called"); error("") },
            scryfall = { fail("should not be called"); error("") },
        )
        try {
            repo.importDeck("https://example.com/not-a-deck")
            fail("expected InvalidDeckUrlException")
        } catch (e: InvalidDeckUrlException) {
            // expected
        }
    }

    @Test
    fun `importDeck builds cards from archidekt and scryfall and prefetches images`() = runTest {
        val deckDto = ArchidektDeckDto(
            name = "Test Deck",
            cards = listOf(
                archidektCard("uid-1", "Forest", 4),
                archidektCard("uid-2", "Sol Ring", 1),
            ),
        )
        val repo = repository(
            archidekt = { deckDto },
            scryfall = { req ->
                ScryfallCollectionResponse(
                    data = req.identifiers.map {
                        when (it.id) {
                            "uid-1" -> scryfallCard("uid-1", "Forest", "Basic Land — Forest")
                            else -> scryfallCard("uid-2", "Sol Ring", "Artifact")
                        }
                    },
                )
            },
        )

        repo.importDeck("https://archidekt.com/decks/999/test")

        val deck = repo.observeDeck().first()!!
        assertEquals("Test Deck", deck.name)
        assertEquals(2, deck.cards.size)
        val forest = deck.cards.first { it.name == "Forest" }
        assertEquals(4, forest.requiredQty)
        assertEquals(0, forest.pulledQty)
        assertEquals("Basic Land — Forest", forest.typeLine)
        assertEquals("uid-1-n.jpg", forest.imageUrl)
        assertTrue(prefetched.containsAll(listOf("uid-1-n.jpg", "uid-2-n.jpg")))
    }

    @Test
    fun `importDeck falls back to archidekt name and Unknown type for missing scryfall card`() = runTest {
        val deckDto = ArchidektDeckDto(
            name = "Deck",
            cards = listOf(archidektCard("uid-x", "Mystery Card", 1)),
        )
        val repo = repository(
            archidekt = { deckDto },
            scryfall = { ScryfallCollectionResponse(data = emptyList()) },
        )

        repo.importDeck("https://archidekt.com/decks/1")

        val card = repo.observeDeck().first()!!.cards.single()
        assertEquals("Mystery Card", card.name)
        assertEquals("Unknown", card.typeLine)
        assertNull(card.imageUrl)
    }

    @Test
    fun `setPulled updates the stored count`() = runTest {
        val repo = repository(
            archidekt = {
                ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4)))
            },
            scryfall = {
                ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land")))
            },
        )
        repo.importDeck("https://archidekt.com/decks/1")
        val cardId = repo.observeDeck().first()!!.cards.single().id

        repo.setPulled(cardId, 2)

        assertEquals(2, repo.observeDeck().first()!!.cards.single().pulledQty)
    }

    @Test
    fun `clearDeck empties the deck`() = runTest {
        val repo = repository(
            archidekt = {
                ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 1)))
            },
            scryfall = {
                ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land")))
            },
        )
        repo.importDeck("https://archidekt.com/decks/1")

        repo.clearDeck()

        assertNull(repo.observeDeck().first())
    }
}
