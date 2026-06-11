package com.deckpuller.ui.pull

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

private const val CASCADE_CARD_RATIO = 0.716f // standard Magic card width/height
private const val CARD_COUNT = 18
private const val MAX_DISTINCT_CARDS = 12 // cap decoded bitmaps to keep memory in check
private const val LAUNCH_STAGGER_MS = 130f
private const val CARD_LIFETIME_MS = 2100f
private const val MAX_BOUNCES = 2
private const val EXPLODE_MS = 480f
private const val PIECE_COLS = 4
private const val PIECE_ROWS = 5

/** One bouncing card in the cascade, plus its burst once it detonates. */
private class CascadeCard(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val launchAtMs: Float,
    var rotation: Float,
    val rotationVel: Float,
    val seed: Int,
    val bitmapIndex: Int,
) {
    var bounces = 0
    var exploding = false
    var explodeT = 0f // 0..1
    var dead = false
    var shards: List<Shard>? = null
    var elapsedMs = 0f // latest simulated time, so the renderer can gate on launch

    /** Hidden until its staggered launch time arrives (and not yet detonated). */
    val notYetLaunched: Boolean get() = !exploding && elapsedMs < launchAtMs
}

/** A frozen-position fragment with its own outward velocity (screen-relative) and spin. */
private class Shard(val vx: Float, val vy: Float, val spin: Float)

/**
 * A Windows-95-Solitaire-style winning flourish: a stream of the deck's cards ([imageUrls])
 * spills out from [source] (the commander), arcs down under gravity, ricochets off the floor
 * and walls, then bursts into pieces. Each falling card shows a different deck card. Physics
 * are screen-relative so it reads the same on any device. Renders nothing until at least one
 * image loads. The caller controls its lifetime by mounting/unmounting it.
 */
@Composable
fun DeckCompletionCascade(imageUrls: List<String>, source: Rect) {
    if (imageUrls.isEmpty()) return

    val context = LocalContext.current
    // A stable, bounded subset of the deck to rain down (keeps decode memory in check).
    val urls = remember(imageUrls) { imageUrls.distinct().take(MAX_DISTINCT_CARDS) }
    var bitmaps by remember(urls) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    LaunchedEffect(urls) {
        bitmaps = urls.mapNotNull { context.loadCardBitmap(it) }
    }
    val loaded = bitmaps
    if (loaded.isEmpty()) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        if (screenW <= 0f || screenH <= 0f) return@BoxWithConstraints

        val cardH = screenH * 0.12f
        val cardW = cardH * CASCADE_CARD_RATIO
        val gravity = screenH * 2.2f

        val cards = remember(loaded, screenW, screenH) {
            val originX = if (source != Rect.Zero) source.center.x - cardW / 2f else screenW / 2f - cardW / 2f
            val originY = if (source != Rect.Zero) source.center.y - cardH / 2f else screenH * 0.06f
            List(CARD_COUNT) { i ->
                val rnd = Random(i * 2_654_435_761L + 41)
                val sign = if (rnd.nextBoolean()) 1f else -1f
                CascadeCard(
                    x = originX,
                    y = originY,
                    vx = sign * screenW * (0.28f + rnd.nextFloat() * 0.32f),
                    vy = screenH * (-0.12f + rnd.nextFloat() * 0.22f),
                    launchAtMs = i * LAUNCH_STAGGER_MS,
                    rotation = rnd.nextFloat() * 360f,
                    rotationVel = (rnd.nextFloat() - 0.5f) * 320f,
                    seed = i,
                    // Cycle through the loaded cards so the stream is varied.
                    bitmapIndex = i % loaded.size,
                )
            }
        }

        val frame = remember(cards) { mutableIntStateOf(0) }
        LaunchedEffect(cards) {
            var lastNanos = 0L
            var elapsedMs = 0f
            // Run until every card has launched and burst out, then stop (the celebration
            // can linger until the user taps, and a dead simulation needn't spin a frame loop).
            while (cards.any { !it.dead }) {
                withFrameNanos { now ->
                    if (lastNanos != 0L) {
                        val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.04f)
                        elapsedMs += dt * 1000f
                        cards.forEach { step(it, dt, elapsedMs, screenW, screenH, cardW, cardH, gravity) }
                        frame.intValue++
                    }
                    lastNanos = now
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame.intValue // subscribe: redraw every simulated frame
            cards.forEach { card ->
                if (card.dead || card.notYetLaunched) return@forEach
                val bmp = loaded[card.bitmapIndex]
                if (!card.exploding) {
                    withTransform({
                        rotate(card.rotation, pivot = Offset(card.x + cardW / 2f, card.y + cardH / 2f))
                    }) {
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bmp.width, bmp.height),
                            dstOffset = IntOffset(card.x.roundToInt(), card.y.roundToInt()),
                            dstSize = IntSize(cardW.roundToInt().coerceAtLeast(1), cardH.roundToInt().coerceAtLeast(1)),
                        )
                    }
                } else {
                    val shards = card.shards ?: return@forEach
                    val t = card.explodeT
                    val alpha = (1f - t).coerceIn(0f, 1f)
                    if (alpha <= 0f) return@forEach
                    val srcPieceW = bmp.width / PIECE_COLS
                    val srcPieceH = bmp.height / PIECE_ROWS
                    val pieceW = cardW / PIECE_COLS
                    val pieceH = cardH / PIECE_ROWS
                    shards.forEachIndexed { idx, shard ->
                        val row = idx / PIECE_COLS
                        val col = idx % PIECE_COLS
                        val left = card.x + col * pieceW + shard.vx * t
                        val top = card.y + row * pieceH + shard.vy * t + gravity * 0.5f * t * t
                        val cx = left + pieceW / 2f
                        val cy = top + pieceH / 2f
                        withTransform({ rotate(shard.spin * t, pivot = Offset(cx, cy)) }) {
                            drawImage(
                                image = bmp,
                                srcOffset = IntOffset(col * srcPieceW, row * srcPieceH),
                                srcSize = IntSize(srcPieceW, srcPieceH),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(pieceW.roundToInt().coerceAtLeast(1), pieceH.roundToInt().coerceAtLeast(1)),
                                alpha = alpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun step(
    card: CascadeCard,
    dt: Float,
    elapsedMs: Float,
    screenW: Float,
    screenH: Float,
    cardW: Float,
    cardH: Float,
    gravity: Float,
) {
    if (card.dead) return
    card.elapsedMs = elapsedMs
    if (elapsedMs < card.launchAtMs) return

    if (card.exploding) {
        card.explodeT += dt * 1000f / EXPLODE_MS
        if (card.explodeT >= 1f) card.dead = true
        return
    }

    card.vy += gravity * dt
    card.x += card.vx * dt
    card.y += card.vy * dt
    card.rotation += card.rotationVel * dt

    // Floor.
    if (card.y + cardH >= screenH && card.vy > 0f) {
        card.y = screenH - cardH
        card.vy = -card.vy * 0.62f
        card.vx *= 0.86f
        card.bounces++
    }
    // Walls.
    if (card.x <= 0f && card.vx < 0f) {
        card.x = 0f
        card.vx = -card.vx * 0.7f
        card.bounces++
    } else if (card.x + cardW >= screenW && card.vx > 0f) {
        card.x = screenW - cardW
        card.vx = -card.vx * 0.7f
        card.bounces++
    }

    val aged = elapsedMs - card.launchAtMs > CARD_LIFETIME_MS
    if (card.bounces >= MAX_BOUNCES || aged) {
        card.exploding = true
        card.shards = buildShards(card.seed, screenH)
    }
}

/** Outward velocities for a card's fragments, radiating from its centre. */
private fun buildShards(seed: Int, screenH: Float): List<Shard> = buildList {
    val cx = (PIECE_COLS - 1) / 2f
    val cy = (PIECE_ROWS - 1) / 2f
    for (row in 0 until PIECE_ROWS) {
        for (col in 0 until PIECE_COLS) {
            val rnd = Random((seed * 31 + row * PIECE_COLS + col) * 2_654_435_761L + 7)
            var dx = col - cx
            var dy = row - cy
            val len = hypot(dx, dy).coerceAtLeast(0.001f)
            dx /= len; dy /= len
            val speed = screenH * (0.20f + rnd.nextFloat() * 0.35f)
            add(
                Shard(
                    vx = dx * speed + (rnd.nextFloat() - 0.5f) * screenH * 0.1f,
                    vy = dy * speed - screenH * (0.05f + rnd.nextFloat() * 0.1f),
                    spin = (rnd.nextFloat() - 0.5f) * 900f,
                ),
            )
        }
    }
}
