package com.deckpuller.ui.pull

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    // press-and-hold keeps incrementing from the current count, not a stale snapshot.
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
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = card.category.ifBlank { card.typeLine }
            if (subtitle.isNotBlank() && subtitle != "Unknown") {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalIconButton(
            onClick = { onDecrement(card) },
            enabled = card.pulledQty > 0,
            modifier = Modifier.semantics { contentDescription = "Decrement ${card.name}" },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (card.isComplete) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (card.isComplete) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = "${card.pulledQty}/${card.requiredQty}",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
