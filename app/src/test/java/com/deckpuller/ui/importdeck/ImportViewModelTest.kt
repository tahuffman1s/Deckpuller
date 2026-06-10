package com.deckpuller.ui.importdeck

import app.cash.turbine.test
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repo(
        onImport: suspend (String) -> Unit,
    ): DeckRepository = object : DeckRepository {
        override fun observeDeck(): Flow<Deck?> = flowOf(null)
        override suspend fun importDeck(url: String) = onImport(url)
        override suspend fun setPulled(cardId: Long, pulled: Int) = Unit
        override suspend fun clearDeck() = Unit
    }

    @Test
    fun `import shows loading then returns to idle on success`() = runTest {
        val vm = ImportViewModel(repo { /* success */ })

        vm.state.test {
            assertEquals(ImportUiState.Idle, awaitItem())
            vm.import("https://archidekt.com/decks/1")
            assertEquals(ImportUiState.Loading, awaitItem())
            assertEquals(ImportUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid url produces a friendly error`() = runTest {
        val vm = ImportViewModel(repo { throw InvalidDeckUrlException() })

        vm.state.test {
            assertEquals(ImportUiState.Idle, awaitItem())
            vm.import("nope")
            assertEquals(ImportUiState.Loading, awaitItem())
            val error = awaitItem()
            assertTrue(error is ImportUiState.Error)
            assertTrue((error as ImportUiState.Error).message.contains("Archidekt", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network failure produces a generic error`() = runTest {
        val vm = ImportViewModel(repo { throw RuntimeException("boom") })

        vm.state.test {
            assertEquals(ImportUiState.Idle, awaitItem())
            vm.import("https://archidekt.com/decks/1")
            assertEquals(ImportUiState.Loading, awaitItem())
            assertTrue(awaitItem() is ImportUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissError returns to idle`() = runTest {
        val vm = ImportViewModel(repo { throw RuntimeException("boom") })
        vm.import("https://archidekt.com/decks/1")

        vm.dismissError()

        assertEquals(ImportUiState.Idle, vm.state.value)
    }
}
