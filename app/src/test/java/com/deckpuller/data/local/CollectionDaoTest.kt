package com.deckpuller.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deckpuller.data.local.entity.CollectionCardEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CollectionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.collectionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(nameKey: String, qty: Int, setCode: String = "AAA") = CollectionCardEntity(
        nameKey = nameKey, name = nameKey, setCode = setCode, setName = "Set",
        collectorNumber = "1", scryfallId = "sid-$setCode", finish = "normal",
        condition = "near_mint", language = "en", binderName = "EDH", quantity = qty,
    )

    @Test
    fun `replaceAll wipes prior rows`() = runTest {
        dao.replaceAll(listOf(row("sol ring", 1)))
        dao.replaceAll(listOf(row("rhystic study", 2), row("rhystic study", 1, setCode = "BBB")))
        assertEquals(2, dao.count())
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun `observeOwnedTotals sums quantity across printings`() = runTest {
        dao.replaceAll(
            listOf(
                row("rhystic study", 2, setCode = "AAA"),
                row("rhystic study", 1, setCode = "BBB"),
                row("sol ring", 4),
            ),
        )
        val totals = dao.observeOwnedTotals().first().associate { it.nameKey to it.qty }
        assertEquals(3, totals["rhystic study"])
        assertEquals(4, totals["sol ring"])
    }
}
