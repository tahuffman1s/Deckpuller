package com.deckpuller.ui.pull

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tactile/audible feedback for the pull loop. A light tick (plus the system click
 * sound, which honours the user's touch-sounds setting) on every pull, and a heavier
 * confirm buzz on the pull that finishes a card. Haptics respect the system haptic
 * setting via the platform [View] feedback path.
 */
class PullFeedback(private val view: View) {
    /** One card incremented (but not yet complete). */
    fun pulled() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    /** The increment that brings a card up to its required count. */
    fun completed() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(type)
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    /** One card decremented — a faint tick so up/down both feel responsive. */
    fun removed() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberPullFeedback(): PullFeedback {
    val view = LocalView.current
    return remember(view) { PullFeedback(view) }
}
