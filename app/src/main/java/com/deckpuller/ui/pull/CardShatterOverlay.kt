package com.deckpuller.ui.pull

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/** A card that just got completed, about to explode into pieces. Bounds are in root coords. */
data class ShatteringCard(val imageUrl: String?, val source: Rect)

private const val COLS = 5
private const val ROWS = 7
private const val SHATTER_MS = 820
private const val GRAVITY_PX = 900f

/** One fragment of the shattered card: a slice of the source bitmap with its own trajectory. */
private data class ShatterPiece(
    val srcOffset: IntOffset,
    val srcSize: IntSize,
    val origin: Offset,
    val size: Size,
    val velocity: Offset,
    val spin: Float,
)

/**
 * Bursts [shard]'s artwork into a grid of fragments that fly outward from where the card
 * sat — each piece carries its own speed, spin, gravity arc and fade, so finishing a card
 * detonates it on the spot. Loads the image as a software [ImageBitmap] (so its slices can
 * be drawn with custom transforms) and calls [onFinished] when the burst ends — or
 * immediately if there's no image / unmeasured bounds. Render this in a non-padded,
 * full-screen Box so its coordinates line up with the root-space [ShatteringCard.source].
 */
@Composable
fun CardShatterOverlay(shard: ShatteringCard, onFinished: () -> Unit) {
    val src = shard.source
    if (src == Rect.Zero || src.width <= 0f || shard.imageUrl == null) {
        LaunchedEffect(shard) { onFinished() }
        return
    }

    val context = LocalContext.current
    var bitmap by remember(shard) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(shard) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(shard.imageUrl)
                // Software bitmap so we can re-draw its slices through a custom canvas.
                .allowHardware(false)
                .build(),
        )
        val drawable = result.drawable
        val androidBitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (androidBitmap == null) {
            onFinished()
        } else {
            bitmap = androidBitmap.asImageBitmap()
        }
    }

    val bmp = bitmap ?: return
    val pieces = remember(bmp, src) { buildPieces(bmp, src) }

    val progress = remember(shard) { Animatable(0f) }
    LaunchedEffect(bmp) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = SHATTER_MS, easing = LinearEasing))
        onFinished()
    }
    val p = progress.value
    val spread = LinearOutSlowInEasing.transform(p)
    val gravity = GRAVITY_PX * p * p
    // Hold full opacity through the burst, then fade the debris out.
    val alpha = if (p < 0.55f) 1f else (1f - (p - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Canvas(Modifier.fillMaxSize()) {
        if (alpha <= 0f) return@Canvas
        pieces.forEach { piece ->
            val left = piece.origin.x + piece.velocity.x * spread
            val top = piece.origin.y + piece.velocity.y * spread + gravity
            val centerX = left + piece.size.width / 2f
            val centerY = top + piece.size.height / 2f
            withTransform({ rotate(piece.spin * p, pivot = Offset(centerX, centerY)) }) {
                drawImage(
                    image = bmp,
                    srcOffset = piece.srcOffset,
                    srcSize = piece.srcSize,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(
                        piece.size.width.roundToInt().coerceAtLeast(1),
                        piece.size.height.roundToInt().coerceAtLeast(1),
                    ),
                    alpha = alpha,
                )
            }
        }
    }
}

/** Slices [bmp] into a [COLS]x[ROWS] grid and assigns each piece an outward trajectory. */
private fun buildPieces(bmp: ImageBitmap, src: Rect): List<ShatterPiece> {
    val pieceW = src.width / COLS
    val pieceH = src.height / ROWS
    val srcPieceW = bmp.width / COLS
    val srcPieceH = bmp.height / ROWS
    val center = src.center

    return buildList {
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val rnd = Random((row * COLS + col) * 2_654_435_761L + 17)
                val origin = Offset(src.left + col * pieceW, src.top + row * pieceH)
                val pieceCenter = Offset(origin.x + pieceW / 2f, origin.y + pieceH / 2f)

                var dir = pieceCenter - center
                val len = hypot(dir.x, dir.y)
                dir = if (len < 1f) {
                    // Dead-centre pieces get a random direction so they don't sit still.
                    val a = rnd.nextFloat() * 6.2831855f
                    Offset(kotlin.math.cos(a), kotlin.math.sin(a))
                } else {
                    dir / len
                }

                val speed = 240f + rnd.nextFloat() * 520f
                // Outward shove, a little upward pop, and some per-piece jitter.
                val velocity = Offset(
                    dir.x * speed + (rnd.nextFloat() - 0.5f) * 160f,
                    dir.y * speed - (120f + rnd.nextFloat() * 160f),
                )
                val spin = (rnd.nextFloat() - 0.5f) * 1080f

                add(
                    ShatterPiece(
                        srcOffset = IntOffset(col * srcPieceW, row * srcPieceH),
                        srcSize = IntSize(srcPieceW, srcPieceH),
                        origin = origin,
                        size = Size(pieceW, pieceH),
                        velocity = velocity,
                        spin = spin,
                    ),
                )
            }
        }
    }
}
