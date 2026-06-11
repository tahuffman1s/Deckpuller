package com.deckpuller.ui.pull

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tactile/audible feedback for the pull loop. A solid click (plus the system click sound,
 * which honours the user's touch-sounds setting) on every pull, and a heftier double-tap on
 * the pull that finishes a card. Where the device supports predefined [VibrationEffect]s
 * (API 29+) we drive the vibrator directly for a more satisfying, stronger feel; otherwise we
 * fall back to the platform [View] haptic constants. Either path respects the user's system
 * haptic setting.
 */
class PullFeedback(private val view: View) {
    private val context = view.context

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** The user's "touch feedback" toggle — we stay haptically silent when it's off. */
    private val hapticsEnabled: Boolean
        @Suppress("DEPRECATION") // still the only direct read of this preference pre-API 34
        get() = Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) != 0

    /** Fire a strong predefined effect; returns false if this device/path can't, so we fall back. */
    private fun vibratePredefined(effectId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val v = vibrator ?: return false
        if (!v.hasVibrator() || !hapticsEnabled) return false
        v.vibrate(VibrationEffect.createPredefined(effectId))
        return true
    }

    /** One card incremented (but not yet complete) — a solid, confident click. */
    fun pulled() {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        if (!vibratePredefined(VibrationEffect.EFFECT_CLICK)) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** The increment that brings a card up to its required count — a satisfying double thunk. */
    fun completed() {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        if (!vibratePredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(type)
        }
    }

    /** One card decremented — a light tick so up/down both feel responsive. */
    fun removed() {
        if (!vibratePredefined(VibrationEffect.EFFECT_TICK)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

@Composable
fun rememberPullFeedback(): PullFeedback {
    val view = LocalView.current
    return remember(view) { PullFeedback(view) }
}
