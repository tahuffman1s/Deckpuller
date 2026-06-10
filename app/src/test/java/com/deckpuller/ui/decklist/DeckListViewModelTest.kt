package com.deckpuller.ui.decklist

import app.cash.turbine.test
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
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
import org.junit.Before
import org.junit.Test

class DeckListViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun deckWithCards(id: Long, name: String, vararg pulledOfRequired: Pair<Int, Int>) =
        DeckWithCards(
            deck = DeckEntity(id = id, name = name, archidektId = "$id", sourceUrl = "u", importedAt = id),
            cards = pulledOfRequired.mapIndexed { i, (pulled, required) ->
                CardEntity(id = id * 100 + i, deckId = id, scryfallId = "s$i", name = "c$i",
                    typeLine = "Creature", imageUrl = null, requiredQty = required, pulledQty = pulled)
            },
        )

    private class FakeRepo(decks: List<DeckWithCards>) : DeckRepository {
        val flow = MutableStateFlow(decks)
        var deleted: Long? = null
        override fun observeDecks(): Flow<List<DeckWithCards>> = flow
        override fun observeDeck(id: Long): Flow<Deck?> = flowOf(null)
        override suspend fun importDeck(url: String): Long = 0
        override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long = 0
        override suspend fun refreshDeck(deckId: Long) {}
        override suspend fun resetProgress(deckId: Long) {}
        override suspend fun deleteDeck(deckId: Long) { deleted = deckId }
        override suspend fun setPulled(cardId: Long, pulled: Int) {}
        override suspend fun searchDecks(username: String): List<DeckSummary> = emptyList()
    }

    @Test
    fun `maps decks to list items with progress totals`() = runTest {
        val repo = FakeRepo(listOf(deckWithCards(1, "Goblins", 1 to 4, 0 to 1)))
        DeckListViewModel(repo).items.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items[0].id)
            assertEquals("Goblins", items[0].name)
            assertEquals(1, items[0].pulled)
            assertEquals(5, items[0].total)
        }
    }

    @Test
    fun `delete delegates to repository`() = runTest {
        val repo = FakeRepo(emptyList())
        DeckListViewModel(repo).delete(42L)
        assertEquals(42L, repo.deleted)
    }
}
