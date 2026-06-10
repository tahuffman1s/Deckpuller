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

    private fun card(pulled: Int, required: Int) = DeckCard(
        id = 1, scryfallId = "uid", name = "Sol Ring", typeLine = "Artifact",
        imageUrl = null, requiredQty = required, pulledQty = pulled,
    )

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
    fun `shows pulled over required count`() {
        rule.setContent {
            CardRow(card(pulled = 2, required = 4), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithText("2/4").assertExists()
    }
}
