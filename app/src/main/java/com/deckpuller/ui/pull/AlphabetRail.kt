package com.deckpuller.ui.pull

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.domain.model.CardGroup

/** A..Z shown by the fast-scroll rail. */
val ALPHABET: List<Char> = ('A'..'Z').toList()

/**
 * Maps each starting letter to the LazyColumn item index of the first card with
 * that initial, in display order. Accounts for one sticky-header item per group.
 * Non-letter initials collapse to '#'.
 */
fun buildAlphabetIndex(groups: List<CardGroup>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    var index = 0
    groups.forEach { group ->
        index++ // sticky header occupies one item slot
        group.cards.forEach { card ->
            val initial = card.name.firstOrNull()?.uppercaseChar()
                ?.takeIf { it.isLetter() } ?: '#'
            if (initial !in map) map[initial] = index
            index++
        }
    }
    return map
}

/**
 * Vertical alphabet index on the screen edge. Touch or drag a letter to jump;
 * letters with no cards are dimmed and ignored (Niagara-launcher style).
 */
@Composable
fun AlphabetRail(
    enabled: Set<Char>,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledState = rememberUpdatedState(enabled)
    val onSelectState = rememberUpdatedState(onSelect)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 4.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    var last: Char? = null
                    fun handle(y: Float) {
                        val h = size.height
                        if (h <= 0) return
                        val i = ((y / h) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
                        val c = ALPHABET[i]
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
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ALPHABET.forEach { letter ->
            val active = letter in enabled
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
    }
}
