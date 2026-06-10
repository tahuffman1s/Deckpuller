package com.deckpuller.ui.importdeck

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tapping import passes the entered url`() {
        var imported: String? = null
        rule.setContent {
            ImportScreen(state = ImportUiState.Idle, onImport = { imported = it })
        }

        rule.onNodeWithText("Archidekt deck URL").performTextInput("https://archidekt.com/decks/5")
        rule.onNodeWithText("Import").assertIsEnabled()
        rule.onNodeWithText("Import").performClick()

        assertEquals("https://archidekt.com/decks/5", imported)
    }

    @Test
    fun `error message is shown`() {
        rule.setContent {
            ImportScreen(state = ImportUiState.Error("Bad URL"), onImport = {})
        }
        rule.onNodeWithText("Bad URL").assertExists()
    }
}
