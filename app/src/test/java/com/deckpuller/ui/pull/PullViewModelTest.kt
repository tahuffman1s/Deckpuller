package com.deckpuller.ui.pull

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.CollectionImportResult
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.domain.model.DeckSummary
import com.deckpuller.domain.model.OwnedInfo
import com.deckpuller.domain.model.OwnedPrinting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PullViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(name: String, required: Int, pulled: Int) =
        DeckCard(id = name.hashCode().toLong(), scryfallId = name, name = name,
            typeLine = "Creature", imageUrl = null, requiredQty = required, pulledQty = pulled)

    private class FakeRepo(deck: Deck) : DeckRepository {
        val flow = MutableStateFlow<Deck?>(deck)
        var resetCalled = false
        var refreshCalled = false
        override fun observeDecks(): Flow<List<DeckWithCards>> = flowOf(emptyList())
        override fun observeDeck(id: Long): Flow<Deck?> = flow
        override suspend fun importDeck(url: String): Long = 0
        override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long = 0
        override suspend fun refreshDeck(deckId: Long) { refreshCalled = true }
        override suspend fun resetProgress(deckId: Long) { resetCalled = true }
        override suspend fun deleteDeck(deckId: Long) {}
        override suspend fun setPulled(cardId: Long, pulled: Int) {}
        override suspend fun searchDecks(username: String): List<DeckSummary> = emptyList()
        override suspend fun colorIdentity(scryfallId: String): List<String> = emptyList()
        override suspend fun prices(scryfallIds: List<String>): Map<String, Double?> = emptyMap()
    }

    private class FakeCollectionRepo(
        private val owned: Map<String, OwnedInfo> = emptyMap(),
    ) : CollectionRepository {
        override fun observeOwnedByName(): Flow<Map<String, OwnedInfo>> = flowOf(owned)
        override fun observeAll(): Flow<List<CollectionCardEntity>> = flowOf(emptyList())
        override val importedAt: Flow<Long?> = flowOf(null)
        override val count: Flow<Int> = flowOf(0)
        override suspend fun importCsv(csv: String, now: Long): CollectionImportResult =
            CollectionImportResult(0, 0)
    }

    private fun vm(repo: DeckRepository, collectionRepo: CollectionRepository = FakeCollectionRepo()) =
        PullViewModel(repo, collectionRepo, SavedStateHandle(mapOf("deckId" to 7L)))

    @Test
    fun `state exposes deck totals from the full deck`() = runTest {
        val deck = Deck("My Deck", listOf(card("Forest", 4, 1), card("Sol Ring", 1, 0)))
        vm(FakeRepo(deck)).state.test {
            val s = awaitItem()!!
            assertEquals("My Deck", s.deckName)
            assertEquals(1, s.pulled)
            assertEquals(5, s.total)
        }
    }

    @Test
    fun `search filters cards but totals stay full`() = runTest {
        val deck = Deck("D", listOf(card("Forest", 4, 1), card("Sol Ring", 1, 0)))
        val model = vm(FakeRepo(deck))
        model.onSearchChange("sol")
        model.state.test {
            val s = awaitItem()!!
            assertEquals(5, s.total)
            val names = s.cards.map { it.name }
            assertEquals(listOf("Sol Ring"), names)
            assertEquals("sol", s.searchQuery)
        }
    }

    @Test
    fun `cards are returned as one flat alphabetical list`() = runTest {
        val deck = Deck("D", listOf(card("Sol Ring", 1, 0), card("Anger", 1, 0), card("forest", 1, 0)))
        vm(FakeRepo(deck)).state.test {
            val s = awaitItem()!!
            assertEquals(listOf("Anger", "forest", "Sol Ring"), s.cards.map { it.name })
        }
    }

    @Test
    fun `reset and refresh delegate to the repository for this deck`() = runTest {
        val repo = FakeRepo(Deck("D", listOf(card("Forest", 1, 0))))
        val model = vm(repo)
        model.reset()
        model.refresh()
        assertEquals(true, repo.resetCalled)
        assertEquals(true, repo.refreshCalled)
    }

    @Test
    fun `marks cards owned from the collection`() = runTest {
        val deck = Deck("D", listOf(card("Sol Ring", 1, 0), card("Forest", 4, 0)))
        val collectionRepo = FakeCollectionRepo(
            mapOf(
                "sol ring" to OwnedInfo(
                    totalQty = 2,
                    printings = listOf(OwnedPrinting("LTC", "normal", 2, "EDH")),
                ),
            ),
        )
        val model = vm(FakeRepo(deck), collectionRepo)
        model.state.test {
            var s = awaitItem()
            while (s == null || s.cards.isEmpty()) s = awaitItem()
            val sol = s.cards.first { it.name.equals("Sol Ring", ignoreCase = true) }
            assertEquals(2, sol.ownedQty)
            assertTrue(sol.isOwned)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
