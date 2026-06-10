package com.deckpuller.ui.importdeck

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.deckpuller.domain.model.DeckSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AddDeckScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `import button forwards the typed url`() {
        var url: String? = null
        rule.setContent {
            AddDeckScreen(
                state = ImportUiState.Idle, results = emptyList(), savedUsername = null,
                onImportUrl = { url = it }, onFindMyDecks = {}, onPickDeck = {}, onBack = {},
            )
        }
        rule.onNodeWithText("Archidekt deck URL").performTextInput("https://archidekt.com/decks/1")
        rule.onNodeWithText("Import").performClick()
        assertEquals("https://archidekt.com/decks/1", url)
    }

    @Test
    fun `tapping a browsed deck result imports it`() {
        var picked: DeckSummary? = null
        val summary = DeckSummary("111", "Goblins", 100, null)
        rule.setContent {
            AddDeckScreen(
                state = ImportUiState.Idle, results = listOf(summary), savedUsername = "me",
                onImportUrl = {}, onFindMyDecks = {}, onPickDeck = { picked = it }, onBack = {},
            )
        }
        rule.onNodeWithText("Goblins").assertIsDisplayed()
        rule.onNodeWithText("Goblins").performClick()
        assertEquals(summary, picked)
    }
}
