package com.deckpuller.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.deckpuller.data.local.entity.CURRENT_DECK_ID
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DeckDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.deckDao()
    }

    @After
    fun tearDown() = db.close()

    private fun card(name: String, required: Int, pulled: Int = 0) = CardEntity(
        deckId = CURRENT_DECK_ID,
        scryfallId = name,
        name = name,
        typeLine = "Creature",
        imageUrl = null,
        requiredQty = required,
        pulledQty = pulled,
    )

    @Test
    fun `observeDeck emits null when empty`() = runTest {
        assertNull(dao.observeDeck().first())
    }

    @Test
    fun `replaceDeck stores deck with its cards`() = runTest {
        dao.replaceDeck(
            DeckEntity(name = "Deck A", importedAt = 1L),
            listOf(card("Forest", 4), card("Sol Ring", 1)),
        )

        val stored = dao.observeDeck().first()!!
        assertEquals("Deck A", stored.deck.name)
        assertEquals(2, stored.cards.size)
    }

    @Test
    fun `replaceDeck wipes the previous deck`() = runTest {
        dao.replaceDeck(DeckEntity(name = "Old", importedAt = 1L), listOf(card("Forest", 1)))
        dao.replaceDeck(DeckEntity(name = "New", importedAt = 2L), listOf(card("Island", 1)))

        val stored = dao.observeDeck().first()!!
        assertEquals("New", stored.deck.name)
        assertEquals(1, stored.cards.size)
        assertEquals("Island", stored.cards.single().name)
    }

    @Test
    fun `updatePulled changes the pulled count and emits`() = runTest {
        dao.replaceDeck(DeckEntity(name = "Deck", importedAt = 1L), listOf(card("Forest", 4)))
        val cardId = dao.observeDeck().first()!!.cards.single().id

        dao.observeDeck().test {
            assertEquals(0, awaitItem()!!.cards.single().pulledQty)
            dao.updatePulled(cardId, 3)
            assertEquals(3, awaitItem()!!.cards.single().pulledQty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearDecks removes everything`() = runTest {
        dao.replaceDeck(DeckEntity(name = "Deck", importedAt = 1L), listOf(card("Forest", 1)))
        dao.clearDecks()
        assertNull(dao.observeDeck().first())
    }
}
