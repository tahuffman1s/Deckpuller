package com.deckpuller.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deckpuller.data.update.UpdateInfo
import com.deckpuller.ui.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `shows version and check button fires callback`() {
        var checked = false
        rule.setContent {
            SettingsScreen(
                currentVersion = "1.2.3",
                status = UpdateStatus.Idle,
                onCheck = { checked = true },
                onUpdate = {},
                onBack = {},
            )
        }
        rule.onNodeWithText("1.2.3").assertIsDisplayed()
        rule.onNodeWithText("Check for updates").performClick()
        assertEquals(true, checked)
    }

    @Test
    fun `up-to-date status is shown`() {
        rule.setContent {
            SettingsScreen("1.0.0", UpdateStatus.UpToDate, onCheck = {}, onUpdate = {}, onBack = {})
        }
        rule.onNodeWithText("You're on the latest version.").assertIsDisplayed()
    }

    @Test
    fun `available update offers install and forwards it`() {
        var picked: UpdateInfo? = null
        val info = UpdateInfo("2.0.0", "https://example/app.apk", 10L, "Shiny new things")
        rule.setContent {
            SettingsScreen("1.0.0", UpdateStatus.Available(info), onCheck = {}, onUpdate = { picked = it }, onBack = {})
        }
        rule.onNodeWithText("Update available: 2.0.0").assertIsDisplayed()
        rule.onNodeWithText("Download & install").performClick()
        assertEquals(info, picked)
    }
}
