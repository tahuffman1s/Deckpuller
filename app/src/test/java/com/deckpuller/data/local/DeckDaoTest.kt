package com.deckpuller.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

    private fun deck(name: String, archidektId: String) =
        DeckEntity(name = name, archidektId = archidektId, sourceUrl = "url/$archidektId", importedAt = 1L)

    private fun card(scryfallId: String, required: Int, pulled: Int = 0) = CardEntity(
        deckId = 0, scryfallId = scryfallId, name = scryfallId,
        typeLine = "Creature", imageUrl = null, requiredQty = required, pulledQty = pulled,
    )

    @Test
    fun `insertDeckWithCards stores deck and returns its id`() = runTest {
        val id = dao.insertDeckWithCards(deck("A", "1"), listOf(card("forest", 4), card("sol", 1)))
        val stored = dao.observeDeck(id).first()!!
        assertEquals("A", stored.deck.name)
        assertEquals(2, stored.cards.size)
        assertEquals(id, stored.cards.first().deckId)
    }

    @Test
    fun `observeDecks lists multiple decks`() = runTest {
        dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 1)))
        dao.insertDeckWithCards(deck("B", "2"), listOf(card("y", 1)))
        assertEquals(2, dao.observeDecks().first().size)
    }

    @Test
    fun `deleteDeck removes one deck and cascades its cards`() = runTest {
        val a = dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 1)))
        val b = dao.insertDeckWithCards(deck("B", "2"), listOf(card("y", 1)))
        dao.deleteDeck(a)
        assertNull(dao.observeDeck(a).first())
        assertEquals(1, dao.observeDecks().first().size)
        assertEquals("B", dao.observeDeck(b).first()!!.deck.name)
    }

    @Test
    fun `resetProgress zeroes only the target deck`() = runTest {
        val a = dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 4, pulled = 3)))
        val b = dao.insertDeckWithCards(deck("B", "2"), listOf(card("y", 4, pulled = 2)))
        dao.resetProgress(a)
        assertEquals(0, dao.observeDeck(a).first()!!.cards.single().pulledQty)
        assertEquals(2, dao.observeDeck(b).first()!!.cards.single().pulledQty)
    }

    @Test
    fun `updatePulled changes a single card`() = runTest {
        val id = dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 4)))
        val cardId = dao.observeDeck(id).first()!!.cards.single().id
        dao.updatePulled(cardId, 3)
        assertEquals(3, dao.observeDeck(id).first()!!.cards.single().pulledQty)
    }
}
