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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    collectionPresent: Boolean = false,
    // Fired with the card's thumbnail bounds (in root coords) on the pull that completes
    // it, so the screen can launch the "fly into the deck" animation.
    onCardCompleted: (DeckCard, Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // Read the latest card/callback inside the long-running gesture coroutine so a
    // press-and-hold keeps (de)incrementing from the current count, not a stale snapshot.
    val currentCard by rememberUpdatedState(card)
    val scope = rememberCoroutineScope()
    val feedback = rememberPullFeedback()
    var thumbBounds by remember { mutableStateOf(Rect.Zero) }

    // Every pull funnels through here: a tick (or a confirm buzz + fly-away on the
    // increment that finishes the card), then the actual state change.
    val pullNow by rememberUpdatedState<(DeckCard) -> Unit> { c ->
        if (!c.isComplete) {
            if (c.pulledQty + 1 >= c.requiredQty) {
                feedback.completed()
                onCardCompleted(c, thumbBounds)
            } else {
                feedback.pulled()
            }
            onIncrement(c)
        }
    }

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
                        // Stop once the card is fully pulled — otherwise the loop keeps
                        // firing no-op increments for the whole hold ("keeps trying to add").
                        while (isActive && !currentCard.isComplete) {
                            pullNow(currentCard)
                            delay(HOLD_REPEAT_MS)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    if (up != null && !repeated && !currentCard.isComplete) pullNow(currentCard)
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (card.isComplete) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumbnailPlaceholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        // Owned-foil cards get a gentle holographic shimmer right in the list.
        val foilShimmer = if (card.isFoil) {
            Modifier.animatedFoilSheen(shape = RoundedCornerShape(8.dp), intensity = 0.5f)
        } else {
            Modifier
        }
        // Once a card is complete it's exploded away — the slot stays (so the row and its
        // − button remain) but the little card vanishes. Hitting − drops it back below
        // complete and the thumbnail returns.
        if (card.isComplete) {
            Box(modifier = Modifier.size(width = 46.dp, height = 64.dp))
        } else {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                placeholder = thumbnailPlaceholder,
                error = thumbnailPlaceholder,
                fallback = thumbnailPlaceholder,
                modifier = Modifier
                    .size(width = 46.dp, height = 64.dp)
                    .onGloballyPositioned { thumbBounds = it.boundsInRoot() }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(foilShimmer)
                    .clickable { onImageClick(card) },
            )
        }
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
                // Drop the trailing stop-indicator dot that M3 draws by default.
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        contentDescription = "${card.pulledQty} of ${card.requiredQty} pulled"
                    },
            )
            if (collectionPresent) {
                OwnershipBadge(card)
            }
        }
        // Hold − to repeat-decrement, hold + to repeat-increment (tap = one step).
        HoldRepeatButton(
            enabled = card.pulledQty > 0,
            description = "Decrement ${card.name}",
            onAction = { feedback.removed(); onDecrement(card) },
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        HoldRepeatButton(
            enabled = card.pulledQty < card.requiredQty,
            description = "Increment ${card.name}",
            onAction = { pullNow(card) },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun OwnershipBadge(card: DeckCard) {
    // A themed checkmark (owned) / cross (missing) tucked under the progress bar on the
    // right, with the total owned count beside it — summed across every printing, no
    // per-set breakdown and no foil markers.
    val tint = if (card.isOwned) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    val count = card.ownedQty.takeIf { it > 0 }?.let { "$it×" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count != null) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (card.isOwned) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (card.isOwned) "Owned" else "Missing",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
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
            // The gesture stays attached even when disabled. Swapping it out for a plain
            // Modifier mid-hold (e.g. when the count hits its bound) cancels the gesture
            // before holdJob.cancel() runs, orphaning the repeat loop on the composition
            // scope so it spins forever. Instead we keep it and guard every action with
            // enabledNow, breaking the loop the moment the bound is reached.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    down.consume()
                    var repeated = false
                    val holdJob = scope.launch {
                        delay(HOLD_DELAY_MS)
                        repeated = true
                        while (isActive && enabledNow) {
                            actionNow()
                            delay(HOLD_REPEAT_MS)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    up?.consume()
                    if (up != null && !repeated && enabledNow) actionNow()
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
