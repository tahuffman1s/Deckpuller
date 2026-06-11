package com.deckpuller.ui.pull

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A vertically-scrolling list fronted by the left-edge alphabet rail and the same
 * floating letter indicator the pull screen uses. The caller supplies the scrollable
 * area (usually a LazyColumn bound to [listState]) via [list], receiving a modifier to
 * apply to it. [names] must be in the same display order as the list items so the rail
 * jumps land correctly and the bubble shows the right initial while flinging.
 */
@Composable
fun AlphabetIndexedColumn(
    names: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    list: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val alphabetIndex = remember(names) { buildAlphabetIndexFromNames(names) }

    // Floating letter indicator: while scrubbing the rail it tracks the letter and
    // vertical position under the thumb; while the list flings on its own it shows the
    // first visible item's initial, hovering just above centre.
    var scrub by remember { mutableStateOf<RailScrub?>(null) }
    val scrollLetter by remember(names) {
        derivedStateOf {
            names.getOrNull(listState.firstVisibleItemIndex)
                ?.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }
    }
    // After a scrub ends the list keeps settling (a programmatic scroll), which would
    // otherwise flash the centred scroll-bubble. Suppress the scroll-driven bubble from
    // the moment a scrub begins until the list comes fully to rest.
    var suppressScrollBubble by remember { mutableStateOf(false) }
    LaunchedEffect(scrub != null) {
        if (scrub != null) {
            suppressScrollBubble = true
        } else {
            delay(300)
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            suppressScrollBubble = false
        }
    }
    val bubbleLetter = scrub?.letter ?: scrollLetter
    val showLetterBubble = alphabetIndex.isNotEmpty() && (
        scrub != null || (listState.isScrollInProgress && !suppressScrollBubble)
        )
    // Freeze the bubble's letter/position while it's hidden so the fade-out doesn't jump.
    var bubbleScrubbing by remember { mutableStateOf(false) }
    var bubbleFraction by remember { mutableStateOf(0.28f) }
    var renderedLetter by remember { mutableStateOf(bubbleLetter) }
    LaunchedEffect(showLetterBubble, scrub, bubbleLetter) {
        if (showLetterBubble) {
            bubbleScrubbing = scrub != null
            bubbleFraction = scrub?.fraction ?: 0.28f
            renderedLetter = bubbleLetter
        }
    }
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val areaHeightPx = constraints.maxHeight
        val areaWidthPx = constraints.maxWidth
        val bubbleSizePx = with(density) { 64.dp.toPx() }
        Row(modifier = Modifier.fillMaxSize()) {
            if (alphabetIndex.isNotEmpty()) {
                AlphabetRail(
                    enabled = alphabetIndex.keys,
                    onSelect = { letter ->
                        alphabetIndex[letter]?.let { index ->
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                    onScrubChange = { scrub = it },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            list(Modifier.weight(1f).fillMaxHeight())
        }
        LetterBubbleOverlay(
            visible = showLetterBubble,
            letter = renderedLetter,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    // While scrubbing, sit just inside the left rail and track the thumb;
                    // while flinging, centre over the list and hover just above the middle.
                    val xPx = if (bubbleScrubbing) {
                        with(density) { 48.dp.toPx() }
                    } else {
                        (areaWidthPx - bubbleSizePx) / 2f
                    }
                    val yPx = (bubbleFraction * areaHeightPx - bubbleSizePx / 2f)
                        .toInt()
                        .coerceIn(0, (areaHeightPx - bubbleSizePx).toInt().coerceAtLeast(0))
                    IntOffset(xPx.toInt().coerceAtLeast(0), yPx)
                },
        )
    }
}

/** Fades the floating letter bubble in/out. */
@Composable
private fun LetterBubbleOverlay(visible: Boolean, letter: Char, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LetterBubble(letter)
    }
}

/** Big circular letter that floats over the list while scrolling or scrubbing the rail. */
@Composable
private fun LetterBubble(letter: Char) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
