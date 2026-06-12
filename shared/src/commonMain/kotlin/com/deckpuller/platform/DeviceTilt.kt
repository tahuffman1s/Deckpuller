package com.deckpuller.platform

import androidx.compose.runtime.Composable

/** A gentle device-orientation lean, in degrees: [xDeg] = pitch lean, [yDeg] = roll lean. */
data class Tilt(val xDeg: Float, val yDeg: Float)

/**
 * Smoothed device-tilt parallax for the 3D card viewer. The neutral pose recenters toward the
 * current hold (so a steady orientation decays back to flat and only active movement leans the
 * card), the result is clamped to ±[maxDegrees], scaled by [gain] and low-passed by [smoothing].
 * Emits `(0, 0)` on devices without a usable orientation sensor.
 *
 * - [maxDegrees]: max lean applied to the card.
 * - [gain]: fraction of the phone's own tilt the card mirrors.
 * - [recenter]: how fast the neutral pose follows the phone, per sensor sample.
 * - [smoothing]: low-pass factor (0..1); higher = smoother but laggier.
 */
@Composable
expect fun rememberDeviceTilt(
    maxDegrees: Float,
    gain: Float,
    recenter: Float,
    smoothing: Float,
): Tilt
