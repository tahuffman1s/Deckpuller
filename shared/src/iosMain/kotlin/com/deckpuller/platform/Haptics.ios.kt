package com.deckpuller.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/** iOS haptics via UIKit feedback generators. Feel is verified on-device (no simulator haptics). */
private class IosHaptics : Haptics {
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val rigid = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val notify = UINotificationFeedbackGenerator()

    override fun pulled() = light.impactOccurred()
    override fun completed() =
        notify.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    override fun removed() = light.impactOccurred()
    override fun faceFlipTick() = rigid.impactOccurred()
}

@Composable
actual fun rememberHaptics(): Haptics = remember { IosHaptics() }
