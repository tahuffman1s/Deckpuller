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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Held as a State rather than unwrapped with `by`: only the bubble reads it, and
    // reading it here would recompose the whole list on every pointer move along the rail.
    val scrub = remember { mutableStateOf<RailScrub?>(null) }

    BoxWithConstraints(modifier) {
        val areaHeightPx = constraints.maxHeight
        val areaWidthPx = constraints.maxWidth
        Row(modifier = Modifier.fillMaxSize()) {
            if (alphabetIndex.isNotEmpty()) {
                AlphabetRail(
                    enabled = alphabetIndex.keys,
                    onSelect = { letter ->
                        alphabetIndex[letter]?.let { index ->
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                    onScrubChange = { scrub.value = it },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            list(Modifier.weight(1f).fillMaxHeight())
        }
        ScrollLetterBubble(
            names = names,
            listState = listState,
            scrub = scrub,
            hasIndex = alphabetIndex.isNotEmpty(),
            areaWidthPx = areaWidthPx,
            areaHeightPx = areaHeightPx,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/**
 * The floating letter indicator: while scrubbing the rail it tracks the letter and vertical
 * position under the thumb; while the list flings on its own it shows the first visible
 * item's initial, hovering just above centre. [suppressed] hides the scroll-driven bubble
 * for scrolls that shouldn't summon it (a pull-to-refresh, say).
 *
 * Every scroll-driven state read lives in here on purpose. [scrub] arrives as a [State] and
 * the first-visible index is only ever read through a [derivedStateOf], so a fling
 * recomposes this bubble — not the screen hosting the list, whose top bar and list body
 * would otherwise re-run for every row that passes by.
 */
@Composable
fun ScrollLetterBubble(
    names: List<String>,
    listState: LazyListState,
    scrub: State<RailScrub?>,
    hasIndex: Boolean,
    areaWidthPx: Int,
    areaHeightPx: Int,
    modifier: Modifier = Modifier,
    suppressed: Boolean = false,
) {
    val currentScrub = scrub.value
    val scrollLetter by remember(names) {
        derivedStateOf {
            names.getOrNull(listState.firstVisibleItemIndex)
                ?.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }
    }
    // After a scrub ends the list keeps settling (a programmatic scroll), which would
    // otherwise flash the centred scroll-bubble — making it "pop up in the middle" of the
    // rail. Suppress the scroll-driven bubble from the moment a scrub begins until the list
    // comes fully to rest.
    var suppressScrollBubble by remember { mutableStateOf(false) }
    LaunchedEffect(currentScrub != null) {
        if (currentScrub != null) {
            suppressScrollBubble = true
        } else {
            // The rail's jump is launched asynchronously, so the programmatic scroll can
            // begin a frame or two AFTER the finger lifts. Hold the suppression past that
            // window, then wait for the list to come fully to rest — otherwise the bubble
            // flashes in the middle when scrubbing to the very top or bottom.
            delay(300)
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            suppressScrollBubble = false
        }
    }
    val bubbleLetter = currentScrub?.letter ?: scrollLetter
    val visible = hasIndex && (
        currentScrub != null ||
            (listState.isScrollInProgress && !suppressScrollBubble && !suppressed)
        )
    // The bubble's letter and position are frozen while it's hidden, so the fade-out after
    // lifting off the rail doesn't visibly jump to the scroll position/letter — it just
    // fades where it sat. They only refresh while the bubble is actually shown.
    var bubbleScrubbing by remember { mutableStateOf(false) }
    var bubbleFraction by remember { mutableStateOf(0.28f) }
    var renderedLetter by remember { mutableStateOf(bubbleLetter) }
    LaunchedEffect(visible, currentScrub, bubbleLetter) {
        if (visible) {
            bubbleScrubbing = currentScrub != null
            bubbleFraction = currentScrub?.fraction ?: 0.28f
            renderedLetter = bubbleLetter
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.offset {
            // While scrubbing, sit just inside the left rail and track the thumb; while
            // flinging, centre over the list and hover just above the middle. Uses the
            // frozen values so the exit fade doesn't jump.
            val bubbleSizePx = BUBBLE_SIZE.toPx()
            val xPx = if (bubbleScrubbing) {
                SCRUB_BUBBLE_INSET.toPx()
            } else {
                (areaWidthPx - bubbleSizePx) / 2f
            }
            val yPx = (bubbleFraction * areaHeightPx - bubbleSizePx / 2f)
                .toInt()
                .coerceIn(0, (areaHeightPx - bubbleSizePx).toInt().coerceAtLeast(0))
            IntOffset(xPx.toInt().coerceAtLeast(0), yPx)
        },
    ) {
        LetterBubble(renderedLetter)
    }
}

private val BUBBLE_SIZE = 64.dp
private val SCRUB_BUBBLE_INSET = 48.dp

/** Big circular letter that floats over the list while scrolling or scrubbing the rail. */
@Composable
private fun LetterBubble(letter: Char) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(BUBBLE_SIZE),
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
