package com.deckpuller.ui.pull

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.deckpuller.domain.DeckGrouping
import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PullScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun card(name: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = name, name = name,
        typeLine = "Creature", imageUrl = null, requiredQty = 1, pulledQty = 0,
    )

    private fun state(query: String = "", cards: List<DeckCard>) = PullUiState(
        deckName = "My Deck",
        groups = DeckGrouping.group(if (query.isBlank()) cards else cards.filter { it.name.contains(query, true) }),
        pulled = 0, total = cards.size, searchQuery = query,
    )

    @Test
    fun `reset action shows a confirmation dialog and confirms`() {
        var reset = false
        rule.setContent {
            PullScreen(
                state = state(cards = listOf(card("Forest"))),
                isRefreshing = false,
                onIncrement = {}, onDecrement = {}, onSearchChange = {},
                onRefresh = {}, onReset = { reset = true }, onBack = {}, onAddDeck = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Reset progress").performClick()
        rule.onNodeWithText("Reset").performClick()
        assertEquals(true, reset)
    }

    @Test
    fun `typing in search forwards the query`() {
        var typed: String? = null
        rule.setContent {
            PullScreen(
                state = state(cards = listOf(card("Forest"), card("Mountain"))),
                isRefreshing = false,
                onIncrement = {}, onDecrement = {}, onSearchChange = { typed = it },
                onRefresh = {}, onReset = {}, onBack = {}, onAddDeck = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNodeWithContentDescription("Search field").performTextInput("mount")
        assertEquals("mount", typed)
    }
}
