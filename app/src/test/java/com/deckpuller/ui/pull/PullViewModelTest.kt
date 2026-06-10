package com.deckpuller.ui.pull

import app.cash.turbine.test
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PullViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun card(id: Long, name: String, type: String, required: Int, pulled: Int) =
        DeckCard(id, "uid-$id", name, type, null, required, pulled)

    /** Fake repo backed by a mutable flow; setPulled rewrites the matching card. */
    private class FakeRepo(initial: Deck?) : DeckRepository {
        val deck = MutableStateFlow(initial)
        override fun observeDeck(): Flow<Deck?> = deck
        override suspend fun importDeck(url: String) = Unit
        override suspend fun setPulled(cardId: Long, pulled: Int) {
            deck.update { current ->
                current?.copy(cards = current.cards.map {
                    if (it.id == cardId) it.copy(pulledQty = pulled) else it
                })
            }
        }
        override suspend fun clearDeck() {
            deck.value = null
        }
    }

    @Test
    fun `state groups cards and reports progress`() = runTest {
        val deck = Deck(
            "Deck",
            listOf(
                card(1, "Forest", "Land", required = 4, pulled = 1),
                card(2, "Sol Ring", "Artifact", required = 1, pulled = 0),
            ),
        )
        val vm = PullViewModel(FakeRepo(deck))

        vm.state.test {
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertEquals("Deck", s!!.deckName)
            assertEquals(1, s.pulled)
            assertEquals(5, s.total)
            assertFalse(s.isComplete)
            assertEquals(listOf("Artifact", "Land"), s.groups.map { it.type })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `increment is clamped to required quantity`() = runTest {
        val repo = FakeRepo(Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 1))))
        val vm = PullViewModel(repo)

        vm.increment(card(1, "Sol Ring", "Artifact", required = 1, pulled = 1))

        assertEquals(1, repo.deck.value!!.cards.single().pulledQty)
    }

    @Test
    fun `decrement is clamped to zero`() = runTest {
        val repo = FakeRepo(Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 0))))
        val vm = PullViewModel(repo)

        vm.decrement(card(1, "Sol Ring", "Artifact", required = 1, pulled = 0))

        assertEquals(0, repo.deck.value!!.cards.single().pulledQty)
    }

    @Test
    fun `isComplete is true when every card is fully pulled`() = runTest {
        val deck = Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 1)))
        val vm = PullViewModel(FakeRepo(deck))

        vm.state.test {
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertTrue(s!!.isComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear empties the repository`() = runTest {
        val repo = FakeRepo(Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 1))))
        val vm = PullViewModel(repo)

        vm.clear()
        advanceUntilIdle()

        assertEquals(null, repo.deck.value)
    }
}
