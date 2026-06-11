package com.deckpuller.ui.importdeck

import app.cash.turbine.test
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

class ImportViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(
        val importResult: () -> Long = { 9L },
        val summaries: List<DeckSummary> = emptyList(),
    ) : DeckRepository {
        override fun observeDecks(): Flow<List<DeckWithCards>> = flowOf(emptyList())
        override fun observeDeck(id: Long): Flow<Deck?> = flowOf(null)
        override suspend fun importDeck(url: String): Long = importResult()
        override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long = importResult()
        override suspend fun refreshDeck(deckId: Long) {}
        override suspend fun resetProgress(deckId: Long) {}
        override suspend fun deleteDeck(deckId: Long) {}
        override suspend fun setPulled(cardId: Long, pulled: Int) {}
        override suspend fun searchDecks(username: String): List<DeckSummary> = summaries
        override suspend fun colorIdentity(scryfallId: String): List<String> = emptyList()
        override suspend fun prices(scryfallIds: List<String>): Map<String, Double?> = emptyMap()
    }

    private fun prefs(name: String? = null): UserPreferences = mockk(relaxed = true) {
        coEvery { username } returns flowOf(name)
    }

    @Test
    fun `successful import emits Imported with the new deck id`() = runTest {
        val vm = ImportViewModel(FakeRepo(importResult = { 42L }), prefs())
        vm.import("https://archidekt.com/decks/1")
        vm.state.test {
            assertEquals(ImportUiState.Imported(42L), awaitItem())
        }
    }

    @Test
    fun `invalid url emits Error`() = runTest {
        val repo = object : DeckRepository by FakeRepo() {
            override suspend fun importDeck(url: String): Long = throw InvalidDeckUrlException()
        }
        val vm = ImportViewModel(repo, prefs())
        vm.import("nope")
        vm.state.test {
            assertTrue(awaitItem() is ImportUiState.Error)
        }
    }

    @Test
    fun `findMyDecks populates results from the repository`() = runTest {
        val vm = ImportViewModel(
            FakeRepo(summaries = listOf(DeckSummary("111", "Goblins", 100, null))),
            prefs(),
        )
        vm.findMyDecks("me")
        vm.results.test {
            val r = awaitItem()
            assertEquals(1, r.size)
            assertEquals("Goblins", r[0].name)
        }
    }
}
