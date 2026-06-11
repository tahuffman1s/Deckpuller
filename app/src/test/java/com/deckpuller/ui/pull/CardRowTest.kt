package com.deckpuller.ui.pull

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun card(pulled: Int, required: Int, category: String = "") = DeckCard(
        id = 1, scryfallId = "uid", name = "Sol Ring", typeLine = "Artifact",
        imageUrl = null, requiredQty = required, pulledQty = pulled, category = category,
    )

    @Test
    fun `shows the archidekt category instead of the type line`() {
        rule.setContent {
            CardRow(card(pulled = 0, required = 1, category = "Ramp"), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithText("Ramp").assertExists()
    }

    @Test
    fun `tapping the row increments`() {
        var incremented = false
        rule.setContent {
            CardRow(card(pulled = 0, required = 1), onIncrement = { incremented = true }, onDecrement = {})
        }
        rule.onNodeWithText("Sol Ring").performClick()
        assertEquals(true, incremented)
    }

    @Test
    fun `tapping the image triggers onImageClick and not increment`() {
        var imageClicked = false
        var incremented = false
        rule.setContent {
            CardRow(
                card(pulled = 0, required = 1),
                onIncrement = { incremented = true },
                onDecrement = {},
                onImageClick = { imageClicked = true },
            )
        }
        rule.onNodeWithContentDescription("Sol Ring").performClick()
        assertEquals(true, imageClicked)
        assertEquals(false, incremented)
    }

    @Test
    fun `decrement button is disabled at zero`() {
        rule.setContent {
            CardRow(card(pulled = 0, required = 4), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithContentDescription("Decrement Sol Ring").assertIsNotEnabled()
    }

    @Test
    fun `shows a progress meter for pulled over required`() {
        rule.setContent {
            CardRow(card(pulled = 2, required = 4), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithContentDescription("2 of 4 pulled").assertExists()
    }

    @Test
    fun `increment button is disabled when complete`() {
        rule.setContent {
            CardRow(card(pulled = 4, required = 4), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithContentDescription("Increment Sol Ring").assertIsNotEnabled()
    }

    @Test
    fun `shows owned icon when fully owned and collection present`() {
        val c = card(pulled = 0, required = 1).copy(ownedQty = 2)
        rule.setContent { CardRow(card = c, collectionPresent = true, onIncrement = {}, onDecrement = {}) }
        rule.onNodeWithContentDescription("Owned").assertExists()
    }

    @Test
    fun `shows missing icon when not owned and collection present`() {
        val c = card(pulled = 0, required = 1).copy(ownedQty = 0)
        rule.setContent { CardRow(card = c, collectionPresent = true, onIncrement = {}, onDecrement = {}) }
        rule.onNodeWithContentDescription("Missing").assertExists()
    }

    @Test
    fun `hides ownership icon when no collection imported`() {
        val c = card(pulled = 0, required = 1).copy(ownedQty = 0)
        rule.setContent { CardRow(card = c, collectionPresent = false, onIncrement = {}, onDecrement = {}) }
        rule.onNodeWithContentDescription("Missing").assertDoesNotExist()
    }
}
