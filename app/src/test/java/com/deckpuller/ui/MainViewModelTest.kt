package com.deckpuller.ui

import app.cash.turbine.test
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(initial: Deck?) : DeckRepository {
        val deck = MutableStateFlow(initial)
        override fun observeDeck(): Flow<Deck?> = deck
        override suspend fun importDeck(url: String) = Unit
        override suspend fun setPulled(cardId: Long, pulled: Int) = Unit
        override suspend fun clearDeck() { deck.value = null }
    }

    @Test
    fun `hasDeck reflects deck presence`() = runTest {
        val repo = FakeRepo(Deck("Deck", emptyList()))
        val vm = MainViewModel(repo)

        vm.hasDeck.test {
            assertEquals(null, awaitItem())       // initial seed (loading)
            assertEquals(true, awaitItem())        // deck present
            repo.clearDeck()
            assertEquals(false, awaitItem())       // deck gone
            cancelAndIgnoreRemainingEvents()
        }
    }
}
