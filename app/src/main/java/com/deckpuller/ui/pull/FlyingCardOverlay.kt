package com.deckpuller.ui.pull

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/** A card that just got completed, on its way into the deck. Bounds are in root coords. */
data class FlyingCard(val imageUrl: String?, val source: Rect)

/**
 * Animates [flying]'s thumbnail from where it sat in the list up into the commander
 * art ([target]) — a quick arc + shrink + fade so finishing a card feels like dropping
 * it onto the pile. Calls [onFinished] when done (or immediately if either rect is
 * unmeasured, e.g. a deck with no commander). Render this in a non-padded, full-screen
 * Box so its offsets line up with the root-space bounds.
 */
@Composable
fun FlyingCardOverlay(flying: FlyingCard, target: Rect, onFinished: () -> Unit) {
    val src = flying.source
    if (src == Rect.Zero || src.width <= 0f || target == Rect.Zero) {
        LaunchedEffect(flying) { onFinished() }
        return
    }

    val progress = remember(flying) { Animatable(0f) }
    LaunchedEffect(flying) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing))
        onFinished()
    }
    val p = progress.value
    val density = LocalDensity.current

    val left = lerp(src.left, target.left, p)
    val top = lerp(src.top, target.top, p)
    // A gentle upward arc on the way over (peaks at the midpoint).
    val arc = with(density) { 56.dp.toPx() } * 4f * p * (1f - p)
    val scale = lerp(1f, (target.width / src.width).coerceIn(0.05f, 1f), p)
    val alpha = if (p < 0.75f) 1f else (1f - (p - 0.75f) / 0.25f)

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), (top - arc).roundToInt()) }
            .size(with(density) { src.width.toDp() }, with(density) { src.height.toDp() })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                this.alpha = alpha
            },
    ) {
        AsyncImage(
            model = flying.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
        )
    }
}
