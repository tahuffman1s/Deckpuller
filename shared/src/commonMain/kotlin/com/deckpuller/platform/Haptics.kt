package com.deckpuller.platform

import androidx.compose.runtime.Composable

/**
 * Tactile feedback for the pull loop and the 3D card viewer. The four events map to the
 * original Android [com.deckpuller.ui.pull.PullFeedback] vocabulary; each platform wires them
 * to its own haptics API (Android `Vibrator`/`View`, iOS `UIFeedbackGenerator`).
 */
interface Haptics {
    /** One card incremented (but not yet complete) — a solid, confident click. */
    fun pulled()

    /** The increment that finishes a card — a satisfying double thunk. */
    fun completed()

    /** One card decremented — a light tick so up/down both feel responsive. */
    fun removed()

    /** Crossing a 90°/270° card-flip boundary in the viewer — a crisp tick. */
    fun faceFlipTick()
}

/** Remembers the platform [Haptics] for the current composition. */
@Composable
expect fun rememberHaptics(): Haptics
