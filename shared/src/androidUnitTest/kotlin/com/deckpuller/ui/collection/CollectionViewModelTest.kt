package com.deckpuller.ui.collection

import app.cash.turbine.test
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.repository.CollectionImportResult
import com.deckpuller.data.repository.CollectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {

    private val repo = mockk<CollectionRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun row(name: String) = CollectionCardEntity(
        nameKey = name.lowercase(), name = name, setCode = "AAA", setName = "Set",
        collectorNumber = "1", scryfallId = "s", finish = "normal", condition = "nm",
        language = "en", binderName = "EDH", quantity = 1,
    )

    @Test
    fun `filters by search query`() = runTest {
        coEvery { repo.observeAll() } returns flowOf(listOf(row("Sol Ring"), row("Rhystic Study")))
        coEvery { repo.importedAt } returns flowOf(123L)
        coEvery { repo.count } returns flowOf(2)
        val vm = CollectionViewModel(repo)

        vm.onSearchChange("sol")
        vm.state.test {
            // Skip emissions until the filtered one arrives.
            var s = awaitItem()
            while (s.cards.size != 1) s = awaitItem()
            assertEquals("Sol Ring", s.cards.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `importCsv success message includes skipped when nonzero`() = runTest {
        coEvery { repo.importCsv("csv", 1L) } returns CollectionImportResult(imported = 10, skipped = 2)
        val vm = CollectionViewModel(repo)
        vm.importCsv("csv", 1L)
        advanceUntilIdle()
        assertEquals("Imported 10 cards · 2 skipped", vm.importMessage.value)
    }

    @Test
    fun `importCsv success message omits skipped when zero`() = runTest {
        coEvery { repo.importCsv("csv", 1L) } returns CollectionImportResult(imported = 5, skipped = 0)
        val vm = CollectionViewModel(repo)
        vm.importCsv("csv", 1L)
        advanceUntilIdle()
        assertEquals("Imported 5 cards", vm.importMessage.value)
    }

    @Test
    fun `importCsv failure message uses class name when message null`() = runTest {
        coEvery { repo.importCsv("csv", 1L) } throws RuntimeException()
        val vm = CollectionViewModel(repo)
        vm.importCsv("csv", 1L)
        advanceUntilIdle()
        assertEquals("Import failed: RuntimeException", vm.importMessage.value)
    }
}
