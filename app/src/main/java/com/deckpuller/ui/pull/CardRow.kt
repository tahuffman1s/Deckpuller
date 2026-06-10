package com.deckpuller.ui.pull

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard

@Composable
fun CardRow(
    card: DeckCard,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onIncrement(card) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (card.isComplete) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Text(
            text = card.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (card.isComplete) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onDecrement(card) },
            enabled = card.pulledQty > 0,
            modifier = Modifier.semantics { contentDescription = "Decrement ${card.name}" },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "${card.pulledQty}/${card.requiredQty}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(48.dp),
        )
    }
}
