package com.deckpuller.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test

/**
 * Generates the app's baseline profile. Run on a connected device/emulator with:
 *
 *     ./gradlew :androidApp:generateBaselineProfile
 *
 * The result is written to `androidApp/src/<variant>/generated/baselineProfiles/` and packaged
 * into the release APK automatically by the baselineprofile plugin. Re-run after meaningful UI
 * changes. We launch the app and scroll the first list so startup *and* the scroll/image-decode
 * paths (the ones that felt janky) get AOT-compiled on first run.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.deckpuller") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        val list = UiScrollable(UiSelector().scrollable(true))
        if (list.exists()) {
            // Keep the gesture off the screen edges so system back/gesture nav doesn't intercept.
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) { list.scrollForward() }
            device.waitForIdle()
        }
    }
}
