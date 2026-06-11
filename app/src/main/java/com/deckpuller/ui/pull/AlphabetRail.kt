package com.deckpuller.ui.pull

import androidx.compose.animation.core.animateFloatAsState
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

/**
 * Maps each starting letter to the LazyColumn item index of the first card with
 * that initial, in display order. The list is now flat (no section headers), so
 * the index is simply the card's position. Non-letter initials collapse to '#'.
 */
fun buildAlphabetIndex(cards: List<DeckCard>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    cards.forEachIndexed { index, card ->
        val initial = card.name.firstOrNull()?.uppercaseChar()
            ?.takeIf { it.isLetter() } ?: '#'
        if (initial !in map) map[initial] = index
    }
    return map
}

/**
 * Vertical alphabet index on the screen edge. Touch or drag a letter to jump;
 * letters with no cards are dimmed and ignored. As your finger moves, the letter
 * under it and its neighbours magnify and bulge toward the list — the fisheye
 * scrubber popularised by Niagara Launcher.
 */
@Composable
fun AlphabetRail(
    enabled: Set<Char>,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onActiveLetterChange: (Char?) -> Unit = {},
) {
    val enabledState = rememberUpdatedState(enabled)
    val onSelectState = rememberUpdatedState(onSelect)
    val onActiveLetterChangeState = rememberUpdatedState(onActiveLetterChange)
    // The index of the letter currently under the finger, or null when not dragging.
    var activeIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(end = 4.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    var last: Char? = null
                    fun handle(y: Float) {
                        val h = size.height
                        if (h <= 0) return
                        val i = ((y / h) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
                        activeIndex = i
                        val c = ALPHABET[i]
                        if (c != last) {
                            last = c
                            onActiveLetterChangeState.value(c)
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
                    onActiveLetterChangeState.value(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ALPHABET.forEachIndexed { i, letter ->
            val active = letter in enabled
            val focused = activeIndex == i
            // Distance from the finger drives a fisheye falloff over the nearest few letters.
            val distance = activeIndex?.let { abs(it - i) } ?: Int.MAX_VALUE
            val targetScale = when {
                activeIndex == null -> 1f
                distance == 0 -> 2.2f
                distance == 1 -> 1.7f
                distance == 2 -> 1.35f
                distance == 3 -> 1.12f
                else -> 1f
            }
            // Magnified letters lean left, toward the list, so the active one stands proud.
            val targetShift = when {
                activeIndex == null -> 0f
                distance == 0 -> -26f
                distance == 1 -> -16f
                distance == 2 -> -8f
                distance == 3 -> -3f
                else -> 0f
            }
            val scale by animateFloatAsState(targetScale, label = "alphabetScale")
            val shift by animateFloatAsState(targetShift, label = "alphabetShift")

            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelMedium,
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
                    // Grow from the right edge so the rail stays pinned and bulges inward.
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
            )
        }
    }
}
