package com.deckpuller.ui.decklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckListScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `shows deck name and progress and routes clicks`() {
        var clicked: Long? = null
        rule.setContent {
            DeckListScreen(
                decks = listOf(DeckListItem(id = 5, name = "Goblins", pulled = 2, total = 10)),
                onDeckClick = { clicked = it },
                onAddDeck = {},
                onDeleteDeck = {},
            )
        }
        rule.onNodeWithText("Goblins").assertIsDisplayed()
        rule.onNodeWithText("2 / 10").assertIsDisplayed()
        rule.onNodeWithText("Goblins").performClick()
        assertEquals(5L, clicked)
    }

    @Test
    fun `empty state offers adding a deck`() {
        var added = false
        rule.setContent {
            DeckListScreen(decks = emptyList(), onDeckClick = {}, onAddDeck = { added = true }, onDeleteDeck = {})
        }
        rule.onNodeWithContentDescription("Add deck").performClick()
        assertEquals(true, added)
    }
}
