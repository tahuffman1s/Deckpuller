package com.deckpuller.data.repository

import com.deckpuller.data.local.CollectionDao
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCollectionRepositoryTest {

    private val dao = mockk<CollectionDao>(relaxed = true)
    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val repo = DefaultCollectionRepository(dao, prefs)

    private val header =
        "Name,Set code,Set name,Collector number,Foil,Quantity,Scryfall ID,Condition,Language,Binder Name"

    @Test
    fun `importCsv replaces rows and stamps freshness`() = runTest {
        val csv = listOf(
            header,
            "Sol Ring,LTC,Commander,472,normal,2,sid-1,near_mint,en,EDH",
            "Bad Row,LTC,Commander,472,normal,notanumber,sid-x,near_mint,en,EDH",
        ).joinToString("\n")

        val captured = slot<List<CollectionCardEntity>>()
        coEvery { dao.replaceAll(capture(captured)) } returns Unit
        coEvery { dao.count() } returns 1

        val result = repo.importCsv(csv, now = 123L)

        assertEquals(CollectionImportResult(imported = 1, skipped = 1), result)
        assertEquals(1, captured.captured.size)
        assertEquals("sol ring", captured.captured.single().nameKey)
        coVerify { prefs.setCollectionImported(123L, 1) }
    }

    @Test
    fun `observeOwnedByName aggregates printings`() = runTest {
        every { dao.observeAll() } returns flowOf(
            listOf(
                CollectionCardEntity(
                    nameKey = "rhystic study", name = "Rhystic Study", setCode = "CMR",
                    setName = "Commander Legends", collectorNumber = "1", scryfallId = "s1",
                    finish = "normal", condition = "nm", language = "en", binderName = "EDH", quantity = 1,
                ),
                CollectionCardEntity(
                    nameKey = "rhystic study", name = "Rhystic Study", setCode = "PCY",
                    setName = "Prophecy", collectorNumber = "2", scryfallId = "s2",
                    finish = "foil", condition = "nm", language = "en", binderName = "Box", quantity = 2,
                ),
            ),
        )

        val map = repo.observeOwnedByName().first()
        assertEquals(3, map["rhystic study"]!!.totalQty)
        assertEquals(2, map["rhystic study"]!!.printings.size)
    }
}
