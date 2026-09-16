package com.deckpuller.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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
 *
 * [sweep] is a lambda, not a value, on purpose: it is invoked inside the draw phase, so an
 * animating sheen invalidates drawing only. Taking a `Float` here would make every caller
 * re-compose on every animation frame — with a shimmering thumbnail per list row that
 * alone is enough to drop a scrolling list to single-digit frame rates.
 */
fun Modifier.foilSheen(
    sweep: () -> Float,
    shape: Shape = RoundedCornerShape(8.dp),
    intensity: Float = 1f,
): Modifier = this.drawWithCache {
    if (intensity <= 0f || size.minDimension <= 0f) {
        return@drawWithCache onDrawWithContent { drawContent() }
    }

    // Everything below depends only on the size/shape, so it is built once per layout
    // rather than once per frame. The two gradients matter most: a fresh Brush forces a
    // new native shader on every draw, which is the bulk of the old per-frame cost.
    val clip = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
    }
    val diag = hypot(size.width, size.height)
    val dir = Offset(size.width / diag, size.height / diag)
    // A repeated gradient is periodic with period = |end - start|, so shifting the field by
    // exactly one period lands on identical pixels: sweep == 0 draws what sweep == 1 does,
    // which is what makes the looping (Restart) animation seamless with no jump at the wrap.
    val period = diag * 0.85f
    val axis = dir * period

    // Rolling rainbow. HOLO_COLORS starts and ends on the same hue, so tiles abut
    // without a seam.
    val band = Brush.linearGradient(
        colors = HOLO_COLORS,
        start = Offset.Zero,
        end = axis,
        tileMode = TileMode.Repeated,
    )
    val bandAlpha = 0.35f * intensity

    // A crisp specular glint, offset a quarter-tile so it doesn't sit on the band.
    val glint = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.45f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.6f * intensity),
            0.55f to Color.Transparent,
            1f to Color.Transparent,
        ),
        start = Offset.Zero,
        end = axis,
        tileMode = TileMode.Repeated,
    )

    onDrawWithContent {
        drawContent()
        val phase = (sweep() % 1f + 1f) % 1f
        clipPath(clip) {
            // Both brushes are built at phase 0, so the phase is applied by sliding the
            // canvas along the gradient axis instead of rebuilding the gradient. Drawing
            // the rect back at -offset puts it exactly where it started on screen while
            // the (canvas-local) shader moves — same pixels, no allocation.
            drawSheen(band, dir * (phase * period), bandAlpha)
            drawSheen(glint, dir * (((phase + 0.25f) % 1f) * period), 1f)
        }
    }
}

private fun DrawScope.drawSheen(
    brush: Brush,
    offset: Offset,
    alpha: Float,
) {
    translate(offset.x, offset.y) {
        drawRect(
            brush = brush,
            topLeft = -offset,
            size = size,
            alpha = alpha,
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
    // Deliberately NOT `by`: holding the State and handing [foilSheen] a reader keeps the
    // per-frame value read in the draw phase. Unwrapping it here would recompose every
    // composable showing a foil, 120 times a second.
    val sweep: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "foil-sweep",
    )
    val reader: () -> Float = remember(sweep) { { sweep.value } }
    return foilSheen(sweep = reader, shape = shape, intensity = intensity)
}
