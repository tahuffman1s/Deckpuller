package com.deckpuller.ui.pull

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
private const val CARD_COUNT = 18 // cards aloft at once; each is recycled the moment it bursts
private const val CASCADE_DECODE_PX = 256 // decode height — cards render small, so this caps memory
private const val LAUNCH_STAGGER_MS = 130f
private const val RESPAWN_STAGGER_MS = 90f // gap before a burst card relaunches, so they don't sync up
private const val CARD_LIFETIME_MS = 2100f
private const val MAX_BOUNCES = 2
private const val EXPLODE_MS = 480f
private const val PIECE_COLS = 4
private const val PIECE_ROWS = 5

/** One bouncing card in the cascade, plus its burst once it detonates. Recycled via [launch]. */
private class CascadeCard {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var launchAtMs = 0f
    var rotation = 0f
    var rotationVel = 0f
    var seed = 0
    var bitmapIndex = 0
    var bounces = 0
    var exploding = false
    var explodeT = 0f // 0..1
    var dead = false
    var shards: List<Shard>? = null
    var elapsedMs = 0f // latest simulated time, so the renderer can gate on launch

    /** Hidden until its staggered launch time arrives (and not yet detonated). */
    val notYetLaunched: Boolean get() = !exploding && elapsedMs < launchAtMs

    /**
     * (Re)arm this card: fling it out from the commander with a fresh randomized trajectory and
     * the next card's art. Called once at spawn and again every time it bursts, which is what
     * makes the rain a seamless, never-ending loop.
     */
    fun launch(
        originX: Float,
        originY: Float,
        screenW: Float,
        screenH: Float,
        launchAt: Float,
        bmpIndex: Int,
        newSeed: Int,
    ) {
        val rnd = Random(newSeed * 2_654_435_761L + 41)
        val sign = if (rnd.nextBoolean()) 1f else -1f
        x = originX
        y = originY
        vx = sign * screenW * (0.28f + rnd.nextFloat() * 0.32f)
        vy = screenH * (-0.12f + rnd.nextFloat() * 0.22f)
        launchAtMs = launchAt
        rotation = rnd.nextFloat() * 360f
        rotationVel = (rnd.nextFloat() - 0.5f) * 320f
        seed = newSeed
        bitmapIndex = bmpIndex
        bounces = 0
        exploding = false
        explodeT = 0f
        dead = false
        shards = null
    }
}

/** A frozen-position fragment with its own outward velocity (screen-relative) and spin. */
private class Shard(val vx: Float, val vy: Float, val spin: Float)

/**
 * A Windows-95-Solitaire-style winning flourish: a stream of the deck's cards ([imageUrls])
 * spills out from [source] (the commander), arcs down under gravity, ricochets off the floor
 * and walls, then bursts into pieces. Every card that bursts is immediately relaunched with the
 * next card's art, so the stream loops seamlessly — cycling through the whole deck, lands and
 * all — and never stops until the caller unmounts it (the user taps to dismiss). Physics are
 * screen-relative so it reads the same on any device. Renders nothing until the first image loads.
 */
@Composable
fun DeckCompletionCascade(imageUrls: List<String>, source: Rect) {
    if (imageUrls.isEmpty()) return

    val context = LocalContext.current
    // Every distinct card in the deck — lands included — so the loop eventually shows them all.
    val urls = remember(imageUrls) { imageUrls.distinct() }
    // Decoded progressively at the small size the cascade renders, so even a full deck's worth of
    // bitmaps stays cheap. The rain starts as soon as the first card is ready and the pool it
    // cycles through grows as the rest stream in.
    val bitmaps = remember(urls) { mutableStateListOf<ImageBitmap>() }
    LaunchedEffect(urls) {
        bitmaps.clear()
        for (url in urls) {
            context.loadCardBitmap(url, CASCADE_DECODE_PX)?.let { bitmaps.add(it) }
        }
    }
    if (bitmaps.isEmpty()) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        if (screenW <= 0f || screenH <= 0f) return@BoxWithConstraints

        val cardH = screenH * 0.12f
        val cardW = cardH * CASCADE_CARD_RATIO
        val gravity = screenH * 2.2f
        val originX = if (source != Rect.Zero) source.center.x - cardW / 2f else screenW / 2f - cardW / 2f
        val originY = if (source != Rect.Zero) source.center.y - cardH / 2f else screenH * 0.06f

        // A fixed pool of cards aloft at once; each is relaunched (see [CascadeCard.launch]) the
        // instant it bursts, so the stream never stops until the overlay is dismissed.
        val cards = remember(screenW, screenH, originX, originY) {
            List(CARD_COUNT) { i ->
                CascadeCard().apply { launch(originX, originY, screenW, screenH, i * LAUNCH_STAGGER_MS, i, i) }
            }
        }
        // Walks through every loaded card across relaunches; `% size` at draw time covers the rest
        // as they stream in. Monotonic so a long-running celebration keeps cycling all the art.
        val nextCard = remember(cards) { mutableIntStateOf(CARD_COUNT) }

        val frame = remember(cards) { mutableIntStateOf(0) }
        LaunchedEffect(cards) {
            var lastNanos = 0L
            var elapsedMs = 0f
            // Loops until the celebration is dismissed — that unmounts this composable and cancels
            // the effect. Every card that bursts is immediately re-armed, so the rain is seamless.
            while (true) {
                withFrameNanos { now ->
                    if (lastNanos != 0L) {
                        val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.04f)
                        elapsedMs += dt * 1000f
                        cards.forEach { card ->
                            if (step(card, dt, elapsedMs, screenW, screenH, cardW, cardH, gravity)) {
                                val n = nextCard.intValue
                                card.launch(originX, originY, screenW, screenH, elapsedMs + RESPAWN_STAGGER_MS, n, n)
                                nextCard.intValue = n + 1
                            }
                        }
                        frame.intValue++
                    }
                    lastNanos = now
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame.intValue // subscribe: redraw every simulated frame
            if (bitmaps.isEmpty()) return@Canvas
            cards.forEach { card ->
                if (card.dead || card.notYetLaunched) return@forEach
                val bmp = bitmaps[card.bitmapIndex % bitmaps.size]
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

/** Advances one card by [dt]. Returns true on the frame it finishes bursting, so the caller can
 * recycle it back into the stream. */
private fun step(
    card: CascadeCard,
    dt: Float,
    elapsedMs: Float,
    screenW: Float,
    screenH: Float,
    cardW: Float,
    cardH: Float,
    gravity: Float,
): Boolean {
    if (card.dead) return false
    card.elapsedMs = elapsedMs
    if (elapsedMs < card.launchAtMs) return false

    if (card.exploding) {
        card.explodeT += dt * 1000f / EXPLODE_MS
        if (card.explodeT >= 1f) {
            card.dead = true
            return true
        }
        return false
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
    return false
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
