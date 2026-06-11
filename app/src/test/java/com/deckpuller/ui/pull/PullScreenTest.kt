package com.deckpuller.ui.pull

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PullScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun card(name: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = name, name = name,
        typeLine = "Creature", imageUrl = null, requiredQty = 1, pulledQty = 0,
    )

    private fun state(query: String = "", cards: List<DeckCard>) = PullUiState(
        deckName = "My Deck",
        cards = (if (query.isBlank()) cards else cards.filter { it.name.contains(query, true) })
            .sortedBy { it.name.lowercase() },
        pulled = 0, total = cards.size, searchQuery = query,
    )

    @Test
    fun `reset action shows a confirmation dialog and confirms`() {
        var reset = false
        rule.setContent {
            PullScreen(
                state = state(cards = listOf(card("Forest"))),
                isRefreshing = false,
                onIncrement = {}, onDecrement = {}, onSearchChange = {}, onFilterChange = {},
                onRefresh = {}, onReset = { reset = true }, onBack = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Actions").performClick() // expand the speed-dial FAB
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
                onIncrement = {}, onDecrement = {}, onSearchChange = { typed = it }, onFilterChange = {},
                onRefresh = {}, onReset = {}, onBack = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Search").performClick() // search button in the top bar
        rule.onNodeWithContentDescription("Search field").performTextInput("mount")
        assertEquals("mount", typed)
    }

    @Test
    fun `filter action lists subtitles and forwards the choice`() {
        var filter: String? = "untouched"
        rule.setContent {
            PullScreen(
                state = state(cards = listOf(card("Forest"))).copy(subtitles = listOf("Ramp", "Removal")),
                isRefreshing = false,
                onIncrement = {}, onDecrement = {}, onSearchChange = {}, onFilterChange = { filter = it },
                onRefresh = {}, onReset = {}, onBack = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Actions").performClick() // expand the speed-dial FAB
        rule.onNodeWithContentDescription("Filter").performClick()
        rule.onNodeWithText("Ramp").performClick()
        assertEquals("Ramp", filter)
    }
}
