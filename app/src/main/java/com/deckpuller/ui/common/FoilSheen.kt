package com.deckpuller.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** Classic trading-card holo spectrum — pink → violet → cyan → green → gold, looped. */
private val HOLO_COLORS = listOf(
    Color(0xFFFF2D95),
    Color(0xFF7A5CFF),
    Color(0xFF00E0FF),
    Color(0xFF49FF7E),
    Color(0xFFFFE24A),
    Color(0xFFFF2D95),
)

/**
 * Draws an animated holographic sheen on top of the content it modifies, clipped to
 * [shape]. [sweep] (any float; only its fractional progression matters) slides the
 * rainbow band and the bright specular glint diagonally across the card, so driving it
 * from a clock gives an idle shimmer and driving it from a tilt angle makes the card read
 * as a real foil that catches the light as it turns. [intensity] scales the whole effect
 * (0 = invisible, 1 = full strength).
 */
fun Modifier.foilSheen(
    sweep: Float,
    shape: Shape = RoundedCornerShape(8.dp),
    intensity: Float = 1f,
): Modifier = this.drawWithContent {
    drawContent()
    if (intensity <= 0f || size.minDimension <= 0f) return@drawWithContent

    val outline = shape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { addOutline(outline) }
    clipPath(clip) {
        // Both layers are diagonal gradients tiled with TileMode.Repeated and translated
        // along their own axis by exactly one period over sweep 0→1. A repeated gradient
        // is periodic with period = |end - start|, so shifting by one period lands on an
        // identical field: sweep == 0 draws the same pixels as sweep == 1, which makes the
        // looping (Restart) animation seamless with no jump at the wrap.
        val phase = (sweep % 1f + 1f) % 1f
        val diag = hypot(size.width, size.height)
        val dir = Offset(size.width / diag, size.height / diag)

        // Rolling rainbow. HOLO_COLORS starts and ends on the same hue, so tiles abut
        // without a seam.
        val bandPeriod = diag * 0.85f
        val bandStart = dir * (phase * bandPeriod)
        drawRect(
            brush = Brush.linearGradient(
                colors = HOLO_COLORS,
                start = bandStart,
                end = bandStart + dir * bandPeriod,
                tileMode = TileMode.Repeated,
            ),
            alpha = 0.35f * intensity,
            blendMode = BlendMode.Plus,
        )

        // A crisp specular glint, offset a quarter-tile so it doesn't sit on the band.
        val glintPeriod = diag * 0.85f
        val glintStart = dir * ((phase + 0.25f) * glintPeriod)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.6f * intensity),
                    0.55f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                start = glintStart,
                end = glintStart + dir * glintPeriod,
                tileMode = TileMode.Repeated,
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * A self-animating [foilSheen] that shimmers on a loop — for spots with no tilt input
 * (e.g. list thumbnails). [periodMs] is one full sweep across the card.
 */
@Composable
fun Modifier.animatedFoilSheen(
    shape: Shape = RoundedCornerShape(8.dp),
    intensity: Float = 0.55f,
    periodMs: Int = 3200,
): Modifier {
    val transition = rememberInfiniteTransition(label = "foil")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "foil-sweep",
    )
    return foilSheen(sweep = sweep, shape = shape, intensity = intensity)
}
