package com.deckpuller.ui.pull

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.domain.model.DeckCard
import kotlin.math.abs

/** A..Z shown by the fast-scroll rail. */
val ALPHABET: List<Char> = ('A'..'Z').toList()

/** Where the finger is on the rail mid-scrub: the touched letter and its 0..1 vertical fraction. */
data class RailScrub(val letter: Char, val fraction: Float)

/**
 * Maps each starting letter to the LazyColumn item index of the first card with
 * that initial, in display order. The list is now flat (no section headers), so
 * the index is simply the card's position. Non-letter initials collapse to '#'.
 */
fun buildAlphabetIndex(cards: List<DeckCard>): Map<Char, Int> =
    buildAlphabetIndexFromNames(cards.map { it.name })

/** Same as [buildAlphabetIndex] but driven by a plain list of names (collection, cart). */
fun buildAlphabetIndexFromNames(names: List<String>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    names.forEachIndexed { index, name ->
        val initial = name.firstOrNull()?.uppercaseChar()
            ?.takeIf { it.isLetter() } ?: '#'
        if (initial !in map) map[initial] = index
    }
    return map
}

/**
 * Vertical alphabet index on the LEFT screen edge. Touch or drag a letter to jump;
 * letters with no cards are dimmed and ignored. As your finger moves, the letter
 * under it and its neighbours magnify and bulge rightward toward the list — the
 * fisheye scrubber popularised by Niagara Launcher, here with an exaggerated,
 * springy falloff for a more dramatic feel.
 */
@Composable
fun AlphabetRail(
    enabled: Set<Char>,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onScrubChange: (RailScrub?) -> Unit = {},
) {
    val enabledState = rememberUpdatedState(enabled)
    val onSelectState = rememberUpdatedState(onSelect)
    val onScrubChangeState = rememberUpdatedState(onScrubChange)
    // The index of the letter currently under the finger, or null when not dragging.
    var activeIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
            .padding(start = 4.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    var last: Char? = null
                    fun handle(y: Float) {
                        val h = size.height
                        if (h <= 0) return
                        val fraction = (y / h).coerceIn(0f, 1f)
                        val i = ((y / h) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
                        activeIndex = i
                        val c = ALPHABET[i]
                        // Report every move so the floating bubble can track the thumb.
                        onScrubChangeState.value(RailScrub(c, fraction))
                        if (c != last) {
                            last = c
                            if (c in enabledState.value) onSelectState.value(c)
                        }
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    handle(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        handle(change.position.y)
                        change.consume()
                    }
                    activeIndex = null
                    onScrubChangeState.value(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ALPHABET.forEachIndexed { i, letter ->
            val active = letter in enabled
            val focused = activeIndex == i
            // Distance from the finger drives an exaggerated fisheye falloff over the
            // nearest several letters for a dramatic bulge.
            val distance = activeIndex?.let { abs(it - i) } ?: Int.MAX_VALUE
            val targetScale = when {
                activeIndex == null -> 1f
                distance == 0 -> 3.0f
                distance == 1 -> 2.1f
                distance == 2 -> 1.55f
                distance == 3 -> 1.25f
                distance == 4 -> 1.08f
                else -> 1f
            }
            // Magnified letters lean right, toward the list (the rail now lives on the
            // left edge), so the active one bulges proudly over the cards.
            val targetShift = when {
                activeIndex == null -> 0f
                distance == 0 -> 40f
                distance == 1 -> 26f
                distance == 2 -> 14f
                distance == 3 -> 6f
                distance == 4 -> 2f
                else -> 0f
            }
            // A bouncy spring makes the magnification snap and overshoot — far livelier
            // than a plain tween.
            val scale by animateFloatAsState(
                targetScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "alphabetScale",
            )
            val shift by animateFloatAsState(
                targetShift,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "alphabetShift",
            )

            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    focused -> MaterialTheme.colorScheme.primary
                    active -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = shift
                    // Grow from the left edge so the rail stays pinned and bulges inward
                    // toward the list on the right.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
        }
    }
}
