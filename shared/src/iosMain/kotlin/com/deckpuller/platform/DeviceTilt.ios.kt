package com.deckpuller.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.PI

/**
 * iOS device tilt via CoreMotion device-motion (gyro+accel fused attitude — the analog of
 * Android's game-rotation-vector). Same recenter/gain/clamp/low-pass math as the Android actual.
 *
 * Note: pitch/roll sign conventions differ between Android `getOrientation` and CoreMotion
 * `attitude`; if the lean direction reads mirrored on device, flip the sign on pitchDeg/rollDeg.
 */
@Composable
actual fun rememberDeviceTilt(
    maxDegrees: Float,
    gain: Float,
    recenter: Float,
    smoothing: Float,
): Tilt {
    var tilt by remember { mutableStateOf(Tilt(0f, 0f)) }
    DisposableEffect(Unit) {
        val manager = CMMotionManager()
        var basePitch = Float.NaN
        var baseRoll = Float.NaN
        var tx = 0f
        var ty = 0f
        if (manager.deviceMotionAvailable) {
            manager.deviceMotionUpdateInterval = 1.0 / 60.0
            manager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, _ ->
                val attitude = motion?.attitude
                if (attitude != null) {
                    val pitchDeg = (attitude.pitch * 180.0 / PI).toFloat()
                    val rollDeg = (attitude.roll * 180.0 / PI).toFloat()
                    if (basePitch.isNaN()) {
                        basePitch = pitchDeg
                        baseRoll = rollDeg
                    } else {
                        basePitch += (pitchDeg - basePitch) * recenter
                        baseRoll += (rollDeg - baseRoll) * recenter
                    }
                    val targetX = ((pitchDeg - basePitch) * gain).coerceIn(-maxDegrees, maxDegrees)
                    val targetY = ((rollDeg - baseRoll) * gain).coerceIn(-maxDegrees, maxDegrees)
                    tx = tx * smoothing + targetX * (1f - smoothing)
                    ty = ty * smoothing + targetY * (1f - smoothing)
                    tilt = Tilt(tx, ty)
                }
            }
        }
        onDispose { manager.stopDeviceMotionUpdates() }
    }
    return tilt
}
