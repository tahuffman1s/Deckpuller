package com.deckpuller.ui.pull

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HOLD_DELAY_MS = 350L
private const val HOLD_REPEAT_MS = 140L

@Composable
fun CardRow(
    card: DeckCard,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    onImageClick: (DeckCard) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Read the latest card/callback inside the long-running gesture coroutine so a
    // press-and-hold keeps (de)incrementing from the current count, not a stale snapshot.
    val currentCard by rememberUpdatedState(card)
    val incrementNow by rememberUpdatedState(onIncrement)
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = true)
                    var repeated = false
                    val holdJob = scope.launch {
                        delay(HOLD_DELAY_MS)
                        repeated = true
                        while (isActive) {
                            incrementNow(currentCard)
                            delay(HOLD_REPEAT_MS)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    if (up != null && !repeated) incrementNow(currentCard)
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (card.isComplete) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumbnailPlaceholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            placeholder = thumbnailPlaceholder,
            error = thumbnailPlaceholder,
            fallback = thumbnailPlaceholder,
            modifier = Modifier
                .size(width = 46.dp, height = 64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onImageClick(card) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (card.isComplete) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = subtitleOf(card)
            if (subtitle.isNotBlank() && subtitle != "Unknown") {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Progress meter replaces the textual count.
            LinearProgressIndicator(
                progress = {
                    if (card.requiredQty == 0) 0f
                    else card.pulledQty.toFloat() / card.requiredQty
                },
                color = if (card.isComplete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        contentDescription = "${card.pulledQty} of ${card.requiredQty} pulled"
                    },
            )
        }
        // Hold − to repeat-decrement, hold + to repeat-increment (tap = one step).
        HoldRepeatButton(
            enabled = card.pulledQty > 0,
            description = "Decrement ${card.name}",
            onAction = { onDecrement(card) },
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        HoldRepeatButton(
            enabled = card.pulledQty < card.requiredQty,
            description = "Increment ${card.name}",
            onAction = { onIncrement(card) },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

/**
 * Circular tonal button that fires once on tap and repeats while held — the press is
 * consumed so the row's own hold-to-increment gesture doesn't also fire.
 */
@Composable
private fun HoldRepeatButton(
    enabled: Boolean,
    description: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    val actionNow by rememberUpdatedState(onAction)
    val enabledNow by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()

    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .size(40.dp)
            .semantics {
                this.contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            down.consume()
                            var repeated = false
                            val holdJob = scope.launch {
                                delay(HOLD_DELAY_MS)
                                repeated = true
                                while (isActive) {
                                    if (enabledNow) actionNow()
                                    delay(HOLD_REPEAT_MS)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            up?.consume()
                            if (up != null && !repeated) actionNow()
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
