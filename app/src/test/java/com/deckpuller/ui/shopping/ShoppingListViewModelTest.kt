package com.deckpuller.ui.shopping

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.domain.model.OwnedInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {

    private val deckRepo = mockk<DeckRepository>(relaxed = true)
    private val collectionRepo = mockk<CollectionRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(name: String, required: Int, scryfallId: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = scryfallId, name = name,
        typeLine = "x", imageUrl = null, requiredQty = required, pulledQty = 0,
    )

    @Test
    fun `lists only missing cards with need quantity and prices`() = runTest {
        coEvery { deckRepo.observeDeck(1L) } returns flowOf(
            Deck(
                name = "D",
                cards = listOf(card("Sol Ring", 1, "s-sol"), card("Rhystic Study", 1, "s-rhy")),
            ),
        )
        coEvery { collectionRepo.observeOwnedByName() } returns flowOf(
            mapOf("sol ring" to OwnedInfo(totalQty = 1, printings = emptyList())),
        )
        coEvery { deckRepo.prices(listOf("s-rhy")) } returns mapOf("s-rhy" to 30.0)

        val vm = ShoppingListViewModel(deckRepo, collectionRepo, SavedStateHandle(mapOf("deckId" to 1L)))
        vm.state.test {
            var s = awaitItem()
            while (s == null || s.items.isEmpty() || s.items.first().unitPrice == null) s = awaitItem()
            assertEquals(1, s.items.size)
            assertEquals("Rhystic Study", s.items.single().name)
            assertEquals(1, s.items.single().need)
            assertEquals(30.0, s.items.single().unitPrice!!, 0.001)
            assertEquals(30.0, s.totalPrice, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
