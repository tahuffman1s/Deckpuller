package com.deckpuller.ui.pull

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
        val w = size.width
        val h = size.height
        // Diagonal rainbow band, mirror-tiled so it wraps seamlessly as it slides.
        val phase = (sweep % 1f + 1f) % 1f
        val travel = w * 1.5f
        val offset = phase * travel - w * 0.25f
        val band = w * 0.9f
        drawRect(
            brush = Brush.linearGradient(
                colors = HOLO_COLORS,
                start = Offset(offset - band, 0f),
                end = Offset(offset, h),
                tileMode = TileMode.Mirror,
            ),
            alpha = 0.35f * intensity,
            blendMode = BlendMode.Plus,
        )
        // A narrow specular glint riding just ahead of the band.
        val glint = ((sweep + 0.15f) % 1f + 1f) % 1f
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    (glint - 0.12f).coerceIn(0f, 1f) to Color.Transparent,
                    glint.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.55f * intensity),
                    (glint + 0.12f).coerceIn(0f, 1f) to Color.Transparent,
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
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
