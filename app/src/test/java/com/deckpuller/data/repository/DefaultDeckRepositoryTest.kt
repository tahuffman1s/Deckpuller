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
import com.deckpuller.data.remote.dto.ArchidektDeckListDto
import com.deckpuller.data.remote.dto.ArchidektDeckSummaryDto
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
        id = id, name = name, typeLine = type,
        imageUris = ScryfallImageUris(small = "$id-s.jpg", normal = "$id-n.jpg"),
    )

    private class FakeArchidektApi(
        val deck: (String) -> ArchidektDeckDto = { error("getDeck not stubbed") },
        val list: ArchidektDeckListDto = ArchidektDeckListDto(),
    ) : ArchidektApi {
        override suspend fun getDeck(deckId: String) = deck(deckId)
        override suspend fun searchByOwner(owner: String, exact: Boolean, orderBy: String, pageSize: Int) = list
    }

    private fun repo(archidekt: ArchidektApi, scryfall: ScryfallApi) =
        DefaultDeckRepository(archidekt, scryfall, db.deckDao(), fakePrefetcher)

    @Test
    fun `importDeck throws on bad url`() = runTest {
        val r = repo(FakeArchidektApi(), { fail("unused"); error("") })
        try {
            r.importDeck("https://example.com/not-a-deck")
            fail("expected InvalidDeckUrlException")
        } catch (e: InvalidDeckUrlException) { /* expected */ }
    }

    @Test
    fun `importDeck stores deck with archidektId and returns its id`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Test Deck", listOf(archidektCard("uid-1", "Forest", 4)))
        })
        val r = repo(archidekt, { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })

        val id = r.importDeck("https://archidekt.com/decks/999/test")

        val deck = r.observeDeck(id).first()!!
        assertEquals("Test Deck", deck.name)
        assertEquals(4, deck.cards.single().requiredQty)
        assertTrue(prefetched.contains("uid-1-n.jpg"))
    }

    @Test
    fun `refreshDeck preserves pulled progress matched by scryfallId and clamps to new required`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4), archidektCard("uid-2", "Sol Ring", 1)))
        })
        val scryfall = ScryfallApi {
            ScryfallCollectionResponse(it.identifiers.map { ident ->
                when (ident.id) {
                    "uid-1" -> scryfallCard("uid-1", "Forest", "Land")
                    else -> scryfallCard("uid-2", "Sol Ring", "Artifact")
                }
            })
        }
        val r = repo(archidekt, scryfall)
        val id = r.importDeck("https://archidekt.com/decks/1")
        val forest = r.observeDeck(id).first()!!.cards.first { it.name == "Forest" }
        r.setPulled(forest.id, 3)

        val archidekt2 = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck Renamed", listOf(archidektCard("uid-1", "Forest", 2), archidektCard("uid-3", "Llanowar Elves", 1)))
        })
        val r2 = repo(archidekt2, ScryfallApi {
            ScryfallCollectionResponse(it.identifiers.map { ident ->
                when (ident.id) {
                    "uid-1" -> scryfallCard("uid-1", "Forest", "Land")
                    else -> scryfallCard("uid-3", "Llanowar Elves", "Creature")
                }
            })
        })
        r2.refreshDeck(id)

        val deck = r2.observeDeck(id).first()!!
        assertEquals("Deck Renamed", deck.name)
        assertEquals(2, deck.cards.size)
        val refreshedForest = deck.cards.first { it.name == "Forest" }
        assertEquals(2, refreshedForest.requiredQty)
        assertEquals(2, refreshedForest.pulledQty)
        assertEquals(0, deck.cards.first { it.name == "Llanowar Elves" }.pulledQty)
    }

    @Test
    fun `resetProgress zeroes the deck`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4)))
        })
        val r = repo(archidekt, { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })
        val id = r.importDeck("https://archidekt.com/decks/1")
        val cardId = r.observeDeck(id).first()!!.cards.single().id
        r.setPulled(cardId, 4)

        r.resetProgress(id)

        assertEquals(0, r.observeDeck(id).first()!!.cards.single().pulledQty)
    }

    @Test
    fun `deleteDeck removes the deck`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 1)))
        })
        val r = repo(archidekt, { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })
        val id = r.importDeck("https://archidekt.com/decks/1")

        r.deleteDeck(id)

        assertNull(r.observeDeck(id).first())
    }

    @Test
    fun `searchDecks maps owner results to summaries`() = runTest {
        val archidekt = FakeArchidektApi(
            list = ArchidektDeckListDto(
                listOf(
                    ArchidektDeckSummaryDto(id = 111, name = "Goblins", size = 100, featured = "http://img/a.jpg"),
                    ArchidektDeckSummaryDto(id = 222, name = "Elves", size = 99, featured = ""),
                ),
            ),
        )
        val r = repo(archidekt, { ScryfallCollectionResponse() })

        val summaries = r.searchDecks("me")

        assertEquals(2, summaries.size)
        assertEquals("111", summaries[0].archidektId)
        assertEquals("Goblins", summaries[0].name)
        assertEquals(100, summaries[0].cardCount)
        assertEquals("http://img/a.jpg", summaries[0].thumbnailUrl)
        assertNull(summaries[1].thumbnailUrl)
    }
}
