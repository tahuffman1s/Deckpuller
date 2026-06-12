package com.deckpuller.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * iOS haptics via UIKit feedback generators. Feel is verified on-device (no simulator haptics).
 *
 * The pull loop fires haptics seconds apart, and the Taptic Engine drops back to an idle low-power
 * state between events — so an un-warmed `impactOccurred()` produces *no* tap at all. Apple's fix
 * is to call `prepare()` to wake the engine ahead of the next event; we prime each generator on
 * creation and re-prime it right after firing, keeping the engine warm for the following tap.
 */
private class IosHaptics : Haptics {
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val rigid = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val notify = UINotificationFeedbackGenerator()

    init {
        light.prepare()
        rigid.prepare()
        notify.prepare()
    }

    override fun pulled() {
        light.impactOccurred()
        light.prepare()
    }

    override fun completed() {
        notify.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        notify.prepare()
    }

    override fun removed() {
        light.impactOccurred()
        light.prepare()
    }

    override fun faceFlipTick() {
        rigid.impactOccurred()
        rigid.prepare()
    }
}

@Composable
actual fun rememberHaptics(): Haptics = remember { IosHaptics() }
