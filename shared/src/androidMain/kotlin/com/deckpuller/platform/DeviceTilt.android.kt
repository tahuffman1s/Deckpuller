package com.deckpuller.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android device tilt via the game rotation vector (drift-free and magnetometer-free), falling
 * back to the plain rotation vector, then to a no-op `(0, 0)` on devices without either. The
 * recenter/gain/clamp/low-pass math is identical to the original in-line `CardImageDialog`
 * implementation so the Android feel is unchanged.
 */
@Composable
actual fun rememberDeviceTilt(
    maxDegrees: Float,
    gain: Float,
    recenter: Float,
    smoothing: Float,
): Tilt {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Tilt(0f, 0f)) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // Captured on the first reading so tilt is relative to however you're holding the phone
        // right now, not to some absolute flat-on-a-table pose.
        var basePitch = Float.NaN
        var baseRoll = Float.NaN
        var tx = 0f
        var ty = 0f
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val angles = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, angles)
                val pitchDeg = Math.toDegrees(angles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(angles[2].toDouble()).toFloat()
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

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager?.unregisterListener(listener) }
    }
    return tilt
}
