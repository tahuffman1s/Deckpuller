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
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import com.deckpuller.data.remote.dto.ScryfallImageUris
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
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

    private fun archidektCard(uid: String, name: String, qty: Int, categories: List<String> = emptyList()) =
        ArchidektCardDto(
            quantity = qty,
            categories = categories,
            card = ArchidektCardDetailDto(uid = uid, oracleCard = ArchidektOracleCardDto(name)),
        )

    private fun scryfallCard(id: String, name: String, type: String) = ScryfallCardDto(
        id = id, name = name, typeLine = type,
        imageUris = ScryfallImageUris(small = "$id-s.jpg", normal = "$id-n.jpg"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Builds an [ArchidektApi] whose getDeck/searchByOwner are served from JSON via a Ktor MockEngine. */
    private fun fakeArchidekt(
        deck: (String) -> ArchidektDeckDto = { error("getDeck not stubbed") },
        list: ArchidektDeckListDto = ArchidektDeckListDto(),
    ): ArchidektApi {
        val client = HttpClient(
            MockEngine { request ->
                val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
                val url = request.url
                val body = when {
                    url.encodedPath.contains("/decks/v3/") ->
                        json.encodeToString(ArchidektDeckListDto.serializer(), list)
                    else -> {
                        // .../api/decks/{id}/
                        val id = url.encodedPath.trimEnd('/').substringAfterLast('/')
                        json.encodeToString(ArchidektDeckDto.serializer(), deck(id))
                    }
                }
                respond(body, headers = jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(json) }
        }
        return ArchidektApi(client)
    }

    /** Builds a [ScryfallApi] whose getCollection responds based on the posted request. */
    private fun fakeScryfall(
        handler: (ScryfallCollectionRequest) -> ScryfallCollectionResponse,
    ): ScryfallApi {
        val client = HttpClient(
            MockEngine { request ->
                val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
                val raw = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val req = json.decodeFromString(ScryfallCollectionRequest.serializer(), raw)
                respond(
                    json.encodeToString(ScryfallCollectionResponse.serializer(), handler(req)),
                    headers = jsonHeaders,
                )
            },
        ) {
            install(ContentNegotiation) { json(json) }
        }
        return ScryfallApi(client)
    }

    private fun repo(archidekt: ArchidektApi, scryfall: ScryfallApi) =
        DefaultDeckRepository(archidekt, scryfall, db.deckDao(), fakePrefetcher)

    @Test
    fun `importDeck throws on bad url`() = runTest {
        val r = repo(fakeArchidekt(), fakeScryfall { fail("unused"); error("") })
        try {
            r.importDeck("https://example.com/not-a-deck")
            fail("expected InvalidDeckUrlException")
        } catch (e: InvalidDeckUrlException) { /* expected */ }
    }

    @Test
    fun `importDeck stores deck with archidektId and returns its id`() = runTest {
        val archidekt = fakeArchidekt(deck = {
            ArchidektDeckDto("Test Deck", listOf(archidektCard("uid-1", "Forest", 4, categories = listOf("Ramp"))))
        })
        val r = repo(archidekt, fakeScryfall { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })

        val id = r.importDeck("https://archidekt.com/decks/999/test")

        val deck = r.observeDeck(id).first()!!
        assertEquals("Test Deck", deck.name)
        assertEquals(4, deck.cards.single().requiredQty)
        assertEquals("Ramp", deck.cards.single().category)
        assertTrue(prefetched.contains("uid-1-n.jpg"))
    }

    @Test
    fun `refreshDeck preserves pulled progress matched by scryfallId and clamps to new required`() = runTest {
        val archidekt = fakeArchidekt(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4), archidektCard("uid-2", "Sol Ring", 1)))
        })
        val scryfall = fakeScryfall {
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

        val archidekt2 = fakeArchidekt(deck = {
            ArchidektDeckDto("Deck Renamed", listOf(archidektCard("uid-1", "Forest", 2), archidektCard("uid-3", "Llanowar Elves", 1)))
        })
        val r2 = repo(archidekt2, fakeScryfall {
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
        val archidekt = fakeArchidekt(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4)))
        })
        val r = repo(archidekt, fakeScryfall { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })
        val id = r.importDeck("https://archidekt.com/decks/1")
        val cardId = r.observeDeck(id).first()!!.cards.single().id
        r.setPulled(cardId, 4)

        r.resetProgress(id)

        assertEquals(0, r.observeDeck(id).first()!!.cards.single().pulledQty)
    }

    @Test
    fun `deleteDeck removes the deck`() = runTest {
        val archidekt = fakeArchidekt(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 1)))
        })
        val r = repo(archidekt, fakeScryfall { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })
        val id = r.importDeck("https://archidekt.com/decks/1")

        r.deleteDeck(id)

        assertNull(r.observeDeck(id).first())
    }

    @Test
    fun `searchDecks maps owner results to summaries`() = runTest {
        val archidekt = fakeArchidekt(
            list = ArchidektDeckListDto(
                listOf(
                    ArchidektDeckSummaryDto(id = 111, name = "Goblins", size = 100, featured = "http://img/a.jpg"),
                    ArchidektDeckSummaryDto(id = 222, name = "Elves", size = 99, featured = ""),
                ),
            ),
        )
        val r = repo(archidekt, fakeScryfall { ScryfallCollectionResponse() })

        val summaries = r.searchDecks("me")

        assertEquals(2, summaries.size)
        assertEquals("111", summaries[0].archidektId)
        assertEquals("Goblins", summaries[0].name)
        assertEquals(100, summaries[0].cardCount)
        assertEquals("http://img/a.jpg", summaries[0].thumbnailUrl)
        assertNull(summaries[1].thumbnailUrl)
    }
}
